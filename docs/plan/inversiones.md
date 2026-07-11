# Plan de desarrollo — Módulo Inversiones (`investments`)

> Documento de **seguimiento de implementación**. La fuente de verdad funcional es
> `docs/prd/inversiones.md` (v0.26, diseño aprobado): este plan no repite el diseño,
> lo referencia (§ y RN-x apuntan al PRD).
>
> **Estado: en curso.** Marca cada tarea con `[x]` al completarla. Un hito está
> cerrado cuando todas sus tareas están marcadas **y existe su commit**.

## Cómo retomar una sesión

Prompt sugerido para continuar en una sesión nueva:

> Lee `docs/plan/inversiones.md` y `docs/prd/inversiones.md`. Continúa por la primera
> tarea sin marcar del primer hito abierto, con TDD estricto (red-green-refactor).
> Al cerrar cada hito: tests en verde, PRD actualizado si procede, marcar las tareas
> en el plan y commit.

## Convenciones de trabajo

- **TDD obligatorio** (ver `CLAUDE.md`): cada tarea de código es uno o más ciclos
  red-green-refactor; nunca producción sin test rojo previo.
- **Un commit por hito** (tests verdes + plan actualizado + PRD si el hito cambia
  comportamiento documentado). Un hito no empieza hasta que el anterior está commiteado.
- El **orden de los hitos es dependencia real**: no adelantar hitos.
- Al marcar tareas, actualizar también la tabla de estado global.
- Fixtures del Flex: usar los informes reales `docs/investment/2024.xml` y `2025.xml`
  como referencia de formato (anonimizar lo necesario en los fixtures de test).
- Al cerrar F1 completa: actualizar el estado del PRD y de `docs/README.md`
  (📐 diseño → 🚧 parcial / ✅ implementado según avance).

## Estado global

| Fase | Contenido | Hitos | Estado |
|---|---|---|---|
| F1 | Modelo + import Flex + posiciones/valoración multidivisa | H1.1 – H1.12 | 🔨 En curso (H1.1 ✅) |
| F2 | Vistas de rentas y alta manual | H2.1 – H2.4 | ⬜ Pendiente |
| F3 | Rentabilidad TWR/XIRR | H3.1 – H3.4 | ⬜ Pendiente |
| F4 | Automatización (precios online, Flex Web Service) | H4.1 – H4.3 | 🗄️ Backlog (sin planificar) |

Leyenda: ⬜ pendiente · 🔨 en curso · ✅ hecho (commit creado) · 🗄️ backlog

---

## F1 — Modelo + import Flex + posiciones/valoración multidivisa

### H1.1 — Value objects del contexto ✅

Tests: unitarios de dominio.

- [x] `CurrencyMoney` (importe + divisa ISO 4217; escala 4, redondeo fijado; operaciones solo entre misma divisa) — PRD §3.
- [x] Ids tipados `PortfolioId`, `SecurityId` (y los que pida el modelo: `InvestmentTransactionId`).
- [x] `Quantity` decimal (escala 8; fracciones de acción y residuos FX; comparación con tolerancia de precisión para RN-4).
- [x] Refactor + suite verde (500 tests).
- [x] Commit del hito.

### H1.2 — Agregados `Security` + `PriceQuote` + `ExchangeRate` + conversión ⬜

Tests: unitarios de dominio.

- [ ] `Security`: identidad ISIN+divisa, `isin`/`name`/`currency` obligatorios, metadatos `ticker`/`exchange`/`figi` (§9); refresco de metadatos no identitarios (RN-9).
- [ ] `PriceQuote`: unicidad (security, fecha), precio escala 8, semántica upsert (RN-9).
- [ ] `ExchangeRate`: unicidad (fecha, par), normalización divisa→EUR, semántica upsert (RN-9).
- [ ] Servicio de conversión de dominio: último tipo ≤ fecha; **pares cruzados vía pivote EUR** (`from→to = (from→EUR) ÷ (to→EUR)`), RN-7.
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.3 — Agregados `Portfolio` e `InvestmentTransaction` ⬜

Tests: unitarios de dominio.

