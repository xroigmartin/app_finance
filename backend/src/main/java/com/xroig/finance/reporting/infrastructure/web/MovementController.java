package com.xroig.finance.reporting.infrastructure.web;

import com.xroig.finance.reporting.application.MovementView;
import com.xroig.finance.reporting.application.port.FindMovements;
import com.xroig.finance.shared.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Inbound web adapter for the combined "Movimientos" feed (transactions +
 * transfers, paginated). Thin: delegates to {@link FindMovements}; an omitted
 * {@code from}/{@code to} defaults to a wide range, mirroring {@code
 * TransactionController}/{@code TransferController}.
 */
@RestController
@RequestMapping("/api/movements")
public class MovementController {

    private final FindMovements findMovements;

    public MovementController(FindMovements findMovements) {
        this.findMovements = findMovements;
    }

    @GetMapping
    public Page<MovementView> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2999, 12, 31);
        return findMovements.findMovements(start, end, accountId, categoryId, page, size);
    }
}
