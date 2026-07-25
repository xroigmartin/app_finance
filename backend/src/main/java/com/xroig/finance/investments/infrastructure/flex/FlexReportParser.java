package com.xroig.finance.investments.infrastructure.flex;

import com.xroig.finance.investments.application.FlexInstrument;
import com.xroig.finance.investments.application.FlexQuote;
import com.xroig.finance.investments.application.FlexReport;
import com.xroig.finance.investments.application.FlexReportReader;
import com.xroig.finance.investments.application.FlexRow;
import com.xroig.finance.investments.application.FlexRowError;
import com.xroig.finance.investments.domain.ExchangeRate;
import com.xroig.finance.investments.domain.InvestmentTransactionType;
import com.xroig.finance.shared.domain.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Anti-corruption layer for the IBKR Activity Flex report (§9, validated Flex
 * configuration): translates the broker's XML into {@link FlexRow}s in the
 * domain's language, copying the Flex signs almost verbatim (the report already
 * uses the cash-flow convention of §3). Each section consumes exactly one level
 * of detail — Trades→{@code ORDER}, Cash Transactions→{@code DETAIL}, Corporate
 * Actions→{@code DETAIL} (the {@code SUMMARY} row duplicates the entry),
 * Transaction Taxes→{@code ORDER_SUMMARY} (2025 duplicates each FTT as
 * {@code TransactionTaxDetail}) — and other levels are ignored silently.
 * Unsupported or unreadable rows become per-row {@link FlexRowError}s and the
 * rest continues (§8); only a file-level problem (not XML, no statement, no
 * account information) aborts with a {@code ValidationException}.
 *
 * <p>FX conversions arrive as {@code assetCategory="CASH"} orders on a currency
 * pair symbol ({@code EUR.GBP}): the signs of {@code quantity} (in the symbol's
 * first currency) and {@code proceeds} (in the entry's currency) identify the
 * outgoing and incoming legs of the {@code FX_TRADE}. The {@code fxRateToBase}
 * snapshot refers to the entry's currency, so it only travels with the row when
 * the outgoing leg is in that currency. Conversion rates are persisted only for
 * currencies present in the report, in the already-normalized currency→EUR
 * direction (RN-7).
 */
@Component
public class FlexReportParser implements FlexReportReader {

    @Override
    public FlexReport read(MultipartFile file) {
        Document document = parseXml(file);
        Element statement = onlyElement(document.getDocumentElement(), "FlexStatement");

        Element accountInformation = onlyElement(statement, "AccountInformation");
        String accountId = attr(statement, "accountId");
        String baseCurrency = required(attr(accountInformation, "currency"),
                "El informe Flex no informa la divisa base de la cuenta");
        LocalDate fromDate = date(attr(statement, "fromDate"));
        LocalDate toDate = required(date(attr(statement, "toDate")),
                "El informe Flex no informa su fecha de fin (toDate)");

        Map<String, String> securityNames = parseSecuritiesInfo(statement);

        List<FlexRow> rows = new ArrayList<>();
        List<FlexRowError> errors = new ArrayList<>();

        forEach(statement, "Order", order -> parseOrder(order, rows, errors, securityNames));
        forEach(statement, "CashTransaction", cash -> parseCashTransaction(cash, rows, errors, securityNames));
        forEach(statement, "CorporateAction", action -> parseCorporateAction(action, rows, errors, securityNames));
        forEach(statement, "TransactionTax", tax -> parseTransactionTax(tax, rows, errors, securityNames));

        List<FlexQuote> quotes = new ArrayList<>();
        forEach(statement, "OpenPosition",
                position -> parseOpenPosition(position, toDate, quotes, errors, securityNames));

        List<ExchangeRate> rates = parseConversionRates(statement, reportCurrencies(rows, quotes), errors);

        return new FlexReport(accountId, baseCurrency, fromDate, toDate,
                List.copyOf(rows), List.copyOf(quotes), List.copyOf(rates), List.copyOf(errors));
    }

    // ---- Trades (level ORDER) ----

    private void parseOrder(Element order, List<FlexRow> rows, List<FlexRowError> errors,
                            Map<String, String> securityNames) {
        if (!"ORDER".equals(attr(order, "levelOfDetail"))) {
            return;
        }
        String reference = attr(order, "ibOrderID");
        try {
            String assetCategory = attr(order, "assetCategory");
            if ("STK".equals(assetCategory)) {
                rows.add(parseStockOrder(order, securityNames));
            } else if ("CASH".equals(assetCategory)) {
                rows.add(parseFxOrder(order));
            } else {
                throw new IllegalArgumentException(
                        "categoría de activo no soportada: " + assetCategory);
            }
        } catch (Exception e) {
            errors.add(new FlexRowError("Trades", reference, e.getMessage()));
        }
    }

    private FlexRow parseStockOrder(Element order, Map<String, String> securityNames) {
        boolean buy = "BUY".equals(attr(order, "buySell"));
        String currency = attr(order, "currency");
        return new FlexRow(
                buy ? InvestmentTransactionType.BUY : InvestmentTransactionType.SELL,
                externalId("ORD-", attr(order, "ibOrderID")),
                required(date(attr(order, "tradeDate")), "la orden no informa tradeDate"),
                decimal(attr(order, "quantity")),
                decimal(attr(order, "tradePrice")),
                decimal(attr(order, "proceeds")),
                currency,
                null, null,
                nonZero(decimal(attr(order, "ibCommission"))),
                commissionCurrency(order, currency),
                decimal(attr(order, "fxRateToBase")),
                attr(order, "description"),
                instrument(order, securityNames));
    }

    private FlexRow parseFxOrder(Element order) {
        BigDecimal quantity = decimal(attr(order, "quantity"));
        BigDecimal proceeds = decimal(attr(order, "proceeds"));
        String symbol = required(attr(order, "symbol"), "la conversión no informa el par");
        int dot = symbol.indexOf('.');
        if (dot <= 0 || quantity == null || proceeds == null) {
            throw new IllegalArgumentException("conversión de divisa ilegible: " + symbol);
        }
        String symbolCurrency = symbol.substring(0, dot);
        String entryCurrency = attr(order, "currency");

        BigDecimal amount;
        String amountCurrency;
        BigDecimal counter;
        String counterCurrency;
        if (proceeds.signum() < 0 && quantity.signum() > 0) {
            amount = proceeds;
            amountCurrency = entryCurrency;
            counter = quantity;
            counterCurrency = symbolCurrency;
        } else if (proceeds.signum() > 0 && quantity.signum() < 0) {
            amount = quantity;
            amountCurrency = symbolCurrency;
            counter = proceeds;
            counterCurrency = entryCurrency;
        } else {
            throw new IllegalArgumentException(
                    "piernas de la conversión no reconocibles por signos: " + symbol);
        }

        // El snapshot fxRateToBase del Flex refiere a la divisa del apunte (currency):
        // solo acompaña a la fila si la pierna saliente queda en esa divisa.
        BigDecimal snapshot = amountCurrency.equals(entryCurrency)
                ? decimal(attr(order, "fxRateToBase"))
                : null;

        return new FlexRow(InvestmentTransactionType.FX_TRADE,
                externalId("ORD-", attr(order, "ibOrderID")),
                required(date(attr(order, "tradeDate")), "la conversión no informa tradeDate"),
                null, null,
                amount, amountCurrency,
                counter, counterCurrency,
                nonZero(decimal(attr(order, "ibCommission"))),
                commissionCurrency(order, entryCurrency),
                snapshot,
                symbol,
                null);
    }

    // ---- Cash Transactions (level DETAIL) ----

    private void parseCashTransaction(Element cash, List<FlexRow> rows, List<FlexRowError> errors,
                                      Map<String, String> securityNames) {
        if (!"DETAIL".equals(attr(cash, "levelOfDetail"))) {
            return;
        }
        String reference = attr(cash, "transactionID");
        try {
            BigDecimal amount = decimal(attr(cash, "amount"));
            InvestmentTransactionType type = cashType(attr(cash, "type"), amount);
            rows.add(new FlexRow(type,
                    externalId("CT-", attr(cash, "transactionID")),
                    required(cashDate(cash), "el apunte de efectivo no informa fecha"),
                    null, null,
                    amount,
                    attr(cash, "currency"),
                    null, null, null, null,
                    decimal(attr(cash, "fxRateToBase")),
                    attr(cash, "description"),
                    instrumentIfPresent(cash, securityNames)));
        } catch (Exception e) {
            errors.add(new FlexRowError("CashTransactions", reference, e.getMessage()));
        }
    }

    private InvestmentTransactionType cashType(String flexType, BigDecimal amount) {
        return switch (flexType == null ? "" : flexType) {
            case "Dividends", "Payment In Lieu Of Dividends" -> InvestmentTransactionType.DIVIDEND;
            case "Withholding Tax" -> InvestmentTransactionType.TAX;
            case "Deposits/Withdrawals" -> amount != null && amount.signum() < 0
                    ? InvestmentTransactionType.WITHDRAWAL
                    : InvestmentTransactionType.DEPOSIT;
            case "Broker Interest Received" -> InvestmentTransactionType.INTEREST;
            case "Broker Interest Paid", "Other Fees" -> InvestmentTransactionType.FEE;
            default -> throw new IllegalArgumentException(
                    "tipo de apunte de efectivo no soportado: " + flexType);
        };
    }

    private LocalDate cashDate(Element cash) {
        LocalDate settle = date(attr(cash, "settleDate"));
        if (settle != null) {
            return settle;
        }
        LocalDate report = date(attr(cash, "reportDate"));
        return report != null ? report : date(attr(cash, "dateTime"));
    }

    // ---- Corporate Actions (level DETAIL, FS/RS only) ----

    private void parseCorporateAction(Element action, List<FlexRow> rows, List<FlexRowError> errors,
                                      Map<String, String> securityNames) {
        if (!"DETAIL".equals(attr(action, "levelOfDetail"))) {
            return; // la fila SUMMARY (accountId="-") duplica el apunte (§9)
        }
        String reference = attr(action, "transactionID");
        try {
            String type = attr(action, "type");
            if (!"FS".equals(type) && !"RS".equals(type)) {
                throw new IllegalArgumentException("acción corporativa no soportada: " + type);
            }
            LocalDate actionDate = date(attr(action, "dateTime"));
            rows.add(new FlexRow(InvestmentTransactionType.SPLIT,
                    externalId("CA-", attr(action, "transactionID")),
                    required(actionDate != null ? actionDate : date(attr(action, "reportDate")),
                            "la acción corporativa no informa fecha"),
                    decimal(attr(action, "quantity")),
                    null,
                    BigDecimal.ZERO,
                    attr(action, "currency"),
                    null, null, null, null, null,
                    attr(action, "actionDescription"),
                    instrument(action, securityNames)));
        } catch (Exception e) {
            errors.add(new FlexRowError("CorporateActions", reference, e.getMessage()));
        }
    }

    // ---- Transaction Taxes (level ORDER_SUMMARY only, §9) ----

    private void parseTransactionTax(Element tax, List<FlexRow> rows, List<FlexRowError> errors,
                                     Map<String, String> securityNames) {
        if (!"ORDER_SUMMARY".equals(attr(tax, "levelOfDetail"))) {
            return;
        }
        String reference = attr(tax, "tradeId");
        try {
            rows.add(new FlexRow(InvestmentTransactionType.TRADE_TAX,
                    externalId("FTT-", attr(tax, "tradeId")),
                    required(date(attr(tax, "reportDate")), "la tasa no informa fecha"),
                    null, null,
                    decimal(attr(tax, "taxAmount")),
                    attr(tax, "currency"),
                    null, null, null, null, null,
                    attr(tax, "taxDescription"),
                    instrument(tax, securityNames)));
        } catch (Exception e) {
            errors.add(new FlexRowError("TransactionTaxes", reference, e.getMessage()));
        }
    }

    // ---- Open Positions (level SUMMARY) → quotes ----

    private void parseOpenPosition(Element position, LocalDate toDate,
                                   List<FlexQuote> quotes, List<FlexRowError> errors,
                                   Map<String, String> securityNames) {
        if (!"SUMMARY".equals(attr(position, "levelOfDetail"))) {
            return;
        }
        try {
            quotes.add(new FlexQuote(instrument(position, securityNames),
                    required(decimal(attr(position, "markPrice")), "la posición no informa markPrice"),
                    toDate));
        } catch (Exception e) {
            errors.add(new FlexRowError("OpenPositions", attr(position, "isin"), e.getMessage()));
        }
    }

    // ---- Securities Info (name lookup, §11 bis) ----

    /**
     * One clean {@code isin|currency → name} entry per instrument, independent of
     * any single row: the {@code description} of a {@code CashTransaction} is the
     * dividend/tax line ("ASML(...) CASH DIVIDEND EUR 1.75 PER SHARE..."), not the
     * instrument's name, and it even differs between a dividend and its own
     * withholding row for the same security — so it must never be used as the
     * {@code Security}'s name (it was, and got persisted verbatim on refresh).
     */
    private Map<String, String> parseSecuritiesInfo(Element statement) {
        Map<String, String> names = new LinkedHashMap<>();
        forEach(statement, "SecurityInfo", info -> {
            String isin = attr(info, "isin");
            String name = attr(info, "description");
            if (isin != null && name != null) {
                names.put(securityKey(isin, attr(info, "currency")), name);
            }
        });
        return names;
    }

    private static String securityKey(String isin, String currency) {
        return isin + "|" + currency;
    }

    // ---- Conversion Rates (report currencies only, currency→EUR) ----

    private List<ExchangeRate> parseConversionRates(Element statement, Set<String> currencies,
                                                    List<FlexRowError> errors) {
        List<ExchangeRate> rates = new ArrayList<>();
        forEach(statement, "ConversionRate", rate -> {
            String from = attr(rate, "fromCurrency");
            if (!ExchangeRate.PIVOT.equals(attr(rate, "toCurrency")) || !currencies.contains(from)) {
                return;
            }
            try {
                rates.add(ExchangeRate.toEur(date(attr(rate, "reportDate")), from,
                        decimal(attr(rate, "rate"))));
            } catch (Exception e) {
                errors.add(new FlexRowError("ConversionRates", from, e.getMessage()));
            }
        });
        return rates;
    }

    /** Currencies the report actually touches (rows + quoted instruments), minus the EUR pivot. */
    private Set<String> reportCurrencies(List<FlexRow> rows, List<FlexQuote> quotes) {
        Set<String> currencies = new LinkedHashSet<>();
        for (FlexRow row : rows) {
            add(currencies, row.currency());
            add(currencies, row.counterCurrency());
            add(currencies, row.feeCurrency());
        }
        for (FlexQuote quote : quotes) {
            add(currencies, quote.instrument().currency());
        }
        currencies.remove(ExchangeRate.PIVOT);
        return currencies;
    }

    private static void add(Set<String> currencies, String currency) {
        if (currency != null) {
            currencies.add(currency);
        }
    }

    // ---- shared row helpers ----

    private FlexInstrument instrument(Element row, Map<String, String> securityNames) {
        String isin = required(attr(row, "isin"), "la fila no informa el ISIN del instrumento");
        String currency = attr(row, "currency");
        String name = securityNames.getOrDefault(securityKey(isin, currency), attr(row, "description"));
        return new FlexInstrument(isin, currency, name,
                attr(row, "symbol"), attr(row, "listingExchange"), attr(row, "figi"));
    }

    private FlexInstrument instrumentIfPresent(Element row, Map<String, String> securityNames) {
        return attr(row, "isin") == null ? null : instrument(row, securityNames);
    }

    private String commissionCurrency(Element order, String fallback) {
        String currency = attr(order, "ibCommissionCurrency");
        return currency != null ? currency : fallback;
    }

    private static String externalId(String prefix, String id) {
        return prefix + required(id, "la fila no informa su identificador (" + prefix + ")");
    }

    private static BigDecimal nonZero(BigDecimal value) {
        return value == null || value.signum() == 0 ? null : value;
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    /** Flex dates are ISO {@code yyyy-MM-dd}, sometimes with a {@code ;HHmmss} time suffix. */
    private static LocalDate date(String value) {
        if (value == null) {
            return null;
        }
        int separator = value.indexOf(';');
        return LocalDate.parse(separator > 0 ? value.substring(0, separator) : value);
    }

    private static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T required(T value, String message) {
        if (value == null) {
            throw new ValidationException(message);
        }
        return value;
    }

    // ---- XML plumbing ----

    private Document parseXml(MultipartFile file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(file.getInputStream());
        } catch (Exception e) {
            throw new ValidationException("No se pudo leer el informe Flex (¿XML válido?): " + e.getMessage());
        }
    }

    /** The single element of that tag under the root; a missing one is a file-level error. */
    private Element onlyElement(Element root, String tag) {
        NodeList elements = root.getElementsByTagName(tag);
        if (elements.getLength() == 0) {
            throw new ValidationException("El informe Flex no contiene la sección " + tag);
        }
        return (Element) elements.item(0);
    }

    private void forEach(Element root, String tag, java.util.function.Consumer<Element> consumer) {
        NodeList elements = root.getElementsByTagName(tag);
        for (int i = 0; i < elements.getLength(); i++) {
            consumer.accept((Element) elements.item(i));
        }
    }
}
