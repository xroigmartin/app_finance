/**
 * Investments bounded context — infrastructure layer. {@code persistence} holds
 * the JPA entities mapped to the separate {@code investments} PostgreSQL schema
 * (V7 migration), the Spring Data repositories, the mappers and the adapters
 * implementing the domain's outbound ports (including the RN-9 upsert contracts
 * of quotes and exchange rates). Depends inward on application/domain, never the
 * reverse.
 */
package com.xroig.finance.investments.infrastructure;
