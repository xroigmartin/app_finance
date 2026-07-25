/**
 * Reporting bounded context — persistence adapters (read-only). They implement the
 * reporting outbound query ports resolving the read figures with the existing aggregation
 * queries.
 *
 * <p><b>Strangler note (H8):</b> these adapters reuse the legacy {@code repository.*}
 * Spring Data repositories (and their {@code @Query} aggregations over the legacy {@code
 * model.*} entities), the same transitional tie {@code BudgetQueryAdapter} already has.
 * H9 will repoint them onto the migrated JPA repositories and delete the legacy ones.
 */
package com.xroig.finance.reporting.infrastructure.persistence;
