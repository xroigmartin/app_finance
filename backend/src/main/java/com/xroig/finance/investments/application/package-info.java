/**
 * Investments bounded context — application layer. The inbound ports (use cases)
 * live in {@code application.port};
 * {@link com.xroig.finance.investments.application.PortfolioService} and
 * {@link com.xroig.finance.investments.application.SecurityService} implement them
 * by orchestrating the domain aggregates and the outbound ports (RN-5 deletion
 * guards included). May use Spring stereotypes, but never depends on
 * {@code infrastructure}.
 */
package com.xroig.finance.investments.application;
