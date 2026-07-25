package com.xroig.finance.transfers.infrastructure.persistence;

import com.xroig.finance.PostgresTestBase;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaEntity;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Level-2 tests for {@link TransferJpaRepository} against real PostgreSQL: the
 * optional-account filter of {@code search} — which must match a transfer when the
 * account is either side of it — and the directional {@code totalInUntil}/
 * {@code totalOutUntil} sums the reporting context folds into an account's balance.
 */
class TransferJpaRepositoryTest extends PostgresTestBase {

    @Autowired private TransferJpaRepository transferRepository;
    @Autowired private AccountJpaRepository accountRepository;

    private static final LocalDate JAN = LocalDate.of(2024, 1, 10);
    private static final LocalDate FEB = LocalDate.of(2024, 2, 10);
    private static final LocalDate MAR = LocalDate.of(2024, 3, 10);

    private AccountJpaEntity corriente;
    private AccountJpaEntity ahorro;

    @BeforeEach
    void setUp() {
        corriente = account("Corriente");
        ahorro = account("Ahorro");
    }

    @Test
    void search_withoutAccount_returnsRangeOrderedByDateThenIdDesc() {
        TransferJpaEntity jan = transfer(corriente, ahorro, "100", JAN);
        TransferJpaEntity febA = transfer(corriente, ahorro, "200", FEB);
        TransferJpaEntity febB = transfer(corriente, ahorro, "300", FEB);
        transfer(corriente, ahorro, "999", LocalDate.of(2024, 4, 1)); // out of range

        List<TransferJpaEntity> result = transferRepository.search(JAN, MAR, null);

        // Two Feb rows first (later id first on the same date), then the Jan one.
        assertThat(result).extracting(TransferJpaEntity::getId)
                .containsExactly(febB.getId(), febA.getId(), jan.getId());
    }

    @Test
    void search_byAccount_matchesEitherSideOfTheTransfer() {
        AccountJpaEntity tercera = account("Tercera");
        transfer(corriente, ahorro, "100", JAN); // ahorro is the destination
        transfer(ahorro, tercera, "50", FEB);     // ahorro is the source
        transfer(corriente, tercera, "70", FEB);  // ahorro on neither side

        List<TransferJpaEntity> result = transferRepository.search(JAN, MAR, ahorro.getId());

        assertThat(result).extracting(TransferJpaEntity::getAmount)
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

    @Test
    void existsByFromOrToAccount_seesEitherSide() {
        AccountJpaEntity sinUso = account("Sin uso");
        transfer(corriente, ahorro, "100", JAN);

        assertThat(transferRepository.existsByFromAccountIdOrToAccountId(corriente.getId(), corriente.getId())).isTrue();
        assertThat(transferRepository.existsByFromAccountIdOrToAccountId(ahorro.getId(), ahorro.getId())).isTrue();
        assertThat(transferRepository.existsByFromAccountIdOrToAccountId(sinUso.getId(), sinUso.getId())).isFalse();
    }

    // ---- helpers ----

    private AccountJpaEntity account(String name) {
        AccountJpaEntity a = new AccountJpaEntity();
        a.setName(name);
        a.setType("CORRIENTE");
        a.setInitialBalance(BigDecimal.ZERO);
        return accountRepository.save(a);
    }

    private TransferJpaEntity transfer(AccountJpaEntity from, AccountJpaEntity to, String amount, LocalDate date) {
        TransferJpaEntity t = new TransferJpaEntity();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(new BigDecimal(amount));
        t.setDate(date);
        return transferRepository.save(t);
    }
}
