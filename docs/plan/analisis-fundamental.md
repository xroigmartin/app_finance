---
dominio: analisis-fundamental
estado: en-diseño
tags: [plan, dominio/analisis-fundamental]
---

# Diseño técnico — Análisis fundamental

> Documento de **implementación**: modelo físico, arquitectura, API y plan por fases. Las reglas de negocio, los requisitos y el glosario viven en [[prd/analisis-fundamental]], que es la fuente de verdad funcional. Aquí solo se decide **cómo** se construye lo que allí se especifica.

**Estado**: sin implementar. **Última actualización**: 2026-08-08.

---

## 1. Contexto y arquitectura

Bounded context autocontenido `fundamentals`, con la misma estructura hexagonal + DDD que el resto (`domain/` ← `application/` ← `infrastructure/`), fencado por ArchUnit.

**Esquema PostgreSQL separado `fundamentals`**, misma decisión que `investments`: el aislamiento del contexto es también físico y no existen claves foráneas hacia otros esquemas. Migración `V9__fundamentals.sql` (última existente: `V8__import_record.sql`).

Dependencias hacia otros contextos, ambas de **solo lectura y a través de puertos propios**:

| Necesidad | Solución |
|---|---|
| Precio actual de la acción (RF-17) | Puerto propio `SharePriceProviderPort`, implementado por un adaptador que delega en el `PriceProviderPort` de `investments` (Yahoo Finance) y en su `YahooExchangeResolver`. No se importa el puerto ajeno directamente: el contrato lo define este contexto. |
| Correspondencia compañía ↔ posición (RF-20, RN-20) | Se resuelve en la **capa de lectura del lado de `investments`**: es su pantalla la que se enriquece. Un puerto de consulta expone "dame el veredicto de la compañía con este ISIN/ticker" y `fundamentals` lo implementa. Así la dependencia va de `investments` → `fundamentals` y no al revés, y la tabla de posiciones sigue funcionando si el módulo no tiene datos. |

**Precisión decimal**: importes y magnitudes contables `numeric(19,4)`; porcentajes, ratios y múltiplos `numeric(19,8)`; número de acciones `numeric(19,4)`. `BigDecimal` en todo el dominio, con escalas y redondeo fijados en los value objects. El frontend no calcula: recibe agregados ya computados y los formatea.

## 2. Modelo físico

### `company`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `name` | `varchar NOT NULL` | |
| `ticker` | `varchar NOT NULL` | `UNIQUE` |
| `isin` | `varchar` nullable | `UNIQUE` (Postgres admite varios NULL) |
| `exchange` | `varchar` nullable | Llave para resolver el símbolo del proveedor de precios |
| `reporting_currency` | `varchar(3) NOT NULL` | ISO 4217 |
| `scale` | `varchar NOT NULL` | `UNITS` / `THOUSANDS` / `MILLIONS` |
| `business_type` | `varchar NOT NULL` | `NON_FINANCIAL` / `FINANCIAL` / `REIT` (RN-19) |
| `sector` | `varchar` nullable | Metadato libre |
| `threshold_roic_min` | `numeric(19,8)` nullable | Override; nulo = valor por defecto del módulo |
| `threshold_net_debt_ebitda_max` | `numeric(19,8)` nullable | Ídem |
| `manual_share_price` | `numeric(19,4)` nullable | Override manual del precio (RF-17) |
| `share_price` / `share_price_date` | `numeric(19,4)` / `date` nullable | Último precio obtenido del proveedor |
| `created_at` | `timestamp NOT NULL` | |

### `fiscal_year`

Una fila por (`company_id`, `year`), **única**. Modelo **ancho**: una columna por partida en lugar de clave-valor. Las columnas de abajo son la **proyección física de la taxonomía de §3.1**, no su definición: el dominio habla de partidas, el adaptador las traduce a columnas mediante una tabla de correspondencia con test de guarda. Se elige ancho frente a `jsonb` porque el conjunto de partidas de una compañía no financiera es cerrado y estable, y a cambio hay tipado exacto (`numeric`, no coma flotante), restricciones y consultas legibles; el día que el esquema deba ser heterogéneo por sector, cambia el adaptador y no el dominio.

