package com.xroig.finance.investments.application;

import com.xroig.finance.investments.application.port.ImportFlexReport;
import com.xroig.finance.investments.domain.CurrencyConverter;
import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.ExchangeRateRepository;
import com.xroig.finance.investments.domain.ImportRecord;
import com.xroig.finance.investments.domain.ImportRecordRepository;
import com.xroig.finance.investments.domain.ImportRowIssue;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioPositions;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.investments.domain.PositionCalculator;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.PriceQuoteRepository;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Application service for the Flex import use case (RF-3/RF-4): reads the report
 * through the {@link FlexReportReader} ACL, validates the account↔portfolio base
 * currency before touching any row (§8 — the {@code fxRateToBase} snapshots point
 * to the account's base, RN-7a) and persists rows/quotes/rates through the
 * outbound ports.
 */
@Service
@Transactional
public class FlexImportService implements ImportFlexReport {

    /** Business-tier logger (RN-4 of docs/prd/observabilidad.md): "business.<contexto>". */
    private static final Logger businessLog = LoggerFactory.getLogger("business.investments");

    private final FlexReportReader reader;
    private final PortfolioRepository portfolios;
    private final SecurityRepository securities;
    private final InvestmentTransactionRepository transactions;
    private final PriceQuoteRepository priceQuotes;
    private final ExchangeRateRepository exchangeRates;
    private final ImportRecordRepository importRecords;

    public FlexImportService(FlexReportReader reader, PortfolioRepository portfolios,
                             SecurityRepository securities, InvestmentTransactionRepository transactions,
                             PriceQuoteRepository priceQuotes, ExchangeRateRepository exchangeRates,
                             ImportRecordRepository importRecords) {
        this.reader = reader;
        this.portfolios = portfolios;
        this.securities = securities;
        this.transactions = transactions;
        this.priceQuotes = priceQuotes;
        this.exchangeRates = exchangeRates;
        this.importRecords = importRecords;
    }

    @Override
    public FlexImportResult importReport(long portfolioId, MultipartFile file) {
        Portfolio portfolio = portfolios.findById(new PortfolioId(portfolioId))
                .orElseThrow(() -> new NotFoundException("Cartera no encontrada"));

        FlexReport report = reader.read(file);
        if (!portfolio.baseCurrency().equals(report.baseCurrency())) {
            throw new ValidationException(
                    "La divisa base de la cuenta del informe (" + report.baseCurrency()
                            + ") no coincide con la de la cartera (" + portfolio.baseCurrency()
                            + "): import rechazado (§8)");
        }

        int imported = 0;
        int duplicated = 0;
        List<FlexRowError> errors = new ArrayList<>(report.errors());
        Set<String> seenExternalIds = new HashSet<>();
        Map<String, Security> resolvedSecurities = new HashMap<>();
        for (FlexRow row : report.rows()) {
            try {
                Security security = row.instrument() == null
                        ? null
                        : resolveSecurity(resolvedSecurities, row.instrument());
                if (!seenExternalIds.add(row.externalId())
                        || transactions.existsByPortfolioAndExternalId(portfolio.id(), row.externalId())) {
                    duplicated++;
                    continue;
                }
                transactions.save(toTransaction(portfolio, row, security));
                imported++;
            } catch (Exception e) {
                errors.add(new FlexRowError(String.valueOf(row.type()), row.externalId(), e.getMessage()));
                logRejectedRow(portfolio, row, e);
            }
        }
        for (FlexQuote quote : report.quotes()) {
            Security security = resolveSecurity(resolvedSecurities, quote.instrument());
            priceQuotes.upsert(PriceQuote.of(security.id(), quote.quoteDate(), quote.price()));
        }
        report.exchangeRates().forEach(exchangeRates::upsert);

        FlexImportResult result = new FlexImportResult(imported, duplicated, List.copyOf(errors),
                positionWarnings(portfolio));
        importRecords.save(ImportRecord.of(portfolio.id(), Instant.now(), file.getOriginalFilename(),
                report.fromDate(), report.toDate(), result.imported(), result.duplicated(),
                toRowIssues(result.errors()), result.warnings()));
        return result;
    }

    private static List<ImportRowIssue> toRowIssues(List<FlexRowError> errors) {
        return errors.stream()
                .map(error -> new ImportRowIssue(error.section(), error.reference(), error.message()))
                .toList();
    }

    /**
     * Business-log trace of a rejected row (RF-5 of {@code docs/prd/observabilidad.md}):
     * the fields a domain sign-convention violation needs to be diagnosed from
     * {@code business.log} alone, without opening the source Flex file. Never
     * includes the report's real account id — only the app's own {@code portfolio_id}.
     */
    private void logRejectedRow(Portfolio portfolio, FlexRow row, Exception e) {
        var event = businessLog.atWarn()
                .setMessage("import_row_rejected")
                .addKeyValue("action", "ImportFlexReport")
                .addKeyValue("portfolio_id", portfolio.id().value())
                .addKeyValue("type", row.type())
                .addKeyValue("external_id", row.externalId())
                .addKeyValue("trade_date", row.tradeDate())
                .addKeyValue("amount", row.amount())
                .addKeyValue("currency", row.currency())
                .addKeyValue("reason", e.getMessage());
        if (row.instrument() != null) {
            event = event.addKeyValue("security_isin", row.instrument().isin())
                    .addKeyValue("security_ticker", row.instrument().ticker());
        }
        if (row.description() != null) {
            event = event.addKeyValue("description", row.description());
        }
        event.log();
    }

    /**
     * Recomputes the portfolio positions after importing and surfaces the
     * calculator's non-blocking incidents (sale without enough position — RN-4's
     * import side —, missing rates) as summary warnings.
     */
    private List<String> positionWarnings(Portfolio portfolio) {
        PortfolioPositions positions = new PositionCalculator().calculate(
                portfolio.baseCurrency(),
                transactions.findByPortfolio(portfolio.id()),
                new CurrencyConverter(exchangeRates.findAll()));
        return positions.warnings().stream()
                .map(warning -> warning.tradeDate() + ": " + warning.message())
                .toList();
    }

    /**
     * Resolves the row's instrument against the catalog by its ISIN+currency
     * identity, auto-registering unknown ones and refreshing the non-identity
     * metadata of known ones (RN-9) — once per identity and file.
     */
    private Security resolveSecurity(Map<String, Security> cache, FlexInstrument instrument) {
        return cache.computeIfAbsent(instrument.isin() + "/" + instrument.currency(), key ->
                securities.findByIsinAndCurrency(instrument.isin(), instrument.currency())
                        .map(existing -> {
                            existing.refreshMetadata(instrument.name(), instrument.ticker(),
                                    instrument.exchange(), instrument.figi());
                            return securities.save(existing);
                        })
                        .orElseGet(() -> securities.save(Security.create(
                                instrument.isin(), instrument.currency(), instrument.name(),
                                instrument.ticker(), null, instrument.exchange(), instrument.figi()))));
    }

    private InvestmentTransaction toTransaction(Portfolio portfolio, FlexRow row, Security security) {
        return InvestmentTransaction.builder()
                .portfolio(portfolio.id())
                .security(security == null ? null : security.id())
                .type(row.type())
                .tradeDate(row.tradeDate())
                .quantity(row.quantity() == null ? null : Quantity.of(row.quantity()))
                .price(row.price())
                .amount(CurrencyMoney.of(row.amount(), row.currency()))
                .counterAmount(row.counterAmount() == null ? null
                        : CurrencyMoney.of(row.counterAmount(), row.counterCurrency()))
                .fee(row.fee() == null ? null
                        : CurrencyMoney.of(row.fee(), row.feeCurrency() == null ? row.currency() : row.feeCurrency()))
                .fxRateToBase(row.fxRateToBase())
                .description(row.description())
                .externalId(row.externalId())
                .build();
    }
}