- [ ] `Portfolio`: nombre obligatorio, `base_currency` ISO 4217.
- [ ] `InvestmentTransaction`: los 11 tipos (§3), campos condicionales (`security` nulo en efectivo, `counter_*` solo en `FX_TRADE`, `fee_currency`/`tax_currency` solo si difieren).
- [ ] Invariantes del **convenio de signos por tipo** (tabla §3): `BUY` qty>0/amount<0, `SELL` qty<0/amount>0, `SPLIT` amount=0, `FX_TRADE` amount<0/counter>0, etc. → `ValidationException` (§8).
- [ ] `external_id` opcional (nulo en apuntes manuales) — RN-10.
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.4 — Servicio de dominio `PositionCalculator` ⬜

Tests: unitarios de dominio.

- [ ] Cantidad por instrumento = Σ `quantity` directa (compras, ventas, deltas de `SPLIT`) — RN-3.
- [ ] Coste medio promedio con **coste de compra capitalizado** (`|amount| + |fee| + |trade_tax|`) — RN-3.
- [ ] P&L realizado en ventas (neto percibido − coste capitalizado promedio) y ventas parciales (§9).
- [ ] `SPLIT` como delta a coste 0: reduce coste medio sin reprocesar histórico — RN-3.
- [ ] Efectivo por divisa: suma directa firmada de `amount` + `counter_amount` + `fee`/`tax` según divisa efectiva — RN-2.
- [ ] Venta sin posición suficiente: detección con tolerancia 1e-8 (RN-4) — el calculador la señala; la dureza (400 vs warning) la decide el caso de uso.
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.5 — Puertos de salida + servicios de aplicación CRUD ⬜

Tests: aplicación con puertos mockeados.

- [ ] Puertos: `PortfolioRepository`, `SecurityRepository`, `InvestmentTransactionRepository`, `PriceQuoteRepository`, `ExchangeRateRepository` (y `PriceProviderPort` definido, sin adaptador — §2).
- [ ] `PortfolioService`: crear/listar/editar/eliminar; guarda RN-5 (cartera con operaciones → `ConflictException`).
- [ ] `SecurityService`: CRUD; guarda RN-5 (instrumento con operaciones → `ConflictException`).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.6 — Migración `V6__investments.sql` + persistencia JPA ⬜

Tests: `@DataJpaTest` (Testcontainers).

- [ ] Migración V6: `CREATE SCHEMA investments` + tablas `security`, `price_quote`, `portfolio`, `investment_transaction`, `exchange_rate` con tipos/constraints de §3 (`UNIQUE (isin, currency)`, `UNIQUE (portfolio_id, external_id)`, unicidades de cotización y tipo).
- [ ] Entidades JPA con `@Table(schema = "investments")` + mappers dominio↔entidad.
- [ ] Adaptadores de persistencia implementando los puertos de H1.5 (upsert de cotizaciones/tipos, RN-9).
- [ ] Tests: round-trip de mappers, violación de unicidades, `ddl-auto=validate` pasa contra el esquema.
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.7 — Read-side CQRS (query port + views + adapter) ⬜

Tests: `@DataJpaTest` del adapter.

- [ ] `InvestmentQueryPort` + records `PositionView`, `PortfolioSummaryView`, `ValuationHistoryView`.
- [ ] Posiciones valoradas: última cotización ≤ fecha (RN-6), posición sin cotización → a coste con aviso.
- [ ] Conversión a divisa base con **doble mecanismo** RN-7: snapshots `fx_rate_to_base` para importes fijados (a), tabla `exchange_rate` para valoración a fecha (b).
- [ ] Summary por cartera: valor total, aportado neto, P&L latente (% sobre coste capitalizado), efectivo por divisa, dividendos del año, fecha de valoración (§6).
- [ ] Valuation-history: serie `{fecha, valor, aportado acumulado}` (§6/§7).
- [ ] Resumen global multi-cartera **en EUR** (conversión vía pivote; fecha = la más antigua) — RF-10.
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.8 — Capa web + ArchUnit ⬜

Tests: `@WebMvcTest` + `ArchitectureTest`.

