package com.xroig.finance.transfers.infrastructure.web;

import com.xroig.finance.imports.application.ImportResult;
import com.xroig.finance.imports.application.port.ImportTransfers;
import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.application.port.CreateTransfer;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;
import com.xroig.finance.transfers.application.port.DeleteTransfer;
import com.xroig.finance.transfers.application.port.FindTransfers;
import com.xroig.finance.transfers.application.port.UpdateTransfer;
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
 * Inbound web adapter for the transfers context. Thin: it (de)serializes DTOs and
 * delegates to the inbound ports, returning the {@link TransferView} read model. The
 * {@code /import} endpoint forwards the upload to the imports context's {@link ImportTransfers} use case.
 */
@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transferencias", description = "Movimientos entre dos cuentas propias (excluidos de los agregados de ingresos/gastos), y su importación desde extractos.")
public class TransferController {

    private final FindTransfers findTransfers;
    private final CreateTransfer createTransfer;
    private final UpdateTransfer updateTransfer;
    private final DeleteTransfer deleteTransfer;
    private final ImportTransfers importTransfers;

    public TransferController(FindTransfers findTransfers, CreateTransfer createTransfer,
                             UpdateTransfer updateTransfer, DeleteTransfer deleteTransfer,
                             ImportTransfers importTransfers) {
        this.findTransfers = findTransfers;
        this.createTransfer = createTransfer;
        this.updateTransfer = updateTransfer;
        this.deleteTransfer = deleteTransfer;
        this.importTransfers = importTransfers;
    }

    @Operation(summary = "Importar extracto bancario (transferencias)",
            description = "Requiere columnas de origen/destino ('origen'/'desde'/'from' y 'destino'/'hasta'/'to'); si no las tiene, rechaza el fichero (probablemente es un extracto normal a importar desde Movimientos). Filas válidas se importan aunque otras fallen; los duplicados se omiten y se cuentan aparte.")
    @ApiResponse(responseCode = "200", description = "Resultado: nº importadas, nº duplicadas y lista de errores por fila")
    @ApiResponse(responseCode = "400", description = "Fichero ilegible, formato no soportado, o sin columnas de origen/destino", content = @Content)
    @PostMapping("/import")
    public ImportResult importFile(@Parameter(description = "Fichero .csv o .xls/.xlsx del banco") @RequestParam("file") MultipartFile file) {
        return importTransfers.importTransfers(file);
    }

    @Operation(summary = "Buscar transferencias", description = "from/to por defecto cubren todo el rango de fechas posible.")
    @ApiResponse(responseCode = "200", description = "Transferencias que cumplen el filtro")
    @GetMapping
    public List<TransferView> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return findTransfers.search(start, end, accountId);
    }

    @Operation(summary = "Crear transferencia")
    @ApiResponse(responseCode = "201", description = "Transferencia creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, cuenta origen/destino no válida, o mismo origen y destino", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferView create(@Valid @RequestBody TransferRequest request) {
        return createTransfer.create(toCommand(request));
    }

    @Operation(summary = "Actualizar transferencia")
    @ApiResponse(responseCode = "200", description = "Transferencia actualizada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos, cuenta origen/destino no válida, o mismo origen y destino", content = @Content)
    @ApiResponse(responseCode = "404", description = "Transferencia no encontrada", content = @Content)
    @PutMapping("/{id}")
    public TransferView update(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return updateTransfer.update(id, toCommand(request));
    }

    @Operation(summary = "Eliminar transferencia", description = "Idempotente: no falla si la transferencia no existía.")
    @ApiResponse(responseCode = "204", description = "Transferencia eliminada (o inexistente)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteTransfer.delete(id);
    }

    private static TransferCommand toCommand(TransferRequest r) {
        return new TransferCommand(r.date(), r.amount(), r.description(), r.fromAccountId(), r.toAccountId());
    }
}
