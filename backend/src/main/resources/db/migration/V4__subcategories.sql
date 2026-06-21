-- Subcategorías: una categoría puede tener una categoría padre (un solo nivel).
alter table categories add column parent_id bigint references categories (id);

create index idx_categories_parent on categories (parent_id);

-- La unicidad del nombre pasa a ser por ámbito (cuenta) y por categoría padre,
-- para que dos subcategorías de padres distintos puedan llamarse igual.
drop index ux_categories_name_scope;
create unique index ux_categories_name_scope
    on categories (name, (coalesce(account_id, 0)), (coalesce(parent_id, 0)));
