-- Categorías por cuenta: una categoría es global (account_id null) o
-- pertenece a una cuenta concreta. El nombre deja de ser único de forma
-- global y pasa a ser único por ámbito (global, o dentro de cada cuenta).
alter table categories add column account_id bigint references accounts (id);

alter table categories drop constraint categories_name_key;
create unique index ux_categories_name_scope on categories (name, (coalesce(account_id, 0)));
create index idx_categories_account on categories (account_id);

-- Presupuestos por cuenta + categoría: un presupuesto se define para una
-- cuenta y una categoría (global o de esa cuenta) en un mes.
alter table monthly_budgets add column account_id bigint references accounts (id);

-- Los presupuestos existentes no tenían cuenta; se asignan a la cuenta de
-- menor id. Si no hay ninguna cuenta, se eliminan (no pueden tener dueño).
update monthly_budgets set account_id = (select min(id) from accounts) where account_id is null;
delete from monthly_budgets where account_id is null;

alter table monthly_budgets alter column account_id set not null;

alter table monthly_budgets drop constraint monthly_budgets_category_id_year_month_key;
alter table monthly_budgets
    add constraint uq_monthly_budgets_account_category_period
        unique (account_id, category_id, year, month);

create index idx_monthly_budgets_account on monthly_budgets (account_id);