Todas las columnas de importe son `numeric(19,4)` y **nullable** salvo `sales`. Un nulo se agrega como 0, pero se distingue de un 0 explícito para los avisos de calidad del dato.

**Cuenta de resultados**: `sales` (NOT NULL), `ebit`, `depreciation_amortization`, `interest_expense`, `interest_income`, `tax_expense`, `net_income`, `minority_interests`, `diluted_shares`.

**Balance**: `cash_and_equivalents`, `marketable_securities`, `short_term_debt`, `long_term_debt`, `operating_leases_current`, `operating_leases_non_current`, `equity`, `inventories`, `accounts_receivable`, `accounts_payable`, `unearned_revenue`.

**Flujo de caja**: `capex`, `intangibles_capex`, `ppe_disposals`, `depreciation_cf`, `acquisitions`, `divestitures`, `buybacks`, `dividends_paid`, `debt_issued`, `debt_repaid`, `stock_based_compensation`, `share_issuance`.

**Extraordinarios** (solo red flags): `asset_writedowns`, `goodwill_impairment`, `merger_restructuring_charges`, `legal_settlements`, `other_unusual_items`.

**Mercado**: `market_cap` (capitalización al cierre del ejercicio; sin ella ese año no entra en múltiplos ni medianas, RN-11).

**Trazabilidad**: `imported_at` `timestamp NOT NULL`, `source_file` `varchar` nullable. Al ser los datos de solo lectura (RN-2), el origen de cada ejercicio es siempre una importación concreta.

Nota sobre `depreciation_amortization` y `depreciation_cf`: la primera viene de la cuenta de resultados y reconstruye el EBITDA; la segunda es la depreciación del estado de flujos y es la referencia del CapEx de mantenimiento (RN-7). Si `depreciation_cf` falta, se usa `|depreciation_amortization|`.

### `scenario`

Tres filas por compañía, creadas juntas al dar de alta la ficha.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `company_id` | FK → `company` | `UNIQUE (company_id, kind)` |
| `kind` | `varchar NOT NULL` | `BEAR` / `BASE` / `BULL` |
| `target_per`, `target_ev_fcf`, `target_ev_ebitda`, `target_ev_ebit` | `numeric(19,8)` nullable | Nulo = ese método no promedia (RN-16) |
| `required_annual_return` | `numeric(19,8) NOT NULL` | |
| `updated_at` | `timestamp NOT NULL` | Alimenta el aviso de valoración caducada (RF-20) |

### `scenario_year`

Cinco filas por escenario (`offset` 1..5), `UNIQUE (scenario_id, offset)`. Todas `numeric(19,8)`:

`sales_growth`, `ebit_margin`, `tax_rate`, `share_change`, `maintenance_capex_pct`, `working_capital_pct`, `expansion_capex_pct`, `acquisitions_pct`, `buybacks_pct`, `dividends_pct`, `debt_repayment_pct`.

### `valuation_snapshot`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `company_id` | FK → `company` | |
| `taken_at` | `timestamp NOT NULL` | |
| `label` | `varchar` nullable | |
| `share_price` | `numeric(19,4) NOT NULL` | |
| `target_price_base` | `numeric(19,4)` | Denormalizado para listar sin abrir el JSON |
| `margin_of_safety_base` | `numeric(19,8)` | Ídem |
| `payload` | `jsonb NOT NULL` | Volcado inmutable: ejercicios usados, los tres escenarios y todos los resultados (RN-18). Se lee siempre como bloque, nunca se consulta por dentro — mismo criterio que `investments.import_record.errors` |

### `financials_import`

