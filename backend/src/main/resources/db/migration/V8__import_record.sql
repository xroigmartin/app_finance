CREATE TABLE investments.import_record (
    id                BIGSERIAL PRIMARY KEY,
    portfolio_id      BIGINT      NOT NULL REFERENCES investments.portfolio (id),
    imported_at       TIMESTAMP   NOT NULL,
    file_name         VARCHAR,
    from_date         DATE,
    to_date           DATE        NOT NULL,
    imported_count    INT         NOT NULL,
    duplicated_count  INT         NOT NULL,
    errors            JSONB       NOT NULL DEFAULT '[]',
    warnings          JSONB       NOT NULL DEFAULT '[]'
);

CREATE INDEX idx_import_record_portfolio ON investments.import_record (portfolio_id, imported_at DESC);
