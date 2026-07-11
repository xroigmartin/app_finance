package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.CreateSecurity.CreateSecurityCommand;
import com.xroig.finance.investments.application.port.UpdateSecurity.UpdateSecurityCommand;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application-service tests for the security catalogue use cases (H1.5), mocking
 * the outbound ports: id resolution, not-found mapping, the proactive duplicate
 * check on the ISIN+currency identity and the RN-5 deletion guard (an instrument
 * with operations cannot be deleted). Editing touches only non-identity metadata.
 */
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    private static final SecurityId ID = new SecurityId(7L);

    @Mock private SecurityRepository securities;
    @Mock private InvestmentTransactionRepository transactions;

    private SecurityService service() {
        return new SecurityService(securities, transactions);
    }

    private static Security stored() {
        return Security.rehydrate(ID, "IE00BK5BQT80", "USD", "Vanguard", "VWCE", "ETF", "AEB", "FIGI1");
    }

    @Test
    void all_delegatesToTheRepository() {
        when(securities.findAll()).thenReturn(List.of(stored()));

        assertThat(service().all()).hasSize(1);
    }

    @Test
    void create_savesANewAggregate() {
        when(securities.findByIsinAndCurrency("IE00BK5BQT80", "USD")).thenReturn(Optional.empty());
        when(securities.save(any(Security.class))).thenAnswer(i -> i.getArgument(0));

        Security created = service().create(new CreateSecurityCommand(
                "IE00BK5BQT80", "USD", "Vanguard", "VWCE", "ETF", "AEB", "FIGI1"));

        assertThat(created.id()).isNull();
        assertThat(created.isin()).isEqualTo("IE00BK5BQT80");
        verify(securities).save(any(Security.class));
    }

    @Test
    void create_duplicateIdentityThrowsConflict() {
        when(securities.findByIsinAndCurrency("IE00BK5BQT80", "USD")).thenReturn(Optional.of(stored()));

        assertThatThrownBy(() -> service().create(new CreateSecurityCommand(
                "ie00bk5bqt80", "usd", "Vanguard", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(securities, never()).save(any());
    }

    @Test
    void update_refreshesNonIdentityMetadata() {
        when(securities.findById(ID)).thenReturn(Optional.of(stored()));
        when(securities.save(any(Security.class))).thenAnswer(i -> i.getArgument(0));

        Security updated = service().update(7L,
                new UpdateSecurityCommand("Vanguard FTSE All-World", "VWCE", "Fondo", "SBF", "FIGI2"));

        assertThat(updated.name()).isEqualTo("Vanguard FTSE All-World");
        assertThat(updated.type()).isEqualTo("Fondo");
        assertThat(updated.exchange()).isEqualTo("SBF");
        assertThat(updated.figi()).isEqualTo("FIGI2");
        // La identidad no se edita.
        assertThat(updated.isin()).isEqualTo("IE00BK5BQT80");
        assertThat(updated.currency()).isEqualTo("USD");
    }

    @Test
    void update_notFoundThrows() {
        when(securities.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(7L,
                new UpdateSecurityCommand("X", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
        verify(securities, never()).save(any());
    }

    @Test
    void delete_withOperationsThrowsConflict() {
        when(transactions.existsBySecurity(ID)).thenReturn(true);

        assertThatThrownBy(() -> service().delete(7L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("operaciones");
        verify(securities, never()).deleteById(any());
    }

    @Test
    void delete_happyPathDeletesById() {
        when(transactions.existsBySecurity(ID)).thenReturn(false);

        service().delete(7L);

        verify(securities).deleteById(ID);
    }
}
