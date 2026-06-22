package com.xroig.finance.shared.domain;

/** A value or invariant of the request is invalid for the domain. Maps to HTTP 400. */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(message);
    }
}
