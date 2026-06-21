
Lee docs/plan/recurrencias-presupuestos.md y ejecútalo. Sigue el orden del documento (migración V5 → entidades → repos → BudgetService → API → frontend → PRDs) y actualiza la documentación como indica el propio plan. Si tienes dudas preguntame antes de realizar cualquier cambio
claude --resume 34239185-3343-46ef-ae40-227cdcbbd4f0

# Plan: recurrencias de presupuesto en categorías

> Documento de plan. **Implementado el 2026-06-17.** Se conserva como registro de diseño.
> Estado: ejecutado. El comportamiento real está documentado en `docs/prd/presupuestos.md` y `docs/prd/categorias.md`.

## 1. Objetivo

Permitir que una **categoría hoja ligada a una cuenta** declare un **pago recurrente**
(comunidad, hipoteca, seguro…) indicando:

- **en qué meses** ocurre (selector múltiple de meses, no un enum cerrado de frecuencias),
- el **importe**, con **histórico fechado**: cuando el importe cambia se añade un nuevo importe
  con su mes de inicio; los meses anteriores conservan el importe viejo y los futuros toman el nuevo.

La recurrencia **solo alimenta el lado "presupuesto/previsto"** de la matriz anual. **No genera
movimientos reales** (el real sigue saliendo de las transacciones importadas/creadas; si generara
apuntes habría doble contabilidad al importar el cargo del banco).

## 2. Decisiones de diseño ya cerradas (no reabrir sin motivo)

1. **Previsión, no real.** La recurrencia rellena el previsto; el real no se toca.
2. **Selector de meses** como conjunto libre `{1..12}` (cubre mensual, trimestral, anual e irregular).
   Implementado como **bitmask** `smallint` (bit 0 = enero … bit 11 = diciembre).
3. **Importes con vigencia** (`válido desde` = año-mes). El previsto de un mes usa el importe cuyo
   `validoDesde` sea el más reciente ≤ ese mes. La UI pide explícitamente "válido desde"
   (mes/año precargado al mes actual) → opción de **máximo control**. Si resulta invasivo, se suaviza
   más adelante a "desde el mes actual" por defecto.
4. **Una recurrencia por categoría** (unicidad por `category_id`).
5. **Solo categorías hoja ligadas a una cuenta.** Las **categorías globales NO admiten recurrencia**
   (son transversales a toda la app). Si una global necesita recurrencia, el usuario crea una
   **subcategoría ligada a una cuenta** y la define ahí. Tampoco se permite en una categoría con
   subcategorías (no es hoja, igual que con los presupuestos).
6. **Punto único de gestión: el formulario de alta/edición de categoría.** Como la categoría ya
   determina la cuenta, no hace falta selector de cuenta en la recurrencia: se hereda.
7. **Override manual.** La edición inline de celdas en la pantalla de presupuestos sigue funcionando
   y **prevalece** sobre el valor calculado de la recurrencia (p. ej. una derrama puntual). Mecanismo:
   si existe un `Budget` (manual) para esa celda se usa ese; si no, el valor de la recurrencia.

## 3. Modelo de datos

### Migración `V5__recurring_budgets.sql`

(Flyway es dueño del esquema; Hibernate corre con `ddl-auto=validate`. No editar migraciones
existentes.)

```sql
-- Recurrencia de presupuesto por categoría hoja ligada a cuenta.
CREATE TABLE recurring_budgets (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT   NOT NULL UNIQUE REFERENCES categories(id) ON DELETE CASCADE,
    months      SMALLINT NOT NULL,           -- bitmask: bit 0=ene … bit 11=dic (1..4095)
    active      BOOLEAN  NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_recurring_months CHECK (months > 0 AND months <= 4095)
);

-- Histórico de importes con vigencia (válido desde el primer día del mes indicado).
CREATE TABLE recurring_budget_amounts (
    id                  BIGSERIAL PRIMARY KEY,
    recurring_budget_id BIGINT        NOT NULL REFERENCES recurring_budgets(id) ON DELETE CASCADE,
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    valido_desde        DATE          NOT NULL,   -- normalizado a día 1 del mes
    CONSTRAINT uq_amount_vigencia UNIQUE (recurring_budget_id, valido_desde)
);
CREATE INDEX idx_rba_recurring ON recurring_budget_amounts (recurring_budget_id);
```

