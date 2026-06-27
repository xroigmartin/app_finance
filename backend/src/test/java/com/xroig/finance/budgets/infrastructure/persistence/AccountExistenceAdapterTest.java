package com.xroig.finance.budgets.infrastructure.persistence;

import com.xroig.finance.accounts.domain.AccountId;
import com.xroig.finance.accounts.infrastructure.persistence.AccountJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit test for the budgets {@link AccountExistenceAdapter}: delegates to {@code existsById}. */
@ExtendWith(MockitoExtension.class)
class AccountExistenceAdapterTest {

    @Mock private AccountJpaRepository accounts;
    @InjectMocks private AccountExistenceAdapter adapter;

    @Test
    void exists_delegates() {
        when(accounts.existsById(1L)).thenReturn(true);
        assertThat(adapter.exists(new AccountId(1L))).isTrue();
    }

    @Test
    void exists_falseWhenUnknown() {
        when(accounts.existsById(9L)).thenReturn(false);
        assertThat(adapter.exists(new AccountId(9L))).isFalse();
    }
}
