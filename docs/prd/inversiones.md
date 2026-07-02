# PRD — Inversiones

| Campo | Valor |
|---|---|
| Estado | Diseño aprobado (pendiente de implementación) |
| Versión | 0.10 |
| Última actualización | 2026-07-02 |
| Dominio | Inversiones (`investments`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento del módulo de inversión (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

---

## 1. Propósito

Módulo de **seguimiento de cartera de inversión** inspirado en Portfolio Performance: registrar las operaciones de una cartera de bolsa (actualmente en Interactive Brokers), calcular posiciones, valoración, dividendos y rentabilidad (TWR/XIRR), y visualizarlo en un dashboard propio.

Es un **bounded context autocontenido** (`investments`), deliberadamente aislado de la economía doméstica: las compra-ventas, dividendos, intereses y comisiones viven y se reflejan **solo** dentro de este módulo. La única relación con el resto de la app es conceptual: el traspaso de fondos entre una cuenta doméstica y la cartera se registra de forma **independiente en cada lado** (ver RN-1).

## 2. Objetivos y no-objetivos

**Objetivos**
- Registrar carteras y sus operaciones: compra, venta, dividendo, interés, comisión, retención, split, aportación, retirada de efectivo y **conversión de divisa**.
- Importar las operaciones desde informes **Flex Query** de Interactive Brokers (CSV/XML), reutilizando el patrón ACL del contexto `imports`.
- Calcular posiciones (títulos, coste medio, P&L latente/realizado) y el efectivo de la cartera — siempre **computados, nunca almacenados**.
- Valorar la cartera con las cotizaciones de cierre incluidas en el propio Flex (sin dependencias online en v1).
- Soporte **multidivisa** interno al contexto, con conversión a EUR solo en la capa de lectura.
- Métricas de rentabilidad **TWR** (ponderada por tiempo) y **XIRR** (ponderada por dinero), por posición y por cartera.
- Dashboard de inversión: valoración, P&L, dividendos cobrados, asignación de activos, rentabilidad.

**No-objetivos (fuera de alcance de este PRD)**
- Integración contable con la economía doméstica: las operaciones de inversión **no** generan movimientos en `transactions`/`transfers` ni afectan a ingresos/gastos, presupuestos o dashboard domésticos.
- Descarga automática del Flex vía **Flex Web Service** (token IBKR) → backlog.
- Cotizaciones desde APIs externas (Yahoo Finance u otras) → backlog; el puerto `PriceProviderPort` queda definido desde v1 para que sea solo un adaptador nuevo.
- Órdenes/operativa real contra el broker, fiscalidad (informes de plusvalías), derivados.

## 3. Modelo de datos (diseño)

**Esquema PostgreSQL separado**: todas las tablas del módulo viven en el esquema **`investments`** (la economía doméstica sigue en `public`), de modo que el aislamiento del bounded context también es físico — ningún esquema se contamina con datos del otro ámbito y no existen foreign keys entre esquemas. La migración `V6` crea el esquema (`CREATE SCHEMA IF NOT EXISTS investments`) y Flyway lo gestiona desde el mismo histórico de migraciones; las entidades JPA del contexto declaran `@Table(schema = "investments")` y `ddl-auto=validate` valida contra él.

Nuevas tablas vía migraciones Flyway `V6+`, todas en `investments.*`. Todo importe monetario del contexto usa un value object propio **con divisa** (p. ej. `CurrencyMoney(amount, currency)`); el `Money` del kernel compartido (EUR implícito) **no se toca**.

**`security`** — instrumento (acción, ETF, fondo…)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `isin` | `varchar` | Identidad de negocio junto a la divisa de cotización (única por par). |
| `ticker` | `varchar` | Símbolo (p. ej. `VWCE`). |
| `name` | `varchar NOT NULL` | |
| `currency` | `varchar(3) NOT NULL` | Divisa de cotización (ISO 4217). |
| `type` | `varchar` | Acción / ETF / fondo / otro. |

**`price_quote`** — serie de cierres por instrumento

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `security_id` | FK → `security` | |
| `quote_date` | `date` | Única por (`security_id`, `quote_date`). |
| `price` | `numeric` | En la divisa del instrumento. Se alimenta del Flex al importar. |

**`portfolio`** — cartera

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `name` | `varchar NOT NULL` | P. ej. "Interactive Brokers". |
| `base_currency` | `varchar(3) NOT NULL` | Divisa base de la cartera (EUR por defecto). |

**`investment_transaction`** — operación de la cartera

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `portfolio_id` | FK → `portfolio` | |
| `security_id` | FK → `security`, nullable | Nulo en operaciones puras de efectivo (aportación, retirada, interés, conversión de divisa). |
| `type` | `varchar NOT NULL` | `BUY`, `SELL`, `DIVIDEND`, `INTEREST`, `FEE`, `TAX`, `SPLIT`, `DEPOSIT`, `WITHDRAWAL`, `FX_TRADE`. |
| `trade_date` | `date NOT NULL` | |
| `quantity` | `numeric` | Títulos (nulo si no aplica). |
| `price` | `numeric` | Precio unitario en la divisa del instrumento. |
| `amount` | `numeric NOT NULL` | Importe total con signo, en `currency`. En `FX_TRADE`: la pierna **saliente** (negativa). |
| `currency` | `varchar(3) NOT NULL` | |
| `counter_amount` | `numeric`, nullable | Solo `FX_TRADE`: pierna **entrante** (positiva) de la conversión, en `counter_currency`. |
| `counter_currency` | `varchar(3)`, nullable | Solo `FX_TRADE`. |
| `fee` / `tax` | `numeric` | Comisión y retención asociadas a la operación. |
| `fx_rate_to_base` | `numeric`, nullable | **Snapshot del tipo de cambio aplicado en el apunte** (`fxRateToBase` del Flex, tipo divisa del apunte → divisa base). Nulo en apuntes manuales (fallback: tabla `exchange_rate`, RN-7). |
| `description` | `varchar` | |
| `external_id` | `varchar` | Id de operación del Flex (`tradeID`/`transactionID`), único por cartera → idempotencia del import. |

**`exchange_rate`** — tipos de cambio (del propio Flex)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `rate_date` | `date` | Único por (`rate_date`, `from_currency`, `to_currency`). |
| `from_currency` / `to_currency` | `varchar(3)` | |
| `rate` | `numeric` | |

**Nada materializado**: posiciones, coste medio, efectivo de la cartera y valoración se calculan siempre a partir de `investment_transaction` + `price_quote` + `exchange_rate`.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | El usuario puede crear, listar, editar y eliminar carteras. |
| RF-2 | El usuario puede registrar manualmente operaciones de cualquier tipo (§3) en una cartera. |
| RF-3 | El usuario puede importar un informe Flex Query de IBKR (CSV/XML) sobre una cartera: operaciones, dividendos, comisiones, posiciones abiertas (→ cotizaciones) y tipos de cambio. |
| RF-4 | El import es idempotente: una operación ya importada (`external_id`) no se duplica. |
| RF-5 | El usuario ve las posiciones actuales de la cartera: títulos, coste medio, valor de mercado, P&L latente y % del total. |
| RF-6 | El usuario ve el efectivo de la cartera por divisa. |
| RF-7 | El usuario ve los dividendos e intereses cobrados y las comisiones/retenciones pagadas, agregados por periodo y por instrumento. |
| RF-8 | El usuario ve la rentabilidad TWR y XIRR por posición y por cartera. |
| RF-9 | Toda la valoración agregada se muestra convertida a EUR (o a la divisa base de la cartera), usando el último tipo de cambio disponible. |
| RF-10 | El dashboard doméstico muestra una tarjeta informativa de patrimonio con el valor total de la cartera y su fecha de valoración (solo lectura de la API de `investments`; sin mezclar agregados domésticos). |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | **Aislamiento de contextos**: ninguna operación de inversión crea, modifica ni afecta a movimientos, transferencias, categorías, presupuestos ni dashboard domésticos. El traspaso de fondos se registra de forma independiente en cada lado: gasto/ingreso (categoría del usuario, p. ej. "Inversión") en la cuenta doméstica, y `DEPOSIT`/`WITHDRAWAL` en la cartera. Sin enlace automático. |
| RN-2 | El **efectivo de la cartera se calcula por divisa**: `Σ DEPOSIT − Σ WITHDRAWAL − Σ compras + Σ ventas + Σ dividendos + Σ intereses − Σ comisiones − Σ retenciones − Σ piernas salientes de FX_TRADE en esa divisa + Σ piernas entrantes de FX_TRADE en esa divisa`. Una conversión de divisa mueve dos saldos a la vez y **no** es un flujo externo de la cartera. |
| RN-3 | Las **posiciones se calculan** por instrumento: cantidad = Σ compras − Σ ventas (ajustada por splits); coste medio por método de coste promedio. |
| RN-4 | No se puede vender más cantidad de la que hay en posición a la fecha de la operación (validación de dominio). |
| RN-5 | Un instrumento con operaciones no se puede eliminar; una cartera con operaciones no se puede eliminar (409, como cuentas/categorías). |
| RN-6 | La valoración usa la **última cotización disponible** ≤ fecha de valoración; si un instrumento no tiene cotización, su posición se muestra a coste con aviso. |
| RN-7 | **Doble mecanismo de conversión de divisa**, siempre en la capa de lectura (los datos se almacenan en su divisa original): (a) los importes **fijados en el pasado** (coste de adquisición, P&L realizado, dividendos cobrados) se convierten con el **snapshot** `fx_rate_to_base` del propio apunte — inmutables ante reimportaciones y cuadran con la liquidación real de IBKR; (b) la **valoración a una fecha** (valor de mercado, evolución, pesos) usa el último tipo ≤ fecha de la tabla `exchange_rate`, que también es el fallback para apuntes sin snapshot (manuales). El P&L latente incorpora así automáticamente el efecto divisa. |
| RN-8 | **TWR**: rentabilidad encadenada por subperiodos delimitados por flujos externos (`DEPOSIT`/`WITHDRAWAL`), calculada sobre la serie de valoraciones disponible. **XIRR**: TIR de los flujos externos + valor actual (Newton-Raphson con fallback de bisección). Los `FX_TRADE` **no** son flujos externos: no delimitan subperiodos ni cuentan como cashflow (solo cambian la divisa del efectivo). Con cotizaciones solo en fechas de import, ambas son aproximaciones sobre esos puntos; mejorarán al llegar la API de precios. |
| RN-9 | Cotizaciones y tipos de cambio del Flex hacen *upsert*: un valor más reciente para la misma fecha sobrescribe (clave natural: `security`+fecha y fecha+par de divisas). Nunca generan filas duplicadas. |
| RN-10 | **Idempotencia de la importación** (doble defensa): (a) el caso de uso omite toda fila cuyo `external_id` ya exista en la cartera y la reporta como "duplicada" en el resumen (no es error: reimportar el mismo informe o periodos solapados es un uso esperado); (b) la BD lo garantiza físicamente con `UNIQUE (portfolio_id, external_id)` en `investments.investment_transaction`. `external_id` = `ibOrderID` (operaciones), `transactionID` (efectivo), `tradeId` (tasas FTT). Límite conocido: los apuntes manuales (sin `external_id`) no son deduplicables frente a un import posterior (ver §9). |

## 6. API (diseño)

Base: `/api/investments`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET/POST` | `/portfolios` · `PUT/DELETE /portfolios/{id}` | CRUD de carteras. |
| `GET` | `/portfolios/{id}/positions` | Posiciones actuales (view CQRS). |
| `GET/POST` | `/portfolios/{id}/transactions` · `PUT/DELETE /transactions/{id}` | Operaciones (listado filtrable + alta/edición manual). |
| `POST` | `/portfolios/{id}/import` | Import de Flex Query (multipart, como `/api/imports`); devuelve resumen filas ok/errores. |
| `GET` | `/portfolios/{id}/performance` | TWR/XIRR por posición y total. |
| `GET` | `/portfolios/{id}/income` | Dividendos/intereses/comisiones agregados. |
| `GET/POST` | `/securities` · `PUT /securities/{id}` | Catálogo de instrumentos (alta automática en import). |

## 7. UI/UX (diseño)

Nueva página lazy `pages/investments` en el menú lateral ("Inversión"). Los gráficos usan Chart.js directamente y se integran con `ThemeService` (colores/rejilla y redibujado al cambiar de tema), como el dashboard doméstico.

**Cabecera de KPIs (tarjetas):**
- Valor total de la cartera en EUR, con la **fecha de valoración** visible (RN-6: última cotización disponible).
- Capital aportado neto (Σ aportaciones − Σ retiradas).
- P&L latente (€ y %).
- Efectivo disponible (por divisa).
- Dividendos cobrados en el año en curso.
- TWR y XIRR del total (desde F4).

**Gráficos:**
1. **Evolución: valor vs capital aportado** (línea) — la serie de aportado es exacta y escalonada (fechas de `DEPOSIT`/`WITHDRAWAL`); la de valor solo tiene puntos en fechas con cotización (imports). Al llegar la API de precios (F5) la misma vista gana la curva diaria sin rediseño.
2. **Asignación de la cartera** (donut) — peso % de cada posición, con el efectivo como una porción más.
3. **Dividendos por periodo** (barras) — vista por defecto: **mensual del año seleccionado**, apiladas por instrumento, con selector de año (y opción "todo" anual).
4. **P&L por posición** (barras horizontales divergentes) — ganancia/pérdida latente en € por instrumento, ordenadas de mejor a peor, verde/rojo.
5. **Rentabilidad por posición** (barras horizontales, %) — TWR/XIRR por instrumento (desde F4).

**Tabla de posiciones**: instrumento, títulos, coste medio, precio, valor, P&L (€ y %), peso.

**Pestañas**: operaciones (listado filtrable + alta manual) y dividendos (cobros por año/instrumento).

**Botón Importar Flex**: reutiliza el patrón del diálogo de import existente (`components/import-dialog.ts`) adaptado al Flex.

**Tarjeta de patrimonio en el dashboard doméstico**: única presencia de la inversión fuera de su página — una tarjeta informativa con el valor total de la cartera (y fecha de valoración), leyendo el resumen del contexto `investments` vía su API. No mezcla agregados: los ingresos/gastos/saldos domésticos no incorporan nada de inversión (RN-1 intacta a nivel de datos). Al implementarla se actualizará también el PRD Dashboard.

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Venta sin posición suficiente | `400 ValidationException` de dominio. |
| Operación con importe/cantidad no positivos donde aplica | `400`. |
| Eliminar cartera o instrumento con operaciones | `409 ConflictException`. |
| Import: fila ilegible o instrumento sin ISIN/símbolo | Fila reportada como error, el resto se importa (tolerante, como el import bancario). |
| Import: operación duplicada (`external_id`) | Se omite y se reporta como duplicada. |

## 9. Casos límite y notas

- **Conversiones de divisa (`FX_TRADE`)**: concepto de dominio genérico e independiente del broker — pierna saliente + pierna entrante + coste, sin flujo externo. En el Flex de IBKR llegan como órdenes `assetCategory="CASH"` (símbolo del par, p. ej. `EUR.USD`; los signos de `quantity`/`proceeds` identifican cada pierna); esa traducción es responsabilidad exclusiva del ACL `FlexReportParser`. Incluye las micro-conversiones automáticas de IBKR (residuos de pocos céntimos), que se importan igual. Un futuro broker que reporte la conversión de otra forma (p. ej. dos líneas débito/crédito) la traducirá al mismo `FX_TRADE` en su propio parser. El P&L por efectivo en divisa no se calcula aparte: emerge al valorar el efectivo al tipo del día (RN-7b).
- **Splits**: ajustan cantidad y coste medio sin generar flujo de caja.
- **Dividendo con retención en origen**: el Flex los trae como apuntes separados (dividendo + withholding tax); se importan como `DIVIDEND` + `TAX` vinculados al mismo instrumento y fecha.
- **Ventas parciales**: P&L realizado por coste promedio en el momento de la venta.
- **Cartera sin cotizaciones recientes**: la valoración queda "a fecha de último import"; la UI muestra la fecha de valoración.
- **Apunte manual + import del mismo apunte**: una operación registrada a mano no tiene `external_id`, por lo que un import posterior que contenga esa misma operación la duplicará (RN-10). Convención de uso: en carteras alimentadas por Flex, no registrar a mano operaciones que vayan a llegar en el informe; el alta manual es para lo que el Flex no cubre.
- **Configuración validada del Flex Query** (Activity Flex Query, formato **XML**, fechas ISO `yyyy-MM-dd`, verificada contra un informe real de la cuenta el 2026-07-02):
  - *Account Information*: solo `accountId` y `currency` (sin datos personales).
  - *Trades*: nivel **Orders** únicamente (las filas `SYMBOL_SUMMARY`/`ASSET_SUMMARY`, si aparecen, se ignoran filtrando `levelOfDetail="ORDER"`).
  - *Cash Transactions*: nivel **Detail** (tipos Dividends, Withholding Tax, Deposits/Withdrawals; incluir Broker Interest si la cuenta genera intereses).
  - *Open Positions* (`markPrice` a fecha del informe → fuente de cotizaciones en v1), *Securities (Financial Instrument Information)*, *Corporate Actions*, *Transaction Taxes* (FTT itemizada; en la fila de la orden `taxes` viene a 0) y *Conversion Rates* (el parser filtra y persiste solo los pares con divisas presentes en la cartera, **normalizados a una sola dirección** divisa→EUR — IBKR exporta ambas direcciones de muchos pares irrelevantes; la inversa se obtiene aritméticamente. Volumen resultante: ~365 filas/año por divisa extranjera en cartera).
  - Secciones vacías o no marcadas (`Transfers`, `ComplexPositions`, `FxPositions`…) se ignoran.
- **Identificadores para la idempotencia** (`external_id`): a nivel ORDER `tradeID`/`transactionID` vienen vacíos → usar **`ibOrderID`** en operaciones, **`transactionID`** en apuntes de efectivo y **`tradeId`** en `TransactionTax`. El vínculo de una FTT con su orden se resuelve por instrumento+fecha (su `tradeId` apunta al nivel ejecución, que no se importa).
- Un informe Flex cubre como máximo 365 días: la carga inicial del histórico se hace con un informe por año, importados en orden; la idempotencia hace inocuos los solapamientos.

## 10. Backlog / mejoras futuras

- **Flex Web Service**: descarga automática del informe con token IBKR (mismo puerto de entrada que el import manual).
- **API externa de cotizaciones** (híbrido): adaptador de `PriceProviderPort` (Yahoo Finance u otro) + botón de refresco.
- Benchmarks (comparar TWR contra un índice).
- Clasificación de activos por dimensiones (región, sector) al estilo Portfolio Performance.
- Informe fiscal de plusvalías.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Método de coste | Coste promedio. | FIFO sería el fiscalmente correcto en España; evaluar al abordar el informe fiscal. |
| Serie de valoración dispersa | TWR/XIRR sobre las cotizaciones disponibles (fechas de import). | Se refina solo al integrar la API de precios. |
| ~~Formato Flex~~ | **Resuelto (2026-07-02): XML.** Atributos con nombre estable e independientes de las columnas marcadas; validado contra informes reales de la cuenta (ver §9). | — |

## 12. Fases de implementación

Desarrollo con **TDD obligatorio** (ver `CLAUDE.md`): cada hito se construye en ciclos red-green-refactor y se cierra con **un commit** (tests en verde + PRD actualizado). Un hito no empieza hasta que el anterior está commiteado.

### F1 — Modelo + import Flex + posiciones/valoración

| Hito | Contenido | Tests |
|---|---|---|
| H1.1 | Value objects del contexto: `CurrencyMoney`, `PortfolioId`, `SecurityId`, `Quantity`. | Unitarios de dominio. |
| H1.2 | Agregado `Security` + `PriceQuote` (invariantes: divisa ISO, identidad ISIN+divisa, unicidad de cotización por fecha, upsert). | Unitarios de dominio. |
| H1.3 | Agregados `Portfolio` e `InvestmentTransaction` (tipos de operación, invariantes de importes/cantidades, `external_id`). | Unitarios de dominio. |
| H1.4 | Servicio de dominio `PositionCalculator`: posiciones, coste medio, P&L latente/realizado, efectivo por divisa (RN-2/RN-3/RN-4, venta sin posición). | Unitarios de dominio. |
| H1.5 | Puertos de salida + `PortfolioService`/`SecurityService` (casos de uso CRUD, guardas de borrado RN-5). | Aplicación con puertos mockeados. |
| H1.6 | Migración `V6__investments.sql` (crea el **esquema `investments`** + tablas §3 salvo `exchange_rate`) + entidades/mappers/adaptadores JPA (`@Table(schema = "investments")`). | `@DataJpaTest` (Testcontainers): round-trip mappers, unicidades, esquema separado. |
| H1.7 | Read-side CQRS: `InvestmentQueryPort`, `PositionView`, `PortfolioSummaryView` + query adapter (valoración con última cotización, RN-6). | `@DataJpaTest` del adapter. |
| H1.8 | Web: `PortfolioController`/`SecurityController` + DTOs; endpoints CRUD y `GET /positions`; el contexto entra en ArchUnit. | `@WebMvcTest` + `ArchitectureTest`. |
| H1.9 | `FlexReportParser` (ACL): secciones *Trades* (órdenes de valores y conversiones `FX_TRADE`), *Open Positions* y ***Cash Transactions* completa** (depósitos, retiradas, dividendos y retenciones — el par dividendo+retención llega como apuntes separados, §9); fixtures de informes Flex reales. | Unitarios del parser con fixtures. |
| H1.10 | Caso de uso `ImportFlexReport`: idempotencia por `external_id` (RF-4), alta automática de `Security`, upsert de cotizaciones (RN-9), errores por fila; endpoint `POST /portfolios/{id}/import`. Con esto el efectivo (RN-2) y el capital aportado son exactos desde el primer import. | Aplicación mockeada + `@WebMvcTest`. |
| H1.11 | Frontend: página `pages/investments` (KPIs, donut de asignación, evolución valor vs aportado, P&L por posición, tabla de posiciones), diálogo de import Flex, ruta lazy y entrada en el menú. | Build + revisión manual. |
| H1.12 | Tarjeta de patrimonio en el dashboard doméstico (valor total + fecha de valoración, leyendo la API de `investments`); actualización del PRD Dashboard. | Build + revisión manual. |

### F2 — Vistas de rentas y alta manual

El parseo e importación de dividendos/retenciones ya quedó en F1 (H1.9); esta fase añade su lectura y la operativa manual.

| Hito | Contenido | Tests |
|---|---|---|
| H2.1 | Dominio: agregados de rentas por periodo/instrumento (RF-7) sobre los apuntes ya importados. | Unitarios de dominio. |
| H2.2 | `IncomeView` + query adapter + endpoint `GET /portfolios/{id}/income`. | `@DataJpaTest` + `@WebMvcTest`. |
| H2.3 | Alta/edición manual de operaciones (RF-2): casos de uso + endpoints + formulario en la UI. | Aplicación + `@WebMvcTest`. |
| H2.4 | Frontend: pestañas de operaciones y dividendos + gráfico de dividendos (mensual apilado por instrumento, selector de año) y KPI de dividendos del año. | Build + revisión manual. |

### F3 — Multidivisa

| Hito | Contenido | Tests |
|---|---|---|
| H3.1 | Agregado `ExchangeRate` + servicio de conversión de dominio (último tipo ≤ fecha, RN-7). | Unitarios de dominio. |
| H3.2 | Migración `V7__exchange_rates.sql` + persistencia + parser de *Conversion Rates* (upsert RN-9). | `@DataJpaTest` + fixtures. |
| H3.3 | Conversión a EUR/divisa base en la capa de lectura según el doble mecanismo de RN-7 (snapshot `fx_rate_to_base` para importes fijados; tabla `exchange_rate` para valoración y fallback) + efectivo por divisa en la UI. | `@DataJpaTest` del adapter + `@WebMvcTest`. |

### F4 — Rentabilidad TWR/XIRR

| Hito | Contenido | Tests |
|---|---|---|
| H4.1 | `PerformanceCalculator` — XIRR: Newton-Raphson con fallback de bisección sobre flujos externos + valor actual (RN-8). | Unitarios de dominio (casos conocidos, convergencia, extremos). |
| H4.2 | `PerformanceCalculator` — TWR: encadenado por subperiodos delimitados por `DEPOSIT`/`WITHDRAWAL` sobre la serie de valoraciones. | Unitarios de dominio. |
| H4.3 | `PerformanceView` + query adapter + endpoint `GET /portfolios/{id}/performance` (por posición y total). | `@DataJpaTest` + `@WebMvcTest`. |
| H4.4 | Frontend: TWR/XIRR en el resumen y por posición; gráfico de evolución del valor (Chart.js). | Build + revisión manual. |

### F5 — Automatización (backlog)

| Hito | Contenido |
|---|---|
| H5.1 | Adaptador externo de `PriceProviderPort` (API de cotizaciones) + acción de refresco de precios. |
| H5.2 | Flex Web Service: descarga del informe con token IBKR (mismo caso de uso `ImportFlexReport`). |
| H5.3 | Modo híbrido: precios del Flex al importar + refresco bajo demanda/programado. |

## 13. Referencias de código

Pendiente de implementación. Estructura prevista del contexto `investments` (idéntica al resto):

- **Dominio**: agregados `Portfolio`, `Security`, `InvestmentTransaction`, `ExchangeRate`; VOs `CurrencyMoney`, `PortfolioId`, `SecurityId`, `Quantity`; servicios `PositionCalculator`, `PerformanceCalculator`; puertos de salida `PortfolioRepository`, `SecurityRepository`, `InvestmentTransactionRepository`, `ExchangeRateRepository`, `PriceProviderPort`.
- **Aplicación**: casos de uso CRUD + `ImportFlexReport`; read-side CQRS `InvestmentQueryPort` + views (`PositionView`, `PerformanceView`, `IncomeView`).
- **Infraestructura**: persistencia JPA (entities/mappers/adapters), web (`InvestmentController` y DTOs), y el ACL `FlexReportParser` en infraestructura de import.
- **Frontend**: `pages/investments/`, modelos en `models.ts`, llamadas en `api.service.ts`.