Historial de importaciones, un registro por intento (también los que no cambiaron nada). Precursor de la carga automática (§14 del PRD) y soporte del informe de RF-4.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `company_id` | FK → `company` | |
| `imported_at` | `timestamp NOT NULL` | |
| `file_name` | `varchar` nullable | |
| `created_count` / `updated_count` | `int NOT NULL` | |
| `changes` | `jsonb NOT NULL DEFAULT '[]'` | Diferencias por ejercicio y partida (`year`, `field`, `oldValue`, `newValue`) |
| `issues` | `jsonb NOT NULL DEFAULT '[]'` | Filas rechazadas y partidas no reconocidas (`section`, `reference`, `message`) |

## 3. Componentes de dominio

### 3.1 Taxonomía de partidas

**El dominio no conoce 35 campos: conoce una taxonomía.** Las columnas de `fiscal_year` son la proyección física de esa taxonomía, no su definición. Es la única decisión de diseño tomada pensando en que este proyecto es la antesala de un producto mayor: cuando haya que cubrir bancos, aseguradoras y REITs, cada tipo de negocio traerá su propio juego de partidas, y con esta separación eso no toca ni el agregado ni el resto del contexto.

**`LineItem`** — cada partida canónica, con sus metadatos:

| Atributo | Para qué sirve |
|---|---|
| `code` | Identificador estable (`SALES`, `EBIT`, `CAPEX`…). Nunca se renombra ni se reutiliza (ver versionado). |
| `statement` | `INCOME_STATEMENT` / `BALANCE_SHEET` / `CASH_FLOW` / `MARKET`. Agrupa la plantilla y la pantalla de datos. |
| `signRule` | `POSITIVE` / `NEGATIVE` / `ANY`. Es donde vive, declarativamente, la validación de RN-4 (convenio de signos). |
| `required` | Solo `SALES` lo es hoy (§12 del PRD). |
| `label` | Etiqueta en español para la plantilla y la interfaz. |
| `synonyms` | Etiquetas de fuentes externas que mapean a esta partida. Alimenta el ACL de importación. |

Muestra de la taxonomía:

| Código | Estado | Signo | Etiqueta |
|---|---|---|---|
| `SALES` | Resultados | `POSITIVE`, obligatoria | Ventas |
| `EBIT` | Resultados | `ANY` | Resultado operativo |
| `DEPRECIATION_AMORTIZATION` | Resultados | `NEGATIVE` | Amortización |
| `DILUTED_SHARES` | Resultados | `POSITIVE` | Acciones diluidas |
| `EQUITY` | Balance | `ANY` | Fondos propios |
| `LONG_TERM_DEBT` | Balance | `POSITIVE` | Deuda a largo plazo |
| `CAPEX` | Flujos | `NEGATIVE` | Inversión en inmovilizado |
| `BUYBACKS` | Flujos | `NEGATIVE` | Recompra de acciones |
| `MARKET_CAP` | Mercado | `POSITIVE` | Capitalización bursátil |

**`StatementTaxonomy`** — el conjunto de partidas aplicable a un `BusinessType`. En v1 solo existe la de `NON_FINANCIAL`; añadir `FINANCIAL` o `REIT` (RN-19) es declarar otra taxonomía, no rehacer el modelo. Los cálculos sí seguirán siendo específicos por tipo de negocio — el ROIC de un banco no existe —, así que a cada taxonomía le corresponderá su propio juego de servicios de dominio.

**Una sola fuente para cuatro consumidores.** Hoy la validación de signos, la plantilla en blanco, el mapeo del importador y las etiquetas de la interfaz serían cuatro listas paralelas que se desincronizan a la primera. Con la taxonomía las cuatro se derivan del mismo sitio y no pueden divergir.

**Versionado.** Los códigos son estables: **nunca se renombran ni se reutilizan**, solo se marcan como obsoletos. El motivo es concreto: las instantáneas congelan su contenido como documento con los códigos dentro (RN-18), así que renombrar `SALES` rompería la lectura de una instantánea de hace dos años. La taxonomía lleva número de versión y cada instantánea registra bajo cuál se tomó.

