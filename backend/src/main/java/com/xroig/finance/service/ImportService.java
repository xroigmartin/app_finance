package com.xroig.finance.service;

import com.xroig.finance.categorization.domain.PatternMatcher;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.dto.ImportDtos.ImportResult;
import com.xroig.finance.dto.ImportDtos.RowError;
import com.xroig.finance.model.*;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Bulk import of transactions and transfers from CSV/XLSX files.
 * Valid rows are saved; invalid rows are skipped and reported.
 *
 * Transactions expect columns: fecha, importe and optionally cuenta,
 * categoria, tipo, descripcion (or movimiento / mas datos, as found in
 * bank exports). Without "tipo", a negative amount means expense and a
 * positive one income. Rows without "cuenta" use the default account
 * given with the upload; rows without "categoria" fall back to
 * "Otros gastos" / "Otros ingresos".
 *
 * Transfers expect columns: fecha, importe, origen, destino
 * (optional: descripcion).
 */
@Service
public class ImportService {

    private final ImportFileParser parser;
    private final TransactionRepository transactionRepository;
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryRuleRepository ruleRepository;

    public ImportService(ImportFileParser parser,
                         TransactionRepository transactionRepository,
                         TransferRepository transferRepository,
                         AccountRepository accountRepository,
                         CategoryRepository categoryRepository,
                         CategoryRuleRepository ruleRepository) {
        this.parser = parser;
        this.transactionRepository = transactionRepository;
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
    }

