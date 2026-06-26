package com.xroig.finance.transfers.infrastructure.web;

import com.xroig.finance.dto.ImportDtos.ImportResult;
import com.xroig.finance.service.ImportService;
import com.xroig.finance.transfers.application.TransferView;
import com.xroig.finance.transfers.application.port.CreateTransfer;
import com.xroig.finance.transfers.application.port.CreateTransfer.TransferCommand;
import com.xroig.finance.transfers.application.port.DeleteTransfer;
import com.xroig.finance.transfers.application.port.FindTransfers;
import com.xroig.finance.transfers.application.port.UpdateTransfer;
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
 * {@code /import} endpoint still delegates to the legacy {@code ImportService} (H7).
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final FindTransfers findTransfers;
    private final CreateTransfer createTransfer;
    private final UpdateTransfer updateTransfer;
    private final DeleteTransfer deleteTransfer;
    private final ImportService importService;

    public TransferController(FindTransfers findTransfers, CreateTransfer createTransfer,
                             UpdateTransfer updateTransfer, DeleteTransfer deleteTransfer,
                             ImportService importService) {
        this.findTransfers = findTransfers;
        this.createTransfer = createTransfer;
        this.updateTransfer = updateTransfer;
        this.deleteTransfer = deleteTransfer;
        this.importService = importService;
    }

    @PostMapping("/import")
    public ImportResult importFile(@RequestParam("file") MultipartFile file) {
        return importService.importTransfers(file);
    }

    @GetMapping
    public List<TransferView> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return findTransfers.search(start, end, accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferView create(@Valid @RequestBody TransferRequest request) {
        return createTransfer.create(toCommand(request));
    }

    @PutMapping("/{id}")
    public TransferView update(@PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return updateTransfer.update(id, toCommand(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        deleteTransfer.delete(id);
    }

    private static TransferCommand toCommand(TransferRequest r) {
        return new TransferCommand(r.date(), r.amount(), r.description(), r.fromAccountId(), r.toAccountId());
    }
}
