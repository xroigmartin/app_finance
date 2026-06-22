package com.xroig.finance.shared.domain;

/** The operation clashes with the current state (a uniqueness or guard rule). Maps to HTTP 409. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