Notas:
- `ON DELETE CASCADE` en `category_id`: borrar la categoría arrastra su recurrencia. (En la práctica
  rara vez se borra una categoría con recurrencia porque tendrá movimientos, pero deja el FK limpio.)
- `valido_desde` se guarda como `DATE` a día 1 para comparar cómodamente con `(año, mes)`.

### Entidades JPA (`backend/.../model/`)

**`RecurringBudget.java`** (`@Table(name = "recurring_budgets")`):
- `Long id`
- `@OneToOne @JoinColumn(name="category_id", unique=true) Category category` (la cuenta y el tipo
  se derivan de la categoría; no se duplican).
- `@Column(nullable=false) Integer months` (bitmask).
- `@Column(nullable=false) boolean active = true`.
- `@OneToMany(mappedBy="recurringBudget", cascade=ALL, orphanRemoval=true) List<RecurringBudgetAmount> amounts`.

**`RecurringBudgetAmount.java`** (`@Table(name = "recurring_budget_amounts")`):
- `Long id`
- `@ManyToOne(optional=false) @JoinColumn(name="recurring_budget_id") RecurringBudget recurringBudget`
- `@Column BigDecimal amount`
- `@Column LocalDate validoDesde`

## 4. Backend — capa de acceso

**`RecurringBudgetRepository`** (Spring Data):
- `Optional<RecurringBudget> findByCategoryId(Long categoryId)`
- `boolean existsByCategoryId(Long categoryId)`
- `List<RecurringBudget> findByCategoryAccountId(Long accountId)` — recurrencias de las categorías de
  una cuenta (para la matriz cuando hay cuenta concreta).
- `@Query("select r from RecurringBudget r where r.active = true")` `findAllActive()` (o `findAll`) —
  para la vista agregada (`accountId == null`).

Cargar `amounts` con `JOIN FETCH` o `@EntityGraph` para evitar N+1 en el cálculo de la matriz.

## 5. Backend — cálculo en la matriz anual (`BudgetService.annual`)

Refactor del método `annual(int year, Long accountId)`. Hoy el previsto sale solo de `budgetByCat`
(presupuestos manuales). Cambiar para **fusionar manual + recurrencia**:

1. Cargar recurrencias relevantes:
   - `accountId != null` → `recurringBudgetRepository.findByCategoryAccountId(accountId)`.
   - `accountId == null` → todas (cada una cuelga de su categoría, que ya tiene su cuenta).
2. Construir, además de `budgetByCat` (suma de importes manuales) y `budgetIdByCat`, un
   **`Map<Long, boolean[]> hasManualByCat`** que marque, por categoría y mes, si existe un `Budget`
   manual para esa celda. (Hoy `getOrDefault(zero12())` pierde la diferencia entre "0 explícito" y
   "sin presupuesto"; este flag la recupera.)
3. Calcular un **`Map<Long, BigDecimal[]> recurrenceByCat`**: para cada recurrencia activa, para cada
   mes `m` (0..11):
   - si el bit `m` no está en `months` → 0;
   - si está → importe vigente = el `amount` del `RecurringBudgetAmount` con `validoDesde` más
     reciente ≤ `LocalDate.of(year, m+1, 1)`; si no hay ninguno (la recurrencia empieza más tarde) → 0.
4. **Previsto fusionado** `plannedByCat[catId][m] = hasManualByCat ? budgetByCat[m] : recurrenceByCat[m]`.
   - El manual (override) siempre gana sobre la recurrencia.
   - Para categoría hoja ligada a cuenta sólo hay una cuenta, así que la fusión es inequívoca tanto
     en vista por cuenta como en agregado.
   - Categoría global: nunca tiene recurrencia → su previsto sigue siendo la suma de manuales (como hoy).
