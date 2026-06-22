package com.xroig.finance.repository;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for {@link TransactionRepository} against real PostgreSQL
 * (stage T1): the refund netting baked into the seven net sums, the one-level
 * subcategory roll-up, and the {@code extract(month/year)} grouping — all things
 * the Mockito service tests could only assume.
 *
 * <p>A refund carries the original's type (EXPENSE) and points at it via
 * {@code refundOf}; in every aggregation it flips the sign, so it subtracts from
 * spending and adds back to the balance.
 */
class TransactionRepositoryTest extends PostgresTestBase {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CategoryRepository categoryRepository;

    private static final LocalDate JAN = LocalDate.of(2024, 1, 15);
    private static final LocalDate FEB = LocalDate.of(2024, 2, 10);

    private Account corriente;
    private Category comida;

    @BeforeEach
    void setUp() {
        corriente = account("Corriente");
        comida = category("Comida", TransactionType.EXPENSE, corriente, null);
    }

    // ---- persistence helpers (entities are built without id; the DB assigns it) ----

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private Category category(String name, TransactionType type, Account account, Category parent) {
        Category c = new Category();
        c.setName(name);
        c.setType(type);
        c.setColor("#" + name.toLowerCase());
        c.setAccount(account);
        c.setParent(parent);
        return categoryRepository.save(c);
    }

    private Transaction tx(TransactionType type, String amount, Account account, Category category,
                           LocalDate date, Transaction refundOf) {
        Transaction t = new Transaction();
        t.setType(type);
        t.setAmount(new BigDecimal(amount));
        t.setAccount(account);
        t.setCategory(category);
        t.setDate(date);
        t.setRefundOf(refundOf);
        return transactionRepository.save(t);
    }

    private Transaction expense(String amount, Category category, LocalDate date) {
        return tx(TransactionType.EXPENSE, amount, corriente, category, date, null);
    }

    private Transaction income(String amount, Category category, LocalDate date) {
        return tx(TransactionType.INCOME, amount, corriente, category, date, null);
    }

    private Transaction refundOf(Transaction original, String amount, LocalDate date) {
        return tx(TransactionType.EXPENSE, amount, original.getAccount(), original.getCategory(), date, original);
    }

    // ---------- refund netting in the period sums ----------

    @Test
    void sumByTypeAndPeriod_netsRefunds() {
        Transaction gasto = expense("100", comida, JAN);
        refundOf(gasto, "30", JAN);

        BigDecimal sum = transactionRepository.sumByTypeAndPeriod(
                TransactionType.EXPENSE, JAN, FEB, corriente.getId());

        assertThat(sum).isEqualByComparingTo("70"); // 100 - 30
    }

    @Test
    void sumByCategoryAndPeriod_netsRefundsAndScopesByAccount() {
        Account otra = account("Otra");
        expense("100", comida, JAN);
        refundOf(expense("100", comida, JAN), "40", JAN);
        // A movement of the same category on another account must be excluded when scoped.
        tx(TransactionType.EXPENSE, "999", otra, comida, JAN, null);

        assertThat(transactionRepository.sumByCategoryAndPeriod(comida.getId(), JAN, FEB, corriente.getId()))
                .isEqualByComparingTo("160"); // 100 + (100 - 40)
        assertThat(transactionRepository.sumByCategoryAndPeriod(comida.getId(), JAN, FEB, null))
                .isEqualByComparingTo("1159"); // + 999 of the other account
    }

    @Test
    void sumByCategoryAndPeriod_excludesOutOfRange() {
        expense("100", comida, JAN);
        expense("50", comida, LocalDate.of(2024, 3, 1)); // out of [JAN, FEB]

        assertThat(transactionRepository.sumByCategoryAndPeriod(comida.getId(), JAN, FEB, corriente.getId()))
                .isEqualByComparingTo("100");
    }

    // ---------- one-level subcategory roll-up ----------

