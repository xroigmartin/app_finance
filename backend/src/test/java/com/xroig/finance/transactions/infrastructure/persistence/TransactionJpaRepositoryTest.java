package com.xroig.finance.transactions.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaEntity;
import com.xroig.finance.categories.infrastructure.persistence.CategoryJpaRepository;
import com.xroig.finance.shared.domain.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for the cross-context aggregation/existence queries on
 * {@link TransactionJpaRepository} against real PostgreSQL: the refund netting baked
 * into the net sums, the one-level subcategory roll-up, the {@code extract(month/year)}
 * grouping and the per-account net used to fold movements into a balance — all things
 * the Mockito application tests could only assume.
 *
 * <p>A refund carries the original's type (EXPENSE) and points at it via
 * {@code refundOf}; in every aggregation it flips the sign, so it subtracts from
 * spending and adds back to the balance.
 */
class TransactionJpaRepositoryTest extends PostgresTestBase {

    @Autowired private TransactionJpaRepository transactionRepository;
    @Autowired private AccountJpaRepository accountRepository;
    @Autowired private CategoryJpaRepository categoryRepository;

    private static final LocalDate JAN = LocalDate.of(2024, 1, 15);
    private static final LocalDate FEB = LocalDate.of(2024, 2, 10);

    private AccountJpaEntity corriente;
    private CategoryJpaEntity comida;

    @BeforeEach
    void setUp() {
        corriente = account("Corriente");
        comida = category("Comida", TransactionType.EXPENSE, corriente, null);
    }

    // ---- persistence helpers (entities are built without id; the DB assigns it) ----

