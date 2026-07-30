package com.xroig.finance.transactions.infrastructure.web;

import com.xroig.finance.imports.application.ImportResult;
import com.xroig.finance.imports.application.port.ImportTransactions;
import com.xroig.finance.transactions.application.TransactionView;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import com.xroig.finance.transactions.application.port.DeleteTransaction;
import com.xroig.finance.transactions.application.port.FindTransactions;
import com.xroig.finance.transactions.application.port.UpdateTransaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound web adapter for the transactions context. Thin: it (de)serializes DTOs and
 * delegates to the inbound ports, returning the {@link TransactionView} read model.
 *
 * <p>The {@code /import} endpoint forwards the upload to the imports context's {@link
 * ImportTransactions} use case.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Movimientos (transacciones)", description = "Ingresos y gastos, incluyendo devoluciones (parciales) de un gasto previo, y su importación desde extractos bancarios.")
public class TransactionController {

    private final FindTransactions findTransactions;
    private final CreateTransaction createTransaction;
    private final UpdateTransaction updateTransaction;
    private final DeleteTransaction deleteTransaction;
    private final ImportTransactions importTransactions;

    public TransactionController(FindTransactions findTransactions, CreateTransaction createTransaction,
                                UpdateTransaction updateTransaction, DeleteTransaction deleteTransaction,
                                ImportTransactions importTransactions) {
        this.findTransactions = findTransactions;
        this.createTransaction = createTransaction;
        this.updateTransaction = updateTransaction;
        this.deleteTransaction = deleteTransaction;
        this.importTransactions = importTransactions;
    }

    @Operation(summary = "Importar extracto bancario (movimientos)",
            description = "Acepta CSV o XLS/XLSX. Detecta la cabecera tras preámbulos del banco, fechas dd/MM/yyyy o ISO, importes con '.'/',' y separador ','/';'. Las categorías desconocidas se crean como globales; las cuentas deben existir ya. accountId es la cuenta por defecto para filas sin cuenta explícita. Las filas válidas se importan aunque otras fallen; los duplicados (misma cuenta/fecha/tipo/importe/descripción) se omiten y se cuentan aparte.")
    @ApiResponse(responseCode = "200", description = "Resultado: nº importadas, nº duplicadas y lista de errores por fila (no aborta el import completo)")
    @ApiResponse(responseCode = "400", description = "Fichero ilegible o de formato no soportado", content = @Content)
    @PostMapping("/import")
    public ImportResult importFile(@Parameter(description = "Fichero .csv o .xls/.xlsx del banco") @RequestParam("file") MultipartFile file,
                                   @Parameter(description = "Cuenta por defecto para las filas sin cuenta explícita") @RequestParam(required = false) Long accountId) {
        return importTransactions.importTransactions(file, accountId);
    }

    @Operation(summary = "Buscar movimientos", description = "from/to por defecto cubren todo el rango de fechas posible.")
    @ApiResponse(responseCode = "200", description = "Movimientos que cumplen el filtro")
    @GetMapping
    public List<TransactionView> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return findTransactions.search(start, end, accountId, categoryId);
    }

    @Operation(summary = "Movimientos recientes", description = "Últimos movimientos para el widget del dashboard.")
    @ApiResponse(responseCode = "200", description = "Movimientos recientes")
    @GetMapping("/recent")
    public List<TransactionView> recent() {
        return findTransactions.recent();
    }

    @Operation(summary = "Crear movimiento", description = "Si refundOfId está presente, se crea como devolución (parcial) de un gasto existente; si no, como ingreso o gasto normal.")
    @ApiResponse(responseCode = "201", description = "Movimiento creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, cuenta/categoría no válida, categoría de otra cuenta, o regla de devolución violada (gasto original inexistente, ya reembolsado, devolución de una devolución, etc.)", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionView create(@Valid @RequestBody TransactionRequest request) {
        return createTransaction.create(toCommand(request));
    }

    @Operation(summary = "Actualizar movimiento")
    @ApiResponse(responseCode = "200", description = "Movimiento actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, cuenta/categoría no válida, o regla de devolución violada", content = @Content)
    @ApiResponse(responseCode = "404", description = "Movimiento no encontrado", content = @Content)
    @PutMapping("/{id}")
    public TransactionView update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return updateTransaction.update(id, toCommand(request));
    }

    @Operation(summary = "Eliminar movimiento", description = "Idempotente: no falla si el movimiento no existía.")
    @ApiResponse(responseCode = "204", description = "Movimiento eliminado (o inexistente)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteTransaction.delete(id);
    }

    private static TransactionCommand toCommand(TransactionRequest r) {
        return new TransactionCommand(r.date(), r.amount(), r.description(), r.type(),
                r.accountId(), r.categoryId(), r.refundOfId());
    }
}
