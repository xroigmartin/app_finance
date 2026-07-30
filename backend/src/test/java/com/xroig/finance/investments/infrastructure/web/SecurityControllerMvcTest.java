package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.PriceRefreshFailure;
import com.xroig.finance.investments.application.PriceRefreshResult;
import com.xroig.finance.investments.application.port.CreateSecurity;
import com.xroig.finance.investments.application.port.CreateSecurity.CreateSecurityCommand;
import com.xroig.finance.investments.application.port.DeleteSecurity;
import com.xroig.finance.investments.application.port.FindSecurities;
import com.xroig.finance.investments.application.port.RefreshPrices;
import com.xroig.finance.investments.application.port.UpdateSecurity;
import com.xroig.finance.investments.application.port.UpdateSecurity.UpdateSecurityCommand;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HTTP contract of the {@link SecurityController} (§6): instrument catalogue CRUD
 * under {@code /api/investments/securities}, {@code @Valid} → 400, duplicate
 * identity → 409 and the RN-5 deletion guard → 409 {@code problem+json}.
 */
@WebMvcTest(SecurityController.class)
class SecurityControllerMvcTest {

    @Autowired private MockMvcTester mvc;

    @MockitoBean private FindSecurities findSecurities;
    @MockitoBean private CreateSecurity createSecurity;
    @MockitoBean private UpdateSecurity updateSecurity;
    @MockitoBean private DeleteSecurity deleteSecurity;
    @MockitoBean private RefreshPrices refreshPrices;

    private static Security security(long id) {
        return Security.rehydrate(new SecurityId(id), "IE00BK5BQT80", "EUR",
                "Vanguard FTSE All-World", "VWCE", "ETF", "AEB", "BBG00LPTPGD4");
    }

    @Test
    void findAll_returns200WithJsonArray() {
        when(findSecurities.all()).thenReturn(List.of(security(3L)));

        assertThat(mvc.get().uri("/api/investments/securities"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().extractingPath("$[0].isin").isEqualTo("IE00BK5BQT80");
    }

    @Test
    void create_valid_returns201WithEveryField() {
        when(createSecurity.create(any(CreateSecurityCommand.class))).thenReturn(security(3L));

        assertThat(mvc.post().uri("/api/investments/securities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isin":"IE00BK5BQT80","currency":"EUR","name":"Vanguard FTSE All-World",
                         "ticker":"VWCE","type":"ETF","exchange":"AEB","figi":"BBG00LPTPGD4"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.ticker").isEqualTo("VWCE");
    }

    @Test
    void create_blankName_returns400AndDoesNotCallUseCase() {
        assertThat(mvc.post().uri("/api/investments/securities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isin":"IE00BK5BQT80","currency":"EUR","name":""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verify(createSecurity, never()).create(any());
    }

    @Test
    void create_duplicateIdentity_returns409ProblemJson() {
        when(createSecurity.create(any(CreateSecurityCommand.class)))
                .thenThrow(new ConflictException("Ya existe un instrumento con ese ISIN y divisa"));

        assertThat(mvc.post().uri("/api/investments/securities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isin":"IE00BK5BQT80","currency":"EUR","name":"Duplicado"}
                        """))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void update_metadata_returns200() {
        when(updateSecurity.update(anyLong(), any(UpdateSecurityCommand.class))).thenReturn(security(3L));

        assertThat(mvc.put().uri("/api/investments/securities/3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Vanguard FTSE All-World","ticker":"VWCE"}
                        """))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Vanguard FTSE All-World");
    }

    @Test
    void update_missingSecurity_returns404ProblemJson() {
        when(updateSecurity.update(anyLong(), any(UpdateSecurityCommand.class)))
                .thenThrow(new NotFoundException("Instrumento no encontrado"));

        assertThat(mvc.put().uri("/api/investments/securities/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Da igual"}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void delete_returns204() {
        assertThat(mvc.delete().uri("/api/investments/securities/3"))
                .hasStatus(HttpStatus.NO_CONTENT);
        verify(deleteSecurity).delete(3L);
    }

    @Test
    void delete_withOperations_returns409ProblemJson() {
        doThrow(new ConflictException("El instrumento tiene operaciones asociadas y no se puede eliminar"))
                .when(deleteSecurity).delete(3L);

        assertThat(mvc.delete().uri("/api/investments/securities/3"))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void refreshPrices_returns200WithSummary() {
        when(refreshPrices.refresh()).thenReturn(new PriceRefreshResult(
                12, List.of(new PriceRefreshFailure(9L, "ZEG", "Sin cotización disponible"))));

        assertThat(mvc.post().uri("/api/investments/securities/prices/refresh"))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().extractingPath("$.updated").isEqualTo(12);
        verify(refreshPrices).refresh();
    }
}
