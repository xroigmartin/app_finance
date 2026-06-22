package com.xroig.finance.shared.domain;

/**
 * Base of the domain's own failures. The domain speaks in these — never in
 * HTTP terms; a web adapter ({@code shared.web.DomainExceptionHandler})
 * translates each subtype to a status. Keeping it abstract forces callers to
 * pick a concrete, meaningful subtype ({@link NotFoundException},
 * {@link ConflictException}, {@link ValidationException}).
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