5. `leafRow` y `parentRow` deben leer **`plannedByCat`** (el fusionado) en lugar de `budgetByCat`
   directamente. `budgetIdByCat` se sigue usando para el `budgetId` de la celda (sólo presente con
   cuenta concreta) → cuando la celda viene de la recurrencia, `budgetId` es `null` y el front, al
   editarla, hará **POST** creando un `Budget` (= override). Comportamiento ya existente, no cambia.
   - `parentRow`: el agregado del padre debe sumar el `plannedByCat` de los hijos (que ya incluye
     recurrencia), no el `budgetByCat` crudo.

Sin materializar nada: las celdas siguen siendo calculadas. Revertir un override = borrar ese
`Budget` (DELETE ya existe) y la celda vuelve a mostrar el valor de la recurrencia.

**Interacción con `/api/budgets/copy`:** copia sólo `Budget` manuales; la recurrencia no es un
`Budget`, así que no se ve afectada. No requiere cambios.

## 6. Backend — API de la recurrencia

Nuevo **`RecurringBudgetController`** (mantener `CategoryController` enfocado) con sub-recurso bajo
la categoría:

- `GET    /api/categories/{id}/recurrence` → DTO de recurrencia o `404` si no existe.
- `PUT    /api/categories/{id}/recurrence` → **upsert** (reemplaza meses, `active` y la lista completa
  de importes). Body:
  ```json
  {
    "months": [1,4,7,10],            // o bitmask; ver DTO abajo
    "active": true,
    "amounts": [
      { "amount": 50.00, "validoDesde": "2026-01" },
      { "amount": 60.00, "validoDesde": "2026-04" }
    ]
  }
  ```
- `DELETE /api/categories/{id}/recurrence` → elimina la recurrencia.

DTOs (`RecurringBudgetDtos`): conviene exponer **`months` como lista de enteros `1..12`** en la API
(más legible para el front) y convertir a/desde bitmask en el servicio. `validoDesde` como `"YYYY-MM"`
(string) o `{year, month}`; elegir uno y reflejarlo en `models.ts`.

**Validaciones (en `RecurringBudgetService`/controller), devolver `400`/`409` con mensaje en español:**
- La categoría existe.
- La categoría es **hoja**: `!categoryRepository.existsByParentId(id)` → si no, *"No se puede definir
  recurrencia en una categoría con subcategorías; defínela en sus subcategorías"*.
- La categoría está **ligada a una cuenta** (`category.getAccount() != null`) → si es global:
  *"Las categorías globales no admiten recurrencia; crea una subcategoría ligada a una cuenta"*.
- `months` no vacío (bitmask `> 0`).
- Al menos un importe; importes positivos; `validoDesde` válidos; sin `validoDesde` duplicados.

**Guardas adicionales en `CategoryController`:**
- `update(...)`: si la categoría **pasa a global** (account → null) o **pasa a tener subcategorías**
  y ya tiene recurrencia → rechazar con `409` *("Quita primero la recurrencia")* **o** eliminar la
  recurrencia en cascada. Recomendado: **rechazar** y avisar, para no borrar datos de forma silenciosa.
- `delete(...)`: el FK es `ON DELETE CASCADE`, pero por coherencia con las demás guardas (movimientos,
  presupuestos, reglas) se puede añadir un mensaje específico si tiene recurrencia. Opcional; con el
  cascade la integridad ya está cubierta.

## 7. Frontend

### `models.ts`
```ts
export interface RecurringBudgetAmount {
  id?: number;
  amount: number;
  validoDesde: string; // "YYYY-MM"
}
export interface RecurringBudget {
  months: number[];        // 1..12
  active: boolean;
  amounts: RecurringBudgetAmount[];
}
```

### `api.service.ts`
- `getRecurrence(categoryId): Observable<RecurringBudget | null>` (mapear 404 → null).
- `saveRecurrence(categoryId, body: RecurringBudget): Observable<RecurringBudget>` (PUT).
- `deleteRecurrence(categoryId): Observable<void>`.

### `pages/categories/categories.ts` + `categories.html`
En el formulario de alta/edición de categoría, añadir una sección **"Recurrencia"** que se muestra
**solo cuando**:
- la categoría está ligada a una cuenta (`formAccountId != null`), **y**
- es hoja: en edición, que no tenga hijos (`childrenOf(editingId).length === 0`); en alta es hoja por
  definición. (Si es subcategoría también vale, sigue siendo hoja.)

