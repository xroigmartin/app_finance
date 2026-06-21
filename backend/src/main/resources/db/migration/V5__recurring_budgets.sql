-- Recurrencia de presupuesto por categoría hoja ligada a una cuenta.
-- Solo alimenta el lado "previsto" de la matriz anual; no genera movimientos.
CREATE TABLE recurring_budgets (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT  NOT NULL UNIQUE REFERENCES categories(id) ON DELETE CASCADE,
    months      INTEGER NOT NULL,            -- bitmask: bit 0=ene … bit 11=dic (1..4095)
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_recurring_months CHECK (months > 0 AND months <= 4095)
);

-- Histórico de importes con vigencia (válido desde el primer día del mes indicado).
-- El previsto de un mes usa el importe cuyo valido_desde sea el más reciente <= ese mes.
CREATE TABLE recurring_budget_amounts (
    id                  BIGSERIAL PRIMARY KEY,
    recurring_budget_id BIGINT        NOT NULL REFERENCES recurring_budgets(id) ON DELETE CASCADE,
    amount              NUMERIC(38,2) NOT NULL CHECK (amount > 0),
    valido_desde        DATE          NOT NULL,   -- normalizado a día 1 del mes
    CONSTRAINT uq_amount_vigencia UNIQUE (recurring_budget_id, valido_desde)
);
CREATE INDEX idx_rba_recurring ON recurring_budget_amounts (recurring_budget_id);
