# PRD — Inversiones

| Campo | Valor |
|---|---|
| Estado | ✅ **Implementado** — F1, F2, F3 y F3.5 completas (modelo + import Flex + posiciones/valoración multidivisa + rentas + alta manual + rentabilidad TWR/XIRR + historial de imports, con UI); F4 (automatización) en backlog |
| Versión | 0.49 |
| Última actualización | 2026-07-29 |
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
- Soporte **multidivisa** interno al contexto, con conversión a la divisa base de cada cartera (cualquier ISO 4217; EUR como **pivote** de tipos) solo en la capa de lectura.
- Métricas de rentabilidad **TWR** (ponderada por tiempo) y **XIRR** (ponderada por dinero), por posición y por cartera.
- Dashboard de inversión: valoración, P&L, dividendos cobrados, asignación de activos, rentabilidad.

**No-objetivos (fuera de alcance de este PRD)**
- Integración contable con la economía doméstica: las operaciones de inversión **no** generan movimientos en `transactions`/`transfers` ni afectan a ingresos/gastos, presupuestos o dashboard domésticos.
- Descarga automática del Flex vía **Flex Web Service** (token IBKR) → backlog.
- Cotizaciones desde APIs externas (Yahoo Finance u otras) → backlog; el puerto `PriceProviderPort` queda definido desde v1 para que sea solo un adaptador nuevo.
- Órdenes/operativa real contra el broker, fiscalidad (informes de plusvalías), derivados.

## 3. Modelo de datos (diseño)

**Esquema PostgreSQL separado**: todas las tablas del módulo viven en el esquema **`investments`** (la economía doméstica sigue en `public`), de modo que el aislamiento del bounded context también es físico — ningún esquema se contamina con datos del otro ámbito y no existen foreign keys entre esquemas. La migración `V7` (`V7__investments.sql`; el diseño preveía `V6`, pero ese número lo ocupó la migración de devoluciones de la economía doméstica) crea el esquema (`CREATE SCHEMA IF NOT EXISTS investments`) y Flyway lo gestiona desde el mismo histórico de migraciones; las entidades JPA del contexto declaran `@Table(schema = "investments")` y `ddl-auto=validate` valida contra él.

Nuevas tablas vía migraciones Flyway `V7+`, todas en `investments.*`. Todo importe monetario del contexto usa un value object propio **con divisa** (p. ej. `CurrencyMoney(amount, currency)`); el `Money` del kernel compartido (EUR implícito) **no se toca**.

**Precisión decimal fijada** (los informes reales traen fracciones de acción — compras de `2.303` títulos — y residuos FX de 8 decimales): cantidades, precios y tipos de cambio `numeric(19,8)`; importes monetarios `numeric(19,4)`. En Java todo es `BigDecimal` (los VOs fijan escala y redondeo). El frontend **no calcula, solo formatea**: recibe los agregados ya computados por la capa de lectura como números JSON (`number` de TypeScript, suficiente para visualización) y los muestra con los pipes de Angular; si alguna vista necesitara aritmética en cliente, ese campo pasaría a string + librería decimal.

**`security`** — instrumento (acción, ETF, fondo…)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `isin` | `varchar NOT NULL` | Identidad de negocio junto a la divisa de cotización — constraint física `UNIQUE (isin, currency)` en la migración V7. |
| `ticker` | `varchar` | Símbolo (p. ej. `VWCE`). |
| `name` | `varchar NOT NULL` | |
| `currency` | `varchar(3) NOT NULL` | Divisa de cotización (ISO 4217). |
| `type` | `varchar` | Acción / ETF / fondo / otro. |
| `exchange` | `varchar`, nullable | Mercado de cotización (`listingExchange` del Flex: AEB, SBF, NASDAQ, LSE, BVME…). **Metadato**, no parte de la identidad en v1. Llave para la API de precios externa (backlog). |
| `figi` | `varchar`, nullable | Identificador Bloomberg (FIGI), **único por cotización** (más fino que ISIN). Metadato para la API de precios externa (backlog). |

**`price_quote`** — serie de cierres por instrumento

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `security_id` | FK → `security` | |
| `quote_date` | `date` | Única por (`security_id`, `quote_date`). |
| `price` | `numeric(19,8)` | En la divisa del instrumento. Se alimenta del Flex al importar. |

**`portfolio`** — cartera

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `name` | `varchar NOT NULL` | P. ej. "Interactive Brokers". |
| `base_currency` | `varchar(3) NOT NULL` | Divisa base de la cartera (EUR por defecto, **cualquier ISO 4217**). Debe coincidir con la divisa base de la **cuenta del broker** que la alimenta: los snapshots `fxRateToBase` del Flex apuntan a ella — el import lo valida (§8). |

**`investment_transaction`** — operación de la cartera

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `portfolio_id` | FK → `portfolio` | |
| `security_id` | FK → `security`, nullable | Nulo en operaciones puras de efectivo (aportación, retirada, interés, conversión de divisa). |
| `type` | `varchar NOT NULL` | `BUY`, `SELL`, `DIVIDEND`, `INTEREST`, `FEE`, `TAX`, `TRADE_TAX`, `SPLIT`, `DEPOSIT`, `WITHDRAWAL`, `FX_TRADE`. `TAX` = retención sobre **rentas** (dividendos/intereses); `TRADE_TAX` = tasa sobre una **compraventa** (FTT francesa/italiana, stamp duty…) — la distinción evita que la vista de rentas confunda una FTT con una retención (§9). En `SPLIT`, `quantity` es el **delta de títulos** (con signo) y `amount` = 0 (RN-3). |
| `trade_date` | `date NOT NULL` | |
| `quantity` | `numeric(19,8)` | Títulos (nulo si no aplica); admite fracciones de acción. |
| `price` | `numeric(19,8)` | Precio unitario en la divisa del instrumento. |
| `amount` | `numeric(19,4) NOT NULL` | Importe total con el **signo del flujo de caja** (convenio de signos, tabla más abajo), en `currency`, y **sin comisión** — la comisión vive solo en `fee`, evitando el doble conteo. En `FX_TRADE`: la pierna **saliente** (negativa). |
| `currency` | `varchar(3) NOT NULL` | |
| `counter_amount` | `numeric(19,4)`, nullable | Solo `FX_TRADE`: pierna **entrante** (positiva) de la conversión, en `counter_currency`. |
| `counter_currency` | `varchar(3)`, nullable | Solo `FX_TRADE`. |
| `fee` / `tax` | `numeric(19,4)` | Comisión y retención asociadas a la operación, con el signo del flujo de caja (**negativas**), como todo importe. |
| `fee_currency` / `tax_currency` | `varchar(3)`, nullable | Divisa propia de la comisión/retención **cuando difiere** de `currency`; nulo = la divisa del apunte (caso común). En las FX de IBKR el apunte va en la divisa de proceeds (p. ej. USD) y la comisión en EUR. |
| `fx_rate_to_base` | `numeric(19,8)`, nullable | **Snapshot del tipo de cambio aplicado en el apunte** (`fxRateToBase` del Flex, tipo divisa del apunte → divisa base). Nulo en apuntes manuales (fallback: tabla `exchange_rate`, RN-7). |
| `description` | `varchar` | |
| `external_id` | `varchar` | Id de operación del Flex **prefijado por origen** (`ORD-`/`CT-`/`FTT-`/`CA-`, RN-10), único por cartera → idempotencia del import. |

**`exchange_rate`** — tipos de cambio (del propio Flex), normalizados **divisa→EUR** con EUR como **pivote**: cualquier par se deriva aritméticamente (`from→to = (from→EUR) ÷ (to→EUR)`), de modo que una cartera puede tener cualquier divisa base sin ampliar la tabla

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `rate_date` | `date` | Único por (`rate_date`, `from_currency`, `to_currency`). |
| `from_currency` / `to_currency` | `varchar(3)` | |
| `rate` | `numeric(19,8)` | |

**Convenio de signos — "perspectiva del efectivo"** (el mismo que usa el Flex de IBKR: `proceeds` positivo en ventas, `ibCommission` negativo…): todo importe se almacena con el signo del flujo de caja real, de modo que el efectivo (RN-2) y la posición (RN-3) son **sumas directas sin lógica de signos por tipo**, y el parser copia los signos del Flex casi sin traducir. Invariantes por tipo (validados en dominio, §8):

