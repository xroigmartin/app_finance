package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.CreatePortfolio;
import com.xroig.finance.investments.application.port.DeletePortfolio;
import com.xroig.finance.investments.application.port.FindPortfolios;
import com.xroig.finance.investments.application.port.UpdatePortfolio;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;

import java.util.List;

/**
 * Application service for the portfolio use cases (RF-1): orchestrates the
 * aggregate and the outbound ports. Holds no business rules of its own —
 * invariants live in {@link Portfolio}; here we resolve identity, map "not found"
 * and apply the RN-5 deletion guard (a portfolio with operations cannot be
 * deleted).
 *
 * <p>Deliberately not a Spring bean yet: it becomes {@code @Service}/
 * {@code @Transactional} in H1.6, when the persistence adapters that satisfy its
 * ports exist (until then the full application context could not start).
 */
public class PortfolioService implements FindPortfolios, CreatePortfolio, UpdatePortfolio, DeletePortfolio {

    private final PortfolioRepository portfolios;
    private final InvestmentTransactionRepository transactions;

    public PortfolioService(PortfolioRepository portfolios, InvestmentTransactionRepository transactions) {
        this.portfolios = portfolios;
        this.transactions = transactions;
    }

    @Override
    public List<Portfolio> all() {
        return portfolios.findAll();
    }

    @Override
    public Portfolio create(CreatePortfolioCommand command) {
        return portfolios.save(Portfolio.create(command.name(), command.baseCurrency()));
    }

    @Override
    public Portfolio update(long id, UpdatePortfolioCommand command) {
        Portfolio portfolio = portfolios.findById(new PortfolioId(id))
                .orElseThrow(() -> new NotFoundException("Cartera no encontrada"));
        portfolio.rename(command.name());
        return portfolios.save(portfolio);
    }

    @Override
    public void delete(long id) {
        PortfolioId portfolioId = new PortfolioId(id);
        if (transactions.existsByPortfolio(portfolioId)) {
            throw new ConflictException("La cartera tiene operaciones asociadas y no se puede eliminar");
        }
        portfolios.deleteById(portfolioId);
    }
}
