package com.xroig.finance.imports.application;

import com.xroig.finance.categorization.domain.PatternMatcher;
import com.xroig.finance.imports.application.ImportResult.RowError;
import com.xroig.finance.imports.application.port.ImportTransactions;
import com.xroig.finance.imports.application.port.ImportTransfers;
import com.xroig.finance.imports.domain.AccountDirectory;
import com.xroig.finance.imports.domain.AccountDirectory.ImportAccount;
import com.xroig.finance.imports.domain.CategoryDirectory;
import com.xroig.finance.imports.domain.CategoryDirectory.ImportCategory;
import com.xroig.finance.imports.domain.ImportRow;
import com.xroig.finance.imports.domain.MovementWriter;
import com.xroig.finance.imports.domain.MovementWriter.ExistingMovement;
import com.xroig.finance.imports.domain.MovementWriter.NewMovement;
import com.xroig.finance.imports.domain.RuleDirectory;
import com.xroig.finance.imports.domain.RuleDirectory.ImportRule;
import com.xroig.finance.imports.domain.TransferWriter;
import com.xroig.finance.imports.domain.TransferWriter.ExistingTransfer;
import com.xroig.finance.imports.domain.TransferWriter.NewTransfer;
import com.xroig.finance.shared.domain.TextNormalizer;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Application service for the imports context. Parses a bank export (CSV/XLSX) into
 * rows, resolves each row into a movement or a transfer — resolving the account by
 * name (or a default), the category by name (creating unknown ones as global) or by
 * matching a categorization rule, with a fallback to "Otros gastos/ingresos" — dedups
 * against what is already stored in the file's date window, and persists the rest by
 * reusing the create use cases of the transactions/transfers contexts (so their
 * aggregate invariants still apply). Valid rows are imported; per-row failures are
 * collected and reported. The resolution order and messages mirror the legacy service.
 */
@Service
public class ImportService implements ImportTransactions, ImportTransfers {

    private final ImportFileReader reader;
    private final AccountDirectory accounts;
    private final CategoryDirectory categories;
    private final RuleDirectory rules;
    private final MovementWriter movements;
    private final TransferWriter transfers;

    public ImportService(ImportFileReader reader, AccountDirectory accounts, CategoryDirectory categories,
                         RuleDirectory rules, MovementWriter movements, TransferWriter transfers) {
        this.reader = reader;
        this.accounts = accounts;
        this.categories = categories;
        this.rules = rules;
        this.movements = movements;
        this.transfers = transfers;
    }

