package com.xroig.finance.investments.application;

import com.xroig.finance.investments.domain.CurrencyMoney;
import com.xroig.finance.investments.domain.ExchangeRate;
import com.xroig.finance.investments.domain.ExchangeRateRepository;
import com.xroig.finance.investments.domain.InvestmentTransaction;
import com.xroig.finance.investments.domain.InvestmentTransactionRepository;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.investments.domain.Portfolio;
import com.xroig.finance.investments.domain.PortfolioId;
import com.xroig.finance.investments.domain.PortfolioRepository;
import com.xroig.finance.investments.domain.PriceQuote;
import com.xroig.finance.investments.domain.PriceQuoteRepository;
import com.xroig.finance.investments.domain.Quantity;
import com.xroig.finance.investments.domain.Security;
import com.xroig.finance.investments.domain.SecurityId;
import com.xroig.finance.investments.domain.SecurityRepository;
import com.xroig.finance.shared.domain.NotFoundException;
import com.xroig.finance.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Application tests for the Flex import use case (H1.10), mocking the outbound
 * ports: the pre-validation of the account↔portfolio base currency rejects the
 * whole import before touching any row (§8), and an unknown portfolio maps to
 * "not found". Row translation invariants live in {@code InvestmentTransactionTest};
 * the parser's own behavior in {@code FlexReportParserTest}.
 */
@ExtendWith(MockitoExtension.class)
class FlexImportServiceTest {

    @Mock private FlexReportReader reader;
    @Mock private PortfolioRepository portfolios;
    @Mock private SecurityRepository securities;
    @Mock private InvestmentTransactionRepository transactions;
    @Mock private PriceQuoteRepository priceQuotes;
    @Mock private ExchangeRateRepository exchangeRates;
    @Mock private MultipartFile file;

    private static final PortfolioId PORTFOLIO_ID = new PortfolioId(7L);

    private FlexImportService service() {
        return new FlexImportService(reader, portfolios, securities, transactions,
                priceQuotes, exchangeRates);
    }

    private void givenPortfolio(String baseCurrency) {
        when(portfolios.findById(PORTFOLIO_ID))
                .thenReturn(Optional.of(Portfolio.rehydrate(PORTFOLIO_ID, "IBKR", baseCurrency)));
    }

    private static FlexRow cashRow(InvestmentTransactionType type, String externalId, String amount) {
        return new FlexRow(type, externalId, LocalDate.of(2025, 3, 4), null, null,
                new BigDecimal(amount), "EUR", null, null, null, null, null, "Apunte", null);
    }

    private static FlexReport report(String baseCurrency, List<FlexRow> rows) {
        return new FlexReport("U1234567", baseCurrency,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                rows, List.of(), List.of(), List.of());
    }

    @Test
    void cashRow_isTranslatedPersistedAndCounted() {
        givenPortfolio("EUR");
        FlexRow deposit = new FlexRow(InvestmentTransactionType.DEPOSIT, "CT-100",
                LocalDate.of(2025, 3, 4), null, null,
                new BigDecimal("1500.00"), "EUR", null, null, null, null,
                null, "Ingreso inicial", null);
        when(reader.read(file)).thenReturn(report("EUR", List.of(deposit)));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.duplicated()).isZero();
        assertThat(result.errors()).isEmpty();
        ArgumentCaptor<InvestmentTransaction> saved = ArgumentCaptor.forClass(InvestmentTransaction.class);
        verify(transactions).save(saved.capture());
        InvestmentTransaction tx = saved.getValue();
        assertThat(tx.portfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(tx.type()).isEqualTo(InvestmentTransactionType.DEPOSIT);
        assertThat(tx.tradeDate()).isEqualTo(LocalDate.of(2025, 3, 4));
        assertThat(tx.amount()).isEqualTo(CurrencyMoney.of("1500.00", "EUR"));
        assertThat(tx.externalId()).isEqualTo("CT-100");
        assertThat(tx.description()).isEqualTo("Ingreso inicial");
        assertThat(tx.securityId()).isNull();
    }

