# PRD — Presupuestos

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.2 |
| Última actualización | 2026-06-22 |
| Dominio | Presupuestos (`monthly_budgets`, `recurring_budgets`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de los presupuestos (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

---

## 1. Propósito

Un **presupuesto** fija un importe planificado para una categoría, en una cuenta, en un mes concreto. La pantalla de presupuestos es una **matriz anual** (12 meses × categorías) que replica la hoja de cálculo del usuario, contrastando lo planificado con lo real y derivando el ahorro mensual y acumulado.

## 2. Objetivos y no-objetivos

**Objetivos**
- Definir importes planificados por categoría, cuenta y mes.
- Comparar planificado vs. real en una matriz anual editable.
- Calcular totales de ingresos, gastos, ahorro, tasa de ahorro y ahorro acumulado.
- Copiar el presupuesto de un mes a otro.

**No-objetivos (fuera de alcance de este PRD)**
- Progreso de presupuesto del dashboard (`/api/dashboard/budgets`) → PRD Dashboard.
- Definición de categorías → PRD Categorías.

## 3. Modelo de datos

Tabla `monthly_budgets` (migraciones `V1__init.sql`, `V3__categories_per_account.sql`), entidad `Budget`:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `account_id` | `bigint` → `accounts` | `NOT NULL` | Un presupuesto **siempre** pertenece a una cuenta concreta. |
| `category_id` | `bigint` → `categories` | `NOT NULL` | Solo categorías **principales** (ver RN-2). |
| `year` | `integer` | `NOT NULL` | Año. |
| `month` | `integer` | `NOT NULL`, `check between 1 and 12` | Mes. |
| `amount` | `numeric(38,2)` | `NOT NULL`, positivo | Importe planificado. |

**Restricción única**: `(account_id, category_id, year, month)` — un único presupuesto por categoría/cuenta/mes.

### Recurrencias (previsto automático)

Tablas `recurring_budgets` y `recurring_budget_amounts` (migración `V5__recurring_budgets.sql`), entidades `RecurringBudget` y `RecurringBudgetAmount`. Una **recurrencia** declara un pago previsto que se repite (comunidad, hipoteca…) y **solo alimenta el lado "previsto"** de la matriz; **no genera movimientos reales**.

`recurring_budgets`:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `category_id` | `bigint` → `categories` | `NOT NULL`, **UNIQUE**, `ON DELETE CASCADE` | Una recurrencia por categoría. La cuenta y el tipo se derivan de la categoría (debe ser hoja y ligada a cuenta). |
| `months` | `integer` | `NOT NULL`, `check 1..4095` | **Bitmask** de meses activos: bit 0 = enero … bit 11 = diciembre. |
| `active` | `boolean` | `NOT NULL`, por defecto `true` | Permite pausar la recurrencia sin perder su definición. |

`recurring_budget_amounts` (histórico de importes con vigencia):

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `recurring_budget_id` | `bigint` → `recurring_budgets` | `NOT NULL`, `ON DELETE CASCADE` | |
| `amount` | `numeric(38,2)` | `NOT NULL`, positivo | Importe. |
| `valido_desde` | `date` | `NOT NULL`, único por recurrencia | Primer día del mes desde el que aplica el importe. |

El importe vigente de un mes es el de mayor `valido_desde` ≤ ese mes; si no hay ninguno (la recurrencia empieza después), ese mes aporta 0.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Crear un presupuesto para una categoría **hoja** (principal sin subcategorías, o subcategoría), cuenta, año y mes con un importe planificado. |
| RF-2 | Editar el importe de un presupuesto. |
| RF-3 | Eliminar un presupuesto. |
| RF-4 | Consultar la matriz anual de una cuenta (o agregada de todas) para un año. |
| RF-5 | Copiar todos los presupuestos de un mes a otro, sin pisar los que ya existan en el destino. |
| RF-6 | Editar las celdas de la matriz en línea cuando hay una cuenta concreta seleccionada. |
| RF-7 | Definir una **recurrencia** en una categoría hoja ligada a cuenta (meses activos + importes con vigencia) que rellena automáticamente el previsto de esos meses en la matriz, gestionada desde el formulario de categoría (ver PRD Categorías). |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | Un presupuesto se define **por cuenta** (no hay presupuestos globales): `account_id` es obligatorio. |
| RN-2 | Los presupuestos se definen sobre **categorías hoja**: una categoría principal **sin** subcategorías, o una subcategoría. Presupuestar una categoría **con** subcategorías se rechaza: "No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías" (HTTP 400). El padre actúa como **agregado** de sus hijas. |
| RN-3 | La categoría debe ser **compatible con la cuenta**: global o de esa misma cuenta; en caso contrario "La categoría pertenece a otra cuenta" (HTTP 400). |
| RN-4 | No puede existir más de un presupuesto para la misma combinación cuenta+categoría+año+mes (HTTP 409 en alta y en edición que cambie de hueco). |
| RN-5 | El importe debe ser **estrictamente positivo** (`@Positive`); en la matriz, un valor vacío o `0` **elimina** el presupuesto de esa celda. |
| RN-6 | El "real" de cada celda de una fila hoja es la suma de movimientos de **esa categoría exacta** y mes (`TransactionRepository.sumByExactCategoryAndMonthOfYear`); la fila agregada del padre suma su propio real (movimientos directos) más el de todas sus subcategorías. Las **devoluciones** netean en el real con signo invertido (reducen el gasto de su categoría en su mes), por lo que el real puede quedar por debajo del gasto bruto. |
| RN-7 | La matriz **agregada de todas las cuentas** (`accountId` nulo) es de **solo lectura**: las celdas no llevan `budgetId` y no se pueden editar. |
| RN-8 | Copiar un mes a otro **omite** las categorías que ya tengan presupuesto en el mes destino. |
| RN-9 | El previsto de una celda hoja es el **presupuesto manual** si existe (override editado en línea), y si no, el valor generado por la **recurrencia** de la categoría; si no hay ninguno, 0. El manual **siempre prevalece** sobre la recurrencia. Borrar la celda (vaciar/`0`) elimina el override y la celda vuelve a mostrar el valor de la recurrencia. |
| RN-10 | La recurrencia solo aporta al **previsto**, nunca al real. Solo se considera si está `active`. Copiar un mes (RF-5) actúa solo sobre presupuestos manuales; no materializa la recurrencia. |

## 6. API

Base: `/api/budgets`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/budgets?year=&month=&accountId=` | Lista presupuestos de un mes (por defecto, mes/año actuales). Con `accountId`, solo esa cuenta; sin él, todas. | `200` + array. |
| `GET` | `/api/budgets/annual?year=&accountId=` | Matriz anual (por defecto, año actual). Sin `accountId`, cifras agregadas de todas las cuentas (solo lectura). | `200` + `AnnualBudget`. |
| `POST` | `/api/budgets` | Crea un presupuesto. | `201` / `409` si ya existe. |
| `PUT` | `/api/budgets/{id}` | Actualiza un presupuesto. | `200` / `404` / `409` si choca con otro hueco. |
| `DELETE` | `/api/budgets/{id}` | Elimina un presupuesto. | `204`. |
| `POST` | `/api/budgets/copy` | Copia los presupuestos de un mes a otro. | `200` + lista copiada. |

Recurrencias (sub-recurso de la categoría, base `/api/categories/{id}/recurrence`):

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/categories/{id}/recurrence` | Recurrencia de la categoría. | `200` + recurrencia / `404` si no tiene. |
| `PUT` | `/api/categories/{id}/recurrence` | Crea o reemplaza la recurrencia (upsert). | `200` / `400` si la categoría es global o tiene subcategorías. |
| `DELETE` | `/api/categories/{id}/recurrence` | Elimina la recurrencia. | `204`. |

**`RecurringBudgetRequest`** (`months` como lista 1..12, `validoDesde` como `"AAAA-MM"`):
```json
{
  "months": [1, 4, 7, 10],
  "active": true,
  "amounts": [
    { "amount": 50.00, "validoDesde": "2026-01" },
    { "amount": 60.00, "validoDesde": "2026-04" }
  ]
}
```

**`BudgetRequest`:**
```json
{ "accountId": 1, "categoryId": 4, "year": 2026, "month": 6, "amount": 500.00 }
```

**`CopyRequest`:**
```json
{ "fromYear": 2026, "fromMonth": 5, "toYear": 2026, "toMonth": 6 }
```

**`AnnualBudget`** (respuesta de `/annual`):
```
AnnualBudget { year, accountId, income: AnnualRow[], expense: AnnualRow[] }
AnnualRow    { categoryId, category, color, type, editable, months: MonthCell[12], children: AnnualRow[] }
MonthCell    { budgetId, budget, actual }   // budgetId null = sin presupuesto aún / fila agregada
// editable=false en la fila del padre (agregado); sus subcategorías van en children con editable=true
```

## 7. UI/UX

Página `pages/budgets` (componente `BudgetsPage`).

- Matriz anual: filas por categoría (ingresos y gastos), columnas por mes, con **planificado / real / diferencia**.
- **Jerarquía**: una categoría principal **con** subcategorías se muestra como una fila **agregada de solo lectura** (en negrita) seguida de sus subcategorías **anidadas y editables** (con marca `↳`). Una categoría principal **sin** subcategorías es una fila hoja editable directamente. Los totales suman solo las filas de primer nivel (cuyo valor ya es el agregado), sin doble conteo.
- Filas calculadas (derivadas en el front, no en BD): **TOTAL INGRESOS**, **TOTAL GASTOS**, **AHORRO** (ingresos − gastos), **%** (ahorro / ingresos) y **AHORRO ACUMULADO** (suma corriente del ahorro mes a mes).
- Selector de **cuenta** y navegación de **año** (anterior/siguiente).
- **Edición en línea** de cada celda hoja **solo** cuando hay una cuenta concreta seleccionada (`editable = accountId !== null`) y la fila es hoja (`row.editable`). Reusa los endpoints por mes: crea (`POST`) si la celda no tenía presupuesto, actualiza (`PUT`) si lo tenía, y elimina (`DELETE`) si se deja vacío o a `0`. Las filas agregadas del padre no son editables.
- Coloreado de la diferencia: gastar menos de lo previsto o ingresar más es "bueno" (verde); lo contrario, "malo" (rojo). Los ceros se ocultan para mantener la rejilla legible.

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Presupuestar una categoría con subcategorías | `400` "No se puede presupuestar una categoría con subcategorías; presupuesta sus subcategorías". |
| Categoría de otra cuenta | `400` "La categoría pertenece a otra cuenta". |
| Importe 0 o negativo (alta/edición vía API) | Rechazado (`@Positive`). En la matriz, vacío/`0` elimina la celda. |
| Importe no numérico en la matriz | El front muestra "Importe no válido…" y recarga. |
| Duplicado (cuenta+categoría+año+mes) | `409`. |
| Cuenta o categoría inexistente | `400` "Cuenta no válida" / "Categoría no válida". |
| Editar presupuesto inexistente | `404` "Presupuesto no encontrado". |

## 9. Casos límite y notas

- Aunque una categoría sea **global**, su presupuesto se ata a una **cuenta**: la misma categoría global puede tener presupuestos distintos en cuentas distintas.
- El "real" de la fila agregada de una principal incluye sus movimientos directos más los de sus subcategorías.
- Una principal con subcategorías que además tenga **movimientos directos** los muestra en su real agregado, pero esos movimientos no tienen una línea de presupuesto propia (no se puede presupuestar la principal); aparecen como gasto no presupuestado en la diferencia del agregado.
- La matriz agregada (todas las cuentas) sirve para consulta global pero no permite editar, porque una celda agregada no corresponde a un único presupuesto.

## 10. Backlog / mejoras futuras

- Presupuestos anuales (no solo mensuales) o por trimestre.
- Plantilla/duplicado de un año completo a otro.
- Alertas al superar el presupuesto de una categoría.
- Presupuesto agregado editable repartido entre cuentas.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Matriz agregada solo lectura | Con `accountId` nulo la matriz no es editable (una celda agregada no mapea a un único presupuesto). | Decisión consciente; editar requeriría decidir el reparto entre cuentas (ver backlog). |
| Dos caminos para el "gastado" | La matriz anual usa `sumByExactCategoryAndMonthOfYear` y agrega los padres en `BudgetService`; el dashboard usa `sumByCategoryTreeAndPeriod`. | Ambos son coherentes, pero son dos implementaciones del mismo concepto; valorar unificar. |
| Movimientos directos en un padre con hijas | Un padre con subcategorías ya no es presupuestable, pero puede tener movimientos directos sin línea de presupuesto. | Decidir si conviene impedir movimientos directos sobre categorías con subcategorías o presupuestarlos de otro modo. |

## 12. Referencias de código

- Backend: `model/Budget.java`, `controller/BudgetController.java` (incluye `BudgetRequest`, `CopyRequest`), `service/BudgetService.java`, `dto/BudgetDtos.java`, `repository/BudgetRepository.java`.
- Recurrencias: `model/RecurringBudget.java`, `model/RecurringBudgetAmount.java`, `controller/RecurringBudgetController.java`, `service/RecurringBudgetService.java`, `dto/RecurringBudgetDtos.java`, `repository/RecurringBudgetRepository.java`. Fusión previsto manual+recurrencia en `service/BudgetService.java` (`annual`, `plannedByCat`).
- Real por categoría y agregación de padres: `repository/TransactionRepository.java` (`sumByExactCategoryAndMonthOfYear`), agregado en `service/BudgetService.java` (`leafRow` / `parentRow`).
- Esquema: `db/migration/V1__init.sql`, `V3__categories_per_account.sql`, `V5__recurring_budgets.sql`.
- Tests: `service/RecurringBudgetServiceTest.java` (lógica de upsert con repos mockeados) y, contra Postgres real, `repository/RecurringBudgetReconciliationTest.java` (la reconciliación en sitio que evita violar `uq_amount_vigencia` al editar el importe manteniendo el mes de vigencia; constraints `uq_amount_vigencia`, `chk_recurring_months` y `category_id` único; cascada al borrar el histórico de importes). La constraint `uq_monthly_budgets_account_category_period` (un presupuesto por cuenta+categoría+periodo) se verifica en `repository/ConstraintViolationsTest.java`, atándola al `409` real del `GlobalExceptionHandler`. Las queries que alimentan la matriz se prueban contra Postgres real en `repository/RecurringBudgetRepositoryTest.java` (`findActiveByAccountWithAmounts` filtra por `active`, scopa por cuenta y carga el histórico de importes con `distinct` sobre el `left join fetch`; `findAllActiveWithAmounts` devuelve cada recurrencia activa una sola vez). El contrato HTTP de presupuestos se cubre en `controller/BudgetControllerMvcTest.java` (slice `@WebMvcTest`): binding de query params con fallback a la fecha actual (`find` y `/annual`), validación de los records `BudgetRequest`/`CopyRequest` (`@NotNull`/`@Positive`/`@Min(1)`/`@Max(12)` sobre mes→400) y los `ResponseStatusException` como `problem+json` (duplicado→409, categoría con subcategorías→400, no encontrado→404). El sub-recurso de recurrencias (`/api/categories/{id}/recurrence`) tiene su contrato HTTP en `controller/RecurringBudgetControllerMvcTest.java`: binding del path-variable, forma del JSON `RecurringBudgetResponse` y la validación en cascada del `RecurringBudgetRequest` (`@NotEmpty` en las listas + `@Valid` sobre el `AmountRequest` anidado con `@NotNull`/`@Positive`)→400.
- Frontend: `pages/budgets/` (`budgets.ts`, `budgets.html`), modelos `AnnualBudget` / `AnnualRow` / `BudgetRequest` en `models.ts`.
- Relacionado: PRD Categorías, PRD Movimientos, PRD Dashboard.
