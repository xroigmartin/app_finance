package com.xroig.finance.shared.domain;

/** An aggregate referenced by identity does not exist. Maps to HTTP 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