Cuando la categoría es global o tiene subcategorías, mostrar la sección **deshabilitada** con el aviso
*"Liga la categoría a una cuenta para definir una recurrencia"* / *"Las categorías con subcategorías
no admiten recurrencia"*.

Controles:
- **Toggle activa/inactiva.**
- **Selector de meses**: 12 botones `Ene…Dic` con selección múltiple (toggle).
- **Importes con vigencia**: lista editable de filas `{importe, válido desde (mes/año)}` con botón
  "Añadir importe". El "válido desde" del nuevo importe se precarga al **mes actual** (2026-06 según
  la fecha del sistema; usar la fecha real en runtime). Mostrarlos ordenados por `validoDesde`.

Flujo de guardado (`save()`): primero guarda la categoría (create/update) — necesitamos el `id` para
colgar la recurrencia — y **después**:
- si hay recurrencia definida → `saveRecurrence(catId, body)`;
- si el usuario la dejó vacía/inactiva y antes existía → `deleteRecurrence(catId)`.
Encadenar con `switchMap`/`concat` y refrescar al final. Mostrar errores de validación del backend.

Al abrir `openEdit(c)` de una categoría elegible, cargar su recurrencia con `getRecurrence(c.id)` y
precargar la sección.

> La pantalla de **presupuestos no necesita cambios de UI**: ya consume `/api/budgets/annual`, que
> ahora devolverá los previstos con la recurrencia fusionada. La edición inline sigue creando
> overrides como hoy.

## 8. Documentación (obligatoria, misma entrega)

Según `CLAUDE.md`, todo cambio actualiza el PRD del dominio afectado:
- **`docs/prd/presupuestos.md`**: nueva sección "Recurrencias" (modelo, regla manual-prevalece-sobre-
  recurrencia, importes con vigencia, sólo hoja+cuenta, no genera real), nuevos endpoints
  `/api/categories/{id}/recurrence`, y bump de "Última actualización".
- **`docs/prd/categorias.md`**: la sección de recurrencia vive en el formulario de categoría;
  documentar la condición (hoja + ligada a cuenta), la prohibición en globales y el flujo de guardado.
  Bump de "Última actualización".
- Actualizar `CLAUDE.md` (sección Backend/Database) con la mención de las tablas `recurring_budgets`/
  `recurring_budget_amounts`, la migración `V5` y la regla de fusión en `BudgetService`.

## 9. Verificación

- `cd backend && mvn -q -DskipTests compile` y arranque con `ddl-auto=validate` (la `V5` debe casar
  con las entidades).
- Pruebas manuales (`./app.sh start`):
  1. Crear subcategoría ligada a cuenta "Comunidad" con meses = todos, importe 50 desde 2026-01.
     → en presupuestos, los 12 meses muestran previsto 50.
  2. Añadir importe 60 desde 2026-04. → ene-mar = 50, abr-dic = 60. Año anterior sigue a 50.
  3. Trimestral (ene, abr, jul, oct) → sólo esos meses con previsto, el resto 0.
  4. Editar inline una celda (override) → prevalece sobre la recurrencia; borrar esa celda → vuelve al
     valor de la recurrencia.
  5. Intentar definir recurrencia en categoría global → bloqueado con mensaje.
  6. Vista agregada (sin cuenta) → los previstos de la recurrencia aparecen sumados; celdas read-only.
- (No hay tests automatizados en el repo todavía; si se añaden, cubrir el cálculo de
  `recurrenceByCat`/fusión y las validaciones del controller.)

## 10. Cuestiones abiertas / a vigilar

- **N+1** al cargar `amounts` de muchas recurrencias en la matriz → usar `JOIN FETCH`/`@EntityGraph`.
- Si la opción "válido desde explícito" resulta engorrosa en uso real, suavizar a "desde el mes
  actual" por defecto (decisión 3).
- Posible mejora futura: botón "duplicar importe del mes anterior" o copiar recurrencias entre cuentas
  (fuera de alcance ahora).
</content>
</invoke>