**`FinancialStatement`** deja de ser 35 campos con nombre y pasa a contener un `Map<LineItem, Amount>`, validado contra la taxonomía del tipo de negocio de la compañía. Para que los cálculos no se llenen de accesos por clave, el agregado expone accesores con nombre (`sales()`, `ebit()`, `capex()`…) que delegan en el mapa: el **contenedor** es genérico, el **código de cálculo** sigue siendo legible y tipado.

**Proyección a columnas.** El mapper de persistencia traduce `Map<LineItem, Amount>` ↔ columnas mediante una única tabla de correspondencia código → columna, acompañada de un **test de guarda** que verifica que toda partida de la taxonomía tiene exactamente una columna y toda columna tiene exactamente una partida. Sin ese test, un modelo ancho de 35 columnas es un sitio fácil donde perder un dato en silencio.

**Lo que compra de cara al producto definitivo**: si allí hace falta esquema heterogéneo por sector, el adaptador pasa de columnas a documento indexado por código y **el dominio no se entera**. La decisión de almacenamiento deja de ser estructural y se convierte en lo que debe ser: un detalle del adaptador.

### 3.2 Resto del dominio

**Value objects**: `Ticker`, `Isin`, `Scale`, `BusinessType`, `FiscalYearNumber`, `LineItem` + `StatementTaxonomy` (§3.1), `Amount` (convenio de signos de RN-4, validado contra el `signRule` de su partida), `Percentage`, `Multiple`, `ScenarioKind`.

**Agregados**: `Company` (identidad, umbrales override, precio), `FinancialStatement` (un ejercicio como conjunto de partidas de su taxonomía, §3.1), `Scenario` (+ `ScenarioYear`), `ValuationSnapshot`.

**Servicios de dominio** (puros, sin Spring, la mayor parte del valor del módulo y donde se concentra el TDD):

| Servicio | Responsabilidad | Regla |
|---|---|---|
| `HistoricalAnalyzer` | Márgenes, crecimientos interanuales, CAGR y medianas, declarando siempre la base de años | RN-12 |
| `CashFlowCalculator` | CapEx de mantenimiento/expansión, circulante y su variación, FCF y ratios de calidad | RN-7, RN-8 |
| `CapitalAllocationCalculator` | Reparto del FCF por destino; caso FCF ≤ 0 | RN-9 |
| `ReturnsCalculator` | NOPAT, capital invertido, ROIC, ROE, tasa de reinversión | RN-10 |
| `MultiplesCalculator` | Deuda neta, EV, cuatro múltiplos y medianas; excluye ejercicios sin capitalización | RN-11 |
| `RedFlagDetector` | Umbrales por defecto + override, recuentos y partidas sobre ventas | RN-13 |
| `ProjectionCalculator` | Cascada de 5 ejercicios y realimentación caja → deuda neta | RN-15 |
| `ValuationCalculator` | Precio objetivo por método/año, promedio, margen de seguridad, potencial, retorno anualizado y precio máximo de compra | RN-16, RN-17 |

**Puertos de salida**: `CompanyRepository`, `FinancialStatementRepository`, `ScenarioRepository`, `ValuationSnapshotRepository`, `FinancialsImportRepository`, `SharePriceProviderPort`.

**Detalle del `ProjectionCalculator`** (no está en el PRD porque es mecánica de cálculo, no regla de negocio): el interés neto del año *e* se estima aplicando los tipos medios implícitos históricos (gasto financiero sobre deuda media, ingreso financiero sobre caja e inversiones financieras medias) a los **saldos de cierre del año e−1**. Eso rompe la circularidad: el FCF del año *e* necesita el interés del año *e*, que solo depende de saldos ya conocidos. La deuda neta del año *e* se obtiene de la del año anterior más la variación de caja resultante del reparto (RN-15).

## 4. Anticorrupción de la importación