    @Override
    public ImportResult importTransactions(MultipartFile file, Long defaultAccountId) {
        List<ImportAccount> accountList = accounts.all();
        ImportAccount defaultAccount = defaultAccountId == null ? null : accountList.stream()
                .filter(a -> a.id() == defaultAccountId).findFirst().orElse(null);
        List<ImportCategory> catalog = new ArrayList<>(categories.all());
        List<ImportRule> ruleList = rules.all();

        List<ImportRow> rows = reader.read(file);
        List<RowError> errors = new ArrayList<>();
        List<NewMovement> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                parsed.add(toMovement(rows.get(i), accountList, catalog, ruleList, defaultAccount));
            } catch (Exception e) {
                errors.add(new RowError(i + 2, e.getMessage()));
            }
        }

        Map<String, Integer> existing = movementCounts(parsed);
        int imported = 0;
        int duplicated = 0;
        for (NewMovement movement : parsed) {
            if (consume(existing, movementKey(movement.accountId(), movement.date(), movement.type(),
                    movement.amount(), movement.description()))) {
                duplicated++;
            } else {
                movements.create(movement);
                imported++;
            }
        }
        return new ImportResult(imported, duplicated, errors);
    }

    @Override
    public ImportResult importTransfers(MultipartFile file) {
        List<ImportRow> rows = reader.read(file);
        if (!rows.isEmpty()) {
            ImportRow first = rows.get(0);
            boolean hasFrom = first.has("origen") || first.has("desde") || first.has("from");
            boolean hasTo = first.has("destino") || first.has("hasta") || first.has("to");
            if (!hasFrom || !hasTo) {
                throw new ValidationException("El fichero no tiene columnas \"origen\" y \"destino\". "
                        + "Si es un extracto del banco, impórtalo desde la página de Movimientos.");
            }
        }
        List<ImportAccount> accountList = accounts.all();
        List<RowError> errors = new ArrayList<>();
        List<NewTransfer> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                parsed.add(toTransfer(rows.get(i), accountList));
            } catch (Exception e) {
                errors.add(new RowError(i + 2, e.getMessage()));
            }
        }

        Map<String, Integer> existing = transferCounts(parsed);
        int imported = 0;
        int duplicated = 0;
        for (NewTransfer transfer : parsed) {
            if (consume(existing, transferKey(transfer.fromAccountId(), transfer.toAccountId(),
                    transfer.date(), transfer.amount(), transfer.description()))) {
                duplicated++;
            } else {
                transfers.create(transfer);
                imported++;
            }
        }
        return new ImportResult(imported, duplicated, errors);
    }

    // ---- row → movement ----

    private NewMovement toMovement(ImportRow row, List<ImportAccount> accountList,
                                   List<ImportCategory> catalog, List<ImportRule> ruleList,
                                   ImportAccount defaultAccount) {
        LocalDate date = resolveDate(row);
        BigDecimal amount = ImportRow.parseAmount(row.required("importe", "amount"));
        TransactionType type = resolveType(row.value("tipo", "type"), amount);
        String description = buildDescription(row);

        ImportAccount account;
        String accountName = row.value("cuenta", "account");
        if (accountName != null) {
            account = findAccount(accountList, accountName, "Cuenta");
        } else if (defaultAccount != null) {
            account = defaultAccount;
        } else {
            throw new IllegalArgumentException(
                    "Falta la columna \"cuenta\" y no se indicó cuenta por defecto");
        }

        long categoryId;
        String categoryName = row.value("categoria", "category");
        if (categoryName != null) {
            categoryId = resolveCategory(categoryName, type, account.id(), catalog);
        } else {
            Long matched = matchRule(description, type, account.id(), ruleList);
            categoryId = matched != null ? matched : fallbackCategory(type, catalog);
        }
        return new NewMovement(date, amount.abs(), description, type, account.id(), categoryId);
    }

    /**
     * Bank exports put the real operation date in "Más datos" as
     * "Fecha de operación: dd-MM-yyyy"; it takes precedence over the settlement date.
     */
    private LocalDate resolveDate(ImportRow row) {
        String extra = operationDateText(row);
        if (extra != null) {
            return ImportRow.parseDate(extra.substring(extra.indexOf(':') + 1).trim());
        }
        return ImportRow.parseDate(row.required("fecha", "date"));
    }

    private String operationDateText(ImportRow row) {
        String extra = row.value("mas datos", "observaciones");
        return extra != null && TextNormalizer.normalize(extra).startsWith("fecha de operacion")
                && extra.contains(":") ? extra : null;
    }

    /**
     * Bank exports use "movimiento" for the merchant name and "mas datos" for extra
     * detail; a "Fecha de operación: …" entry is consumed as the date, not the text.
     */
    private String buildDescription(ImportRow row) {
        String description = row.value("descripcion", "description", "concepto", "movimiento");
        String extra = row.value("mas datos", "observaciones");
        if (extra != null && TextNormalizer.normalize(extra).startsWith("fecha de operacion")) {
            extra = null;
        }
        if (description == null) {
            return emptyToNull(extra);
        }
        return extra != null ? description + " — " + extra : description;
    }

    private TransactionType resolveType(String raw, BigDecimal amount) {
        if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "ingreso", "income", "i" -> TransactionType.INCOME;
                case "gasto", "expense", "g", "e" -> TransactionType.EXPENSE;
                default -> throw new IllegalArgumentException("Tipo no válido: \"" + raw + "\"");
            };
        }
        return amount.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
    }

    /**
     * Categories are matched by name (case-insensitive) among those usable on the row's
     * account: an account-owned match wins over a global one with the same name. Unknown
     * categories are created as global, on the fly, with the row's type and reused for
     * later rows of the same file.
     */
    private long resolveCategory(String name, TransactionType type, Long accountId,
                                 List<ImportCategory> catalog) {
        String trimmed = name.trim();
        return catalog.stream()
                .filter(c -> c.name().equalsIgnoreCase(trimmed))
                .filter(c -> visibleForAccount(c.accountId(), accountId))
                .min(Comparator.comparingInt(c -> c.global() ? 1 : 0))
                .map(ImportCategory::id)
                .orElseGet(() -> {
                    ImportCategory created = categories.createGlobal(trimmed, type);
                    catalog.add(created);
                    return created.id();
                });
    }

    private long fallbackCategory(TransactionType type, List<ImportCategory> catalog) {
        String name = type == TransactionType.EXPENSE ? "Otros gastos" : "Otros ingresos";
        return resolveCategory(name, type, null, catalog);
    }

    /**
     * First rule whose pattern (substrings separated by "|", compared without case or
     * accents) matches the description and whose category is of the row's type and usable
     * on the row's account (global or owned by it).
     */
    private Long matchRule(String description, TransactionType type, Long accountId,
                           List<ImportRule> ruleList) {
        if (description == null) {
            return null;
        }
        return ruleList.stream()
                .filter(rule -> rule.categoryType() == type)
                .filter(rule -> visibleForAccount(rule.categoryAccountId(), accountId))
                .filter(rule -> PatternMatcher.matches(rule.pattern(), description))
                .map(ImportRule::categoryId)
                .findFirst()
                .orElse(null);
    }

    private boolean visibleForAccount(Long categoryAccountId, Long accountId) {
        return categoryAccountId == null
                || (accountId != null && categoryAccountId.equals(accountId));
    }

    // ---- row → transfer ----

    private NewTransfer toTransfer(ImportRow row, List<ImportAccount> accountList) {
        LocalDate date = ImportRow.parseDate(row.required("fecha", "date"));
        BigDecimal amount = ImportRow.parseAmount(row.required("importe", "amount")).abs();
        String description = emptyToNull(row.value("descripcion", "description", "concepto"));
        ImportAccount from = findAccount(accountList, row.required("origen", "desde", "from"),
                "Cuenta de origen");
        ImportAccount to = findAccount(accountList, row.required("destino", "hasta", "to"),
                "Cuenta de destino");
        if (from.id() == to.id()) {
            throw new IllegalArgumentException("Origen y destino son la misma cuenta");
        }
        return new NewTransfer(date, amount, description, from.id(), to.id());
    }

    private ImportAccount findAccount(List<ImportAccount> accountList, String name, String label) {
        return accountList.stream()
                .filter(a -> a.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        label + " no encontrada: \"" + name + "\""));
    }

    // ---- dedup ----

    /**
     * Counts, per dedup key, how many rows equal to the file's ones already exist in the
     * date range covered by the file. Each match in the file consumes one count, so
     * re-importing a file skips everything while a genuinely new duplicate still gets in.
     */
    private Map<String, Integer> movementCounts(List<NewMovement> parsed) {
        if (parsed.isEmpty()) {
            return Map.of();
        }
        LocalDate min = parsed.stream().map(NewMovement::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = parsed.stream().map(NewMovement::date).max(Comparator.naturalOrder()).orElseThrow();
        Map<String, Integer> counts = new HashMap<>();
        for (ExistingMovement existing : movements.existingBetween(min, max)) {
            counts.merge(movementKey(existing.accountId(), existing.date(), existing.type(),
                    existing.amount(), existing.description()), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> transferCounts(List<NewTransfer> parsed) {
        if (parsed.isEmpty()) {
            return Map.of();
        }
        LocalDate min = parsed.stream().map(NewTransfer::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = parsed.stream().map(NewTransfer::date).max(Comparator.naturalOrder()).orElseThrow();
        Map<String, Integer> counts = new HashMap<>();
        for (ExistingTransfer existing : transfers.existingBetween(min, max)) {
            counts.merge(transferKey(existing.fromAccountId(), existing.toAccountId(), existing.date(),
                    existing.amount(), existing.description()), 1, Integer::sum);
        }
        return counts;
    }

    private boolean consume(Map<String, Integer> existing, String key) {
        Integer count = existing.get(key);
        if (count == null || count == 0) {
            return false;
        }
        existing.put(key, count - 1);
        return true;
    }

    private static String movementKey(long accountId, LocalDate date, TransactionType type,
                                      BigDecimal amount, String description) {
        return accountId + "|" + date + "|" + type + "|"
                + normalizeAmount(amount) + "|" + normalizeText(description);
    }

    private static String transferKey(long fromAccountId, long toAccountId, LocalDate date,
                                      BigDecimal amount, String description) {
        return fromAccountId + "|" + toAccountId + "|" + date + "|"
                + normalizeAmount(amount) + "|" + normalizeText(description);
    }

    private static String normalizeAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String normalizeText(String text) {
        return text == null ? "" : TextNormalizer.normalize(text);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
