-- Módulo Inversiones (PRD docs/prd/inversiones.md §3): esquema PostgreSQL separado.
-- El bounded context "investments" vive en su propio esquema — la economía doméstica
-- sigue en public — de modo que el aislamiento del contexto también es físico: sin
-- foreign keys entre esquemas. Nada materializado: posiciones, coste medio, efectivo
-- y valoración se calculan siempre a partir de investment_transaction + price_quote
-- + exchange_rate.
CREATE SCHEMA IF NOT EXISTS investments;

-- Instrumento (acción, ETF, fondo…). Identidad de negocio = ISIN + divisa de
-- cotización (un cross-listing en otra divisa es otro security); ticker/type/
-- exchange/figi son metadatos no identitarios (llaves para la API de precios, backlog).
CREATE TABLE investments.security (
    id       BIGSERIAL PRIMARY KEY,
    isin     VARCHAR    NOT NULL,
    ticker   VARCHAR,
    name     VARCHAR    NOT NULL,
    currency VARCHAR(3) NOT NULL,
    type     VARCHAR,
    exchange VARCHAR,
    figi     VARCHAR,
    CONSTRAINT uq_security_isin_currency UNIQUE (isin, currency)
);

-- Serie de cierres por instrumento, en la divisa del instrumento; se alimenta del
-- Flex al importar. Upsert por (security_id, quote_date): un valor nuevo para la
-- misma clave sobreescribe, nunca duplica (RN-9).
CREATE TABLE investments.price_quote (
    id          BIGSERIAL PRIMARY KEY,
    security_id BIGINT        NOT NULL REFERENCES investments.security (id),
    quote_date  DATE          NOT NULL,
    price       NUMERIC(19,8) NOT NULL CHECK (price > 0),
    CONSTRAINT uq_price_quote_security_date UNIQUE (security_id, quote_date)
);

-- Cartera alimentada por una cuenta de broker. base_currency inmutable tras la
-- creación (los snapshots fx_rate_to_base RN-7a apuntan a ella).
CREATE TABLE investments.portfolio (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR    NOT NULL,
    base_currency VARCHAR(3) NOT NULL
);

-- Operación de la cartera. Todo importe lleva el signo del flujo de caja real
-- (convenio §3, el mismo del Flex de IBKR), de modo que efectivo (RN-2) y posición
-- (RN-3) son sumas directas sin lógica de signos por tipo; los invariantes por tipo
-- se validan en el dominio. external_id (prefijado ORD-/CT-/FTT-/CA-) da la
-- idempotencia del import por cartera (RN-10); nulo en apuntes manuales.
CREATE TABLE investments.investment_transaction (
    id               BIGSERIAL PRIMARY KEY,
    portfolio_id     BIGINT        NOT NULL REFERENCES investments.portfolio (id),
    security_id      BIGINT        REFERENCES investments.security (id),
    type             VARCHAR       NOT NULL,
    trade_date       DATE          NOT NULL,
    quantity         NUMERIC(19,8),
    price            NUMERIC(19,8),
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    counter_amount   NUMERIC(19,4),
    counter_currency VARCHAR(3),
    fee              NUMERIC(19,4),
    fee_currency     VARCHAR(3),
    tax              NUMERIC(19,4),
    tax_currency     VARCHAR(3),
    fx_rate_to_base  NUMERIC(19,8),
    description      VARCHAR,
    external_id      VARCHAR,
    CONSTRAINT uq_investment_tx_portfolio_external UNIQUE (portfolio_id, external_id)
);
CREATE INDEX idx_investment_tx_portfolio ON investments.investment_transaction (portfolio_id);
CREATE INDEX idx_investment_tx_security ON investments.investment_transaction (security_id);

-- Tipos de cambio del propio Flex, normalizados divisa→EUR con EUR como pivote:
-- cualquier par se deriva aritméticamente (from→to = (from→EUR) ÷ (to→EUR), RN-7).
-- Upsert por (rate_date, from_currency, to_currency) (RN-9).
CREATE TABLE investments.exchange_rate (
    id            BIGSERIAL PRIMARY KEY,
    rate_date     DATE          NOT NULL,
    from_currency VARCHAR(3)    NOT NULL,
    to_currency   VARCHAR(3)    NOT NULL,
    rate          NUMERIC(19,8) NOT NULL CHECK (rate > 0),
    CONSTRAINT uq_exchange_rate_date_pair UNIQUE (rate_date, from_currency, to_currency)
);
