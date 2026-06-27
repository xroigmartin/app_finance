package com.xroig.finance.imports.application;

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
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application tests for {@link ImportService} with the imports outbound ports mocked
 * (the directories, the file reader and the writers): resolveType / resolveCategory /
 * resolveDate, buildDescription, rule matching and fallback, per-row errors, dedup and
 * transfers. The mocked world is a small mutable set of accounts/categories/rules that
 * tests extend in place; {@code reader.read} is stubbed per test to feed rows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    @Mock private ImportFileReader reader;
    @Mock private AccountDirectory accountDirectory;
    @Mock private CategoryDirectory categoryDirectory;
    @Mock private RuleDirectory ruleDirectory;
    @Mock private MovementWriter movementWriter;
    @Mock private TransferWriter transferWriter;

    private ImportService service;

    private final MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);

    private final ImportAccount corriente = new ImportAccount(1, "Corriente");
    private final ImportAccount ahorro = new ImportAccount(2, "Ahorro");
    private final ImportCategory otrosGastos = new ImportCategory(100, "Otros gastos", TransactionType.EXPENSE, null);
    private final ImportCategory otrosIngresos = new ImportCategory(101, "Otros ingresos", TransactionType.INCOME, null);

    private final List<ImportCategory> categories = new ArrayList<>(List.of(otrosGastos, otrosIngresos));
    private final List<ImportRule> rules = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ImportService(reader, accountDirectory, categoryDirectory, ruleDirectory,
                movementWriter, transferWriter);
        when(accountDirectory.all()).thenReturn(List.of(corriente, ahorro));
        when(categoryDirectory.all()).thenReturn(categories);
        when(ruleDirectory.all()).thenReturn(rules);
        when(categoryDirectory.createGlobal(any(), any())).thenAnswer(i ->
                new ImportCategory(900, i.getArgument(0), i.getArgument(1), null));
        when(movementWriter.existingBetween(any(), any())).thenReturn(List.of());
        when(transferWriter.existingBetween(any(), any())).thenReturn(List.of());
    }

    // ---- helpers ----

    private static ImportRow row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return new ImportRow(m);
    }

    private void rows(ImportRow... rows) {
        when(reader.read(file)).thenReturn(List.of(rows));
    }

    private List<NewMovement> capturedMovements() {
        ArgumentCaptor<NewMovement> captor = ArgumentCaptor.forClass(NewMovement.class);
        verify(movementWriter, org.mockito.Mockito.atLeast(0)).create(captor.capture());
        return captor.getAllValues();
    }

    private NewMovement importSingle(ImportRow row, Long defaultAccountId) {
        rows(row);
        service.importTransactions(file, defaultAccountId);
        List<NewMovement> saved = capturedMovements();
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

        assertThat(capturedMovements()).extracting(NewMovement::type).containsExactly(
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
        verify(movementWriter, never()).create(any());
    }

    @Test
    void resolveType_infersFromSignWhenNoType() {
        rows(row("fecha", "01/01/2024", "importe", "-50"),
             row("fecha", "01/01/2024", "importe", "50"));

        service.importTransactions(file, 1L);

        assertThat(capturedMovements()).extracting(NewMovement::type)
                .containsExactly(TransactionType.EXPENSE, TransactionType.INCOME);
    }

    @Test
    void amountIsStoredAsAbsoluteValue() {
        NewMovement m = importSingle(row("fecha", "01/01/2024", "importe", "-1.234,56"), 1L);
        assertThat(m.amount()).isEqualByComparingTo("1234.56");
    }

    // ---------- resolveCategory ----------

    @Test
    void resolveCategory_accountOwnedWinsOverGlobal() {
        categories.add(new ImportCategory(200, "Comida", TransactionType.EXPENSE, null));
        categories.add(new ImportCategory(201, "Comida", TransactionType.EXPENSE, 1L));

        NewMovement m = importSingle(
                row("fecha", "01/01/2024", "importe", "-10", "categoria", "Comida"), 1L);

        assertThat(m.categoryId()).isEqualTo(201);
    }

    @Test
    void resolveCategory_createsUnknownCategoryAsGlobal() {
        when(categoryDirectory.createGlobal("Viajes", TransactionType.EXPENSE))
                .thenReturn(new ImportCategory(202, "Viajes", TransactionType.EXPENSE, null));

        NewMovement m = importSingle(
                row("fecha", "01/01/2024", "importe", "-10", "categoria", "Viajes"), 1L);

        verify(categoryDirectory).createGlobal("Viajes", TransactionType.EXPENSE);
        assertThat(m.categoryId()).isEqualTo(202);
    }

    // ---------- rule matching / fallback ----------

    @Test
    void matchRule_categorizesByDescriptionWhenNoCategoryColumn() {
        categories.add(new ImportCategory(300, "Supermercado", TransactionType.EXPENSE, null));
        rules.add(new ImportRule("mercadona|lidl", 300, TransactionType.EXPENSE, null));

        NewMovement m = importSingle(
                row("fecha", "01/01/2024", "importe", "-30", "descripcion", "Compra en MERCADONA"), 1L);

        assertThat(m.categoryId()).isEqualTo(300);
    }

    @Test
    void matchRule_fallsBackToOtrosWhenNoRuleMatches() {
        NewMovement m = importSingle(
                row("fecha", "01/01/2024", "importe", "-30", "descripcion", "Algo raro"), 1L);

        assertThat(m.categoryId()).isEqualTo(100);
    }

    @Test
    void matchRule_ignoresRuleOfDifferentTypeOrAccount() {
        // Rule category is INCOME but the row is an expense → must not match.
        categories.add(new ImportCategory(301, "Nómina", TransactionType.INCOME, null));
        rules.add(new ImportRule("nomina", 301, TransactionType.INCOME, null));

        NewMovement m = importSingle(
                row("fecha", "01/01/2024", "importe", "-30", "descripcion", "Pago NOMINA"), 1L);

        assertThat(m.categoryId()).isEqualTo(100);
    }

    // ---------- resolveDate ----------

    @Test
    void resolveDate_operationDateInExtraOverridesDateColumn() {
        NewMovement m = importSingle(row(
                "fecha", "20/01/2024", "importe", "-10",
                "mas datos", "Fecha de operación: 15-01-2024"), 1L);

        assertThat(m.date()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void resolveDate_usesDateColumnWhenNoOperationDate() {
        NewMovement m = importSingle(row("fecha", "20/01/2024", "importe", "-10"), 1L);
        assertThat(m.date()).isEqualTo(LocalDate.of(2024, 1, 20));
    }

    // ---------- buildDescription ----------

    @Test
    void buildDescription_joinsDescriptionAndExtra() {
        NewMovement m = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10",
                "descripcion", "Compra", "mas datos", "Tienda X"), 1L);

        assertThat(m.description()).isEqualTo("Compra — Tienda X");
    }

    @Test
    void buildDescription_dropsOperationDateExtra() {
        NewMovement m = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10",
                "descripcion", "Compra", "mas datos", "Fecha de operación: 15-01-2024"), 1L);

        assertThat(m.description()).isEqualTo("Compra");
    }

    @Test
    void buildDescription_usesMovimientoWhenNoDescription() {
        NewMovement m = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10", "movimiento", "Mercadona"), 1L);

        assertThat(m.description()).isEqualTo("Mercadona");
    }

    // ---------- account resolution ----------

    @Test
    void account_namedColumnOverridesDefault() {
        NewMovement m = importSingle(row(
                "fecha", "01/01/2024", "importe", "-10", "cuenta", "Ahorro"), 1L);

        assertThat(m.accountId()).isEqualTo(2);
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
        when(movementWriter.existingBetween(any(), any())).thenReturn(List.of(new ExistingMovement(
                1, LocalDate.of(2024, 1, 1), TransactionType.EXPENSE, new BigDecimal("10"), "Compra")));
        rows(row("fecha", "01/01/2024", "importe", "-10", "descripcion", "Compra"));

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.imported()).isZero();
        assertThat(result.duplicated()).isEqualTo(1);
        verify(movementWriter, never()).create(any());
    }

    @Test
    void dedup_genuineDuplicateInFileStillImportedOnce() {
        when(movementWriter.existingBetween(any(), any())).thenReturn(List.of(new ExistingMovement(
                1, LocalDate.of(2024, 1, 1), TransactionType.EXPENSE, new BigDecimal("10"), "Compra")));
        rows(row("fecha", "01/01/2024", "importe", "-10", "descripcion", "Compra"),
             row("fecha", "01/01/2024", "importe", "-10", "descripcion", "Compra"));

        ImportResult result = service.importTransactions(file, 1L);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.duplicated()).isEqualTo(1);
        verify(movementWriter, times(1)).create(any());
    }

    // ---------- transfers ----------

    @Test
    void transfers_importsRowsAndStoresAbsoluteAmount() {
        when(reader.read(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "-100", "origen", "Corriente", "destino", "Ahorro")));

        ImportResult result = service.importTransfers(file);

        ArgumentCaptor<NewTransfer> captor = ArgumentCaptor.forClass(NewTransfer.class);
        verify(transferWriter).create(captor.capture());
        NewTransfer t = captor.getValue();
        assertThat(result.imported()).isEqualTo(1);
        assertThat(t.fromAccountId()).isEqualTo(1);
        assertThat(t.toAccountId()).isEqualTo(2);
        assertThat(t.amount()).isEqualByComparingTo("100");
    }

    @Test
    void transfers_missingOriginOrDestinationColumnsThrows400() {
        when(reader.read(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100")));

        assertThatThrownBy(() -> service.importTransfers(file))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void transfers_sameOriginAndDestinationReportsRowError() {
        when(reader.read(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100", "origen", "Corriente", "destino", "Corriente")));

        ImportResult result = service.importTransfers(file);

        assertThat(result.imported()).isZero();
        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("misma cuenta"));
    }

    @Test
    void transfers_unknownAccountReportsRowError() {
        when(reader.read(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100", "origen", "Nope", "destino", "Ahorro")));

        ImportResult result = service.importTransfers(file);

        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).contains("Cuenta de origen no encontrada"));
    }

    @Test
    void transfers_dedupSkipsExisting() {
        when(transferWriter.existingBetween(any(), any())).thenReturn(List.of(new ExistingTransfer(
                1, 2, LocalDate.of(2024, 1, 1), new BigDecimal("100"), null)));
        when(reader.read(file)).thenReturn(List.of(
                row("fecha", "01/01/2024", "importe", "100", "origen", "Corriente", "destino", "Ahorro")));

        ImportResult result = service.importTransfers(file);

        assertThat(result.imported()).isZero();
        assertThat(result.duplicated()).isEqualTo(1);
        verify(transferWriter, never()).create(any());
    }
}
