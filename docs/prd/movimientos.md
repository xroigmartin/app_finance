# PRD — Movimientos

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.1 |
| Última actualización | 2026-07-16 |
| Dominio | Movimientos / transacciones (`transactions`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de los movimientos (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

---

## 1. Propósito

Un **movimiento** (transacción) registra un ingreso o un gasto en una cuenta, clasificado por una categoría y fechado en un día concreto. Es el dato base sobre el que se calculan saldos, presupuestos y agregaciones del dashboard.

Nota de terminología: en la UI, la pantalla de "Movimientos" muestra una lista unificada de **transacciones** (este PRD) y **transferencias** (PRD Transferencias). Este documento cubre solo las transacciones de ingreso/gasto; las transferencias tienen su propio PRD.

## 2. Objetivos y no-objetivos

**Objetivos**
- Crear, consultar, editar y eliminar transacciones de ingreso/gasto.
- Filtrar por rango de fechas, cuenta y categoría.
- Ofrecer un acceso rápido a los últimos movimientos (dashboard).

**No-objetivos (fuera de alcance de este PRD)**
- Transferencias entre cuentas → PRD Transferencias.
- Importación de extractos (el endpoint `POST /api/transactions/import` vive aquí pero se documenta en) → PRD Importación de extractos.
- Cálculo de saldos y agregaciones → PRDs Cuentas / Dashboard / Presupuestos.

## 3. Modelo de datos

Tabla `transactions` (migración `V1__init.sql`), entidad `Transaction`:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `date` | `date` | `NOT NULL` | Fecha del movimiento. |
| `amount` | `numeric(38,2)` | `NOT NULL` | Importe **siempre positivo**; el signo lo aporta `type`. La positividad se valida en la API (`@Positive`), no a nivel de BD. |
| `description` | `varchar(255)` | Nullable | Texto libre opcional. |
| `type` | `varchar(255)` | `NOT NULL`, `check in ('INCOME','EXPENSE')` | Ingreso o gasto. |
| `account_id` | `bigint` → `accounts` | `NOT NULL` | Cuenta a la que pertenece. |
| `category_id` | `bigint` → `categories` | `NOT NULL` | Categoría (principal o subcategoría). |
| `refund_of_id` | `bigint` → `transactions` | Nullable, `ON DELETE CASCADE` | Si está, el movimiento es una **devolución** (total o parcial) de ese gasto (migración `V6`). |

Índices: por `date`, `account_id`, `category_id` y `refund_of_id`.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Crear una transacción con fecha, importe, tipo, cuenta, categoría y descripción opcional. |
| RF-2 | Editar cualquier campo de una transacción existente. |
| RF-3 | Eliminar una transacción. |
| RF-4 | Listar transacciones filtrando por rango de fechas, cuenta y categoría (todos opcionales). |
| RF-5 | Obtener las 10 transacciones más recientes. |
| RF-6 | El importe admite coma o punto decimal y hasta dos decimales (p. ej. `1234,56`). |
| RF-7 | Registrar una **devolución** (total o parcial) de un gasto, enlazándola al gasto original. |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | El importe se guarda **positivo**; el carácter de ingreso o gasto lo determina `type`. |
| RN-2 | El importe debe ser **estrictamente positivo** (`@Positive`); 0 o negativo se rechaza (HTTP 400 / validación en front). |
| RN-3 | La categoría debe existir y ser **compatible con la cuenta**: una categoría global vale para cualquier cuenta; una categoría de cuenta concreta solo vale para esa cuenta. En caso contrario, "La categoría pertenece a otra cuenta" (HTTP 400). |
| RN-4 | Cuenta y categoría deben existir; si no, "Cuenta no válida" / "Categoría no válida" (HTTP 400). |
| RN-5 | Un movimiento puede asociarse a una categoría **principal o a una subcategoría**; en las agregaciones las subcategorías se enrollan a su padre (ver PRD Categorías y Dashboard). |
| RN-6 | Las transacciones **sí** cuentan como ingreso/gasto en las agregaciones (a diferencia de las transferencias, que se excluyen). |
| RN-7 | Eliminar una transacción no tiene restricciones de integridad: se borra directamente (afecta de inmediato a los saldos calculados). Si el gasto tiene **devoluciones**, estas se borran en cascada (`ON DELETE CASCADE`). |
| RN-8 | Una **devolución** (`refund_of_id` ≠ null) es un gasto especial: **hereda cuenta, categoría y tipo del original**; solo aporta fecha, importe y descripción propias. El usuario no elige cuenta ni categoría. |
| RN-9 | Solo se pueden devolver **gastos** (`EXPENSE`); no se admite devolución de un ingreso ni de otra devolución. |
| RN-10 | El total devuelto de un gasto **no puede superar su importe** (suma de devoluciones ≤ importe original). Se valida en el front (orientativo) y en el back (autoritativo, con el pendiente en el mensaje). |
| RN-11 | En **todas** las agregaciones (saldo, ingresos/gastos por periodo y categoría, matriz anual, presupuesto "real"), una devolución **netea con signo invertido**: resta gasto en su categoría y suma al saldo. El neteo está centralizado en las queries de `TransactionRepository` (`case when t.refundOf is null then t.amount else -t.amount`), así que todo el dashboard y los presupuestos lo heredan. |

## 6. API

Base: `/api/transactions`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/transactions?from=&to=&accountId=&categoryId=` | Busca transacciones; todos los filtros son opcionales. Sin `from`/`to` el rango es `1970-01-01`–`2999-12-31`. Orden: fecha desc, id desc. | `200` + array. |
| `GET` | `/api/transactions/recent` | Las 10 más recientes (fecha desc, id desc). | `200` + array. |
| `POST` | `/api/transactions` | Crea una transacción. | `201` + transacción. |
| `PUT` | `/api/transactions/{id}` | Actualiza la transacción. | `200` / `404` si no existe. |
| `DELETE` | `/api/transactions/{id}` | Elimina la transacción. | `204`. |
| `POST` | `/api/transactions/import` | Importa un extracto (multipart `file` + `accountId` opcional). | Ver PRD Importación. |

**Cuerpo (`TransactionRequest`, create/update):**
```json
{
  "date": "2026-06-15",
  "amount": 49.90,
  "description": "Compra semanal",
  "type": "EXPENSE",
  "accountId": 1,
  "categoryId": 4,
  "refundOfId": null
}
```
Campos obligatorios: `date`, `amount` (>0), `type`, `accountId`, `categoryId`. `description` y `refundOfId` son opcionales. Si `refundOfId` está, el movimiento se guarda como **devolución** de ese gasto: el back ignora `type`/`accountId`/`categoryId` del cuerpo y los toma del original (el front los envía con los valores del original por comodidad).

## 7. UI/UX

Página `pages/transactions` (componente `TransactionsPage`).

- Lista **unificada de movimientos**: combina transacciones y transferencias en una sola tabla ordenada por fecha desc.
- Filtros: **desde**, **hasta**, **cuenta** y **categoría**. Filtrar por categoría oculta las transferencias (no tienen categoría).
- Formulario de alta/edición en un **diálogo modal** (`<dialog>` nativo abierto con `showModal()`): se centra en el viewport sin importar la posición de scroll, por lo que editar un movimiento desde el fondo de un listado largo no obliga a subir. El modal aporta de forma nativa atrapado de foco, cierre con `Escape` (que limpia el error) y devolución del foco al botón que lo abrió; tiene backdrop que oscurece el fondo. Incluye un selector de **tipo de movimiento**: `Gasto`, `Ingreso` o `Transferencia`. Al elegir Transferencia, el formulario cambia a origen/destino (ver PRD Transferencias).
- El desplegable de **categoría** muestra solo las categorías del tipo elegido y compatibles con la cuenta (globales o de esa cuenta), ordenadas padre→subcategoría con sangría; al cambiar tipo o cuenta, la categoría seleccionada se resincroniza.
- Importe con `inputmode="decimal"`, normalizado con `parseAmount` (admite coma o punto).
- **Conversión de tipo**: si al editar se cambia entre transacción y transferencia, el registro original se elimina y se crea el del otro tipo (no es una edición in situ).
- **Devoluciones**: el selector de tipo incluye **Devolución**. Al elegirla (o con el botón **Devolver** de cada fila de gasto), se muestra un selector del **gasto a devolver** (gastos con importe pendiente) y, en solo lectura, la cuenta/categoría heredadas y el **pendiente por devolver**; el importe se precarga con el pendiente. Una devolución se muestra en el listado con la marca **↩ Devolución**, su importe en positivo/verde (dinero que vuelve) y la categoría del gasto original. El botón **Devolver** solo aparece en gastos con pendiente > 0. (El cálculo del pendiente en el front es orientativo sobre los movimientos cargados; el back es la fuente autoritativa.)
- Botón de **importar** que abre el diálogo de importación (ver PRD Importación).
- **Estilo (sistema de diseño)**: la categoría se muestra como **chip** (pill con borde, fondo `--surface` y un punto de 8px del color de la categoría); las transferencias usan el mismo chip con punto neutro (`--border-strong`) y texto «⇄ Transferencia», con el importe atenuado (`--text-muted`); la marca de devolución es el prefijo **↩** en `--pos` (sin fondo). Importes en JetBrains Mono con `tabular-nums`, coloreados `--pos`/`--neg` según signo.

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Importe 0, negativo o no numérico | El front muestra "Importe no válido…" y no envía; el back rechaza con `@Positive`. |
| Falta fecha / tipo / cuenta / categoría | Rechazado (validación de `TransactionRequest`). |
| Categoría de otra cuenta | `400` "La categoría pertenece a otra cuenta". |
| Cuenta o categoría inexistente | `400` "Cuenta no válida" / "Categoría no válida". |
| Editar transacción inexistente | `404` "Movimiento no encontrado". |
| Devolución de un gasto inexistente | `400` "El gasto que se quiere devolver no existe". |
| Devolución de un ingreso o de otra devolución | `400` "Solo se pueden registrar devoluciones de gastos" / "No se puede registrar una devolución de otra devolución". |
| Devolución que supera el pendiente del gasto | `400` "La devolución supera el importe pendiente del gasto (pendiente: …)". |

## 9. Casos límite y notas

- Una transacción puede colgar de una subcategoría; su importe se enrolla al padre en las agregaciones, pero el registro conserva la subcategoría concreta.
- No hay límite de fecha futura ni pasada: se aceptan fechas cualesquiera.
- Borrar una cuenta o categoría con transacciones está bloqueado desde esos dominios (no desde aquí).
- La descripción está limitada a 255 caracteres por el esquema.

## 10. Backlog / mejoras futuras

- Paginación del listado de movimientos (hoy se devuelven todos los del filtro).
- Adjuntar etiquetas o notas largas a un movimiento.
- Movimientos recurrentes / plantillas.
- Edición en bloque (recategorizar varios a la vez).

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Positividad del importe | `@Positive` se valida solo en la API; la BD no tiene `check`. | Un insert directo en BD podría colar importes ≤ 0. Valorar un check de columna. |
| Borrado sin guardas | `DELETE` no tiene restricciones ni confirmación a nivel API (la confirmación está solo en el front). | Aceptable hoy; tenerlo en cuenta si se exponen integraciones externas. |
| Listado completo | El listado devuelve todas las filas del filtro, sin paginación. | Con históricos grandes conviene paginar (ver backlog). |

## 12. Referencias de código

Movimientos está migrado a **arquitectura hexagonal + DDD** (etapa H3 de `docs/migration-ddd-hexagonal.md`). La semántica de **devolución**, antes en `TransactionController.applyRefund`, es ahora una invariante del agregado.

- **Dominio** (`transactions/domain`): agregado `Transaction` (devolución como invariante: hereda tipo/cuenta/categoría del gasto original, no excede el pendiente, no es devolución de devolución ni de un ingreso; importe positivo), `TransactionId`, y los puertos de salida `TransactionRepository` (con `refundedAmountFor`), `AccountExistence` y `CategoryCatalog`. El tipo usa el enum compartido `shared/domain/TransactionType`.
- **Aplicación** (`transactions/application`): casos de uso `FindTransactions`/`CreateTransaction`/`UpdateTransaction`/`DeleteTransaction` + `TransactionService`, que preserva el orden de `apply`/`applyRefund` legado (incluida la comprobación «no es su propia devolución» y la resolución cuenta/categoría con su comprobación de ámbito). Lectura **CQRS**: read model `TransactionView` (con `account`/`category` anidados y `refundOf` por id) + `TransactionQueryPort` (search con ventana de fechas y filtros; recent).
- **Infraestructura** (`transactions/infrastructure`): `TransactionJpaEntity` (asociaciones `@ManyToOne` cuenta/categoría/refundOf vía `getReferenceById`), `TransactionJpaRepository` (search, recent, `sumRefundedAmount`), `TransactionJpaMapper`, `TransactionPersistenceAdapter`, `TransactionQueryAdapter`, `AccountExistenceAdapter`, `CategoryCatalogAdapter`; web: `TransactionController` fino + `TransactionRequest`. El endpoint `/import` sigue delegando en el `ImportService` legado hasta H7.
- Esquema: `db/migration/V1__init.sql`, `db/migration/V6__transaction_refunds.sql` (devoluciones; sin cambios, `TransactionJpaEntity` y el legado `model/Transaction` mapean la misma tabla).
- Frontend: `pages/transactions/` (`transactions.ts`, `transactions.html`), modelos `Transaction` / `TransactionRequest` en `models.ts` (sin cambios).
- Tests: dominio `transactions/domain/TransactionTest`; aplicación `transactions/application/TransactionServiceTest` (todas las ramas con puertos mockeados); persistencia `TransactionPersistenceAdapterTest` (`@DataJpaTest`: round-trip, suma de devoluciones, read model) más `AccountExistenceAdapterTest`/`CategoryCatalogAdapterTest`; contrato HTTP `TransactionControllerMvcTest`. Sigue vigente, contra Postgres real, `repository/TransactionRepositoryTest.java` (las sumas netas de agregación que aún usan dashboard/presupuestos sobre el repositorio legado).
- Relacionado: PRD Transferencias, PRD Importación de extractos, PRD Categorías, PRD Dashboard.