| Tipo | `quantity` | `amount` | Nota |
|---|---|---|---|
| `BUY` | > 0 | < 0 | Sale efectivo |
| `SELL` | < 0 | > 0 | Entra efectivo |
| `DEPOSIT` | nulo | > 0 | Entra efectivo; el tipo lo deriva el propio parser del signo (§9) |
| `DIVIDEND` / `INTEREST` / `FEE` / `TAX` | nulo | ≠ 0 (habitualmente > 0 en `DIVIDEND`/`INTEREST`, < 0 en `FEE`/`TAX`) | Apuntes de *Cash Transactions*: IBKR puede emitir una **reversa** con el signo invertido de una corrección (original + reversa + re-book, ver §11); el importe admite cualquier signo salvo cero |
| `WITHDRAWAL` / `TRADE_TAX` | nulo | < 0 | Sale efectivo; `WITHDRAWAL` deriva su tipo del signo igual que `DEPOSIT`, `TRADE_TAX` no es un apunte de *Cash Transactions* (tasa de una compraventa) |
| `SPLIT` | delta con signo | = 0 | Sin flujo de caja |
| `FX_TRADE` | nulo | < 0 (pierna saliente) | `counter_amount` > 0 (pierna entrante) |

Coste asumido del convenio: la API expone los signos "contables" y la UI formatea para presentación (p. ej. una venta lista `quantity` negativa). Las agregaciones que necesitan una **magnitud** en vez de un flujo con signo (retenciones/comisiones pagadas en `IncomeCalculator`, RF-7) suman primero los importes con signo (para que una reversa cancele su original) y solo niegan el resultado al final — nunca toman valor absoluto fila a fila, porque eso sumaría una reversa en vez de cancelarla.

**Nada materializado**: posiciones, coste medio, efectivo de la cartera y valoración se calculan siempre a partir de `investment_transaction` + `price_quote` + `exchange_rate`.