    @Test
    void buyRowWithUnknownInstrument_autoRegistersTheSecurityAndLinksIt() {
        givenPortfolio("EUR");
        FlexInstrument instrument = new FlexInstrument("IE00B4L5Y983", "EUR",
                "iShares Core MSCI World", "IWDA", "AEB", "BBG00DDPPS37");
        FlexRow buy = new FlexRow(InvestmentTransactionType.BUY, "ORD-1",
                LocalDate.of(2025, 2, 10), new BigDecimal("10"), new BigDecimal("85.5"),
                new BigDecimal("-855.00"), "EUR", null, null,
                new BigDecimal("-2.00"), null, null, "Compra IWDA", instrument);
        when(reader.read(file)).thenReturn(report("EUR", List.of(buy)));
        when(securities.findByIsinAndCurrency("IE00B4L5Y983", "EUR")).thenReturn(Optional.empty());
        when(securities.save(any(Security.class))).thenAnswer(i -> {
            Security created = i.getArgument(0);
            return Security.rehydrate(new SecurityId(42L), created.isin(), created.currency(),
                    created.name(), created.ticker(), created.type(), created.exchange(), created.figi());
        });
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.imported()).isEqualTo(1);
        ArgumentCaptor<Security> registered = ArgumentCaptor.forClass(Security.class);
        verify(securities).save(registered.capture());
        assertThat(registered.getValue().isin()).isEqualTo("IE00B4L5Y983");
        assertThat(registered.getValue().name()).isEqualTo("iShares Core MSCI World");
        ArgumentCaptor<InvestmentTransaction> saved = ArgumentCaptor.forClass(InvestmentTransaction.class);
        verify(transactions).save(saved.capture());
        InvestmentTransaction tx = saved.getValue();
        assertThat(tx.securityId()).isEqualTo(new SecurityId(42L));
        assertThat(tx.quantity()).isEqualTo(Quantity.of("10"));
        assertThat(tx.price()).isEqualByComparingTo("85.5");
        assertThat(tx.fee()).isEqualTo(CurrencyMoney.of("-2.00", "EUR"));
    }

    @Test
    void knownInstrument_isReusedAndItsMetadataRefreshed() {
        givenPortfolio("EUR");
        FlexInstrument instrument = new FlexInstrument("IE00B4L5Y983", "EUR",
                "iShares Core MSCI World UCITS ETF", "IWDA", "AEB", "BBG00DDPPS37");
        FlexRow buy = new FlexRow(InvestmentTransactionType.BUY, "ORD-1",
                LocalDate.of(2025, 2, 10), new BigDecimal("10"), new BigDecimal("85.5"),
                new BigDecimal("-855.00"), "EUR", null, null, null, null, null, null, instrument);
        Security existing = Security.rehydrate(new SecurityId(42L), "IE00B4L5Y983", "EUR",
                "Nombre viejo", null, "ETF", null, null);
        when(reader.read(file)).thenReturn(report("EUR", List.of(buy)));
        when(securities.findByIsinAndCurrency("IE00B4L5Y983", "EUR")).thenReturn(Optional.of(existing));
        when(securities.save(any(Security.class))).thenAnswer(i -> i.getArgument(0));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        service().importReport(7L, file);

        ArgumentCaptor<Security> refreshed = ArgumentCaptor.forClass(Security.class);
        verify(securities).save(refreshed.capture());
        assertThat(refreshed.getValue().id()).isEqualTo(new SecurityId(42L));
        assertThat(refreshed.getValue().name()).isEqualTo("iShares Core MSCI World UCITS ETF");
        assertThat(refreshed.getValue().ticker()).isEqualTo("IWDA");
        assertThat(refreshed.getValue().figi()).isEqualTo("BBG00DDPPS37");
    }

    @Test
    void quotesAndRates_areUpserted() {
        givenPortfolio("EUR");
        FlexInstrument instrument = new FlexInstrument("US0378331005", "USD",
                "Apple Inc", "AAPL", "NASDAQ", "BBG000B9XRY4");
        FlexQuote quote = new FlexQuote(instrument, new BigDecimal("212.44"), LocalDate.of(2025, 12, 31));
        ExchangeRate rate = ExchangeRate.toEur(LocalDate.of(2025, 12, 31), "USD", "0.85");
        when(reader.read(file)).thenReturn(new FlexReport("U1234567", "EUR",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                List.of(), List.of(quote), List.of(rate), List.of()));
        Security apple = Security.rehydrate(new SecurityId(9L), "US0378331005", "USD",
                "Apple Inc", "AAPL", null, "NASDAQ", "BBG000B9XRY4");
        when(securities.findByIsinAndCurrency("US0378331005", "USD")).thenReturn(Optional.of(apple));
        when(securities.save(any(Security.class))).thenAnswer(i -> i.getArgument(0));

        service().importReport(7L, file);

        ArgumentCaptor<PriceQuote> upserted = ArgumentCaptor.forClass(PriceQuote.class);
        verify(priceQuotes).upsert(upserted.capture());
        assertThat(upserted.getValue().securityId()).isEqualTo(new SecurityId(9L));
        assertThat(upserted.getValue().quoteDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(upserted.getValue().price()).isEqualByComparingTo("212.44");
        verify(exchangeRates).upsert(rate);
    }

    @Test
    void alreadyImportedExternalId_isSkippedAndReportedAsDuplicate() {
        givenPortfolio("EUR");
        FlexRow deposit = cashRow(InvestmentTransactionType.DEPOSIT, "CT-100", "1500.00");
        when(reader.read(file)).thenReturn(report("EUR", List.of(deposit)));
        when(transactions.existsByPortfolioAndExternalId(PORTFOLIO_ID, "CT-100")).thenReturn(true);

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.imported()).isZero();
        assertThat(result.duplicated()).isEqualTo(1);
        verify(transactions, never()).save(any());
    }

    @Test
    void externalIdRepeatedInTheSameFile_importsOnceAndReportsTheRestAsDuplicates() {
        givenPortfolio("EUR");
        FlexRow first = cashRow(InvestmentTransactionType.DEPOSIT, "CT-100", "1500.00");
        FlexRow repeated = cashRow(InvestmentTransactionType.DEPOSIT, "CT-100", "1500.00");
        when(reader.read(file)).thenReturn(report("EUR", List.of(first, repeated)));
        when(transactions.existsByPortfolioAndExternalId(PORTFOLIO_ID, "CT-100")).thenReturn(false);
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.duplicated()).isEqualTo(1);
        verify(transactions, times(1)).save(any());
    }

    @Test
    void invalidRow_isReportedAsErrorAndTheRestIsImported() {
        givenPortfolio("EUR");
        FlexRow invalid = cashRow(InvestmentTransactionType.DEPOSIT, "CT-1", "-100.00");
        FlexRow valid = cashRow(InvestmentTransactionType.DEPOSIT, "CT-2", "200.00");
        when(reader.read(file)).thenReturn(report("EUR", List.of(invalid, valid)));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().reference()).isEqualTo("CT-1");
        assertThat(result.errors().getFirst().message()).contains("convenio de signos");
    }

    @Test
    void parserRowErrors_arePassedThroughToTheSummary() {
        givenPortfolio("EUR");
        FlexRowError parserError = new FlexRowError("CorporateActions", "CA-9", "tipo no soportado: TC");
        when(reader.read(file)).thenReturn(new FlexReport("U1234567", "EUR",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                List.of(), List.of(), List.of(), List.of(parserError)));

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.errors()).containsExactly(parserError);
    }

    @Test
    void sellWithoutPosition_isImportedAndReportedAsWarning() {
        givenPortfolio("EUR");
        FlexInstrument instrument = new FlexInstrument("IE00B4L5Y983", "EUR",
                "iShares Core MSCI World", "IWDA", "AEB", null);
        FlexRow sell = new FlexRow(InvestmentTransactionType.SELL, "ORD-2",
                LocalDate.of(2025, 5, 20), new BigDecimal("-5"), new BigDecimal("90"),
                new BigDecimal("450.00"), "EUR", null, null, null, null, null, null, instrument);
        Security security = Security.rehydrate(new SecurityId(42L), "IE00B4L5Y983", "EUR",
                "iShares Core MSCI World", "IWDA", null, "AEB", null);
        when(reader.read(file)).thenReturn(report("EUR", List.of(sell)));
        when(securities.findByIsinAndCurrency("IE00B4L5Y983", "EUR")).thenReturn(Optional.of(security));
        when(securities.save(any(Security.class))).thenAnswer(i -> i.getArgument(0));
        when(transactions.save(any(InvestmentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(transactions.findByPortfolio(PORTFOLIO_ID)).thenAnswer(i -> List.of(
                InvestmentTransaction.builder()
                        .portfolio(PORTFOLIO_ID).security(new SecurityId(42L))
                        .type(InvestmentTransactionType.SELL)
                        .tradeDate(LocalDate.of(2025, 5, 20))
                        .quantity(Quantity.of("-5")).price("90")
                        .amount(CurrencyMoney.of("450.00", "EUR"))
                        .externalId("ORD-2").build()));
        when(exchangeRates.findAll()).thenReturn(List.of());

        FlexImportResult result = service().importReport(7L, file);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst()).contains("venta sin posición suficiente");
    }

    @Test
    void unknownPortfolio_throwsNotFound() {
        when(portfolios.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().importReport(7L, file))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(reader, securities, transactions, priceQuotes, exchangeRates);
    }

    @Test
    void baseCurrencyMismatch_rejectsTheWholeImportBeforeAnyRow() {
        givenPortfolio("EUR");
        when(reader.read(file)).thenReturn(report("USD", List.of()));

        assertThatThrownBy(() -> service().importReport(7L, file))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");

        verifyNoInteractions(securities, transactions, priceQuotes, exchangeRates);
    }
}
