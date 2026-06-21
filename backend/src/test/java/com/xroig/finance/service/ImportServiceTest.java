package com.xroig.finance.service;

import com.xroig.finance.dto.ImportDtos.ImportResult;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.CategoryRule;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.model.Transfer;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.CategoryRuleRepository;
import com.xroig.finance.repository.TransactionRepository;
import com.xroig.finance.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.xroig.finance.Fixtures.account;
import static com.xroig.finance.Fixtures.category;
import static com.xroig.finance.Fixtures.eur;
import static com.xroig.finance.Fixtures.expense;
import static com.xroig.finance.Fixtures.rule;
import static com.xroig.finance.Fixtures.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service tests for {@link ImportService} with a mocked parser and repositories
 * (stage E2b): resolveType / resolveCategory / resolveDate, buildDescription,
 * rule matching and fallback, per-row errors, dedup and transfers.
 *
 * <p>The mocked world is a small mutable set of accounts/categories/rules that
 * tests extend in place; {@code parser.parse} is stubbed per test to feed rows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    @Mock private ImportFileParser parser;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryRuleRepository ruleRepository;

    private ImportService service;

    private final MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);

    private final Account corriente = account(1, "Corriente");
    private final Account ahorro = account(2, "Ahorro");
    private final Category otrosGastos = category(100, "Otros gastos", TransactionType.EXPENSE);
    private final Category otrosIngresos = category(101, "Otros ingresos", TransactionType.INCOME);

    private final List<Account> accounts = new ArrayList<>(List.of(corriente, ahorro));
    private final List<Category> categories = new ArrayList<>(List.of(otrosGastos, otrosIngresos));
    private final List<CategoryRule> rules = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ImportService(parser, transactionRepository, transferRepository,
                accountRepository, categoryRepository, ruleRepository);
        when(accountRepository.findAll()).thenReturn(accounts);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(corriente));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(ahorro));
        when(categoryRepository.findAll()).thenReturn(categories);
        when(ruleRepository.findAll()).thenReturn(rules);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                .thenReturn(List.of());
        when(transferRepository.findByDateBetween(any(), any())).thenReturn(List.of());
    }

    // ---- helpers ----

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @SafeVarargs
    private void rows(Map<String, String>... rows) {
        when(parser.parse(file)).thenReturn(List.of(rows));
    }

    private List<Transaction> capturedTransactions() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private Transaction importSingle(Map<String, String> row, Long defaultAccountId) {
        rows(row);
        service.importTransactions(file, defaultAccountId);
        List<Transaction> saved = capturedTransactions();
        assertThat(saved).hasSize(1);
        return saved.get(0);
    }

    // ---------- resolveType ----------

    @Test
    void resolveType_readsExplicitTypeVariants() {
        rows(row("fecha", "01/01/2024", "importe", "10", "tipo", "gasto"),
             row("fecha", "01/01/2024", "importe", "10", "tipo", "ingreso"),
             row("fecha", "01/01/2024", "importe", "10", "tipo", "income"),
             row("fecha", "01/01/2024", "importe", "10", "tipo", "e"));

        service.importTransactions(file, 1L);

        assertThat(capturedTransactions()).extracting(Transaction::getType).containsExactly(
                TransactionType.EXPENSE, TransactionType.INCOME,
                TransactionType.INCOME, TransactionType.EXPENSE);
    }

    @Test
    void resolveType_invalidTypeReportsRowError() {
        rows(row("fecha", "01/01/2024", "importe", "10", "tipo", "raro"));

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.imported()).isZero();
        assertThat(result.errors()).singleElement()
                .satisfies(e -> {
                    assertThat(e.row()).isEqualTo(2);
                    assertThat(e.message()).contains("Tipo no válido");
                });
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void resolveType_infersFromSignWhenNoType() {
        rows(row("fecha", "01/01/2024", "importe", "-50"),
             row("fecha", "01/01/2024", "importe", "50"));

        service.importTransactions(file, 1L);

        assertThat(capturedTransactions()).extracting(Transaction::getType)
                .containsExactly(TransactionType.EXPENSE, TransactionType.INCOME);
    }

    @Test
    void amountIsStoredAsAbsoluteValue() {
        Transaction t = importSingle(row("fecha", "01/01/2024", "importe", "-1.234,56"), 1L);
        assertThat(t.getAmount()).isEqualByComparingTo("1234.56");
    }

    // ---------- resolveCategory ----------

    @Test
    void resolveCategory_accountOwnedWinsOverGlobal() {
        Category globalComida = category(200, "Comida", TransactionType.EXPENSE);
        Category cuentaComida = category(201, "Comida", TransactionType.EXPENSE, corriente);
        categories.add(globalComida);
        categories.add(cuentaComida);

        Transaction t = importSingle(
                row("fecha", "01/01/2024", "importe", "-10", "categoria", "Comida"), 1L);

        assertThat(t.getCategory()).isSameAs(cuentaComida);
    }

    @Test
    void resolveCategory_createsUnknownCategoryAsGlobal() {
        Transaction t = importSingle(
                row("fecha", "01/01/2024", "importe", "-10", "categoria", "Viajes"), 1L);

        verify(categoryRepository).save(any(Category.class));
        assertThat(t.getCategory().getName()).isEqualTo("Viajes");
        assertThat(t.getCategory().getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(t.getCategory().getAccount()).isNull();
    }

    // ---------- rule matching / fallback ----------

    @Test
    void matchRule_categorizesByDescriptionWhenNoCategoryColumn() {
        Category supermercado = category(300, "Supermercado", TransactionType.EXPENSE);
        categories.add(supermercado);
        rules.add(rule(1, "mercadona|lidl", supermercado));

        Transaction t = importSingle(
                row("fecha", "01/01/2024", "importe", "-30", "descripcion", "Compra en MERCADONA"), 1L);

        assertThat(t.getCategory()).isSameAs(supermercado);
    }

    @Test
    void matchRule_fallsBackToOtrosWhenNoRuleMatches() {
        Transaction t = importSingle(
                row("fecha", "01/01/2024", "importe", "-30", "descripcion", "Algo raro"), 1L);

        assertThat(t.getCategory()).isSameAs(otrosGastos);
    }

    @Test
    void matchRule_ignoresRuleOfDifferentTypeOrAccount() {
        // Rule category is INCOME but the row is an expense → must not match.
        Category nominaIncome = category(301, "Nómina", TransactionType.INCOME);
        categories.add(nominaIncome);
        rules.add(rule(1, "nomina", nominaIncome));

        Transaction t = importSingle(
                row("fecha", "01/01/2024", "importe", "-30", "descripcion", "Pago NOMINA"), 1L);

        assertThat(t.getCategory()).isSameAs(otrosGastos);
    }

    // ---------- resolveDate ----------

    @Test
    void resolveDate_operationDateInExtraOverridesDateColumn() {
        Transaction t = importSingle(row(
                "fecha", "20/01/2024", "importe", "-10",
                "mas datos", "Fecha de operación: 15-01-2024"), 1L);

        assertThat(t.getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void resolveDate_usesDateColumnWhenNoOperationDate() {
        Transaction t = importSingle(row("fecha", "20/01/2024", "importe", "-10"), 1L);
        assertThat(t.getDate()).isEqualTo(LocalDate.of(2024, 1, 20));
    }

    // ---------- buildDescription ----------

    @Test
    void buildDescription_joinsDescriptionAndExtra() {
        Transaction t = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10",
                "descripcion", "Compra", "mas datos", "Tienda X"), 1L);

        assertThat(t.getDescription()).isEqualTo("Compra — Tienda X");
    }

    @Test
    void buildDescription_dropsOperationDateExtra() {
        Transaction t = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10",
                "descripcion", "Compra", "mas datos", "Fecha de operación: 15-01-2024"), 1L);

        assertThat(t.getDescription()).isEqualTo("Compra");
    }

    @Test
    void buildDescription_usesMovimientoWhenNoDescription() {
        Transaction t = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10", "movimiento", "Mercadona"), 1L);

        assertThat(t.getDescription()).isEqualTo("Mercadona");
    }

    // ---------- account resolution ----------

    @Test
    void account_namedColumnOverridesDefault() {
        Transaction t = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10", "cuenta", "Ahorro"), 1L);

        assertThat(t.getAccount()).isSameAs(ahorro);
    }

    @Test
    void account_unknownNameReportsRowError() {
        rows(row("fecha", "01/01/2024", "importe", "-10", "cuenta", "Inexistente"));

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("Cuenta no encontrada"));
    }

    @Test
    void account_missingWithNoDefaultReportsRowError() {
        rows(row("fecha", "01/01/2024", "importe", "-10"));

        ImportResult result = service.importTransactions(file, null);

        assertThat(result.imported()).isZero();
        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("cuenta"));
    }

    // ---------- per-row errors / numbering ----------

    @Test
    void missingAmountReportsRowErrorWithHeaderOffsetNumbering() {
        rows(row("fecha", "01/01/2024", "importe", "-10"),            // row 2: ok
             row("fecha", "01/01/2024"),                              // row 3: missing importe
             row("fecha", "01/01/2024", "importe", "-5"));            // row 4: ok

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.errors()).singleElement()
                .satisfies(e -> {
                    assertThat(e.row()).isEqualTo(3);
                    assertThat(e.message()).contains("importe");
                });
    }

    // ---------- dedup ----------

    @Test
    void dedup_reimportingExistingRowSkipsIt() {
        Transaction existing = expense(99L, eur("10"), corriente, otrosGastos, LocalDate.of(2024, 1, 1));
        existing.setDescription("Compra");
        when(transactionRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                .thenReturn(List.of(existing));
        rows(row("fecha", "01/01/2024", "importe", "-10", "descripcion", "Compra"));

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.imported()).isZero();
        assertThat(result.duplicated()).isEqualTo(1);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void dedup_genuineDuplicateInFileStillImportedOnce() {
        Transaction existing = expense(99L, eur("10"), corriente, otrosGastos, LocalDate.of(2024, 1, 1));
        existing.setDescription("Compra");
        when(transactionRepository.findByDateBetweenOrderByDateDesc(any(), any()))
                .thenReturn(List.of(existing));
        Map<String, String> r = row("fecha", "01/01/2024", "importe", "-10", "descripcion", "Compra");
        rows(r, row("fecha", "01/01/2024", "importe", "-10", "descripcion", "Compra"));

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.duplicated()).isEqualTo(1);
        verify(transactionRepository, times(1)).save(any());
    }

    // ---------- transfers ----------

    @Test
    void transfers_importsRowsAndStoresAbsoluteAmount() {
        when(parser.parse(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "-100", "origen", "Corriente", "destino", "Ahorro")));

        ImportResult result = service.importTransfers(file);

        ArgumentCaptor<Transfer> captor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(captor.capture());
        Transfer t = captor.getValue();
        assertThat(result.imported()).isEqualTo(1);
        assertThat(t.getFromAccount()).isSameAs(corriente);
        assertThat(t.getToAccount()).isSameAs(ahorro);
        assertThat(t.getAmount()).isEqualByComparingTo("100");
    }

    @Test
    void transfers_missingOriginOrDestinationColumnsThrows400() {
        when(parser.parse(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100")));

        assertThatThrownBy(() -> service.importTransfers(file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void transfers_sameOriginAndDestinationReportsRowError() {
        when(parser.parse(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100", "origen", "Corriente", "destino", "Corriente")));

        ImportResult result = service.importTransfers(file);

        assertThat(result.imported()).isZero();
        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("misma cuenta"));
    }

    @Test
    void transfers_unknownAccountReportsRowError() {
        when(parser.parse(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100", "origen", "Nope", "destino", "Ahorro")));

        ImportResult result = service.importTransfers(file);

        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("Cuenta de origen no encontrada"));
    }

    @Test
    void transfers_dedupSkipsExisting() {
        Transfer existing = transfer(7L, eur("100"), corriente, ahorro, LocalDate.of(2024, 1, 1));
        when(transferRepository.findByDateBetween(any(), any())).thenReturn(List.of(existing));
        when(parser.parse(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100", "origen", "Corriente", "destino", "Ahorro")));

        ImportResult result = service.importTransfers(file);

        assertThat(result.imported()).isZero();
        assertThat(result.duplicated()).isEqualTo(1);
        verify(transferRepository, never()).save(any());
    }
}
