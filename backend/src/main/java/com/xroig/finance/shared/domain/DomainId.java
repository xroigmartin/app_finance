package com.xroig.finance.shared.domain;

/**
 * Marker for the typed identifiers of each aggregate ({@code AccountId},
 * {@code CategoryId}, …). Aggregates reference one another by identity, never by
 * object graph, so these wrappers replace the raw {@code Long} keys at the domain
 * boundary. Each context defines its own {@code record XId(Long value) implements DomainId}.
 */
public interface DomainId {

    Long value();
}
