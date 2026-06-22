# PRD — Categorías y subcategorías

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.1 |
| Última actualización | 2026-06-22 |
| Dominio | Categorías (`categories`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de las categorías (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

---

## 1. Propósito

Una **categoría** clasifica los movimientos como ingreso o gasto y es la unidad sobre la que se construyen las agregaciones (dashboard) y los presupuestos. Cada categoría puede tener **un único nivel de subcategorías** para un desglose más fino, y puede ser **global** (común a todas las cuentas) o estar **acotada a una cuenta** concreta.

## 2. Objetivos y no-objetivos

**Objetivos**
- Crear, consultar, editar y eliminar categorías y subcategorías.
- Distinguir categorías de ingreso y de gasto.
- Permitir un nivel de jerarquía (categoría → subcategoría) que se enrolla al padre en las agregaciones.
- Permitir categorías globales y categorías acotadas a una cuenta, incluso combinando ambos ámbitos dentro de un mismo árbol cuando el padre es global.

**No-objetivos (fuera de alcance de este PRD)**
- Agregaciones y gráficos por categoría → PRD Dashboard.
- Presupuestos por categoría → PRD Presupuestos.
- Reglas de auto-categorización en importación → PRD Reglas de categorización.
- Más de un nivel de jerarquía.

## 3. Modelo de datos

Tabla `categories` (migraciones `V1__init.sql`, `V3__categories_per_account.sql`, `V4__subcategories.sql`), entidad `Category`:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `name` | `varchar(255)` | `NOT NULL`, no vacío | Unicidad por ámbito y por padre (ver índice). |
| `type` | `varchar(255)` | `NOT NULL`, `check in ('INCOME','EXPENSE')` | Tipo de movimiento. |
| `color` | `varchar(255)` | `NOT NULL` | Color de la insignia. Por defecto `#6366f1`. |
| `account_id` | `bigint` → `accounts` | Nullable | `null` = **global**; si tiene valor, la categoría es de esa cuenta. |
| `parent_id` | `bigint` → `categories` | Nullable | `null` = categoría principal; si tiene valor, es subcategoría de ese padre (auto-referencia). |

**Índice único** (`V4`): `(name, coalesce(account_id, 0), coalesce(parent_id, 0))`. Es decir, el nombre es único **por ámbito (cuenta) y por categoría padre**. Dos subcategorías con el mismo nombre bajo padres distintos —o bajo el mismo padre global pero en cuentas distintas— pueden coexistir.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Listar todas las categorías (principales y subcategorías). |
| RF-2 | Crear una categoría principal indicando nombre, tipo, color y ámbito (global o una cuenta). |
| RF-3 | Crear una subcategoría bajo una categoría principal. |
| RF-4 | Editar nombre, color, tipo, ámbito y padre de una categoría, dentro de las reglas de §5. |
| RF-5 | Eliminar una categoría que no tenga subcategorías, movimientos, presupuestos ni reglas asociados. |
| RF-6 | Un movimiento puede asociarse tanto a una categoría principal como a una subcategoría. |

## 5. Reglas de negocio

### Ámbito (global vs. cuenta)

| ID | Regla |
|---|---|
| RN-1 | Una categoría es **global** (`account_id` null, común a todas las cuentas) o pertenece a **una sola cuenta**. |
| RN-2 | Las categorías utilizables en una cuenta son las globales más las propias de esa cuenta (`CategoryRepository.findVisibleForAccount`). |

### Jerarquía (un nivel)

| ID | Regla |
|---|---|
| RN-3 | Solo se permite **un nivel** de subcategorías: una subcategoría no puede tener hijas. |
| RN-4 | Una categoría que ya tiene subcategorías **no puede convertirse** en subcategoría (HTTP 400). |
| RN-5 | Una categoría no puede ser su propia categoría principal (HTTP 400). El padre debe existir y ser de primer nivel (HTTP 400 en caso contrario). |
| RN-6 | Una subcategoría **hereda siempre el tipo** (`INCOME`/`EXPENSE`) de su padre. |

### Ámbito en la jerarquía

| ID | Regla |
|---|---|
| RN-7 | Si el padre está **acotado a una cuenta**, la subcategoría hereda esa misma cuenta (ámbito bloqueado). |
| RN-8 | Si el padre es **global**, la subcategoría puede ser **global o de cualquier cuenta** (ámbito elegible). |
| RN-9 | Una categoría principal solo puede **reasignarse a una cuenta** concreta si **todas** sus subcategorías ya pertenecen a esa misma cuenta; en caso contrario (subcategorías globales o de otra cuenta) se rechaza (HTTP 400). Pasar a global siempre se permite. |

### Agregación

| ID | Regla |
|---|---|
| RN-10 | En las agregaciones, las subcategorías **se enrollan a su categoría principal**: los sumatorios agrupan por `coalesce(parent.id/name, …)` (`TransactionRepository.sumByCategory`, `sumByCategoryAndMonthOfYear`). |
| RN-11 | El "gastado" de un presupuesto incluye el árbol completo de la categoría (principal + subcategorías) vía `sumByCategoryTreeAndPeriod`. |
| RN-12 | Los presupuestos se definen sobre **categorías hoja** (principal sin subcategorías, o subcategoría); presupuestar una categoría **con** subcategorías se rechaza, pues actúa como agregado de sus hijas (ver PRD Presupuestos). |

### Eliminación e integridad

| ID | Regla |
|---|---|
| RN-13 | No se puede eliminar una categoría con **subcategorías** (HTTP 409). |
| RN-14 | No se puede eliminar una categoría con **movimientos** asociados (HTTP 409). |
| RN-15 | No se puede eliminar una categoría con un **presupuesto** asociado (HTTP 409). |
| RN-16 | No se puede eliminar una categoría con **reglas de categorización** asociadas (HTTP 409). |
| RN-17 | Al editar el ámbito, si la categoría tiene movimientos de **otra** cuenta distinta de la nueva, se rechaza (HTTP 409). |

### Recurrencia (pago previsto)

| ID | Regla |
|---|---|
| RN-18 | Una categoría puede declarar una **recurrencia** (pago previsto: meses activos + importes con vigencia) que alimenta el previsto de la matriz de presupuestos. El detalle del modelo y el cálculo viven en el **PRD Presupuestos**. |
| RN-19 | Solo admiten recurrencia las categorías **hoja** (sin subcategorías) y **ligadas a una cuenta**. Las **globales no admiten recurrencia** (HTTP 400 al hacer `PUT` de la recurrencia): si una global la necesita, se crea una subcategoría ligada a una cuenta. |
| RN-20 | Si una categoría tiene recurrencia, **no puede hacerse global** (HTTP 409 al editar) ni **ganar subcategorías** (HTTP 409 al crear una subcategoría bajo ella); hay que quitar la recurrencia primero. Borrar la categoría arrastra su recurrencia en cascada (`ON DELETE CASCADE`). |

### Datos por defecto

| ID | Regla |
|---|---|
| RN-21 | En el primer arranque se siembran categorías **globales** por defecto (`DataSeeder.seedCategories`): ingresos `Nómina`, `Otros ingresos`; gastos `Vivienda`, `Alimentación`, `Transporte`, `Ocio`, `Salud`, `Suscripciones`, `Otros gastos`. Esta siembra es independiente de los datos demo. |
| RN-22 | `Otros gastos` / `Otros ingresos` son la categoría de respaldo para la auto-categorización en importación (ver PRD Reglas de categorización). |

## 6. API

Base: `/api/categories`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/categories` | Lista todas las categorías. | `200` + array. |
| `POST` | `/api/categories` | Crea categoría/subcategoría. El `id` enviado se ignora. | `201` + categoría. |
| `PUT` | `/api/categories/{id}` | Actualiza la categoría según las reglas de §5. | `200` / `404` si no existe. |
| `DELETE` | `/api/categories/{id}` | Elimina si no tiene dependencias. | `204` / `409`. |

El `account` y el `parent` se envían como objetos anidados con solo `{ "id": n }`; `null` significa global / categoría principal respectivamente.

La **recurrencia** de una categoría se gestiona como sub-recurso: `GET/PUT/DELETE /api/categories/{id}/recurrence` (detalle y payload en el PRD Presupuestos).

**Ejemplo (subcategoría de cuenta bajo padre global):**
```json
{ "name": "Restaurante", "color": "#f59e0b", "parent": { "id": 4 }, "account": { "id": 2 } }
```
El backend fuerza `type` al del padre; si el padre fuese de cuenta, ignoraría el `account` enviado y heredaría el del padre.

## 7. UI/UX

Página `pages/categories` (componente `CategoriesPage`).

- Dos columnas: **Gastos** e **Ingresos**. Cada categoría principal muestra su insignia de color y una etiqueta de ámbito (**Global** o nombre de cuenta); sus subcategorías cuelgan debajo con marca `↳` y su propia etiqueta de ámbito.
- Acciones por fila: **＋ Sub** (solo en principales), **Editar**, **Eliminar**.
- El formulario de alta/edición se abre en un **diálogo modal** (`<dialog>` nativo con `showModal()`): se centra en el viewport sin importar el scroll, con focus-trap, cierre con `Escape` (limpia el error) y devolución del foco al botón disparador; tiene `max-height` con scroll interno porque incluye la sección de recurrencia. Campos: **Nombre**, **Categoría principal** (selector; "Ninguna" = principal), **Tipo** (deshabilitado si es subcategoría, se hereda), **Ámbito** (Global o una cuenta) y **Color**.
- El selector de **Ámbito** se bloquea **solo** para subcategorías cuyo padre está acotado a una cuenta (`scopeLocked`). Bajo un padre global, el ámbito de la subcategoría es elegible.
- Texto de ayuda contextual que explica la herencia de tipo y, según el caso, la herencia o libertad de ámbito.
- **Sección Recurrencia** (pago previsto): visible y editable solo cuando la categoría es **hoja y está ligada a una cuenta** (`canHaveRecurrence`); en otro caso muestra un aviso explicando que hay que crear una subcategoría ligada a una cuenta. Permite marcar la recurrencia como activa, seleccionar los **meses** (botones Ene…Dic multiselección) e introducir uno o varios **importes con su "válido desde"** (mes/año, precargado al mes actual). Al guardar la categoría, el formulario hace el `PUT`/`DELETE` de la recurrencia en cadena; si se desactiva el ámbito de cuenta de una categoría con recurrencia, esta se elimina antes de actualizar la categoría para no chocar con la validación del backend. Cada paso de la cadena (recurrencia previa / categoría / recurrencia nueva) mapea su propio error, de modo que un fallo al guardar la recurrencia nunca se reporta como "nombre de categoría duplicado".

  El `upsert` de la recurrencia (`PUT /api/categories/{id}/recurrence`) **reconcilia** los importes por `validoDesde` (actualiza los que permanecen, añade los nuevos y elimina el resto) en lugar de borrarlos y reinsertarlos; así editar un importe conservando su mes de vigencia no viola la restricción de unicidad `(recurring_budget_id, valido_desde)` por el orden insert-antes-de-delete de Hibernate.
- En la misma página vive la sección de **Reglas de categorización automática** (dominio aparte → PRD Reglas de categorización).

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Nombre vacío | Rechazado (`@NotBlank` + `required`). |
| Nombre duplicado en el mismo ámbito/padre | `409` con `detail` "Ya existe una categoría con ese nombre en ese ámbito". La violación del índice único `ux_categories_name_scope` la captura `GlobalExceptionHandler` (`@RestControllerAdvice`), que convierte cualquier `DataIntegrityViolationException` en un `409` con mensaje claro en vez de un `500` opaco; la UI solo lee `detail`/`message`. |
| Padre inexistente o de segundo nivel | `400`. |
| Categoría como su propio padre | `400`. |
| Convertir en subcategoría una categoría con hijas | `400`. |
| Reasignar a cuenta una principal con subcategorías incompatibles | `400` (RN-9). |
| Editar ámbito con movimientos de otra cuenta | `409` (RN-17). |
| Eliminar con subcategorías / movimientos / presupuesto / reglas | `409`. |
| Editar categoría inexistente | `404`. |

## 9. Casos límite y notas

- Una categoría global aparece en todas las cuentas; una subcategoría de cuenta concreta (bajo padre global) solo aparece en esa cuenta, pero sus importes se enrollan a la categoría global en las agregaciones.
- Dos subcategorías con el mismo nombre bajo el mismo padre global pero en cuentas distintas son válidas (índice único por ámbito).
- Cambiar el ámbito de una principal **no** está materializado en saldos; afecta a visibilidad y a validaciones de integridad.
- Los movimientos pueden colgar de una principal o de una subcategoría indistintamente.

## 10. Backlog / mejoras futuras

- Más de un nivel de jerarquía (si surge la necesidad real).
- Reordenar/colorear subcategorías heredando opcionalmente el color del padre.
- Mover en bloque las subcategorías de una cuenta a otra.
- Cascada explícita al cambiar el ámbito de una principal (hoy se valida y se rechaza en vez de propagar).

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Duplicado como `500` | La violación del índice único escapa como `500`; el front lo disfraza de "duplicado" (`categories.ts`). | Capturar el conflicto en el backend y devolver `409` con mensaje claro, sin depender del `500`. |
| Cambio de ámbito del padre | Reasignar una principal a una cuenta con subcategorías incompatibles se **rechaza** (RN-9), no se propaga en cascada. | Decisión consciente; revisar si interesa una cascada explícita (ver backlog). |

## 12. Referencias de código

- Backend: `model/Category.java`, `controller/CategoryController.java`, `controller/GlobalExceptionHandler.java`, `repository/CategoryRepository.java`.
- Recurrencia: `controller/RecurringBudgetController.java`, `service/RecurringBudgetService.java` (validaciones hoja + ligada a cuenta), guardas en `controller/CategoryController.java` (`create`/`update`). Ver PRD Presupuestos.
- Agregación / roll-up: `repository/TransactionRepository.java` (`sumByCategory`, `sumByCategoryAndMonthOfYear`, `sumByCategoryTreeAndPeriod`).
- Siembra por defecto: `config/DataSeeder.java` (`seedCategories`).
- Esquema: `db/migration/V1__init.sql`, `V3__categories_per_account.sql`, `V4__subcategories.sql`.
- Tests: `controller/CategoryControllerTest.java` (reglas de ámbito/recurrencia con repos mockeados), `controller/GlobalExceptionHandlerTest.java` (mapeo de mensajes) y, contra Postgres real, `repository/ConstraintViolationsTest.java` (el índice `ux_categories_name_scope` impide nombres duplicados en el mismo ámbito/padre pero permite el mismo nombre en otra cuenta o bajo otro padre, y la `DataIntegrityViolationException` resultante se mapea al `409` real del handler) y `repository/CategoryRepositoryTest.java` (`findVisibleForAccount` devuelve las globales más las propias de la cuenta excluyendo las de otras cuentas; `findByParentId` devuelve solo los hijos directos). El contrato HTTP se cubre en `controller/CategoryControllerMvcTest.java` (slice `@WebMvcTest`): binding del JSON anidado `parent {id}`/`account {id}` (la subcategoría hereda el `type` del padre), validación `@NotBlank`/`@NotNull`→400, y los `ResponseStatusException` como `problem+json` (padre inexistente→400, subcategoría bajo padre con recurrencia→409, no encontrada→404, borrado con subcategorías→409).
- Frontend: `pages/categories/` (`categories.ts`, `categories.html`), modelo `Category` en `models.ts`.