    private AccountJpaEntity account(String name) {
        AccountJpaEntity a = new AccountJpaEntity();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private CategoryJpaEntity category(String name, TransactionType type, AccountJpaEntity account, CategoryJpaEntity parent) {
        CategoryJpaEntity c = new CategoryJpaEntity();
        c.setName(name);
        c.setType(type);
        c.setColor("#" + name.toLowerCase());
        c.setAccount(account);
        c.setParent(parent);
        return categoryRepository.save(c);
    }

    private TransactionJpaEntity tx(TransactionType type, String amount, AccountJpaEntity account, CategoryJpaEntity category,
                                    LocalDate date, TransactionJpaEntity refundOf) {
        TransactionJpaEntity t = new TransactionJpaEntity();
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setAccount(account);
        t.setCategory(category);
        t.setDate(date);
        t.setRefundOf(refundOf);
        return transactionRepository.save(t);
    }

    private TransactionJpaEntity expense(String amount, CategoryJpaEntity category, LocalDate date) {
        return tx(TransactionType.EXPENSE, amount, corriente, category, date, null);
    }

    private TransactionJpaEntity income(String amount, CategoryJpaEntity category, LocalDate date) {
        return tx(TransactionType.INCOME, amount, corriente, category, date, null);
    }

    private TransactionJpaEntity refundOf(TransactionJpaEntity original, String amount, LocalDate date) {
        return tx(TransactionType.EXPENSE, amount, original.getAccount(), original.getCategory(), date, original);
    }

    // ---------- refund netting in the period sums ----------

    @Test
    void sumByTypeAndPeriod_netsRefunds() {
        TransactionJpaEntity gasto = expense("100", comida, JAN);
        refundOf(gasto, "30", JAN);

        BigDecimal sum = transactionRepository.sumByTypeAndPeriod(
                TransactionType.EXPENSE, JAN, FEB, corriente.getId());

        assertThat(sum).isEqualByComparingTo("70"); // 100 - 30
    }

    // ---------- one-level subcategory roll-up ----------

    @Test
    void sumByCategoryTreeAndPeriod_rollsUpSubcategories() {
        CategoryJpaEntity hogar = category("Hogar", TransactionType.EXPENSE, corriente, null);
        CategoryJpaEntity luz = category("Luz", TransactionType.EXPENSE, corriente, hogar);
        expense("100", hogar, JAN);          // on the parent directly
        expense("40", luz, JAN);             // on the child
        refundOf(expense("0.01", luz, JAN), "0.01", JAN); // net 0 on child, just exercises refund
        expense("50", comida, LocalDate.of(2024, 3, 1)); // out of [JAN, FEB] and other category

        // Tree roll-up: parent + child, scoped to the account and the period.
        assertThat(transactionRepository.sumByCategoryTreeAndPeriod(hogar.getId(), JAN, FEB, corriente.getId()))
                .isEqualByComparingTo("140"); // 100 + 40 + (0.01 - 0.01)
    }

    // ---------- extract(month/year) grouping, no roll-up ----------

    @Test
    void sumByExactCategoryAndMonthOfYear_groupsByExactCategoryAndMonth() {
        CategoryJpaEntity hogar = category("Hogar", TransactionType.EXPENSE, corriente, null);
        CategoryJpaEntity luz = category("Luz", TransactionType.EXPENSE, corriente, hogar);
        expense("40", luz, JAN);
        expense("100", hogar, FEB);
        // a movement in another year must be excluded
        expense("500", luz, LocalDate.of(2023, 1, 5));

        List<Object[]> rows = transactionRepository.sumByExactCategoryAndMonthOfYear(2024, corriente.getId());

        assertThat(cell(rows, luz.getId(), 1)).isEqualByComparingTo("40");
        assertThat(cell(rows, hogar.getId(), 2)).isEqualByComparingTo("100");
        // No 2023 row leaked in.
        assertThat(rows).noneMatch(r -> month(r) != 1 && month(r) != 2);
    }

    // ---------- roll-up to parent name/color, ordered ----------

    @Test
    void sumByCategory_rollsUpToParentNameAndColorOrderedDesc() {
        CategoryJpaEntity hogar = category("Hogar", TransactionType.EXPENSE, corriente, null);
        CategoryJpaEntity luz = category("Luz", TransactionType.EXPENSE, corriente, hogar);
        expense("100", hogar, JAN);
        expense("40", luz, JAN);
        refundOf(expense("20", luz, JAN), "10", JAN); // child net 20-10=10
        expense("50", comida, JAN);

        List<Object[]> rows = transactionRepository.sumByCategory(
                TransactionType.EXPENSE, JAN, FEB, corriente.getId());

        // Two groups: Hogar (100 + 40 + 10 = 150) and Comida (50), ordered desc.
        assertThat(rows).hasSize(2);
        assertThat((String) rows.get(0)[0]).isEqualTo("Hogar");
        assertThat((String) rows.get(0)[1]).isEqualTo("#hogar");
        assertThat((BigDecimal) rows.get(0)[2]).isEqualByComparingTo("150");
        assertThat((String) rows.get(1)[0]).isEqualTo("Comida");
        assertThat((BigDecimal) rows.get(1)[2]).isEqualByComparingTo("50");
    }

    // ---------- per-account net (folded into a balance): type-aware, refunds flip ----------

    @Test
    void netTotalByAccountUntil_isTypeAwareAndFiltersByAccountAndDate() {
        AccountJpaEntity otra = account("Otra");
        income("200", comida, JAN);
        expense("50", comida, JAN);
        expense("30", comida, FEB);
        TransactionJpaEntity gasto = expense("80", comida, FEB);
        refundOf(gasto, "20", FEB);
        tx(TransactionType.EXPENSE, "999", otra, comida, JAN, null); // other account, excluded

        // Up to end of January: income 200 - expense 50 = 150.
        assertThat(transactionRepository.netTotalByAccountUntil(corriente.getId(), LocalDate.of(2024, 1, 31)))
                .isEqualByComparingTo("150");
        // Up to FEB: 200 - 50 - 30 - 80 + 20 (the refund of an expense adds money back) = 60.
        assertThat(transactionRepository.netTotalByAccountUntil(corriente.getId(), FEB))
                .isEqualByComparingTo("60");
    }

    // ---------- refunded amount ----------

    @Test
    void sumRefundedAmount_sumsRefundsAndCanExcludeOne() {
        TransactionJpaEntity gasto = expense("100", comida, JAN);
        TransactionJpaEntity r1 = refundOf(gasto, "30", JAN);
        refundOf(gasto, "20", JAN);

        assertThat(transactionRepository.sumRefundedAmount(gasto.getId(), null))
                .isEqualByComparingTo("50"); // 30 + 20
        assertThat(transactionRepository.sumRefundedAmount(gasto.getId(), r1.getId()))
                .isEqualByComparingTo("20"); // excluding r1
    }

    // ---------- existence checks (deletion guards) ----------

    @Test
    void existenceChecks_seeAccountAndCategoryUsageAcrossAccounts() {
        AccountJpaEntity otra = account("Otra");
        expense("100", comida, JAN);                           // comida used on corriente
        tx(TransactionType.EXPENSE, "999", otra, comida, JAN, null); // comida also used on otra

        assertThat(transactionRepository.existsByAccountId(corriente.getId())).isTrue();
        assertThat(transactionRepository.existsByCategoryId(comida.getId())).isTrue();
        // comida is used by a movement of an account other than 'corriente'.
        assertThat(transactionRepository.existsByCategoryIdAndAccountIdNot(comida.getId(), corriente.getId())).isTrue();
        assertThat(transactionRepository.existsByCategoryIdAndAccountIdNot(comida.getId(), otra.getId())).isTrue();
        // an account/category nobody references
        AccountJpaEntity vacia = account("Vacía");
        assertThat(transactionRepository.existsByAccountId(vacia.getId())).isFalse();
    }

    // ---- helpers to read the Object[] rows ----

    private static int month(Object[] row) {
        return ((Number) row[1]).intValue();
    }

    private static BigDecimal cell(List<Object[]> rows, Long categoryId, int month) {
        return rows.stream()
                .filter(r -> ((Number) r[0]).longValue() == categoryId && month(r) == month)
                .map(r -> (BigDecimal) r[2])
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for category " + categoryId + " month " + month));
    }
}