`FinancialsFileParser` implementa el puerto `FinancialsFileReader` y traduce **un único fichero** (RF-3) al modelo canónico. Mismo patrón que `ImportFileParser` (extractos bancarios) y `FlexReportParser` (IBKR): tolerante fila a fila, errores reportados sin abortar el resto.

Debe absorber, como aquellos:

- Libro con varias hojas (una por estado financiero) o una sola hoja con secciones.
- CSV y Excel (`.xls`/`.xlsx`), reutilizando Apache POI y commons-csv ya presentes.
- Cabeceras insensibles a mayúsculas y acentos, con preámbulos antes de la fila de cabecera.
- Separadores `,` y `;`; importes `1.234,56` y `1234.56`. **Comprobación explícita de separador decimal**: es el fallo más frecuente al pegar datos de una fuente extranjera (§12 del PRD).
- Años en columnas, partidas en filas, con las etiquetas de la fuente externa resueltas contra los `synonyms` de la taxonomía (§3.1).

La **plantilla en blanco** (RF-2) se genera recorriendo la misma taxonomía, agrupando por `statement` y usando `label` como encabezado de fila. Plantilla, parser, validación de signos y etiquetas de la interfaz salen todos de la misma fuente y no pueden divergir.

**Cálculo del diff** (RF-4): antes de escribir, el caso de uso compara el ejercicio entrante con el persistido partida a partida y produce la lista de cambios que se guarda en `financials_import.changes` y se devuelve en la respuesta.

## 5. API

