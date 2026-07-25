# Plan de implementación — Posiciones cerradas / P&L realizado por año

**Rama/worktree:** `inv-posiciones-cerradas` (`/home/xroig/workspace/app_finance-cerradas`)
**Origen:** `docs/investment/mejoras-modulo-inversiones.md` §1.3, roadmap P1.3.
**Estado:** planificado, sin implementar.

## 1. Objetivo y alcance

**Qué entra:** una vista que responda "¿cuánto he ganado/perdido ya con lo que he vendido?", agregando el **P&L realizado por año natural e instrumento**, con el método de coste que ya usa la app (**promedio ponderado**, RN-3 del PRD) — coherente con lo implementado, sin introducir un segundo método de coste.

**Qué NO entra (explícitamente fuera de alcance):**
- **Coste FIFO** (tarea 4.1 del roadmap): el fiscalmente correcto en España; esta tarea no lo calcula. Cuando se aborde 4.1, convivirá con el promedio (uno para gestión, otro para fiscalidad), no lo sustituye.
- **Informe fiscal de plusvalías** (4.2): esta vista es su antesala conceptual (ya habrá dónde contrastar cifras cuando llegue), pero no genera nada con validez fiscal.
- Exportación, filtros avanzados, edición.

**Decisión de producto confirmada con el usuario (2026-07-25):** el desglose por año incluye **toda venta con P&L realizado**, aunque la posición del instrumento siga abierta hoy (venta parcial) — no solo instrumentos con posición hoy en cero. Coherente con el PRD (§9, "Ventas parciales: P&L realizado por coste promedio en el momento de la venta") y con la pregunta motivadora del roadmap ("cuánto gané con lo que ya vendí"). El diseño de este plan ya asumía esta opción; no requiere cambios.

**Orden de pestañas confirmado con el usuario (2026-07-25):** en `investments-operations`, el orden final de las 4 pestañas es **Operaciones / Dividendos / Cerradas / Importaciones**. "Cerradas" es la 3ª pestaña (justo después de Dividendos — misma familia: rendimiento de la cartera), "Importaciones" (rama paralela) es la 4ª. Ver §7 para la guía de coordinación al mergear.

## 2. Punto de partida real en el código (verificado, no es una suposición)

`PositionCalculator.applySell` (`domain/PositionCalculator.java:105-136`) **ya calcula** el P&L realizado por venta con coste promedio capitalizado (RN-3) y lo acumula en `MutablePosition.realized`, expuesto hoy como `Position.realizedPnl()` (`domain/Position.java:14`). Es un **total acumulado histórico**, sin desglose por año, y **no se consume en ningún sitio de producción** (`grep` confirma que solo `PositionCalculatorTest` lo lee) — el dato existe pero no tiene capa de lectura ni endpoint.

`InvestmentQueryAdapter.valueAt` (`infrastructure/persistence/InvestmentQueryAdapter.java:380-382`) **descarta explícitamente** las posiciones cerradas al construir `positions()`:
```java
if (!position.quantity().abs().exceeds(Quantity.ZERO)) {
    continue; // posición cerrada: solo cuenta su P&L realizado, no se lista
}
```
El comentario ya anticipaba este trabajo. Confirmado: **no hace falta ninguna migración Flyway** — todo sale de `investment_transaction`, ya persistida (coherente con "nada materializado", §3 PRD). No reserves ningún número de migración para esta tarea.

## 3. Diseño técnico backend

### 3.1 Dominio — desglose anual en `PositionCalculator`/`Position`

Extender `MutablePosition` (`PositionCalculator.java:186-204`) con `Map<Integer, CurrencyMoney> realizedByYear`, poblado en `applySell` junto al acumulado existente:

```java
realizedByYear.merge(tx.tradeDate().getYear(), netProceeds.subtract(costOfSold), CurrencyMoney::add);
```

Añadir el campo a `Position` (record, `domain/Position.java`):

```java
public record Position(SecurityId securityId, Quantity quantity,
                       CurrencyMoney costBasis, CurrencyMoney realizedPnl,
                       Map<Integer, CurrencyMoney> realizedByYear) { ... }
```