    @Test
    void sumByCategoryTreeAndPeriod_rollsUpSubcategoriesButExactSumDoesNot() {
        Category hogar = category("Hogar", TransactionType.EXPENSE, corriente, null);
        Category luz = category("Luz", TransactionType.EXPENSE, corriente, hogar);
        expense("100", hogar, JAN);          // on the parent directly
        expense("40", luz, JAN);             // on the child
        refundOf(expense("0.01", luz, JAN), "0.01", JAN); // net 0 on child, just exercises refund

        // Tree roll-up: parent + child.
        assertThat(transactionRepository.sumByCategoryTreeAndPeriod(hogar.getId(), JAN, FEB, corriente.getId()))
                .isEqualByComparingTo("140"); // 100 + 40 + (0.01 - 0.01)
        // Exact (no roll-up): only the parent's own movements.
        assertThat(transactionRepository.sumByCategoryAndPeriod(hogar.getId(), JAN, FEB, corriente.getId()))
                .isEqualByComparingTo("100");
    }

    // ---------- extract(month/year) grouping, no roll-up ----------

    @Test
    void sumByExactCategoryAndMonthOfYear_groupsByExactCategoryAndMonth() {
        Category hogar = category("Hogar", TransactionType.EXPENSE, corriente, null);
        Category luz = category("Luz", TransactionType.EXPENSE, corriente, hogar);
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
        Category hogar = category("Hogar", TransactionType.EXPENSE, corriente, null);
        Category luz = category("Luz", TransactionType.EXPENSE, corriente, hogar);
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

    // ---------- net worth sums ----------

    @Test
    void netTotal_incomeAndExpenseAndRefundFlipSign() {
        income("200", comida, JAN);
        Transaction gasto = expense("50", comida, JAN);
        refundOf(gasto, "20", JAN);

        // 200 - 50 + 20 (the refund of an expense adds money back).
        assertThat(transactionRepository.netTotal()).isEqualByComparingTo("170");
    }

    @Test
    void netTotalByAccountUntil_filtersByAccountAndDate() {
        Account otra = account("Otra");
        expense("50", comida, JAN);
        expense("30", comida, FEB);
        tx(TransactionType.EXPENSE, "999", otra, comida, JAN, null);

        // Only 'corriente' and only up to end of January.
        assertThat(transactionRepository.netTotalByAccountUntil(corriente.getId(), LocalDate.of(2024, 1, 31)))
                .isEqualByComparingTo("-50");
        assertThat(transactionRepository.netTotalByAccountUntil(corriente.getId(), FEB))
                .isEqualByComparingTo("-80");
    }

    // ---------- refunded amount ----------

    @Test
    void sumRefundedAmount_sumsRefundsAndCanExcludeOne() {
        Transaction gasto = expense("100", comida, JAN);
        Transaction r1 = refundOf(gasto, "30", JAN);
        refundOf(gasto, "20", JAN);

        assertThat(transactionRepository.sumRefundedAmount(gasto.getId(), null))
                .isEqualByComparingTo("50"); // 30 + 20
        assertThat(transactionRepository.sumRefundedAmount(gasto.getId(), r1.getId()))
                .isEqualByComparingTo("20"); // excluding r1
    }

    // ---------- search: optional filters and ordering ----------

    @Test
    void search_filtersByRangeAccountAndCategoryOrderedDateThenIdDesc() {
        Category ocio = category("Ocio", TransactionType.EXPENSE, corriente, null);
        Transaction jan = expense("100", comida, JAN);
        Transaction febA = expense("200", comida, FEB);
        Transaction febB = expense("300", comida, FEB);
        Transaction ocioJan = expense("50", ocio, JAN);          // other category
        expense("70", comida, LocalDate.of(2024, 4, 1));         // out of range

        // No account/category filter: every in-range row, newest date first and
        // id desc within the same date (the two Feb rows lead the two Jan rows).
        List<Transaction> all = transactionRepository.search(JAN, FEB, null, null);
        assertThat(all).extracting(Transaction::getId)
                .containsExactly(febB.getId(), febA.getId(),
                        Math.max(jan.getId(), ocioJan.getId()),
                        Math.min(jan.getId(), ocioJan.getId()));

        // Narrowed to account + category: only 'comida' on 'corriente' in range.
        assertThat(transactionRepository.search(JAN, FEB, corriente.getId(), comida.getId()))
                .extracting(Transaction::getId)
                .containsExactly(febB.getId(), febA.getId(), jan.getId());
    }

    @Test
    void search_byAccountOnly_excludesOtherAccounts() {
        Account otra = account("Otra");
        expense("100", comida, JAN);
        tx(TransactionType.EXPENSE, "999", otra, comida, JAN, null);

        assertThat(transactionRepository.search(JAN, FEB, corriente.getId(), null))
                .extracting(Transaction::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"));
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
