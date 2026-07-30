package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.port.CreateSecurity;
import com.xroig.finance.investments.application.port.CreateSecurity.CreateSecurityCommand;
import com.xroig.finance.investments.application.port.DeleteSecurity;
import com.xroig.finance.investments.application.port.FindSecurities;
import com.xroig.finance.investments.application.port.RefreshPrices;
import com.xroig.finance.investments.application.port.UpdateSecurity;
import com.xroig.finance.investments.application.port.UpdateSecurity.UpdateSecurityCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Inbound web adapter for the instrument catalogue (§6). Thin by design: DTOs in,
 * inbound ports out. The duplicate ISIN+currency identity and the RN-5 deletion
 * guard surface as 409 through {@code shared.web.DomainExceptionHandler}.
 */
@RestController
@RequestMapping("/api/investments/securities")
@Tag(name = "Instrumentos (inversiones)", description = "Catálogo de valores/instrumentos (acciones, ETF, fondos...) identificados por ISIN+divisa, usados por las operaciones de cartera.")
public class SecurityController {

    private final FindSecurities findSecurities;
    private final CreateSecurity createSecurity;
    private final UpdateSecurity updateSecurity;
    private final DeleteSecurity deleteSecurity;
    private final RefreshPrices refreshPrices;

    public SecurityController(FindSecurities findSecurities, CreateSecurity createSecurity,
                              UpdateSecurity updateSecurity, DeleteSecurity deleteSecurity,
                              RefreshPrices refreshPrices) {
        this.findSecurities = findSecurities;
        this.createSecurity = createSecurity;
        this.updateSecurity = updateSecurity;
        this.deleteSecurity = deleteSecurity;
        this.refreshPrices = refreshPrices;
    }

    @Operation(summary = "Listar instrumentos")
    @ApiResponse(responseCode = "200", description = "Catálogo de instrumentos")
    @GetMapping
    public List<SecurityResponse> findAll() {
        return findSecurities.all().stream().map(SecurityResponse::from).toList();
    }

    @Operation(summary = "Crear instrumento")
    @ApiResponse(responseCode = "201", description = "Instrumento creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (ISIN o nombre vacío)", content = @Content)
    @ApiResponse(responseCode = "409", description = "Ya existe un instrumento con ese ISIN y divisa", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SecurityResponse create(@Valid @RequestBody SecurityRequest request) {
        return SecurityResponse.from(createSecurity.create(new CreateSecurityCommand(
                request.isin(), request.currency(), request.name(),
                request.ticker(), request.type(), request.exchange(), request.figi())));
    }

    @Operation(summary = "Actualizar instrumento", description = "El ISIN y la divisa no se pueden cambiar (identidad del instrumento).")
    @ApiResponse(responseCode = "200", description = "Instrumento actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (nombre vacío)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Instrumento no encontrado", content = @Content)
    @PutMapping("/{id}")
    public SecurityResponse update(@PathVariable Long id, @Valid @RequestBody SecurityRequest request) {
        return SecurityResponse.from(updateSecurity.update(id, new UpdateSecurityCommand(
                request.name(), request.ticker(), request.type(), request.exchange(), request.figi())));
    }

    @Operation(summary = "Eliminar instrumento", description = "Rechazado si tiene operaciones asociadas. Idempotente en lo demás: no falla si el instrumento no existía.")
    @ApiResponse(responseCode = "204", description = "Instrumento eliminado (o inexistente)")
    @ApiResponse(responseCode = "409", description = "El instrumento tiene operaciones asociadas y no puede eliminarse", content = @Content)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteSecurity.delete(id);
    }

    /** Global, on-demand refresh of the whole catalogue's prices (§2.6) — not scoped to a portfolio. */
    @PostMapping("/prices/refresh")
    public PriceRefreshResponse refreshPrices() {
        return PriceRefreshResponse.from(refreshPrices.refresh());
    }
}