Base `/api/fundamentals`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET/POST` | `/companies` · `PUT/DELETE /companies/{id}` | CRUD. `DELETE` con guarda RN-21 → 409 |
| `GET` | `/companies/{id}` | Ficha: datos, ejercicios disponibles, precio y su fecha |
| `GET` | `/watchlist` | Listado comparativo con el veredicto base de cada compañía (RF-19) |
| `GET` | `/financials/template` | Plantilla en blanco (RF-2) |
| `GET` | `/companies/{id}/financials` | Ejercicios cargados (view CQRS) |
| `DELETE` | `/companies/{id}/financials/{year}` | Borra un ejercicio (RF-6) |
| `POST` | `/companies/{id}/financials/import` | Import multipart de un fichero (RF-3); devuelve `{created, updated, changes, issues}` |
| `GET` | `/companies/{id}/financials/imports` | Historial de importaciones, paginado |
| `GET/PUT` | `/companies/{id}/scenarios` · `/scenarios/{kind}` | Hipótesis (RF-13) |
| `POST` | `/companies/{id}/scenarios/{kind}/copy-from/{source}` | Copia de hipótesis entre escenarios (RF-13) |
| `GET` | `/companies/{id}/analysis?scenario=` | **Read-model completo** que alimenta la ficha entera: diagnóstico, FCF, asignación, retornos, múltiplos, red flags, proyección y valoración |
| `GET/POST` | `/companies/{id}/snapshots` · `GET/DELETE /snapshots/{id}` | Instantáneas (RF-18) |
| `POST` | `/companies/{id}/price/refresh` · `PUT /companies/{id}/price` | Precio automático y override (RF-17) |

No hay endpoint de edición de cifras: los datos son de solo lectura (RN-2).

Errores vía `shared.web.DomainExceptionHandler` (400/404/409 como `problem+json`), como el resto de contextos.

## 6. Frontend

Dos páginas lazy bajo el grupo **Inversión** del menú lateral, más el enriquecimiento de una existente:

- `pages/analysis-watchlist` (ruta `/analysis`).
- `pages/analysis-company` (ruta `/analysis/{id}`): cabecera de veredicto + secciones como control segmentado, mismo patrón visual que `investments-operations`.
- `pages/investments-positions`: columnas nuevas de margen de seguridad, precio objetivo/potencial y aviso de caducidad (RF-20).

Gráficos con Chart.js directamente, integrados con `ThemeService` y con el mismo diferido de render (`scheduleRenderCharts`) que las páginas de inversión, por el mismo motivo: Chart.js puede medir un contenedor sin layout calculado y quedarse en 300×150 sin pintar.

Las tablas de años en columnas van dentro de tarjeta con `overflow-x: auto`. La edición en línea de hipótesis reutiliza el patrón de la matriz anual de presupuestos.

Modelos en `models.ts` y llamadas en `api.service.ts`, como el resto.

## 7. Plan de implementación

Desarrollo con **TDD obligatorio** (ver `CLAUDE.md`): ciclos red-green-refactor, un commit por hito con tests en verde y PRD actualizado. Un hito no empieza hasta que el anterior está commiteado.

### F1 — Compañías y datos financieros

| Hito | Contenido | Tests |
|---|---|---|
| H1.1 | Value objects del contexto, incluido `Amount` con validación de signo por naturaleza de partida (RN-4) | Unitarios de dominio |
| H1.2 | Agregado `Company` (identidad, umbrales override, tipo de negocio) | Unitarios de dominio |
| H1.3 | **Taxonomía de partidas** (§3.1): `LineItem` con sus metadatos y `StatementTaxonomy` de `NON_FINANCIAL` | Unitarios de dominio (completitud de la taxonomía, unicidad de códigos) |
| H1.3b | Agregado `FinancialStatement` sobre la taxonomía: partidas, validación de signo contra `signRule`, ventas obligatorias, accesores con nombre | Unitarios de dominio |
| H1.4 | Puertos de salida + `CompanyService` (CRUD, guarda de borrado RN-21) | Aplicación con puertos mockeados |
| H1.5 | Migración `V9__fundamentals.sql` + entidades/mappers/adaptadores JPA, con la tabla de correspondencia partida ↔ columna | `@DataJpaTest` (Testcontainers) + **test de guarda** de la correspondencia en ambos sentidos |
| H1.6 | Web: `CompanyController` + DTOs; el contexto entra en ArchUnit | `@WebMvcTest` + `ArchitectureTest` |
| H1.7 | Generación de la plantilla en blanco desde la taxonomía y `FinancialsFileParser` (ACL) resolviendo etiquetas por `synonyms` | Unitarios del parser con fixtures reales |
| H1.8 | Caso de uso `ImportFinancials` con cálculo de diff y persistencia del historial; endpoint multipart | Aplicación mockeada + `@WebMvcTest` |

### F2 — Motor de diagnóstico

| Hito | Contenido | Tests |
|---|---|---|
| H2.1 | `HistoricalAnalyzer` (RN-12: base de años declarada en cada agregado) | Unitarios de dominio |
| H2.2 | `CashFlowCalculator` (RN-7, RN-8), con los límites de la heurística de CapEx | Unitarios de dominio |
| H2.3 | `CapitalAllocationCalculator` (RN-9), incluido FCF ≤ 0 | Unitarios de dominio |
| H2.4 | `ReturnsCalculator` (RN-10) | Unitarios de dominio |
| H2.5 | `MultiplesCalculator` (RN-11), con ejercicios sin capitalización | Unitarios de dominio |
| H2.6 | `RedFlagDetector` (RN-13), umbrales por defecto y override | Unitarios de dominio |

### F3 — Escenarios, proyección y valoración

| Hito | Contenido | Tests |
|---|---|---|
| H3.1 | Agregados `Scenario`/`ScenarioYear` + persistencia; alta de los tres escenarios con la compañía | Unitarios + `@DataJpaTest` |
| H3.2 | `ProjectionCalculator` (RN-15 + estimación de intereses de §3) | Unitarios de dominio |
| H3.3 | `ValuationCalculator` (RN-16, RN-17): casos con caja neta, con deuda neta y con múltiplos ausentes | Unitarios de dominio |
| H3.4 | Read-side CQRS `AnalysisQueryPort` + `CompanyAnalysisView` + adapter + `GET /companies/{id}/analysis` | `@DataJpaTest` + `@WebMvcTest` |
| H3.5 | Edición de escenarios y copia entre escenarios | Aplicación + `@WebMvcTest` |
| H3.6 | `SharePriceProviderPort` + adaptador sobre el proveedor de `investments`; refresco y override | Aplicación mockeada + `@WebMvcTest` |

### F4 — Interfaz

| Hito | Contenido | Tests |
|---|---|---|
| H4.1 | Modelos, `api.service.ts`, entrada de menú y rutas lazy | Vitest |
| H4.2 | Watchlist ordenable (RF-19) | Vitest + Playwright |
| H4.3 | Cabecera de veredicto con selector de escenario y aviso de RN-19 | Vitest |
| H4.4 | Secciones Resultados y Flujo de caja, con edición en línea de hipótesis | Vitest + Playwright |
| H4.5 | Secciones Retornos, Valoración y Red flags | Vitest |
| H4.6 | Sección Datos: consulta en solo lectura, carga de fichero con informe de cambios, descarga de plantilla | Vitest + Playwright |

### F5 — Instantáneas

| Hito | Contenido | Tests |
|---|---|---|
| H5.1 | Agregado `ValuationSnapshot` y serialización del payload congelado (RN-18) | Unitarios de dominio |
| H5.2 | Persistencia `jsonb` + adaptador + lectura CQRS paginada | `@DataJpaTest` (round-trip JSON) |
| H5.3 | Endpoints de instantáneas y guarda de borrado de compañía | `@WebMvcTest` |
| H5.4 | Sección Instantáneas con comparación frente a la situación actual | Vitest + Playwright |

### F6 — Enlace con la cartera

| Hito | Contenido | Tests |
|---|---|---|
| H6.1 | Puerto de consulta implementado por `fundamentals` y consumido desde el read-side de `investments` (RN-20); dependencia en el sentido correcto | Unitarios + `@DataJpaTest` + `ArchitectureTest` |
| H6.2 | Columnas nuevas en la tabla de posiciones y acceso para crear ficha (RF-20); actualización del PRD de Inversiones | Vitest + Playwright |

### F7 — Gráficos

| Hito | Contenido | Tests |
|---|---|---|
| H7.1 | Sección Gráficos con Chart.js y `ThemeService`; render diferido | Vitest + Playwright (regresión de contenido de canvas) |

## 8. Deuda técnica prevista

| Tema | Situación | Acción |
|---|---|---|
| Formato de la fuente externa | El ACL se escribirá contra el volcado de la fuente que el usuario use hoy; ese formato puede cambiar sin aviso | Mitigado por la plantilla canónica, que es la frontera estable: un cambio de la fuente solo afecta al parser |
| Proveedor de precios no oficial | Se hereda el endpoint de Yahoo Finance de `investments`, sin SLA | Ninguna acción propia: el aislamiento por puerto permite sustituirlo, y el override manual de precio es la mitigación desde el primer día |
| Modelo ancho de `fiscal_year` | ~35 columnas; cada partida nueva es una migración | Asumido conscientemente: el conjunto de partidas de una compañía no financiera es cerrado, y añadir una implica cambiar fórmulas de todos modos. El coste de revertir la decisión está acotado por la taxonomía (§3.1): pasar a documento es cambiar el mapper |
| Esquema heterogéneo por sector | Bancos, aseguradoras y REITs no comparten partidas con las compañías industriales (RN-19). Es el problema real del producto definitivo, y es de **esquema**, no de volumen — lo relacional aguanta de sobra el tamaño del universo cotizado | Preparado, no resuelto: la taxonomía por `BusinessType` (§3.1) es el punto de extensión. Cada tipo de negocio traerá su taxonomía y su juego de servicios de cálculo |
| Divisa de cotización distinta de la de reporte | Caso de peniques/libras ya resuelto en `investments` | Se reutiliza la normalización existente; cualquier otra divisa no equivalente se descarta y se pide precio manual (RN-5) |

---

**Especificación funcional**: [[prd/analisis-fundamental]].
