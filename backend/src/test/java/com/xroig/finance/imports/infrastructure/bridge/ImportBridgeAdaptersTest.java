package com.xroig.finance.imports.infrastructure.bridge;

import com.xroig.finance.accounts.application.port.FindAccounts;
import com.xroig.finance.accounts.domain.Account;
import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.categories.application.CategoryQueryPort;
import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categorization.application.CategoryRuleQueryPort;
import com.xroig.finance.categorization.application.CategoryRuleView;
import com.xroig.finance.imports.domain.AccountDirectory.ImportAccount;
import com.xroig.finance.imports.domain.CategoryDirectory.ImportCategory;
import com.xroig.finance.imports.domain.MovementWriter.ExistingMovement;
import com.xroig.finance.imports.domain.MovementWriter.NewMovement;
import com.xroig.finance.imports.domain.RuleDirectory.ImportRule;
import com.xroig.finance.imports.domain.TransferWriter.ExistingTransfer;
import com.xroig.finance.imports.domain.TransferWriter.NewTransfer;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.application.TransactionQueryPort;
import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import com.xroig.finance.transfers.application.TransferQueryPort;
import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.application.port.CreateTransfer;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the imports bridge adapters: each maps the read models of another
 * context onto the imports outbound-port types and forwards writes to its create use
 * case. Pure Mockito, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class ImportBridgeAdaptersTest {

    @Mock private FindAccounts findAccounts;
    @Mock private CategoryQueryPort categoryQueries;
    @Mock private CreateCategory createCategory;
    @Mock private CategoryRuleQueryPort ruleQueries;
    @Mock private TransactionQueryPort transactionQueries;
    @Mock private CreateTransaction createTransaction;
    @Mock private TransferQueryPort transferQueries;
    @Mock private CreateTransfer createTransfer;

    @Test
    void accountDirectory_mapsIdAndName() {
        when(findAccounts.all()).thenReturn(List.of(
                Account.rehydrate(new AccountId(1L), "Corriente", "Banco", Money.of(new BigDecimal("100")))));

        List<ImportAccount> all = new AccountDirectoryAdapter(findAccounts).all();

        assertThat(all).singleElement()
                .satisfies(a -> {
                    assertThat(a.id()).isEqualTo(1);
                    assertThat(a.name()).isEqualTo("Corriente");
                });
    }

    @Test
    void categoryDirectory_listsAndCreatesGlobal() {
        when(categoryQueries.findAll()).thenReturn(List.of(
                new CategoryView(5L, "Comida", TransactionType.EXPENSE, "#fff",
                        new CategoryView.AccountRef(1L, "Corriente", "Banco", BigDecimal.ZERO), null)));
        when(createCategory.create(any())).thenReturn(
                new CategoryView(9L, "Viajes", TransactionType.EXPENSE, "#64748b", null, null));
        CategoryDirectoryAdapter adapter = new CategoryDirectoryAdapter(categoryQueries, createCategory);

        assertThat(adapter.all()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(5);
            assertThat(c.accountId()).isEqualTo(1L);
        });

        ImportCategory created = adapter.createGlobal("Viajes", TransactionType.EXPENSE);

        ArgumentCaptor<CreateCategoryCommand> captor = ArgumentCaptor.forClass(CreateCategoryCommand.class);
        verify(createCategory).create(captor.capture());
        assertThat(captor.getValue()).satisfies(cmd -> {
            assertThat(cmd.name()).isEqualTo("Viajes");
            assertThat(cmd.color()).isEqualTo("#64748b");
            assertThat(cmd.parentId()).isNull();
            assertThat(cmd.accountId()).isNull();
        });
        assertThat(created.id()).isEqualTo(9);
        assertThat(created.global()).isTrue();
    }

    @Test
    void ruleDirectory_mapsPatternAndTargetScope() {
        CategoryView global = new CategoryView(7L, "Super", TransactionType.EXPENSE, "#fff", null, null);
        when(ruleQueries.findAll()).thenReturn(List.of(new CategoryRuleView(1L, "lidl|mercadona", global)));

        List<ImportRule> all = new RuleDirectoryAdapter(ruleQueries).all();

        assertThat(all).singleElement().satisfies(r -> {
            assertThat(r.pattern()).isEqualTo("lidl|mercadona");
            assertThat(r.categoryId()).isEqualTo(7);
            assertThat(r.categoryType()).isEqualTo(TransactionType.EXPENSE);
            assertThat(r.categoryAccountId()).isNull();
        });
    }

    @Test
    void movementWriter_readsWindowAndDelegatesCreate() {
        when(transactionQueries.search(any(), any(), any(), any())).thenReturn(List.of(new TransactionView(
                3L, LocalDate.of(2024, 1, 5), new BigDecimal("12.50"), "Compra", TransactionType.EXPENSE,
                new TransactionView.AccountRef(1L, "Corriente", "Banco", BigDecimal.ZERO),
                new TransactionView.CategoryRef(2L, "Comida", TransactionType.EXPENSE, "#fff"), null)));
        MovementWriterAdapter adapter = new MovementWriterAdapter(transactionQueries, createTransaction);

        List<ExistingMovement> existing = adapter.existingBetween(LocalDate.MIN, LocalDate.MAX);
        assertThat(existing).singleElement().satisfies(m -> {
            assertThat(m.accountId()).isEqualTo(1);
            assertThat(m.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(m.description()).isEqualTo("Compra");
        });

        adapter.create(new NewMovement(LocalDate.of(2024, 1, 5), new BigDecimal("10"), "x",
                TransactionType.EXPENSE, 1L, 2L));

        ArgumentCaptor<TransactionCommand> captor = ArgumentCaptor.forClass(TransactionCommand.class);
        verify(createTransaction).create(captor.capture());
        assertThat(captor.getValue()).satisfies(cmd -> {
            assertThat(cmd.accountId()).isEqualTo(1L);
            assertThat(cmd.categoryId()).isEqualTo(2L);
            assertThat(cmd.refundOfId()).isNull();
        });
    }

    @Test
    void transferWriter_readsWindowAndDelegatesCreate() {
        when(transferQueries.search(any(), any(), any())).thenReturn(List.of(new TransferView(
                4L, LocalDate.of(2024, 1, 5), new BigDecimal("100"), "x",
                new TransferView.AccountRef(1L, "Corriente", "Banco", BigDecimal.ZERO),
                new TransferView.AccountRef(2L, "Ahorro", "Banco", BigDecimal.ZERO))));
        TransferWriterAdapter adapter = new TransferWriterAdapter(transferQueries, createTransfer);

        List<ExistingTransfer> existing = adapter.existingBetween(LocalDate.MIN, LocalDate.MAX);
        assertThat(existing).singleElement().satisfies(t -> {
            assertThat(t.fromAccountId()).isEqualTo(1);
            assertThat(t.toAccountId()).isEqualTo(2);
        });

        adapter.create(new NewTransfer(LocalDate.of(2024, 1, 5), new BigDecimal("100"), "x", 1L, 2L));

        ArgumentCaptor<TransferCommand> captor = ArgumentCaptor.forClass(TransferCommand.class);
        verify(createTransfer).create(captor.capture());
        assertThat(captor.getValue()).satisfies(cmd -> {
            assertThat(cmd.fromAccountId()).isEqualTo(1L);
            assertThat(cmd.toAccountId()).isEqualTo(2L);
        });
    }
}
