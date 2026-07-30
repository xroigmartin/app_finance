package com.xroig.finance.investments.infrastructure.web;

import com.xroig.finance.investments.application.InvestmentTransactionView;
import com.xroig.finance.investments.application.port.CreateInvestmentTransaction;
import com.xroig.finance.investments.application.port.DeleteInvestmentTransaction;
import com.xroig.finance.investments.application.port.FindInvestmentTransactions;
import com.xroig.finance.investments.application.port.FindInvestmentTransactions.TransactionFilter;
import com.xroig.finance.investments.application.port.UpdateInvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.shared.domain.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Inbound web adapter for the portfolio operations (RF-2, §6). Thin by design:
 * (de)serializes the DTO and delegates to the inbound ports; the views serialize
 * as-is. Domain failures map to HTTP in {@code shared.web.DomainExceptionHandler}
 * (§3 signs and RN-4 hard guard → 400, unknown ids → 404).
 */
@RestController
@RequestMapping("/api/investments")
@Tag(name = "Operaciones de cartera", description = "Compras, ventas, dividendos y demás operaciones manuales sobre una cartera. Las ventas están sujetas a la RN-4: no pueden superar la cantidad en posición a esa fecha.")
public class InvestmentTransactionController {

    private final FindInvestmentTransactions findTransactions;
    private final CreateInvestmentTransaction createTransaction;
    private final UpdateInvestmentTransaction updateTransaction;
    private final DeleteInvestmentTransaction deleteTransaction;

    public InvestmentTransactionController(FindInvestmentTransactions findTransactions,
                                           CreateInvestmentTransaction createTransaction,
                                           UpdateInvestmentTransaction updateTransaction,
                                           DeleteInvestmentTransaction deleteTransaction) {
        this.findTransactions = findTransactions;
        this.createTransaction = createTransaction;
        this.updateTransaction = updateTransaction;
        this.deleteTransaction = deleteTransaction;
    }

    @Operation(summary = "Listar operaciones de una cartera", description = "Filtrable por tipo, rango de fechas e instrumento; paginado.")
    @ApiResponse(responseCode = "200", description = "Página de operaciones")
    @ApiResponse(responseCode = "400", description = "page/size inválidos", content = @Content)
    @ApiResponse(responseCode = "404", description = "Cartera no encontrada", content = @Content)
    @GetMapping("/portfolios/{id}/transactions")
    public Page<InvestmentTransactionView> find(
            @PathVariable Long id,
            @RequestParam(required = false) InvestmentTransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long securityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return findTransactions.find(id, new TransactionFilter(type, from, to, securityId), page, size);
    }

    @Operation(summary = "Crear operación en una cartera", description = "El signo de cantidad/importe debe ser coherente con el tipo de operación; una venta no puede superar la posición mantenida a esa fecha (RN-4).")
    @ApiResponse(responseCode = "201", description = "Operación creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, signos incoherentes con el tipo, o venta sin posición suficiente (RN-4)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Cartera o instrumento no encontrado", content = @Content)
    @PostMapping("/portfolios/{id}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InvestmentTransactionView create(@PathVariable Long id,
                                            @Valid @RequestBody InvestmentTransactionRequest request) {
        return createTransaction.create(id, request.toCommand());
    }

    @Operation(summary = "Actualizar operación")
    @ApiResponse(responseCode = "200", description = "Operación actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, signos incoherentes con el tipo, o venta sin posición suficiente (RN-4)", content = @Content)
    @ApiResponse(responseCode = "404", description = "Operación o instrumento no encontrado", content = @Content)
    @PutMapping("/transactions/{id}")
    public InvestmentTransactionView update(@PathVariable Long id,
                                            @Valid @RequestBody InvestmentTransactionRequest request) {
        return updateTransaction.update(id, request.toCommand());
    }

    @Operation(summary = "Eliminar operación", description = "Idempotente: no falla si la operación no existía.")
    @ApiResponse(responseCode = "204", description = "Operación eliminada (o inexistente)")
    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteTransaction.delete(id);
    }
}
