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
> en el plan y commit. **Trabaja un solo hito y detente**: no empieces el siguiente
> sin el OK explícito del usuario.

## Convenciones de trabajo

- **Un hito por turno, con OK explícito** (decisión del usuario, 2026-07-12): al cerrar
  un hito (tests verdes + PRD + plan + commit) se presenta un resumen y se **espera la
  aprobación del usuario** antes de empezar el siguiente. No encadenar hitos por
  iniciativa propia, aunque el siguiente esté claro.
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
| F1 | Modelo + import Flex + posiciones/valoración multidivisa | H1.1 – H1.12 | 🔨 En curso (H1.1–H1.9 ✅) |
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

### H1.2 — Agregados `Security` + `PriceQuote` + `ExchangeRate` + conversión ✅

Tests: unitarios de dominio.

- [x] `Security`: identidad ISIN+divisa, `isin`/`name`/`currency` obligatorios, metadatos `ticker`/`exchange`/`figi` (§9); refresco de metadatos no identitarios (RN-9) — un valor entrante nulo/vacío conserva el actual (el refresco nunca borra).
- [x] `PriceQuote`: invariantes (instrumento/fecha/precio positivo), precio escala 8. La unicidad (security, fecha) y el upsert (RN-9) son contrato del repositorio → se verifican en H1.6.
- [x] `ExchangeRate`: invariantes y normalización divisa→EUR (rechaza EUR→EUR y direcciones ≠→EUR). Unicidad (fecha, par) y upsert (RN-9) → H1.6.
- [x] Servicio de conversión `CurrencyConverter`: último tipo ≤ fecha; **pares cruzados vía pivote EUR** (`from→to = (from→EUR) ÷ (to→EUR)`), RN-7; sin tipo disponible devuelve vacío (la degradación la decide la capa de lectura).
- [x] Refactor (helper `IsoCurrency` compartido) + suite verde (520 tests).
- [x] Commit del hito.

### H1.3 — Agregados `Portfolio` e `InvestmentTransaction` ✅

Tests: unitarios de dominio.

- [x] `Portfolio`: nombre obligatorio, `base_currency` ISO 4217 — **inmutable tras la creación** (cambiarla corrompería los snapshots RN-7a; editar = renombrar).
- [x] `InvestmentTransaction`: los 11 tipos (§3) como enum con las reglas por tipo codificadas como datos; campos condicionales (`security` REQUIRED/OPTIONAL/FORBIDDEN según tipo, `counter_*` solo en `FX_TRADE` y en divisa distinta, `fee`/`tax` como `CurrencyMoney` con su propia divisa). Construcción vía builder fluido.
- [x] Invariantes del **convenio de signos por tipo** (tabla §3): `BUY` qty>0/amount<0, `SELL` qty<0/amount>0, `SPLIT` amount=0 y delta≠0, `FX_TRADE` amount<0/counter>0, fee/tax negativas, cantidad solo en BUY/SELL/SPLIT → `ValidationException` (§8).
- [x] `external_id` opcional (nulo en apuntes manuales, blanco→nulo) — RN-10; `fx_rate_to_base` opcional positivo (RN-7a).
- [x] Refactor + suite verde (537 tests).
- [x] Commit del hito.

### H1.4 — Servicio de dominio `PositionCalculator` ✅

Tests: unitarios de dominio.

- [x] Cantidad por instrumento = Σ `quantity` directa (compras, ventas, deltas de `SPLIT`) — RN-3. Resultado: `PortfolioPositions` (posiciones + efectivo + warnings `PositionWarning`).
- [x] Coste medio promedio con **coste de compra capitalizado** (`|amount| + |fee| + |trade_tax|`) — RN-3; el coste se mantiene en **divisa base** (cada componente convertido con su snapshot, así el P&L latente incorporará el efecto divisa). Vínculo FTT↔orden por (instrumento, fecha): en fecha de compra capitaliza; en fecha de venta reduce el neto (si el día tiene compra y venta, la compra consume el bucket).
- [x] P&L realizado en ventas (neto percibido − coste capitalizado promedio) y ventas parciales (§9); con posición insuficiente, el coste de lo vendido se calcula sobre la parte cubierta.
- [x] `SPLIT` como delta a coste 0: reduce coste medio sin reprocesar histórico — RN-3.
- [x] Efectivo por divisa: suma directa firmada de `amount` + `counter_amount` + `fee`/`tax` según divisa efectiva — RN-2.
- [x] Venta sin posición suficiente: detección con tolerancia 1e-8 (RN-4), fila procesada igual (posición negativa); dentro de un mismo día las adquisiciones se procesan antes que las ventas. Sin tipo de cambio disponible: 1:1 + warning (no rompe el cálculo).
- [x] Refactor + suite verde (550 tests).
- [x] Commit del hito.

### H1.5 — Puertos de salida + servicios de aplicación CRUD ✅

Tests: aplicación con puertos mockeados.

- [x] Puertos: `PortfolioRepository`, `SecurityRepository`, `InvestmentTransactionRepository` (con `existsByPortfolio`/`existsBySecurity` para RN-5 y `existsByPortfolioAndExternalId` para RN-10), `PriceQuoteRepository` y `ExchangeRateRepository` (contrato upsert RN-9), y `PriceProviderPort` definido sin adaptador (§2).
- [x] `PortfolioService`: crear/listar/editar/eliminar; editar = renombrar (base_currency inmutable); guarda RN-5 (cartera con operaciones → `ConflictException`).
- [x] `SecurityService`: CRUD (editar = metadatos no identitarios + `changeType`); duplicado ISIN+divisa proactivo → `ConflictException`; guarda RN-5 (instrumento con operaciones → `ConflictException`).
- [x] Refactor + suite verde (564 tests). Nota: los servicios quedan **sin `@Service`** hasta H1.6 (sin adaptadores, el contexto Spring completo no arranca — `DataSeederTest`); tarea añadida a H1.6.
- [x] Commit del hito.

