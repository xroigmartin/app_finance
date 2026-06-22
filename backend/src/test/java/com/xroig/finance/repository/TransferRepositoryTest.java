package com.xroig.finance.repository;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.model.Account;
import com.xroig.finance.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for {@link TransferRepository} against real PostgreSQL (stage T4,
 * repository query coverage): the optional-account filter of {@code search} —
 * which must match a transfer when the account is either side of it — and the
 * directional {@code totalInUntil}/{@code totalOutUntil} sums that the dashboard
 * uses to fold transfers into an account's balance.
 */
class TransferRepositoryTest extends PostgresTestBase {

    @Autowired private TransferRepository transferRepository;
    @Autowired private AccountRepository accountRepository;

    private static final LocalDate JAN = LocalDate.of(2024, 1, 10);
    private static final LocalDate FEB = LocalDate.of(2024, 2, 10);
    private static final LocalDate MAR = LocalDate.of(2024, 3, 10);

    private Account corriente;
    private Account ahorro;

    @BeforeEach
    void setUp() {
        corriente = account("Corriente");
        ahorro = account("Ahorro");
    }

    @Test
    void search_withoutAccount_returnsRangeOrderedByDateThenIdDesc() {
        Transfer jan = transfer(corriente, ahorro, "100", JAN);
        Transfer febA = transfer(corriente, ahorro, "200", FEB);
        Transfer febB = transfer(corriente, ahorro, "300", FEB);
        transfer(corriente, ahorro, "999", LocalDate.of(2024, 4, 1)); // out of range

        List<Transfer> result = transferRepository.search(JAN, MAR, null);

        // Two Feb rows first (later id first on the same date), then the Jan one.
        assertThat(result).extracting(Transfer::getId)
                .containsExactly(febB.getId(), febA.getId(), jan.getId());
    }

    @Test
    void search_byAccount_matchesEitherSideOfTheTransfer() {
        Account tercera = account("Tercera");
        transfer(corriente, ahorro, "100", JAN); // ahorro is the destination
        transfer(ahorro, tercera, "50", FEB);     // ahorro is the source
        transfer(corriente, tercera, "70", FEB);  // ahorro on neither side

        List<Transfer> result = transferRepository.search(JAN, MAR, ahorro.getId());

        assertThat(result).extracting(Transfer::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("100"), new BigDecimal("50"));
    }

    @Test
    void totalInAndOutUntil_areDirectionalAndDateBounded() {
        transfer(corriente, ahorro, "100", JAN); // into ahorro, out of corriente
        transfer(corriente, ahorro, "40", MAR);  // after the FEB cutoff
        transfer(ahorro, corriente, "30", JAN);  // into corriente, out of ahorro

        // Up to end of FEB: ahorro received 100, sent 30.
        assertThat(transferRepository.totalInUntil(ahorro.getId(), FEB)).isEqualByComparingTo("100");
        assertThat(transferRepository.totalOutUntil(ahorro.getId(), FEB)).isEqualByComparingTo("30");
    }

    @Test
    void totalInUntil_isZeroWhenNoTransfers() {
        assertThat(transferRepository.totalInUntil(corriente.getId(), MAR)).isEqualByComparingTo("0");
    }

    // ---- helpers ----

    private Account account(String name) {
        Account a = new Account();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private Transfer transfer(Account from, Account to, String amount, LocalDate date) {
        Transfer t = new Transfer();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(new BigDecimal(amount));
        t.setDate(date);
        return transferRepository.save(t);
    }
}