**`import_record`** — historial de imports Flex (migración `V8`; precursor de la 2.1 Flex Web Service, ver §12 F4). Un registro por cada llamada a `POST .../import`, tanto si importó filas como si no (import "vacío" o solo duplicadas también se loguea) — de solo-escritura desde el dominio (`ImportRecordRepository.save`), lectura paginada por CQRS (`ImportRecordQueryPort`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `portfolio_id` | FK → `portfolio` | |
| `imported_at` | `timestamp NOT NULL` | Momento del intento de import. |
| `file_name` | `varchar`, nullable | `MultipartFile.getOriginalFilename()` puede venir nulo. |
| `from_date` / `to_date` | `date`, `to_date NOT NULL` | Periodo cubierto por el informe Flex (`FlexReport.fromDate()/toDate()`); `fromDate` es opcional en el propio Flex, `toDate` no. |
| `imported_count` / `duplicated_count` | `int NOT NULL` | Contadores del `FlexImportResult`. |
| `errors` / `warnings` | `jsonb NOT NULL DEFAULT '[]'` | Detalle de filas rechazadas (`section`, `reference`, `message`) y avisos no bloqueantes. Se leen siempre como bloque junto a su import padre — sin tabla hija, único `@OneToMany` que introduciría el contexto sin necesidad real. |

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | El usuario puede crear, listar, editar y eliminar carteras. |
| RF-2 | El usuario puede registrar manualmente operaciones de cualquier tipo (§3) en una cartera. |
| RF-3 | El usuario puede importar un informe Flex Query de IBKR (CSV/XML) sobre una cartera: operaciones, dividendos, comisiones, posiciones abiertas (→ cotizaciones) y tipos de cambio. |
| RF-4 | El import es idempotente: una operación ya importada (`external_id`) no se duplica. |
| RF-5 | El usuario ve las posiciones actuales de la cartera: títulos, coste medio, valor de mercado, P&L latente y % del total. |
| RF-6 | El usuario ve el efectivo de la cartera por divisa. |
| RF-7 | El usuario ve los dividendos e intereses cobrados (en **bruto**, con el neto disponible en el detalle) y las comisiones/retenciones pagadas, agregados por periodo y por instrumento. |
| RF-8 | El usuario ve la rentabilidad TWR y XIRR por posición y por cartera. |
| RF-9 | Toda la valoración agregada se muestra convertida a EUR (o a la divisa base de la cartera), usando el último tipo de cambio disponible. |
| RF-10 | El dashboard doméstico muestra una tarjeta informativa de patrimonio con el **patrimonio agregado de todas las carteras** y su fecha de valoración (la más antigua de las usadas); con más de una cartera, la tarjeta desglosa el valor de cada una (vía `GET /api/investments/summary`; solo lectura, sin mezclar agregados domésticos). **Comportamiento degradado**: sin carteras o sin valor que mostrar, la tarjeta se **oculta**; si la API de `investments` falla, la tarjeta se muestra con **"—"** y no rompe el resto del dashboard. |
| RF-11 | El usuario puede consultar el **historial de imports Flex** de una cartera: fecha/hora, fichero, periodo cubierto y el resumen completo (importadas/duplicadas/errores/*warnings*) de cada intento, incluidos los que no importaron ninguna fila nueva. Precursor de la descarga automática (F4, ver `docs/plan/historial-imports.md`). |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | **Aislamiento de contextos**: ninguna operación de inversión crea, modifica ni afecta a movimientos, transferencias, categorías, presupuestos ni dashboard domésticos. El traspaso de fondos se registra de forma independiente en cada lado: gasto/ingreso (categoría del usuario, p. ej. "Inversión") en la cuenta doméstica, y `DEPOSIT`/`WITHDRAWAL` en la cartera. Sin enlace automático. |
| RN-2 | El **efectivo de la cartera se calcula por divisa** como **suma directa de importes firmados** (convenio de signos de §3): `Σ amount` de los apuntes en esa divisa `+ Σ counter_amount` de los `FX_TRADE` cuya `counter_currency` es esa divisa `+ Σ fee/tax` cuya divisa efectiva es esa (la divisa efectiva de una comisión/retención es `fee_currency`/`tax_currency` si está informada; la del apunte si no). Sin lógica de signos por tipo: el signo ya está en el dato. Una conversión de divisa mueve dos saldos a la vez y **no** es un flujo externo de la cartera. |
| RN-3 | Las **posiciones se calculan** por instrumento: cantidad = **Σ `quantity` directa** (compras positivas, ventas negativas, deltas de `SPLIT` con su signo — convenio §3); coste medio por método de coste promedio. El **coste de adquisición capitaliza los costes de compra**: `|amount| + |fee| + |trade_tax|` (criterio Portfolio Performance, coherente con el "valor de adquisición" fiscal); en la venta, simétricamente, la comisión reduce el importe neto percibido → P&L realizado = neto percibido − coste capitalizado promedio de los títulos vendidos. Cada componente se convierte a divisa base con su propio snapshot (RN-7a). Un `SPLIT` es un **delta de cantidad a coste 0** (`quantity` del apunte, con signo), no un ratio: sumar títulos sin coste reduce el coste medio automáticamente (mismo coste total repartido entre más títulos) sin reprocesar el histórico. |
| RN-4 | **Venta sin posición suficiente — regla dual**: en alta/edición **manual** es una validación dura de dominio (no se puede vender más cantidad de la que hay en posición a la fecha de la operación → `400`). En **import** la fila se importa igual y se reporta como ***warning*** en el resumen ("posición negativa: falta histórico anterior") — un Flex parcial (p. ej. 2025 sin haber importado 2024) no debe fallar. La UI de posiciones marca en rojo las cantidades negativas. La comparación de cantidades usa la **tolerancia de la precisión** (`numeric(19,8)`), no igualdad estricta: tras compras fraccionadas o splits, cerrar una posición puede dejar residuos de 1e-8 que no deben bloquear la venta. |
| RN-5 | Un instrumento con operaciones no se puede eliminar; una cartera con operaciones no se puede eliminar (409, como cuentas/categorías). |
| RN-6 | La valoración usa la **última cotización disponible** ≤ fecha de valoración; si un instrumento no tiene cotización, su posición se muestra a coste con aviso. |
| RN-7 | **Doble mecanismo de conversión de divisa**, siempre en la capa de lectura (los datos se almacenan en su divisa original): (a) los importes **fijados en el pasado** (coste de adquisición, P&L realizado, dividendos cobrados) se convierten con el **snapshot** `fx_rate_to_base` del propio apunte — inmutables ante reimportaciones y cuadran con la liquidación real de IBKR; (b) la **valoración a una fecha** (valor de mercado, evolución, pesos) usa el último tipo ≤ fecha de la tabla `exchange_rate`, que también es el fallback para apuntes sin snapshot (manuales). Los tipos se almacenan divisa→EUR y **EUR actúa de pivote**: cualquier par se deriva (`from→to = (from→EUR) ÷ (to→EUR)`), así la divisa base de la cartera puede ser cualquiera. Los snapshots (a) apuntan a la base de la cuenta del broker — por eso el import valida que coincida con la de la cartera (§8). El P&L latente incorpora así automáticamente el efecto divisa. |
| RN-8 | **TWR**: rentabilidad encadenada por subperiodos delimitados por flujos externos (`DEPOSIT`/`WITHDRAWAL`), calculada sobre la serie de valoraciones disponible. **XIRR**: TIR de los flujos externos + valor actual (Newton-Raphson con fallback de bisección). Los `FX_TRADE` **no** son flujos externos: no delimitan subperiodos ni cuentan como cashflow (solo cambian la divisa del efectivo). Con cotizaciones solo en fechas de import, ambas son aproximaciones sobre esos puntos; mejorarán al llegar la API de precios. |
| RN-9 | Cotizaciones y tipos de cambio del Flex hacen *upsert*: un valor más reciente para la misma fecha sobrescribe (clave natural: `security`+fecha y fecha+par de divisas). Nunca generan filas duplicadas. El reimport también refresca los **metadatos no identitarios** del `security` (`name`/`ticker`/`exchange`/`figi` — corrige renombres de IBKR); la identidad ISIN+divisa nunca cambia. |
| RN-10 | **Idempotencia de la importación** (doble defensa): (a) el caso de uso omite toda fila cuyo `external_id` ya exista en la cartera y la reporta como "duplicada" en el resumen (no es error: reimportar el mismo informe o periodos solapados es un uso esperado); (b) la BD lo garantiza físicamente con `UNIQUE (portfolio_id, external_id)` en `investments.investment_transaction`. `external_id` se construye **prefijado por origen** para aislar los espacios de numeración de IBKR (secuencias independientes que podrían colisionar numéricamente): `ORD-<ibOrderID>` (operaciones), `CT-<transactionID>` (efectivo), `FTT-<tradeId>` (tasas FTT → filas `TRADE_TAX`), `CA-<transactionID>` (acciones corporativas/splits). Límite conocido: los apuntes manuales (sin `external_id`) no son deduplicables frente a un import posterior (ver §9). |

## 6. API (diseño)

Base: `/api/investments`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET/POST` | `/portfolios` · `PUT/DELETE /portfolios/{id}` | CRUD de carteras. |
| `GET` | `/portfolios/{id}/positions` | Posiciones actuales (view CQRS). |
| `GET` | `/portfolios/{id}/summary` | KPIs de cabecera: valor total, aportado neto, P&L latente, efectivo por divisa, dividendos del año, fecha de valoración. |
| `GET` | `/portfolios/{id}/valuation-history` | Serie `{fecha, valor, aportado acumulado}` para el gráfico de evolución (§7). |
| `GET` | `/summary` | Resumen global: patrimonio **agregado de todas las carteras** + desglose por cartera, **en EUR** (divisa de la app doméstica; cada cartera se convierte desde su base vía pivote si difiere); fecha de valoración = la más antigua de las usadas. Consumido por la tarjeta RF-10. |
| `GET/POST` | `/portfolios/{id}/transactions?type=&from=&to=&securityId=&page=&size=` · `PUT/DELETE /transactions/{id}` | Operaciones (listado filtrable por **tipo, rango de fechas e instrumento**, **paginado** de extremo a extremo — filtro, orden y `LIMIT/OFFSET` en SQL, `page`/`size` por defecto `0`/`25` — + alta/edición manual). Respuesta de `GET`: `{content: InvestmentTransactionView[], page, size, totalElements, totalPages}`. |
| `POST` | `/portfolios/{id}/import` | Import de Flex Query (multipart, como `/api/imports`); devuelve resumen filas ok/duplicadas/errores/*warnings*. |
| `GET` | `/portfolios/{id}/performance` | TWR/XIRR por posición y total. |
| `GET` | `/portfolios/{id}/income` | Dividendos/intereses/comisiones agregados. |
| `GET/POST` | `/securities` · `PUT/DELETE /securities/{id}` | Catálogo de instrumentos (alta automática en import). `DELETE` solo sin operaciones (guarda RN-5 → 409). |
| `GET` | `/portfolios/{id}/import-history?page=&size=` | Historial de imports Flex (RF-11), paginado (view CQRS), más reciente primero. Defaults `page=0`/`size=25`. |

## 7. UI/UX (diseño)

El menú lateral se organiza en **tres grupos** (`app.html`): **Finanzas Personales** (Panel general, Movimientos, Presupuestos, Cuentas, Categorías), **Inversión** (Panel general, Operaciones) y **Administración** (vacío por ahora, reservado para futuras pantallas). Inversión son **dos páginas lazy** en vez de una:

- `pages/investments-dashboard` (ruta `/investments/dashboard`, "Panel general"): KPIs, gráficos y tabla de posiciones — ver detalle más abajo.
- `pages/investments-operations` (ruta `/investments/operations`, "Operaciones"): pestañas Operaciones/Dividendos — ver detalle más abajo.
- `/investments` (sin sufijo) redirige a `/investments/dashboard` por compatibilidad con enlaces antiguos.

Ambas páginas comparten la **cartera seleccionada** vía `InvestmentContextService` (`providedIn: 'root'`, expone `portfolioId$` para que cada página se recargue sola al cambiar de cartera, incluso si el cambio lo dispara la otra página o el alta de una cartera nueva) y la **barra de herramientas** `components/investment-toolbar.ts` (selector/alta de cartera + diálogos de alta manual e import Flex), que ambas incluyen para no duplicar ese bloque. Los gráficos usan Chart.js directamente y se integran con `ThemeService` (colores/rejilla y redibujado al cambiar de tema), como el dashboard doméstico.

**Cabecera de KPIs (tarjetas):**
- Valor total de la cartera en su divisa base, con la **fecha de valoración** visible (RN-6: última cotización disponible).
- Capital aportado neto (Σ aportaciones − Σ retiradas).
- P&L latente (€ y % — el % sobre el coste de adquisición capitalizado, RN-3).
- Efectivo disponible (por divisa).
- Dividendos cobrados en el año en curso — en **bruto** (antes de retención en origen), con el neto en el tooltip/detalle (criterio Portfolio Performance).
- TWR y XIRR del total (desde F3).

**Gráficos:**
1. **Evolución: valor vs capital aportado** (línea) — la serie de aportado es exacta y escalonada (fechas de `DEPOSIT`/`WITHDRAWAL`); la de valor solo tiene puntos en fechas con cotización (imports). Al llegar la API de precios (F4) la misma vista gana la curva diaria sin rediseño.
2. **Asignación de la cartera** (donut) — peso % de cada posición, con el efectivo como una porción más.
3. **Dividendos por periodo** (barras) — importes en **bruto**, con el neto (tras retención) en el tooltip; vista por defecto: **mensual del año seleccionado**, apiladas por instrumento, con selector de año (y opción "todo" anual).
4. **P&L por posición** (barras horizontales divergentes) — ganancia/pérdida latente en € por instrumento, ordenadas de mejor a peor, verde/rojo.
5. **Rentabilidad por posición** (barras horizontales, %) — TWR/XIRR por instrumento (desde F3).

**Tabla de posiciones**: instrumento, títulos, coste medio, precio, valor, P&L (€ y %), peso. Las cantidades negativas (falta histórico anterior, RN-4) se marcan en rojo. Según el sistema de diseño, cada instrumento lleva un **cuadrado de ticker** de 30px (radio 6px, iniciales en mono 10px sobre el soft de su color, alineado con la paleta del donut de asignación) y las tarjetas-tabla anchas tienen `overflow-x: auto`.

**Pestañas** (página Operaciones): operaciones (listado filtrable + alta manual, **paginado** con el componente reutilizable `app-pagination` — 5/10/25/50/100 por página, anterior/siguiente, rango "226–240 de 240" — que también usa Movimientos), dividendos (cobros por año/instrumento, tabla agregada sin paginar: una fila por instrumento) e **importaciones** (RF-11, F3.5: historial de imports Flex, última pestaña). Se presentan como **control segmentado** (contenedor `--surface-2` con borde, segmento activo `--surface` + sombra, texto mono 12px). Las tarjetas KPI (página Panel general) siguen el patrón del sistema: label mono 11px uppercase `--text-faint`, cifra mono 22px/600. Cambiar cualquier filtro de la pestaña Operaciones vuelve a la primera página.

**Pestaña Importaciones** (RF-11): tabla paginada (mismo `app-pagination`) con fecha/hora, fichero, periodo cubierto (`fromDate`–`toDate`, o solo `toDate` si `fromDate` es nulo), importadas, duplicadas y el nº de errores/*warnings*; fila con botón "Ver detalle" (solo si hay algo que mostrar) que despliega una fila expandida reutilizando el mismo bloque visual `<ul class="errors">`/`<ul class="warnings">` de `flex-import-dialog.ts` (duplicado, no extraído a componente compartido — solo 2 usos). Carga **perezosa**: se pide al backend la primera vez que se activa la pestaña, no en cada cambio de cartera; `FlexImportDialog` emite su evento `done` en **cualquier** resultado del import (también con `imported === 0`, a diferencia de antes) para que un reimport "todo duplicado" también quede visible sin recargar la página — Operaciones/Dividendos, que ya escuchaban ese evento, simplemente refrescan con los mismos datos.

**Botón Importar Flex**: reutiliza el patrón del diálogo de import existente (`components/import-dialog.ts`) adaptado al Flex; vive en la barra de herramientas compartida, así que está disponible desde ambas páginas. Si el resultado trae errores o *warnings*, el propio diálogo muestra un enlace **"Ver detalle en Importaciones →"**: cierra el diálogo y navega a Operaciones con la pestaña Importaciones ya activa (deep link `?tab=importaciones`, consumido una vez y limpiado de la URL) — es opcional, no una redirección forzosa: si el usuario no lo pulsa, el diálogo simplemente se cierra con "Cerrar" y se queda donde estaba (Panel general u Operaciones), igual que antes.

**Carga de los gráficos**: en Panel general, los 4 canvas principales viven dentro de `@if (summary)`; en Operaciones, el de dividendos vive dentro de `@if (activeTab === 'dividendos' && income && incomeRows.length > 0)`. En ambas páginas, `renderCharts()` (que destruye y recrea los `Chart` existentes) se difiere con `scheduleRenderCharts()` hasta comprobar que el DOM tiene realmente montados y con tamaño (`getBoundingClientRect().width > 0`) todos los canvas que deberían existir según el estado actual, reintentando por `requestAnimationFrame` (máx. 30 intentos) si no. Ni `setTimeout(0)` ni un número fijo de frames bastan: bajo carga, Chart.js puede medir el contenedor antes de que el navegador calcule su layout y quedarse con el tamaño por defecto del `<canvas>` (300×150, sin pintar nada). En Panel general las llamadas se coalescen (un solo render por ventana) porque `load()` dispara en paralelo el `forkJoin` principal; en Operaciones, la suscripción a `portfolioId$` dispara `loadTransactions()` y `loadIncome()` en paralelo y cada una programa su propio render, coalescido igual.

**Tarjeta de patrimonio en el dashboard doméstico**: única presencia de la inversión fuera de su página — una tarjeta informativa con el patrimonio **agregado de todas las carteras** (y fecha de valoración, la más antigua de las usadas), desglosando el valor por cartera cuando hay más de una, leyendo `GET /api/investments/summary`. No mezcla agregados: los ingresos/gastos/saldos domésticos no incorporan nada de inversión (RN-1 intacta a nivel de datos). Degradación (RF-10): se **oculta** si no hay carteras o no hay valor que mostrar; ante error de la API muestra **"—"** sin tumbar el dashboard doméstico. Al implementarla se actualizará también el PRD Dashboard.

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Venta sin posición suficiente (alta/edición manual) | `400 ValidationException` de dominio (RN-4). |
| Venta sin posición suficiente (import) | La fila se importa y se reporta como *warning* en el resumen (RN-4). |
| Operación que viola el convenio de signos de su tipo (§3: p. ej. `BUY` con `amount` ≥ 0 o `quantity` ≤ 0) | `400 ValidationException` de dominio. |
| Eliminar cartera o instrumento con operaciones | `409 ConflictException`. |
| Import: fila ilegible o instrumento sin ISIN/símbolo | Fila reportada como error, el resto se importa (tolerante, como el import bancario). |
| Import: operación duplicada (`external_id`) | Se omite y se reporta como duplicada. |
| Import: la divisa base de la cuenta del informe (`AccountInformation.currency`) no coincide con `base_currency` de la cartera | **Import rechazado entero** con error explicativo antes de procesar filas — los snapshots `fxRateToBase` apuntarían a otra base y corromperían RN-7a. |

## 9. Casos límite y notas

- **Conversiones de divisa (`FX_TRADE`)**: concepto de dominio genérico e independiente del broker — pierna saliente + pierna entrante + coste, sin flujo externo. En el Flex de IBKR llegan como órdenes `assetCategory="CASH"` (símbolo del par, p. ej. `EUR.USD`; los signos de `quantity`/`proceeds` identifican cada pierna); esa traducción es responsabilidad exclusiva del ACL `FlexReportParser`. Incluye las micro-conversiones automáticas de IBKR (residuos de pocos céntimos), que se importan igual. Un futuro broker que reporte la conversión de otra forma (p. ej. dos líneas débito/crédito) la traducirá al mismo `FX_TRADE` en su propio parser. El P&L por efectivo en divisa no se calcula aparte: emerge al valorar el efectivo al tipo del día (RN-7b).
- **Comisión/retención en divisa distinta del apunte**: `ibCommissionCurrency` puede diferir de `currency` — ocurre en todas las conversiones FX (apunte en la divisa de proceeds, comisión en EUR). El parser rellena `fee_currency`/`tax_currency` solo cuando difieren de la divisa del apunte; RN-2 descuenta cada importe del saldo de su divisa.
- **Instrumento cotizado en varias divisas / mercados (cross-listing)**: la identidad de `security` es **ISIN + divisa de cotización** (única por par). Un valor cross-listed (mismo ISIN en dos mercados) aparecería como **dos securities** y la posición se mostraría partida en dos filas; no ocurre en la cartera actual (ningún ISIN repetido en dos divisas). Caso límite conocido, sin maquinaria de consolidación en v1. Matiz importante: la **divisa no distingue mercados de la misma moneda** (Euronext París y Ámsterdam cotizan ambos en EUR) — para eso está el `exchange` (`listingExchange` del Flex) y el `figi` (único por cotización), que se capturan como metadatos del `security` desde v1 aunque no se usen hasta la API de precios. Cuando llegue esa API (backlog F4/F5) el `exchange`/`figi` será la llave de consulta del precio (como en Portfolio Performance, que pide el mercado al registrar por ISIN) y se **revisará si la identidad debe pasar a ISIN + exchange** para separar cross-listings de la misma divisa.
- **Splits**: se modelan como **delta de cantidad a coste 0** (RN-3), no como ratio. En el Flex llegan en *Corporate Actions* con `type="FS"` (forward split) / `type="RS"` (reverse split) y `quantity` = delta de títulos (p. ej. el 10:1 de NVDA: `quantity="18"`, de 2 a 20 títulos); el ratio "10 FOR 1" solo vive como texto libre en `description` y **no** se parsea. La fila `levelOfDetail="SUMMARY"` (con `accountId="-"`) se ignora; el parser consume solo `levelOfDetail="DETAIL"` (igual que en *Trades*) y usa su `transactionID` como `external_id`. Tipos de acción corporativa distintos de FS/RS → fila reportada como error, como cualquier fila no soportada. Ajustan cantidad y coste medio sin generar flujo de caja.
- **Dividendo con retención en origen**: el Flex los trae como apuntes separados (dividendo + withholding tax); se importan como `DIVIDEND` + `TAX` vinculados al mismo instrumento y fecha.
- **Ventas parciales**: P&L realizado por coste promedio (capitalizado, RN-3) en el momento de la venta.
- **Cartera sin cotizaciones recientes**: la valoración queda "a fecha de último import"; la UI muestra la fecha de valoración.
- **Apunte manual + import del mismo apunte**: una operación registrada a mano no tiene `external_id`, por lo que un import posterior que contenga esa misma operación la duplicará (RN-10). Convención de uso: en carteras alimentadas por Flex, no registrar a mano operaciones que vayan a llegar en el informe; el alta manual es para lo que el Flex no cubre.
- **Borrar un apunte importado y reimportar lo resucita**: al eliminar la fila desaparece su `external_id` de la BD, así que el siguiente import del mismo periodo la vuelve a crear. Comportamiento esperado en v1 (una "lista de exclusión" queda como mejora futura); si algo del Flex no debe contar, la vía es no incluirlo en el informe, no borrarlo en la app.
- **Configuración validada del Flex Query** (Activity Flex Query, formato **XML**, fechas ISO `yyyy-MM-dd`, verificada contra un informe real de la cuenta el 2026-07-02):
  - *Account Information*: solo `accountId` y `currency` (sin datos personales). `currency` es la **divisa base de la cuenta**: el import la valida contra `base_currency` de la cartera antes de procesar filas (§8).
  - *Trades*: nivel **Orders** únicamente (las filas `SYMBOL_SUMMARY`/`ASSET_SUMMARY`, si aparecen, se ignoran filtrando `levelOfDetail="ORDER"`).
  - *Cash Transactions*: nivel **Detail** (tipos Dividends, Withholding Tax, Deposits/Withdrawals; incluir Broker Interest si la cuenta genera intereses).
  - *Corporate Actions*: nivel **Detail** únicamente (la fila `levelOfDetail="SUMMARY"` con `accountId="-"` duplica el apunte y se ignora); solo `type` FS/RS (split directo/inverso), el resto → error de fila.
  - *Transaction Taxes* (FTT): cada tasa se importa como **fila propia de tipo `TRADE_TAX`** (nunca `TAX`: si compras y cobras dividendo del mismo valor el mismo día, la vista de rentas no debe restar la FTT como si fuera retención). El parser consume **solo** `TransactionTax` con `levelOfDetail="ORDER_SUMMARY"` e **ignora** `TransactionTaxDetail` (`ORDER_DETAIL`). Motivo: la FTT cambia de forma entre ejercicios — 2024 trae solo `TransactionTax`; 2025 trae `TransactionTax` (`ORDER_SUMMARY`) **más** `TransactionTaxDetail` (`ORDER_DETAIL`) duplicando la misma tasa con el mismo `tradeId` (p. ej. −0.203 GBP en ambos niveles). `ORDER_SUMMARY` es el único nivel presente en **ambos** años, así que consumirlo da un parser uniforme sin ramas por ejercicio y sin doble conteo. Nota: no hay una regla global "siempre summary" o "siempre detail" — cada sección fija su nivel (Trades→`ORDER`, Corporate Actions→`DETAIL`, FTT→`ORDER_SUMMARY`). **Decisión provisional del formato actual**, revisable al refinar la configuración del Flex Query cuando la app tenga su primera versión.
  - *Open Positions* (`markPrice` → cotización con `quote_date` = `toDate` del `FlexStatement`; fuente de cotizaciones en v1; `listingExchange`/`figi` → metadatos del `security`), *Securities (Financial Instrument Information)* (una fila `SecurityInfo` por isin+divisa con el `description` **canónico** del instrumento — p. ej. "ASML HOLDING NV" —, indexada por el parser y usada como nombre del `security` en cualquier tipo de fila; el `description` de la propia fila queda solo como *fallback* si el isin+divisa no aparece en esta sección), *Corporate Actions*, *Transaction Taxes* (FTT itemizada; en la fila de la orden `taxes` viene a 0; solo nivel `ORDER_SUMMARY`, ver detalle más abajo) y *Conversion Rates* (el parser filtra y persiste solo los pares con divisas presentes en la cartera, **normalizados a una sola dirección** divisa→EUR — IBKR exporta ambas direcciones de muchos pares irrelevantes; la inversa se obtiene aritméticamente. Volumen resultante: ~365 filas/año por divisa extranjera en cartera).
  - Secciones vacías o no marcadas (`Transfers`, `ComplexPositions`, `FxPositions`…) se ignoran.
- **Identificadores para la idempotencia** (`external_id`): a nivel ORDER `tradeID`/`transactionID` vienen vacíos → usar **`ibOrderID`** en operaciones, **`transactionID`** en apuntes de efectivo y en acciones corporativas, y **`tradeId`** en `TransactionTax`. Como son secuencias de numeración independientes de IBKR (podrían colisionar entre sí), el `external_id` se **prefija por origen**: `ORD-`/`CT-`/`FTT-`/`CA-` (RN-10). El vínculo de una FTT con su orden se resuelve por instrumento+fecha (su `tradeId` apunta al nivel ejecución, que no se importa).
- Un informe Flex cubre como máximo 365 días: la carga inicial del histórico se hace con un informe por año, importados en orden; la idempotencia hace inocuos los solapamientos. Si falta histórico anterior (import parcial), las ventas sin posición entran con *warning* y la posición puede quedar negativa hasta importar el año que falta (RN-4); nada queda a medias ni bloqueado.

## 10. Backlog / mejoras futuras

- **Flex Web Service**: descarga automática del informe con token IBKR (mismo puerto de entrada que el import manual).
- **API externa de cotizaciones** (híbrido): adaptador de `PriceProviderPort` (Yahoo Finance u otro) + botón de refresco. Usará `exchange`/`figi` del `security` como llave de consulta; revisar entonces si la identidad pasa a ISIN + exchange (cross-listings de misma divisa, §9).
- Benchmarks (comparar TWR contra un índice).
- Clasificación de activos por dimensiones (región, sector) al estilo Portfolio Performance.
- Informe fiscal de plusvalías.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Método de coste | Coste promedio. | FIFO sería el fiscalmente correcto en España; evaluar al abordar el informe fiscal. |
| Serie de valoración dispersa | TWR/XIRR sobre las cotizaciones disponibles (fechas de import). | Se refina solo al integrar la API de precios. |
| ~~Formato Flex~~ | **Resuelto (2026-07-02): XML.** Atributos con nombre estable e independientes de las columnas marcadas; validado contra informes reales de la cuenta (ver §9). | — |
| Niveles de detalle del Flex (FTT) | El parser consume el mínimo común denominador entre ejercicios (FTT→`ORDER_SUMMARY`, presente en 2024 y 2025). | Provisional del formato actual: revisar la configuración del Flex Query cuando la app tenga su primera versión para exportar un único nivel coherente y no depender de descartes en el parser. |
| ~~Reversas de IBKR en `Cash Transactions` violan el convenio de signos §3~~ | **Resuelto (2026-07-21).** IBKR puede emitir, para un mismo `actionID`, un apunte `Dividends`/`Withholding Tax` original, una **reversa** con el signo invertido y un re-book idéntico al original. `InvestmentTransactionType.DIVIDEND`/`INTEREST`/`FEE`/`TAX` pasan de `AmountRule.POSITIVE`/`NEGATIVE` a `NON_ZERO` (sigue rechazando importe cero; admite el signo invertido de una reversa) — `TRADE_TAX`/`DEPOSIT`/`WITHDRAWAL` no cambian (no son *Cash Transactions* sujetas a reversa, o su tipo ya deriva del signo). `IncomeCalculator` (retenciones/comisiones, RF-7) se corrigió a la vez: sumaba `.abs()` fila a fila, lo que habría *duplicado* una reversa en vez de cancelarla — ahora suma los importes con signo y niega solo el resultado final (§3). | — |
| ~~Nombre del instrumento tomado del `description` de la fila~~ | **Resuelto (2026-07-24).** El ACL usaba `attr(row, "description")` como nombre del `security` para cualquier fila, incluidas `CashTransaction`: ahí `description` es la línea del dividendo/retención ("ASML(...) CASH DIVIDEND EUR 1.75 PER SHARE...", distinta incluso entre el dividendo y su propia retención), no el nombre del emisor — y como `Security.refreshMetadata` sobrescribe el nombre en cualquier reimport con valor no vacío, el `security` quedaba con ese texto en vez de "ASML HOLDING NV". `FlexReportParser` indexa ahora la sección `SecuritiesInfo` (`isin+currency → description`, una entrada estable por instrumento) y la usa como nombre siempre que exista, con el `description` de la fila como *fallback*. | — |

## 12. Fases de implementación

Desarrollo con **TDD obligatorio** (ver `CLAUDE.md`): cada hito se construye en ciclos red-green-refactor y se cierra con **un commit** (tests en verde + PRD actualizado). Un hito no empieza hasta que el anterior está commiteado.

### F1 — Modelo + import Flex + posiciones/valoración multidivisa

| Hito | Contenido | Tests |
|---|---|---|
| H1.1 | Value objects del contexto: `CurrencyMoney`, `PortfolioId`, `SecurityId`, `Quantity` (**decimal** — fracciones de acción y residuos FX; escalas y redondeo de §3). | Unitarios de dominio. |
| H1.2 | Agregados `Security` (con metadatos `exchange`/`figi`, §9) + `PriceQuote` + `ExchangeRate` (invariantes: divisa ISO, identidad ISIN+divisa, unicidad de cotización y de tipo de cambio por fecha, upsert) + servicio de conversión de dominio (último tipo ≤ fecha, **tipos cruzados vía pivote EUR**, RN-7). | Unitarios de dominio. |
| H1.3 | Agregados `Portfolio` e `InvestmentTransaction` (tipos de operación, invariantes del convenio de signos por tipo §3, `external_id`). | Unitarios de dominio. |
| H1.4 | Servicio de dominio `PositionCalculator`: posiciones, coste medio con **coste de compra capitalizado** (importe + comisión + `TRADE_TAX`, RN-3), P&L latente/realizado, efectivo por divisa (RN-2/RN-3/RN-4, venta sin posición). | Unitarios de dominio. |
| H1.5 | Puertos de salida + `PortfolioService`/`SecurityService` (casos de uso CRUD, guardas de borrado RN-5). | Aplicación con puertos mockeados. |
| H1.6 | Migración `V7__investments.sql` (crea el **esquema `investments`** + **todas** las tablas §3, incluida `exchange_rate`) + entidades/mappers/adaptadores JPA (`@Table(schema = "investments")`). | `@DataJpaTest` (Testcontainers): round-trip mappers, unicidades, esquema separado. |
| H1.7 | Read-side CQRS: `InvestmentQueryPort`, `PositionView`, `PortfolioSummaryView`, `ValuationHistoryView` (serie valor vs aportado) y resumen global multi-cartera + query adapter (valoración con última cotización, RN-6, **convertida a divisa base** con el doble mecanismo RN-7). | `@DataJpaTest` del adapter. |
| H1.8 | Web: `PortfolioController`/`SecurityController` + DTOs; endpoints CRUD (incl. `DELETE /securities/{id}` con guarda RN-5), `GET /positions`, `GET /summary` (por cartera y global) y `GET /valuation-history`; el contexto entra en ArchUnit. | `@WebMvcTest` + `ArchitectureTest`. |
| H1.9 | `FlexReportParser` (ACL): secciones *Trades* (órdenes de valores y conversiones `FX_TRADE`), *Corporate Actions* (`SPLIT` como delta de cantidad, solo `DETAIL`, FS/RS), *Transaction Taxes* (filas `TRADE_TAX`, solo `ORDER_SUMMARY`, ignora `TransactionTaxDetail`), *Open Positions* y ***Cash Transactions* completa** (depósitos, retiradas, dividendos y retenciones — el par dividendo+retención llega como apuntes separados, §9) y ***Conversion Rates*** (solo pares con divisas de la cartera, normalizados divisa→EUR, §9); fixtures de informes Flex reales. | Unitarios del parser con fixtures. |
| H1.10 | Caso de uso `ImportFlexReport`: validación divisa base cuenta↔cartera (§8), idempotencia por `external_id` (RF-4), alta automática de `Security`, upsert de cotizaciones y tipos de cambio (RN-9), errores y *warnings* por fila (venta sin posición, RN-4); endpoint `POST /portfolios/{id}/import`. Con esto el efectivo (RN-2) y el capital aportado son exactos desde el primer import. | Aplicación mockeada + `@WebMvcTest`. |
| H1.11 | Frontend: página `pages/investments` (KPIs, donut de asignación, evolución valor vs aportado, P&L por posición, tabla de posiciones), diálogo de import Flex, ruta lazy y entrada en el menú. | Build + revisión manual. |
| H1.13 | Frontend: menú lateral reorganizado en 3 grupos (Finanzas Personales / Inversión / Administración) y `pages/investments` dividida en dos páginas — `investments-dashboard` ("Panel general": KPIs, gráficos, posiciones) e `investments-operations` ("Operaciones": pestañas operaciones/dividendos) —, con `InvestmentContextService` y `components/investment-toolbar.ts` compartidos entre ambas; `/investments` redirige a `/investments/dashboard`. | Vitest por página/servicio/componente + `e2e/investments-dashboard.spec.ts`/`investments-operations.spec.ts`. |
| H1.12 | Tarjeta de patrimonio en el dashboard doméstico (valor total + fecha de valoración, leyendo la API de `investments`); actualización del PRD Dashboard. | Build + revisión manual. |

### F2 — Vistas de rentas y alta manual

El parseo e importación de dividendos/retenciones ya quedó en F1 (H1.9); esta fase añade su lectura y la operativa manual.

| Hito | Contenido | Tests |
|---|---|---|
| H2.1 | Dominio: agregados de rentas por periodo/instrumento (RF-7) sobre los apuntes ya importados. | Unitarios de dominio. |
| H2.2 | `IncomeView` + query adapter + endpoint `GET /portfolios/{id}/income` (dividendos en bruto y neto —tras restar la retención `TAX` vinculada por instrumento+fecha; los `TRADE_TAX` **nunca** entran en rentas—; importes convertidos con el snapshot `fx_rate_to_base`, RN-7a). | `@DataJpaTest` + `@WebMvcTest`. |
| H2.3 | Alta/edición manual de operaciones (RF-2): casos de uso + endpoints + formulario en la UI. | Aplicación + `@WebMvcTest`. |
| H2.4 | Frontend: pestañas de operaciones y dividendos + gráfico de dividendos (mensual apilado por instrumento, selector de año) y KPI de dividendos del año. | Build + revisión manual. |

### F3 — Rentabilidad TWR/XIRR

La multidivisa ya no es una fase: `exchange_rate`, el parser de *Conversion Rates* y la conversión a divisa base quedaron absorbidos por F1 (H1.2, H1.6, H1.7, H1.9), y las vistas de rentas de F2 convierten con los snapshots (RN-7a).

| Hito | Contenido | Tests |
|---|---|---|
| H3.1 | `PerformanceCalculator` — XIRR: Newton-Raphson con fallback de bisección sobre flujos externos + valor actual (RN-8). | Unitarios de dominio (casos conocidos, convergencia, extremos). |
| H3.2 | `PerformanceCalculator` — TWR: encadenado por subperiodos delimitados por `DEPOSIT`/`WITHDRAWAL` sobre la serie de valoraciones. | Unitarios de dominio. |
| H3.3 | `PerformanceView` + query adapter + endpoint `GET /portfolios/{id}/performance` (por posición y total). | `@DataJpaTest` + `@WebMvcTest`. |
| H3.4 | Frontend: TWR/XIRR en el resumen y por posición; gráfico de evolución del valor (Chart.js). | Build + revisión manual. |

### F3.5 — Historial de imports

Precursor obligatorio de F4 (H4.2 Flex Web Service): sin este historial, un import disparado por un proceso desatendido no dejaría rastro de qué falló ni de qué avisó. Plan detallado en `docs/plan/historial-imports.md`.

| Hito | Contenido | Tests |
|---|---|---|
| H-imp.1 | Dominio: agregado `ImportRecord` + VO `ImportRowIssue` + puerto `ImportRecordRepository` (solo escritura desde el dominio). | Unitarios de dominio. |
| H-imp.2 | Migración `V8__import_record.sql` (`errors`/`warnings` como `jsonb`, sin tabla hija) + entidad/mapper/adaptador JPA. | `@DataJpaTest` (Testcontainers): round-trip JSON, `to_date NOT NULL`. |
| H-imp.3 | Enganche en `FlexImportService.importReport`: persiste un `ImportRecord` al final de cada intento, dentro de la misma transacción, incluidos los imports "vacíos" (todo duplicado). | Aplicación mockeada. |
| H-imp.4 | Read-side CQRS: `ImportRecordQueryPort`/`ImportRecordView` + `ImportRecordQueryAdapter` (paginado, orden por `importedAt` desc) + endpoint `GET /portfolios/{id}/import-history`. | `@DataJpaTest` + `@WebMvcTest`. |
| H-imp.5 | Frontend: modelos (`ImportRecordView`) + `api.service.ts` (`getImportHistory`). | Vitest. |
| H-imp.6 | Frontend: cuarta pestaña "Importaciones" en `investments-operations` (tabla paginada con `app-pagination` + detalle expandible de errores/warnings). | Vitest + Playwright. |
| H-imp.7 | Frontend: enlace "Ver detalle en Importaciones →" en `FlexImportDialog` cuando el resultado trae errores/*warnings* (cierra el diálogo y navega a Operaciones con la pestaña Importaciones activa vía deep link `?tab=importaciones`, consumido y limpiado en `investments-operations.ts`). | Vitest (diálogo + deep link) + Playwright (flujo cruzado Panel general → Operaciones). |

### F4 — Automatización (backlog)

| Hito | Contenido |
|---|---|
| H4.1 | Adaptador externo de `PriceProviderPort` (API de cotizaciones) + acción de refresco de precios. |
| H4.2 | Flex Web Service: descarga del informe con token IBKR (mismo caso de uso `ImportFlexReport`). |
| H4.3 | Modo híbrido: precios del Flex al importar + refresco bajo demanda/programado. Idea de especificación pendiente de validar: job automático ~10 min tras el cierre de mercado (requiere horario por mercado, no uno único global — la cartera mezcla LSE/NASDAQ/SBF/IBIS/BVME/AEB — y verificar empíricamente cuánto tarda el EOD del proveedor en free tier en estar disponible tras el cierre real). |

## 13. Referencias de código

Implementado hasta H1.13 (backend en `backend/src/main/java/com/xroig/finance/investments/`, frontend en `frontend/src/app/`):

- **Dominio** (`domain/`, puro): agregados `Portfolio`, `Security`, `InvestmentTransaction` (builder + invariantes de signos por tipo vía `InvestmentTransactionType`); values `CurrencyMoney`, `Quantity`, `PriceQuote`, `ExchangeRate`, ids `PortfolioId`/`SecurityId`/`InvestmentTransactionId`, helper `IsoCurrency`; servicios `PositionCalculator` (→ `PortfolioPositions`, `Position`, `PositionWarning`), `PerformanceCalculator` (H3.1–H3.2, RN-8: XIRR anualizada por Newton-Raphson con fallback de bisección sobre `Cashflow` fechados — vacío sin solución: <2 flujos, mismo signo, mismo día —, y `portfolioXirr` que arma los flujos externos `DEPOSIT`/`WITHDRAWAL` con signo inversor + valor actual; `portfolioTwr` como TWR **acumulada** (sin anualizar) encadenando subperiodos de la serie de valoraciones `ValuationPoint` — el valor de un punto ya incluye el flujo de su fecha, así que cada subperiodo es `(V_i − F_i) / V_(i−1)` con `F_i` = flujos en `(d_(i−1), d_i]`; vacío con <2 puntos o arranque de subperiodo no positivo; `FX_TRADE` y las operaciones de instrumento no son flujo externo ni delimitan), `IncomeCalculator` (H2.1, RF-7 → `IncomeStatement` con `InstrumentIncome` por instrumento y mes: bruto, retención `TAX` vinculada por instrumento y neto — `TRADE_TAX` excluido, §9 — más comisiones y retenciones pagadas por mes; conversión RN-7a snapshot → tabla → 1:1) y `CurrencyConverter` (conversión a fecha RN-7b vía pivote EUR + `fixedToBase` compartido para importes fijados: snapshot RN-7a → tabla → 1:1, usado por rentas, rentabilidad y el read-side; la variante con *warnings* vive en `PositionCalculator`); puertos de salida `PortfolioRepository`, `SecurityRepository`, `InvestmentTransactionRepository`, `PriceQuoteRepository`/`ExchangeRateRepository` (contrato upsert RN-9) y `PriceProviderPort` (sin adaptador, backlog F4).
- **Aplicación** (`application/`): `InvestmentTransactionService` (H2.3, RF-2: crear/editar/eliminar/listar operaciones vía puertos `CreateInvestmentTransaction`/`UpdateInvestmentTransaction`/`DeleteInvestmentTransaction`/`FindInvestmentTransactions` + view `InvestmentTransactionView` con nombre de instrumento; invariantes §3 en el agregado → 400; **RN-4 dura en manual**: venta que supera la posición a su fecha —adquisiciones del mismo día cuentan, tolerancia de precisión, en edición se excluye la propia fila— → `ValidationException`; la edición reconstruye el agregado preservando identidad y `external_id`; listado filtrable por tipo/fechas/instrumento **paginado de extremo a extremo** — `InvestmentTransactionRepository.search` filtra, ordena y pagina en SQL vía `InvestmentTransactionJpaRepository` con `Pageable`; `findByPortfolio` se mantiene aparte, sin paginar, para los cálculos de dominio (RN-4, posiciones) que necesitan siempre el histórico completo —, descendente por fecha, devuelto como `Page<InvestmentTransactionView>` (`shared/domain/Page`, tipo genérico compartido con el feed de Movimientos); `PortfolioService` y `SecurityService` (`@Service`/`@Transactional`) implementando los puertos de entrada de `application/port/` (`CreateX`/`FindX`/`UpdateX`/`DeleteX`); read-side CQRS `InvestmentQueryPort` + views `PositionView`, `PortfolioSummaryView`, `ValuationHistoryView`, `InvestmentsSummaryView` (resumen global en EUR, RF-10), `IncomeView` (H2.2, RF-7: rentas por instrumento y mes —bruto/retenido/neto— más comisiones y retenciones por mes, meses `"YYYY-MM"`, orden por mes y nombre; el adapter delega en `IncomeCalculator`) y `PerformanceView` (H3.3, RN-8: TWR acumulada y XIRR anualizada como **porcentajes escala 2** —null si no calculable—, total y por posición abierta; el total delega en `portfolioTwr` sobre la serie de valoración y `portfolioXirr` con el valor de hoy; por posición, el XIRR usa los **flujos de caja reales del instrumento** (importe+comisión+retención de cada apunte, filas `TRADE_TAX` incluidas) + valor actual, y el TWR encadena la serie cantidad×cotización en fechas de cotización con el efecto de caja negado como flujo — compras/ventas se neutralizan y dividendos y costes cuentan como rendimiento); `FlexImportService` implementando `ImportFlexReport` (H1.10): valida la divisa base cuenta↔cartera antes de tocar filas (§8, rechazo entero), deduplica por `external_id` contra la BD **y dentro del propio fichero** (RN-10), resuelve/da de alta el `Security` por ISIN+divisa refrescando metadatos no identitarios (RN-9, una vez por identidad y fichero), traduce cada `FlexRow` al builder de `InvestmentTransaction` (los invariantes de §3 revalidan cada fila; fila inválida → `FlexRowError` sin abortar el resto), upsert de cotizaciones y tipos de cambio (RN-9) y, al final, recalcula posiciones con `PositionCalculator` para reportar *warnings* (venta sin posición RN-4, tipos de cambio ausentes) en el `FlexImportResult` (ok/duplicadas/errores/*warnings*).
- **Infraestructura** (`infrastructure/persistence/`): migración `V7__investments.sql` (esquema `investments` + 5 tablas); entidades `XJpaEntity` con `@Table(schema = "investments")` (FKs como columnas id planas, sin `@ManyToOne` — los agregados se referencian por id); repositorios Spring Data `XJpaRepository`; mappers `PortfolioJpaMapper`/`SecurityJpaMapper`/`InvestmentTransactionJpaMapper` (divisa propia de fee/tax: columna nula = divisa del apunte); adaptadores `XPersistenceAdapter` (upsert por clave natural en cotizaciones y tipos de cambio); `InvestmentQueryAdapter` (valoración RN-6 con última cotización ≤ hoy, conversión RN-7 dual con degradación 1:1, nada materializado).
- **Infraestructura** (`infrastructure/web/`): `InvestmentTransactionController` (H2.3: `GET/POST /portfolios/{id}/transactions` con filtros `type/from/to/securityId` + `PUT/DELETE /transactions/{id}`, DTO `InvestmentTransactionRequest` con validación estructural mínima); `PortfolioController` (CRUD `/api/investments/portfolios`, `POST .../{id}/import` multipart —campo `file`; límite subido a 10 MB en `application.properties`, los Flex anuales rondan 1,5 MB—, `GET .../positions|summary|valuation-history|income|performance`, `GET /api/investments/summary`) y `SecurityController` (CRUD `/api/investments/securities`) con DTOs `PortfolioRequest`/`PortfolioResponse`/`SecurityRequest`/`SecurityResponse`; las views CQRS se serializan tal cual. Errores vía `shared.web.DomainExceptionHandler` (400/404/409 `problem+json`).
- **Infraestructura** (`infrastructure/flex/`): ACL `FlexReportParser` implementando el puerto `FlexReportReader` (application) → `FlexReport` con `FlexRow`/`FlexInstrument`/`FlexQuote`/`FlexRowError` (application): traduce el Flex XML de IBKR según la configuración validada de §9 (Trades→`ORDER` con `FX_TRADE` por signos, Cash→`DETAIL`, Corporate Actions→`DETAIL` FS/RS, FTT→`ORDER_SUMMARY`, Open Positions→cotizaciones a `toDate`, Conversion Rates filtrados divisa→EUR), `external_id` prefijado `ORD-/CT-/FTT-/CA-` (RN-10) y errores por fila sin abortar el resto (§8). Fixture `flex-sample.xml` + smoke test condicional contra los informes reales.
- **Historial de imports** (H-imp.1–H-imp.4, RF-11): dominio `ImportRecord` (record + factory `of()`, valida `portfolioId`/`importedAt`/`toDate` no nulos) y VO `ImportRowIssue` (`domain/`, duplica la forma de `FlexRowError` para no depender hacia arriba de `application/`) + puerto `ImportRecordRepository` (solo `save`); `FlexImportService.importReport` lo persiste al final de cada intento, dentro de la misma transacción, incluidos los imports sin filas nuevas; migración `V8__import_record.sql` (`errors`/`warnings` como `jsonb`, sin tabla hija) con `ImportRecordJpaEntity`/`ImportRecordJpaMapper` (serializa con el `ObjectMapper` de Jackson 3 — `tools.jackson.databind`, el motor real del proyecto vía `spring-boot-starter-jackson`, no el clásico `com.fasterxml.jackson.databind`) y `ImportRecordPersistenceAdapter`; lectura CQRS `ImportRecordQueryPort`/`ImportRecordView` (reutiliza `FlexRowError` porque esta vista sí vive en `application/`) + `ImportRecordQueryAdapter` (lee `ImportRecordJpaRepository` directamente, sin pasar por el agregado de dominio — es un listado, no un cálculo) y endpoint `GET /portfolios/{id}/import-history` en `PortfolioController`.

- **Frontend** (H2.3): diálogo `components/investment-transaction-dialog.ts` (alta/edición manual con campos condicionales por tipo §3, pistas del convenio de signos y errores 400 del dominio con detalle; método `edit(tx)` para abrirlo desde el listado de H2.4), botón "Nueva operación" en la página de inversión; modelos `InvestmentTransactionView`/`InvestmentTransactionRequest`/`InvestmentSecurity` y llamadas CRUD en `api.service.ts`.
- **Frontend** (H1.11–H1.13, H2.3–H2.4, H3.4): rentabilidad — tarjetas KPI «Rentabilidad (TWR)» (acumulada del periodo) y «Rentabilidad (XIRR)» (anualizada) con «—» si no calculable, columnas TWR/XIRR por posición en la tabla y gráfico de barras horizontales «Rentabilidad por posición (%)» (XIRR y TWR por instrumento, ordenado por XIRR) junto al de P&L, todo en `pages/investments-dashboard/`; modelo `InvestmentPerformance`/`PositionPerformance` y `getInvestmentPerformance()` en `api.service.ts`. Resto del frontend: tarjeta de **patrimonio invertido** en el dashboard doméstico (RF-10: agregado en EUR + fecha de valoración + desglose con >1 cartera; oculta sin carteras, "—" ante error de API — ver PRD Dashboard RF-8); dos páginas lazy bajo Inversión en el menú lateral (H1.13, §7): `pages/investments-dashboard/` (ruta `/investments/dashboard`, "Panel general") con la cabecera de KPIs (valor total con fecha de valoración o aviso "a coste", aportado neto, P&L latente € y %, efectivo por divisa, dividendos del año en bruto), gráficos Chart.js integrados con `ThemeService` (evolución valor vs aportado —aportado escalonado—, donut de asignación con el efectivo como porción, barras horizontales divergentes de P&L por posición) y tabla de posiciones (cantidades negativas en rojo RN-4, badge "a coste" sin cotización RN-6); y `pages/investments-operations/` (ruta `/investments/operations`, "Operaciones") con las pestañas **Operaciones** — listado filtrable por tipo/fechas/instrumento (etiquetas de tipo compartidas `INVESTMENT_TYPE_LABELS`), columna de origen (manual/importado por `externalId`), editar (reutiliza `investment-transaction-dialog.edit(tx)` vía `InvestmentToolbar.edit(tx)`) y borrar con confirmación; y **Dividendos** — gráfico de barras mensuales apiladas por instrumento (bruto, con el neto en el tooltip) con selector de año y vista «Todo (por año)», tabla de cobros por instrumento (bruto/retenido/neto + total) y totales de comisiones/retenciones pagadas del periodo; los intereses sin instrumento agrupan como «Intereses». Ambas páginas comparten `InvestmentContextService` (`src/app/investment-context.service.ts`: carteras, instrumentos, cartera seleccionada vía `portfolioId$`, alta inline de cartera) y `components/investment-toolbar.ts` (selector/alta de cartera + diálogo `components/flex-import-dialog.ts`, adaptación del patrón `import-dialog.ts`, que sube el XML y muestra el resumen ok/duplicadas/errores/warnings). Modelos en `models.ts` (`Portfolio`, `PositionView`, `PortfolioSummary`, `ValuationPoint`, `InvestmentsSummary`, `FlexImportResult`, `InvestmentTransactionView`, `InvestmentIncome`…) y llamadas en `api.service.ts`.
- **Frontend — historial de imports** (H-imp.5–H-imp.6, RF-11): modelo `ImportRecordView` (`models.ts`) y `getImportHistory()` en `api.service.ts`; cuarta pestaña **Importaciones** en `pages/investments-operations/` (última, tras Dividendos) con tabla paginada (`app-pagination`) y fila expandible de detalle (`toggleHistoryDetail`, reutiliza el markup `<ul class="errors">`/`<ul class="warnings">` de `flex-import-dialog.ts`, duplicado en `investments-operations.scss` en vez de extraído a componente — solo 2 usos). Carga perezosa (`loadImportHistory()` solo al activar la pestaña o cuando ya está activa, `reloadImportHistoryIfActive()`); `FlexImportDialog.done` pasa a emitir siempre (antes solo si `imported > 0`), para que un reimport "todo duplicado" también refresque la pestaña.
- **Frontend — enlace desde el diálogo al historial** (H-imp.7, RF-11): `FlexImportDialog.goToImportHistory()` (`flex-import-dialog.ts`) cierra el diálogo y navega con `Router.navigate(['/investments/operations'], { queryParams: { tab: 'importaciones' } })`; botón visible solo si `result.errors.length > 0 || result.warnings.length > 0`. `InvestmentsOperationsPage.ngOnInit` (`investments-operations.ts`) se suscribe a `ActivatedRoute.queryParamMap`: si `tab === 'importaciones'` activa esa pestaña (`setTab`) y limpia el query param con `router.navigate([], { relativeTo: route, queryParams: {}, replaceUrl: true })` para que no quede fijado en recargas posteriores. Funciona igual llamado desde el Panel general o desde la propia Operaciones porque la cartera es estado compartido (`InvestmentContextService`), no viaja por la URL.

Pendiente: F4 (automatización — refresco de precios y descarga automática del Flex) en backlog.
