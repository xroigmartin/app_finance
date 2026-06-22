# PRD — Reglas de categorización automática

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.0 |
| Última actualización | 2026-06-22 |
| Dominio | Reglas de categorización (`category_rules`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de las reglas (modelo, API, lógica de coincidencia o UI). Ver `docs/README.md`.

---

## 1. Propósito

Una **regla de categorización** asigna automáticamente una categoría a los movimientos importados que no traen categoría explícita, según un patrón de texto que aparezca en la descripción. Evita tener que clasificar a mano cada importación.

## 2. Objetivos y no-objetivos

**Objetivos**
- Definir patrones de texto que asignen una categoría.
- Aplicar esos patrones automáticamente durante la importación.
- Al crear o editar una regla, recategorizar los movimientos que cayeron en la categoría de respaldo.

**No-objetivos (fuera de alcance de este PRD)**
- Proceso de importación en sí → PRD Importación de extractos.
- Definición de categorías → PRD Categorías.

## 3. Modelo de datos

Tabla `category_rules` (migración `V2__category_rules.sql`), entidad `CategoryRule`:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `pattern` | `varchar(255)` | `NOT NULL`, no vacío | Patrón de coincidencia (ver §5). |
| `category_id` | `bigint` → `categories` | `NOT NULL` | Categoría destino que asigna la regla. |

No hay restricción de unicidad sobre el patrón: pueden existir varias reglas con patrones solapados.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Crear una regla con un patrón y una categoría destino. |
| RF-2 | Editar el patrón o la categoría de una regla. |
| RF-3 | Eliminar una regla. |
| RF-4 | Listar todas las reglas. |
| RF-5 | Al crear o editar una regla, recategorizar automáticamente los movimientos de respaldo que coincidan, y reportar cuántos se movieron. |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | **Coincidencia**: el patrón es una **subcadena sin distinguir mayúsculas ni acentos** de la descripción. Se admiten varias alternativas separadas por `\|`; coincide si **alguna** está contenida en la descripción (normalización vía `ImportFileParser.normalizeHeader`). |
| RN-2 | Durante la importación, a un movimiento sin categoría se le asigna la categoría de la **primera regla** que coincida; si ninguna coincide, va a la categoría de respaldo "Otros gastos" / "Otros ingresos" según el tipo (ver PRD Importación). |
| RN-3 | Al **guardar** una regla (alta o edición), se reaplican sus patrones a los movimientos que están en la categoría de respaldo del tipo correspondiente, moviendo los que coincidan a la categoría de la regla (`RecategorizationService.applyRule`). |
| RN-4 | La recategorización **solo** toca movimientos de respaldo ("Otros gastos/ingresos"); nunca sobrescribe una categoría asignada explícitamente o por otra regla. |
| RN-5 | Si la categoría destino es **de una cuenta** concreta, solo se recategorizan movimientos de **esa** cuenta; si es **global**, se recategorizan con independencia de la cuenta. |
| RN-6 | El tipo (ingreso/gasto) de los movimientos recategorizados lo determina la categoría de respaldo del mismo tipo que la categoría destino. |
| RN-7 | Una categoría con reglas asociadas **no se puede eliminar** (ver PRD Categorías, RN-16). |

## 6. API

Base: `/api/category-rules`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/category-rules` | Lista todas las reglas. | `200` + array. |
| `POST` | `/api/category-rules` | Crea una regla y la reaplica al respaldo. | `201` + `RuleResponse`. |
| `PUT` | `/api/category-rules/{id}` | Actualiza una regla y la reaplica. | `200` / `404` si no existe. |
| `DELETE` | `/api/category-rules/{id}` | Elimina la regla. | `204`. |

**`RuleRequest`:** `{ "pattern": "consum|lidl|spar", "categoryId": 4 }`

**`RuleResponse`:** `{ "rule": { … }, "recategorized": 7 }` — `recategorized` es cuántos movimientos de respaldo se movieron a la categoría de la regla.

## 7. UI/UX

La gestión de reglas vive en la **página de Categorías** (`pages/categories`), en la sección "Reglas de categorización automática".

- Tabla de reglas con patrón y categoría (con su etiqueta de ámbito).
- El formulario de alta/edición se abre en un **diálogo modal** (`<dialog>` nativo con `showModal()`), centrado en el viewport sin importar el scroll, con focus-trap, cierre con `Escape` (limpia el error) y devolución del foco al botón disparador. Campos: **Patrón** (placeholder `consum|lidl|spar`) y **Categoría** (desplegable ordenado padre→subcategoría con ámbito).
- Tras guardar, se muestra un mensaje con el resultado: cuántos movimientos de "Otros gastos/ingresos" se recategorizaron, o que ninguno coincidió.
- Texto de ayuda que explica la sintaxis del patrón y el uso de `\|` como separador de alternativas.

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Patrón vacío | Rechazado (`@NotBlank` + `required`). El patrón se recorta (`trim`) al guardar. |
| Categoría inexistente | `400` "Categoría no válida". |
| Editar regla inexistente | `404` "Regla no encontrada". |

## 9. Casos límite y notas

- La lógica de coincidencia es **compartida** entre la importación y la recategorización (`RecategorizationService.matches`), de modo que ambas se comportan igual.
- El orden de evaluación en la importación es el de `findAll()` del repositorio; con patrones solapados, gana la primera regla que coincida. No hay prioridad explícita configurable.
- Una alternativa en blanco dentro del patrón (p. ej. `lidl|`) se ignora.

## 10. Backlog / mejoras futuras

- Prioridad/orden explícito de reglas.
- Reaplicar una regla a **todos** los movimientos (no solo a los de respaldo), bajo confirmación.
- Patrones con comodines o expresiones regulares.
- Reglas por cuenta (hoy el ámbito lo hereda la categoría destino, no la regla).

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Sin prioridad explícita | Con patrones solapados gana la primera regla de `findAll()`, cuyo orden no está garantizado. | Introducir un campo de orden/prioridad (deuda compartida con el PRD Importación). |
| Recategorización limitada al respaldo | Guardar una regla solo recategoriza "Otros gastos/ingresos", no todos los movimientos. | Decisión consciente; valorar reaplicar a todo bajo confirmación (ver backlog). |

## 12. Referencias de código

- Backend: `model/CategoryRule.java`, `controller/CategoryRuleController.java` (incluye `RuleRequest`, `RuleResponse`), `service/RecategorizationService.java`, `repository/CategoryRuleRepository.java`.
- Coincidencia compartida con la importación: `service/ImportFileParser.java` (`normalizeHeader`).
- Esquema: `db/migration/V2__category_rules.sql`.
- Tests: `service/RecategorizationServiceTest.java` y `service/RecategorizationServiceApplyRuleTest.java` (lógica de coincidencia y aplicación) y `controller/CategoryRuleControllerTest.java` (CRUD con mocks); el contrato HTTP en `controller/CategoryRuleControllerMvcTest.java` (slice `@WebMvcTest`): validación del record `RuleRequest` (`@NotBlank pattern`/`@NotNull categoryId`→400), forma del JSON `RuleResponse` (`{rule, recategorized}` con `pattern` recortado) y los `ResponseStatusException` como `problem+json` (categoría no válida→400, regla no encontrada→404).
- Frontend: sección de reglas en `pages/categories/` (`categories.ts`, `categories.html`), modelos `CategoryRule` / `RuleRequest` en `models.ts`.
- Relacionado: PRD Importación de extractos, PRD Categorías, PRD Movimientos.