- [ ] `PortfolioController` + `SecurityController` + DTOs bajo `/api/investments` (§6): CRUD carteras, CRUD securities (`DELETE` → 409 con operaciones), `GET /positions`, `GET /portfolios/{id}/summary`, `GET /summary`, `GET /valuation-history`.
- [ ] Mapeo de errores: `DomainException` → 400/404/409 `problem+json` (handler compartido existente).
- [ ] El contexto `investments` entra en las reglas de `ArchitectureTest` (dirección de dependencias).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.9 — `FlexReportParser` (ACL) ⬜

Tests: unitarios del parser con fixtures XML reales (§9, configuración validada del Flex).

- [ ] Fixtures a partir de `docs/investment/2024.xml` / `2025.xml` (recortados/anonimizados).
- [ ] *Account Information*: extraer `currency` (divisa base de la cuenta) para la validación de §8.
- [ ] *Trades* nivel `ORDER`: compras/ventas de valores; conversiones `assetCategory="CASH"` → `FX_TRADE` (piernas por signos, comisión en su divisa, §9); `external_id = ORD-<ibOrderID>`.
- [ ] *Cash Transactions* nivel `DETAIL`: dividendos, withholding tax (`DIVIDEND` + `TAX` separados, §9), depósitos/retiradas, intereses; `external_id = CT-<transactionID>`.
- [ ] *Corporate Actions* solo `DETAIL`, tipos FS/RS → `SPLIT` delta de cantidad (§9); otros tipos → error de fila; `external_id = CA-<transactionID>`.
- [ ] *Transaction Taxes* solo `ORDER_SUMMARY` → filas `TRADE_TAX` (ignorar `TransactionTaxDetail`, §9); `external_id = FTT-<tradeId>`.
- [ ] *Open Positions*: `markPrice` → cotización con `quote_date = toDate` del statement; `listingExchange`/`figi` → metadatos.
- [ ] *Conversion Rates*: solo pares con divisas de la cartera, normalizados divisa→EUR (§9).
- [ ] Filas ilegibles / secciones no soportadas → error por fila, resto continúa (tolerante, §8).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.10 — Caso de uso `ImportFlexReport` + endpoint ⬜

Tests: aplicación mockeada + `@WebMvcTest`.

- [ ] Validación previa divisa base cuenta↔cartera; si difiere, **import rechazado entero** (§8).
- [ ] Idempotencia por `external_id`: duplicados omitidos y reportados (RN-10, RF-4).
- [ ] Alta automática de `Security` (por ISIN+divisa) y refresco de metadatos en reimport (RN-9).
- [ ] Upsert de cotizaciones y tipos de cambio (RN-9).
- [ ] Venta sin posición → la fila entra con *warning* en el resumen (RN-4, regla dual).
- [ ] Resumen de import: ok / duplicadas / errores / warnings.
- [ ] Endpoint `POST /portfolios/{id}/import` (multipart, como `/api/imports`).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H1.11 — Frontend: página de inversión ⬜

Tests: build + revisión manual.

- [ ] Ruta lazy `pages/investments` + entrada "Inversión" en el menú lateral; modelos en `models.ts`, llamadas en `api.service.ts`.
- [ ] Cabecera de KPIs (§7): valor total + fecha de valoración, aportado neto, P&L latente (€ y %), efectivo por divisa.
- [ ] Donut de asignación (posiciones + efectivo) y gráfico evolución valor vs aportado (Chart.js + `ThemeService`).
- [ ] Barras divergentes de P&L por posición.
- [ ] Tabla de posiciones (cantidades negativas en rojo, RN-4; aviso "a coste" sin cotización, RN-6).
- [ ] Diálogo de import Flex (adaptación del patrón `components/import-dialog.ts`) con resumen ok/duplicadas/errores/warnings.
- [ ] `npm run build` verde + revisión manual.
- [ ] Actualizar PRD Inversiones (§13 referencias de código, estado) — obligatorio.
- [ ] Commit del hito.

### H1.12 — Tarjeta de patrimonio en el dashboard doméstico ⬜

Tests: build + revisión manual.