**Por qué en el mismo record y no un cálculo aparte:** el coste promedio solo es correcto en una única pasada cronológica (la que ya hace `calculate()`); separar el desglose anual en un método nuevo obligaría a reprocesar el histórico dos veces con el riesgo de que diverja del cálculo principal. Un solo punto de verdad.

**Impacto de cambiar la firma del record:** solo `MutablePosition.toPosition()` construye `Position` en producción (verificado por grep); los tests existentes de `PositionCalculatorTest` solo leen campos (`.realizedPnl()`), no construyen `Position` a mano — el cambio de firma no debería romper compilación fuera del propio `PositionCalculator`/tests, pero revisar al implementar.

Opcional (refactor del hito 1): un método de conveniencia `Position.isClosed()` (`!quantity().abs().exceeds(Quantity.ZERO)`) para sustituir la comprobación duplicada que hoy vive inline en `InvestmentQueryAdapter.valueAt:380`.

### 3.2 Aplicación — CQRS

Nuevo record en `application/`:

```java
public record ClosedPositionView(long securityId, String isin, String name, String ticker,
                                  String currency, int year, BigDecimal realizedPnl) {}
```

Una fila por (instrumento, año) con realizado ≠ 0 — igual que la tabla de Dividendos ya hace con instrumento/mes (`IncomeView`, patrón §7 PRD): el backend entrega el detalle, el frontend agrega la fila TOTAL y aplica el selector de año. No añadir un campo "total del año" en el backend; replicar el patrón ya validado.

Añadir a `InvestmentQueryPort`:
```java
/** P&L realizado por instrumento y año natural (RF-nuevo), coste promedio (RN-3). */
List<ClosedPositionView> closedPositions(long portfolioId);
```

### 3.3 Infraestructura — `InvestmentQueryAdapter`

Nuevo método `closedPositions(portfolioId)`: reutiliza `load(portfolio, converter())` + `calculator.calculate(...)` (mismo `Context`/`valueAt` ya existentes), pero a diferencia de `positions()` **no filtra por posición abierta** — itera todas las `Position` devueltas por `PortfolioPositions.positions()` y expande cada `realizedByYear` no vacío a una `ClosedPositionView` por año (según la decisión de producto de §1, incluye ventas parciales de posiciones hoy abiertas).

### 3.4 Web

`PortfolioController`: nuevo endpoint
```
GET /api/investments/portfolios/{id}/closed-positions
```
devolviendo `List<ClosedPositionView>`. Mismo patrón que `positions()` (`PortfolioController.java:92-95`), sin filtros ni paginación (volumen bajo: instrumentos × años con venta, no operaciones individuales).

## 4. Plan TDD por hitos

**Hito 1 — Dominio (rojo → verde → refactor)**
- Rojo: nuevo test en `PositionCalculatorTest` (`backend/src/test/java/com/xroig/finance/investments/domain/PositionCalculatorTest.java`), p. ej. `realizedByYear_bucketsBySaleYear()`: venta en 2024 y otra en 2025 del mismo instrumento → `position.realizedByYear()` = `{2024: ..., 2025: ...}` con los importes correctos; caso adicional `realizedByYear_emptyWhenNeverSold()`.
- Verde: extender `Position`/`MutablePosition`/`applySell` según §3.1.
- Refactor: opcionalmente, test de invariante `sum(realizedByYear.values()) == realizedPnl()`; extraer `Position.isClosed()` si se decide usarlo en el paso siguiente.

**Hito 2 — Aplicación + CQRS**
- Rojo: test `@DataJpaTest` del adapter (localizar el fichero existente de tests de `InvestmentQueryAdapter`, mismo paquete que producción) — caso: cartera con venta completa en 2023 (posición cerrada) + venta parcial en 2024 (posición sigue abierta) → `closedPositions()` devuelve 2 filas con el `realizedPnl` esperado por año.
- Verde: `ClosedPositionView`, método en `InvestmentQueryPort`, implementación en `InvestmentQueryAdapter`.

**Hito 3 — Web**
- Rojo: `@WebMvcTest` de `PortfolioController` (fichero existente de tests del controller) — `GET /portfolios/{id}/closed-positions` con el query port mockeado, 200 + JSON esperado.
- Verde: endpoint en `PortfolioController`.

