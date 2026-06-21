-- Devoluciones: un movimiento de gasto puede ser la devolución (total o parcial)
-- de otro gasto. La devolución comparte categoría y cuenta del original y netea
-- contra su categoría (resta gasto / suma saldo); su enlace al original es lo que
-- invierte el signo en las agregaciones. Borrar el original elimina sus devoluciones.
ALTER TABLE transactions ADD COLUMN refund_of_id BIGINT;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_refund_of
        FOREIGN KEY (refund_of_id) REFERENCES transactions (id) ON DELETE CASCADE;

CREATE INDEX idx_transactions_refund_of ON transactions (refund_of_id);
