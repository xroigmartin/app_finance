package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.CreateSecurity;
import com.xroig.finance.investments.application.port.DeleteSecurity;
import com.xroig.finance.investments.application.port.FindSecurities;
import com.xroig.finance.investments.application.port.UpdateSecurity;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.ConflictException;
import com.xroig.finance.shared.domain.NotFoundException;

import java.util.List;

/**
 * Application service for the instrument catalogue use cases: orchestrates the
 * aggregate and the outbound ports. Invariants live in {@link Security}; here we
 * resolve identity, guard the ISIN+currency duplicate proactively (the DB unique
 * constraint is the physical backstop), map "not found" and apply the RN-5
 * deletion guard (an instrument with operations cannot be deleted).
 *
 * <p>Deliberately not a Spring bean yet: it becomes {@code @Service}/
 * {@code @Transactional} in H1.6, when the persistence adapters that satisfy its
 * ports exist (until then the full application context could not start).
 */
public class SecurityService implements FindSecurities, CreateSecurity, UpdateSecurity, DeleteSecurity {

    private final SecurityRepository securities;
    private final InvestmentTransactionRepository transactions;

    public SecurityService(SecurityRepository securities, InvestmentTransactionRepository transactions) {
        this.securities = securities;
        this.transactions = transactions;
    }

    @Override
    public List<Security> all() {
        return securities.findAll();
    }

    @Override
    public Security create(CreateSecurityCommand command) {
        Security security = Security.create(command.isin(), command.currency(), command.name(),
                command.ticker(), command.type(), command.exchange(), command.figi());
        securities.findByIsinAndCurrency(security.isin(), security.currency())
                .ifPresent(existing -> {
                    throw new ConflictException("Ya existe un instrumento con ese ISIN y divisa");
                });
        return securities.save(security);
    }

    @Override
    public Security update(long id, UpdateSecurityCommand command) {
        Security security = securities.findById(new SecurityId(id))
                .orElseThrow(() -> new NotFoundException("Instrumento no encontrado"));
        security.refreshMetadata(command.name(), command.ticker(), command.exchange(), command.figi());
        security.changeType(command.type());
        return securities.save(security);
    }

    @Override
    public void delete(long id) {
        SecurityId securityId = new SecurityId(id);
        if (transactions.existsBySecurity(securityId)) {
            throw new ConflictException("El instrumento tiene operaciones asociadas y no se puede eliminar");
        }
        securities.deleteById(securityId);
    }
}