- [ ] Tarjeta con patrimonio agregado de todas las carteras + fecha de valoración; desglose por cartera si hay >1 (`GET /api/investments/summary`) — RF-10.
- [ ] Degradación: oculta sin carteras/valor; "—" ante error de API sin romper el dashboard — RF-10.
- [ ] Actualizar **PRD Dashboard** (`docs/prd/dashboard.md`) — obligatorio.
- [ ] Commit del hito. **→ Cierra F1**: actualizar estado en PRD Inversiones y `docs/README.md`.

---

## F2 — Vistas de rentas y alta manual

### H2.1 — Dominio: agregación de rentas ⬜

Tests: unitarios de dominio.

- [ ] Agregados de rentas por periodo/instrumento sobre apuntes existentes (RF-7): dividendos/intereses en bruto, neto = bruto − `TAX` vinculada por instrumento+fecha; `TRADE_TAX` **excluido** (§9).
- [ ] Comisiones/retenciones pagadas agregadas por periodo.
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H2.2 — `IncomeView` + adapter + endpoint ⬜

Tests: `@DataJpaTest` + `@WebMvcTest`.

- [ ] `IncomeView` + query adapter (conversión con snapshot `fx_rate_to_base`, RN-7a).
- [ ] Endpoint `GET /portfolios/{id}/income` (§6).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H2.3 — Alta/edición manual de operaciones ⬜

Tests: aplicación + `@WebMvcTest`.

- [ ] Casos de uso crear/editar/eliminar operación (RF-2): invariantes de signos (§8), venta sin posición → `400` (RN-4 dura en manual).
- [ ] Endpoints `GET/POST /portfolios/{id}/transactions` (listado filtrable por tipo/fechas/instrumento, sin paginación) + `PUT/DELETE /transactions/{id}` (§6).
- [ ] Formulario de alta/edición en la UI.
- [ ] Refactor + suite verde + PRD si hay desviaciones.
- [ ] Commit del hito.

### H2.4 — Frontend: pestañas de operaciones y dividendos ⬜

Tests: build + revisión manual.

- [ ] Pestaña operaciones: listado filtrable + alta manual (§7).
- [ ] Pestaña dividendos: cobros por año/instrumento; gráfico mensual apilado por instrumento con selector de año; bruto con neto en tooltip (§7).
- [ ] KPI de dividendos del año en la cabecera.
- [ ] Build verde + revisión manual + PRD actualizado.
- [ ] Commit del hito. **→ Cierra F2.**

---

## F3 — Rentabilidad TWR/XIRR

### H3.1 — `PerformanceCalculator`: XIRR ⬜

Tests: unitarios de dominio (casos conocidos, convergencia, extremos).

- [ ] XIRR por Newton-Raphson con fallback de bisección sobre flujos externos + valor actual (RN-8).
- [ ] `FX_TRADE` no es flujo externo (RN-8).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H3.2 — `PerformanceCalculator`: TWR ⬜

Tests: unitarios de dominio.

- [ ] TWR encadenado por subperiodos delimitados por `DEPOSIT`/`WITHDRAWAL` sobre la serie de valoraciones disponible (RN-8).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H3.3 — `PerformanceView` + adapter + endpoint ⬜

Tests: `@DataJpaTest` + `@WebMvcTest`.

- [ ] `PerformanceView` + query adapter (por posición y total).
- [ ] Endpoint `GET /portfolios/{id}/performance` (§6).
- [ ] Refactor + suite verde.
- [ ] Commit del hito.

### H3.4 — Frontend: rentabilidad ⬜

Tests: build + revisión manual.

- [ ] TWR/XIRR en KPIs de cabecera y por posición en la tabla (§7).
- [ ] Gráfico de rentabilidad por posición (barras horizontales, %).
- [ ] Build verde + revisión manual + PRD actualizado.
- [ ] Commit del hito. **→ Cierra F3**: actualizar estado en PRD y `docs/README.md`.

---

## F4 — Automatización (backlog, sin planificar)

No se desglosa en tareas hasta que se decida abordarla (ver PRD §10/§12):
H4.1 adaptador `PriceProviderPort` · H4.2 Flex Web Service · H4.3 modo híbrido.
Al abordarla: revisar identidad `security` (ISIN+exchange, §9) y la configuración
del nivel FTT del Flex (§11).