    public ImportResult importTransactions(MultipartFile file, Long defaultAccountId) {
        Account defaultAccount = defaultAccountId != null
                ? accountRepository.findById(defaultAccountId).orElse(null) : null;
        List<Map<String, String>> rows = parser.parse(file);
        List<RowError> errors = new ArrayList<>();
        List<Transaction> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                parsed.add(toTransaction(rows.get(i), defaultAccount));
            } catch (Exception e) {
                errors.add(new RowError(i + 2, e.getMessage()));
            }
        }
        Map<String, Integer> existing = existingCounts(parsed, Transaction::getDate,
                (from, to) -> transactionRepository.findByDateBetweenOrderByDateDesc(from, to),
                this::transactionKey);
        int imported = 0;
        int duplicated = 0;
        for (Transaction transaction : parsed) {
            if (consumeExisting(existing, transactionKey(transaction))) {
                duplicated++;
            } else {
                transactionRepository.save(transaction);
                imported++;
            }
        }
        return new ImportResult(imported, duplicated, errors);
    }

    public ImportResult importTransfers(MultipartFile file) {
        List<Map<String, String>> rows = parser.parse(file);
        if (!rows.isEmpty()) {
            Map<String, String> first = rows.get(0);
            boolean hasFrom = first.containsKey("origen") || first.containsKey("desde") || first.containsKey("from");
            boolean hasTo = first.containsKey("destino") || first.containsKey("hasta") || first.containsKey("to");
            if (!hasFrom || !hasTo) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "El fichero no tiene columnas \"origen\" y \"destino\". "
                                + "Si es un extracto del banco, impórtalo desde la página de Movimientos.");
            }
        }
        List<RowError> errors = new ArrayList<>();
        List<Transfer> parsed = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                parsed.add(toTransfer(rows.get(i)));
            } catch (Exception e) {
                errors.add(new RowError(i + 2, e.getMessage()));
            }
        }
        Map<String, Integer> existing = existingCounts(parsed, Transfer::getDate,
                transferRepository::findByDateBetween, this::transferKey);
        int imported = 0;
        int duplicated = 0;
        for (Transfer transfer : parsed) {
            if (consumeExisting(existing, transferKey(transfer))) {
                duplicated++;
            } else {
                transferRepository.save(transfer);
                imported++;
            }
        }
        return new ImportResult(imported, duplicated, errors);
    }

    /**
     * Counts, per dedup key, how many rows equal to the file's ones already
     * exist in the date range covered by the file. Each match in the file
     * consumes one count, so re-importing a file skips everything while a
     * genuinely new duplicate (e.g. two identical purchases the same day,
     * present twice in the file but once in the database) still gets in.
     */
    private <T> Map<String, Integer> existingCounts(
            List<T> parsed,
            Function<T, java.time.LocalDate> dateOf,
            java.util.function.BiFunction<java.time.LocalDate, java.time.LocalDate, List<T>> finder,
            Function<T, String> keyOf) {
        if (parsed.isEmpty()) {
            return Map.of();
        }
        java.time.LocalDate min = parsed.stream().map(dateOf).min(java.time.LocalDate::compareTo).orElseThrow();
        java.time.LocalDate max = parsed.stream().map(dateOf).max(java.time.LocalDate::compareTo).orElseThrow();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (T item : finder.apply(min, max)) {
            counts.merge(keyOf.apply(item), 1, Integer::sum);
        }
        return counts;
    }

    private boolean consumeExisting(Map<String, Integer> existing, String key) {
        Integer count = existing.get(key);
        if (count == null || count == 0) {
            return false;
        }
        existing.put(key, count - 1);
        return true;
    }

    private String transactionKey(Transaction t) {
        return t.getAccount().getId() + "|" + t.getDate() + "|" + t.getType() + "|"
                + normalizeAmount(t.getAmount()) + "|" + normalizeText(t.getDescription());
    }

    private String transferKey(Transfer t) {
        return t.getFromAccount().getId() + "|" + t.getToAccount().getId() + "|" + t.getDate()
                + "|" + normalizeAmount(t.getAmount()) + "|" + normalizeText(t.getDescription());
    }

    private String normalizeAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String normalizeText(String text) {
        return text == null ? "" : ImportFileParser.normalizeHeader(text);
    }

    private Transaction toTransaction(Map<String, String> row, Account defaultAccount) {
        Transaction transaction = new Transaction();
        transaction.setDate(resolveDate(row));
        BigDecimal amount = ImportFileParser.parseAmount(required(row, "importe", "amount"));

        TransactionType type = resolveType(value(row, "tipo", "type"), amount);
        transaction.setType(type);
        transaction.setAmount(amount.abs());
        transaction.setDescription(buildDescription(row));

        String accountName = value(row, "cuenta", "account");
        if (accountName != null) {
            transaction.setAccount(findByName(accountRepository.findAll(), Account::getName,
                    accountName, "Cuenta"));
        } else if (defaultAccount != null) {
            transaction.setAccount(defaultAccount);
        } else {
            throw new IllegalArgumentException(
                    "Falta la columna \"cuenta\" y no se indicó cuenta por defecto");
        }

        Account account = transaction.getAccount();
        String categoryName = value(row, "categoria", "category");
        if (categoryName != null) {
            transaction.setCategory(resolveCategory(categoryName, type, account));
        } else {
            Category matched = matchRule(transaction.getDescription(), type, account);
            transaction.setCategory(matched != null ? matched : fallbackCategory(type));
        }
        return transaction;
    }

    /**
     * Bank exports put the real operation date in "Más datos" as
     * "Fecha de operación: dd-MM-yyyy"; it takes precedence over the
     * settlement date in the "fecha" column.
     */
    private java.time.LocalDate resolveDate(Map<String, String> row) {
        String extra = operationDateText(row);
        if (extra != null) {
            String raw = extra.substring(extra.indexOf(':') + 1).trim();
            return ImportFileParser.parseDate(raw);
        }
        return ImportFileParser.parseDate(required(row, "fecha", "date"));
    }

    private String operationDateText(Map<String, String> row) {
        String extra = value(row, "mas datos", "observaciones");
        return extra != null && ImportFileParser.normalizeHeader(extra).startsWith("fecha de operacion")
                && extra.contains(":") ? extra : null;
    }

    /**
     * Bank exports use "movimiento" for the merchant name and "mas datos"
     * for extra detail; "Fecha de operación: …" entries are consumed as
     * the operation date, not as description.
     */
    private String buildDescription(Map<String, String> row) {
        String description = value(row, "descripcion", "description", "concepto", "movimiento");
        String extra = value(row, "mas datos", "observaciones");
        if (extra != null && ImportFileParser.normalizeHeader(extra).startsWith("fecha de operacion")) {
            extra = null;
        }
        if (description == null) {
            return emptyToNull(extra);
        }
        return extra != null ? description + " — " + extra : description;
    }

    /**
     * First rule whose pattern (substrings separated by "|", compared
     * without case or accents) matches the description and whose category
     * is of the row's type and usable on the row's account (global or owned
     * by it).
     */
    private Category matchRule(String description, TransactionType type, Account account) {
        if (description == null) {
            return null;
        }
        return ruleRepository.findAll().stream()
                .filter(rule -> rule.getCategory().getType() == type)
                .filter(rule -> visibleForAccount(rule.getCategory(), account))
                .filter(rule -> PatternMatcher.matches(rule.getPattern(), description))
                .map(CategoryRule::getCategory)
                .findFirst()
                .orElse(null);
    }

    private Category fallbackCategory(TransactionType type) {
        String name = type == TransactionType.EXPENSE ? "Otros gastos" : "Otros ingresos";
        return resolveCategory(name, type, null);
    }

    private boolean visibleForAccount(Category category, Account account) {
        return category.getAccount() == null
                || (account != null && category.getAccount().getId().equals(account.getId()));
    }

    private Transfer toTransfer(Map<String, String> row) {
        Transfer transfer = new Transfer();
        transfer.setDate(ImportFileParser.parseDate(required(row, "fecha", "date")));
        transfer.setAmount(ImportFileParser.parseAmount(required(row, "importe", "amount")).abs());
        transfer.setDescription(emptyToNull(value(row, "descripcion", "description", "concepto")));
        List<Account> accounts = accountRepository.findAll();
        transfer.setFromAccount(findByName(accounts, Account::getName,
                required(row, "origen", "desde", "from"), "Cuenta de origen"));
        transfer.setToAccount(findByName(accounts, Account::getName,
                required(row, "destino", "hasta", "to"), "Cuenta de destino"));
        if (transfer.getFromAccount().getId().equals(transfer.getToAccount().getId())) {
            throw new IllegalArgumentException("Origen y destino son la misma cuenta");
        }
        return transfer;
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
     * Categories are matched by name (case-insensitive) among those usable on
     * the row's account: an account-owned match wins over a global one with
     * the same name. Unknown categories are created as global, on the fly,
     * with the row's transaction type.
     */
    private Category resolveCategory(String name, TransactionType type, Account account) {
        String trimmed = name.trim();
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(trimmed))
                .filter(c -> visibleForAccount(c, account))
                .min(java.util.Comparator.comparing(c -> c.getAccount() == null ? 1 : 0))
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setName(trimmed);
                    category.setType(type);
                    category.setColor("#64748b");
                    return categoryRepository.save(category);
                });
    }

    private <T> T findByName(List<T> items, Function<T, String> nameOf, String name, String label) {
        return items.stream()
                .filter(item -> nameOf.apply(item).equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        label + " no encontrada: \"" + name + "\""));
    }

    private String required(Map<String, String> row, String... keys) {
        String value = value(row, keys);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la columna \"" + keys[0] + "\"");
        }
        return value;
    }

    private String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