**Hito 4 — Frontend**
- Nueva pestaña "Cerradas" en `investments-operations` (patrón de control segmentado §7 PRD), **3ª posición fija: Operaciones / Dividendos / Cerradas / Importaciones** (orden acordado, ver §7 — la pestaña "Importaciones" de la rama paralela va después, no antes). Selector de año (mismo patrón que Dividendos) y tabla agregada por instrumento + fila TOTAL, sin paginar.
- Rojo: Vitest en `investments-operations.spec.ts` — nueva pestaña, mock de `getClosedPositions`, verifica render de filas y total.
- Verde: nueva pestaña en `investments-operations.ts`/`.html`, modelo `ClosedPosition` en `models.ts`, método `getClosedPositions(portfolioId)` en `api.service.ts`.
- Opcional: extender `e2e/investments-operations.spec.ts` (Playwright) con un caso de venta fixture → verificar P&L realizado visible.

Cada hito cierra con **un commit** (tests en verde + PRD actualizado en el hito que corresponda), como manda `CLAUDE.md`.

## 5. Actualización de PRD requerida

`docs/prd/inversiones.md`:
- §4 (Requisitos funcionales): nueva fila RF (siguiente número libre tras RF-10) — "El usuario ve el P&L realizado agregado por año natural e instrumento (coste promedio)".
- §6 (API): nueva fila `GET /portfolios/{id}/closed-positions`.
- §7 (UI/UX): nueva pestaña "Cerradas" en la página Operaciones, mismo patrón que Dividendos.
- §10 (Backlog): retirar la entrada de "posiciones cerradas" si existe explícita, o dejar constancia de que queda resuelta.
- §11 (Deuda técnica): mantener la fila de "Método de coste" (FIFO pendiente) tal cual — esta tarea no la cierra, solo confirma que el promedio ya tiene capa de lectura completa para P&L realizado.
- §12 (Fases): documentar el hito como parte de F4 o nueva fase, según cómo se quiera versionar; §13 (Referencias de código): añadir las clases nuevas.
- Bump de "Última actualización" y versión.

## 6. Terreno preparado para 4.1/4.2 (sin implementarlos)

Esta vista deja: (a) el punto exacto donde enchufar FIFO el día que se aborde 4.1 — sustituyendo o añadiendo junto a `realizedByYear` un segundo desglose con otro método de coste, sin tocar el resto del pipeline; (b) una UI de referencia donde contrastar las cifras del futuro informe fiscal (4.2) contra las de gestión.

## 7. Puntos de fricción con las otras dos tareas paralelas

- **`InvestmentQueryAdapter.java` / `InvestmentQueryPort.java`**: esta tarea añade un método nuevo (`closedPositions`); la tarea de API de cotizaciones probablemente no toca estos ficheros (opera sobre `PriceProviderPort` y un adaptador nuevo), riesgo bajo. Si esa tarea añade un botón de refresco vía `PortfolioController`, coincidiría en el mismo fichero que el endpoint de este plan — conflicto de merge trivial (métodos distintos), no de lógica.
- **`docs/prd/inversiones.md`**: las tres tareas lo tocan — conflicto de merge garantizado pero textual, resolver a mano al integrar, una rama cada vez (como ya se acordó).
- **Frontend `investments-operations.*`**: **choque confirmado** con la rama de historial de imports — las dos añaden una pestaña nueva al mismo componente (mismo `activeTab` union type, mismo template de control segmentado). Orden acordado con el usuario: **Operaciones / Dividendos / Cerradas / Importaciones**. Protocolo de integración: la rama que mergee primero añade su pestaña en la posición que le toca dentro de las que existan en ese momento; la que mergee segunda hace `rebase`/merge sobre la primera y añade la suya en la posición acordada (no reordena las ya mergeadas). Esta rama (Cerradas) va en 3ª posición pase lo que pase.
- **Migraciones**: esta tarea no reserva ninguna. `V8` queda para historial de imports; `V9` libre por si la tarea de precios acaba necesitando ampliar `price_quote`.
