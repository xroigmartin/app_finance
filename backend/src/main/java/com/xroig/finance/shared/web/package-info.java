/**
 * Shared web adapters: cross-cutting inbound-side infrastructure, currently the
 * {@code DomainExceptionHandler} that maps {@code DomainException} to HTTP. Part
 * of infrastructure (knows Spring/HTTP); the domain does not depend on it.
 */
package com.xroig.finance.shared.web;