### H1.6 — Migración `V7__investments.sql` + persistencia JPA ✅

Tests: `@DataJpaTest` (Testcontainers).

- [x] Migración **V7** (el diseño preveía V6, pero `V6__transaction_refunds.sql` ya existía): `CREATE SCHEMA investments` + tablas `security`, `price_quote`, `portfolio`, `investment_transaction`, `exchange_rate` con tipos/constraints de §3 (`UNIQUE (isin, currency)`, `UNIQUE (portfolio_id, external_id)`, unicidades de cotización y tipo).
- [x] Reactivar `@Service`/`@Transactional` en `PortfolioService`/`SecurityService` (diferidos en H1.5: sin adaptadores el contexto Spring no arrancaba — `DataSeederTest`).
- [x] Entidades JPA con `@Table(schema = "investments")` + mappers dominio↔entidad (FKs como columnas id planas — los agregados se referencian por id; divisa propia de fee/tax: columna nula = divisa del apunte).
- [x] Adaptadores de persistencia implementando los puertos de H1.5 (upsert de cotizaciones/tipos por clave natural, RN-9).
- [x] Tests: round-trip de mappers, violación de unicidades, `ddl-auto=validate` pasa contra el esquema.
- [x] Refactor + suite verde (594 tests).
- [x] Commit del hito.

### H1.7 — Read-side CQRS (query port + views + adapter) ✅

Tests: `@DataJpaTest` del adapter.

- [x] `InvestmentQueryPort` + records `PositionView`, `PortfolioSummaryView`, `ValuationHistoryView` (+ `InvestmentsSummaryView` para el resumen global RF-10).
- [x] Posiciones valoradas: última cotización ≤ fecha (RN-6), posición sin cotización → a coste con aviso (`pricedAtCost`); cerradas excluidas, negativas listadas (RN-4).
- [x] Conversión a divisa base con **doble mecanismo** RN-7: snapshots `fx_rate_to_base` para importes fijados (a), tabla `exchange_rate` para valoración a fecha (b); degradación 1:1 sin tipo.
- [x] Summary por cartera: valor total, aportado neto, P&L latente (% sobre coste capitalizado), efectivo por divisa, dividendos del año (bruto), fecha de valoración = la más antigua usada (§6).
- [x] Valuation-history: serie `{fecha, valor, aportado acumulado}` con puntos en fechas de flujo y de cotización (§6/§7).
- [x] Resumen global multi-cartera **en EUR** (conversión vía pivote; fecha = la más antigua) — RF-10.
- [x] Refactor + suite verde (606 tests).
- [x] Commit del hito.

### H1.8 — Capa web + ArchUnit ✅

Tests: `@WebMvcTest` + `ArchitectureTest`.

- [x] `PortfolioController` + `SecurityController` + DTOs bajo `/api/investments` (§6): CRUD carteras, CRUD securities (`DELETE` → 409 con operaciones), `GET /positions`, `GET /portfolios/{id}/summary`, `GET /summary`, `GET /valuation-history`.
- [x] Mapeo de errores: `DomainException` → 400/404/409 `problem+json` (handler compartido existente).
- [x] El contexto `investments` entra en las reglas de `ArchitectureTest` (las reglas por paquete `..domain../..application../..infrastructure..` ya lo cubren; verificado que el controller solo llega a la aplicación por puertos).
- [x] Refactor + suite verde (626 tests).
- [x] Commit del hito.

### H1.9 — `FlexReportParser` (ACL) ✅

Tests: unitarios del parser con fixtures XML reales (§9, configuración validada del Flex).

- [x] Fixtures a partir de `docs/investment/2024.xml` / `2025.xml` (recortado/anonimizado: `flex-sample.xml`) + smoke test condicional contra los informes reales (0 errores en ambos años completos).
- [x] *Account Information*: extraer `currency` (divisa base de la cuenta) para la validación de §8 (falta la sección → `ValidationException`).
- [x] *Trades* nivel `ORDER`: compras/ventas de valores; conversiones `assetCategory="CASH"` → `FX_TRADE` (piernas por signos, comisión en su divisa, snapshot solo si acompaña a la divisa de la pierna saliente, §9); `external_id = ORD-<ibOrderID>`.
- [x] *Cash Transactions* nivel `DETAIL`: dividendos, withholding tax (`DIVIDEND` + `TAX` separados, §9), depósitos/retiradas (por signo), intereses (Broker Interest Received → `INTEREST`, Paid/Other Fees → `FEE`); `external_id = CT-<transactionID>`.
- [x] *Corporate Actions* solo `DETAIL`, tipos FS/RS → `SPLIT` delta de cantidad a la fecha de la acción (§9); otros tipos → error de fila; `external_id = CA-<transactionID>`.
- [x] *Transaction Taxes* solo `ORDER_SUMMARY` → filas `TRADE_TAX` (ignorar `TransactionTaxDetail`, §9); `external_id = FTT-<tradeId>`.
- [x] *Open Positions*: `markPrice` → cotización con `quote_date = toDate` del statement; `listingExchange`/`figi` → metadatos.
- [x] *Conversion Rates*: solo pares con divisas del informe, normalizados divisa→EUR (§9; de ~12.000 filas a ~310/divisa·año).
- [x] Filas ilegibles / secciones no soportadas → error por fila (`FlexRowError`), resto continúa (tolerante, §8).
- [x] Refactor + suite verde (642 tests).
- [x] Commit del hito.

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
