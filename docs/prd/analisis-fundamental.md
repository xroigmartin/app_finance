---
dominio: analisis-fundamental
estado: en-diseño
tags: [prd, dominio/analisis-fundamental]
---

# PRD — Análisis fundamental y valoración de compañías

| Campo | Valor |
|---|---|
| Estado | 📐 **Diseño** — sin implementar. Documento acordado antes de escribir código. |
| Versión | 0.1 |
| Última actualización | 2026-08-08 |
| Dominio | Análisis fundamental (`fundamentals`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento del módulo (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

**Relacionado:** [[prd/inversiones]] (catálogo de instrumentos, proveedor de precios y pantalla de posiciones), [[roadmap]]

---

## 1. Propósito

Módulo para **analizar fundamentalmente una compañía cotizada y estimar su valor intrínseco**, de modo que la decisión de comprar (o de seguir manteniendo) se apoye en un número propio y no en el precio de mercado. Responde a tres preguntas encadenadas:

1. **¿Es un buen negocio?** — histórico de 5–15 años: crecimiento, márgenes, generación de caja, retorno sobre el capital, asignación del capital y señales de alarma.
2. **¿Cuánto vale?** — proyección explícita a 5 años bajo hipótesis del usuario y valoración por múltiplos de salida, en tres escenarios (pesimista / base / optimista).
3. **¿A qué precio compro?** — margen de seguridad, potencial de revalorización, retorno anualizado esperado y **precio máximo de compra** para lograr el retorno anual exigido.

Es un **bounded context autocontenido** (`fundamentals`), separado tanto de la economía doméstica como de [[prd/inversiones]]. La relación con Inversiones es de **solo lectura y por identidad** (ISIN/ticker), sin claves foráneas entre esquemas: se analiza una compañía se tenga o no en cartera, y cuando además se tiene, la pantalla de posiciones enriquece la fila con el veredicto de la valoración (RF-12).

**Origen del modelo**: la metodología reproduce el flujo de análisis que el usuario venía haciendo en una hoja de cálculo de terceros (histórico → FCF → retornos sobre capital → valoración por múltiplos → red flags). Las técnicas empleadas son financieramente estándar; la implementación, el vocabulario, la estructura de datos y el diseño de pantallas son propios de esta aplicación. La hoja original no se reproduce ni se versiona en el repositorio (`docs/research/` está en `.gitignore`).

## 2. Objetivos y no-objetivos

**Objetivos**

- Mantener una **watchlist** de compañías analizables, independientes de la cartera.
- Cargar los **estados financieros anuales** de cada compañía mediante una **plantilla canónica propia** (CSV/Excel), rellenable a mano o alimentada desde un proveedor externo a través de un adaptador.
- Calcular el **diagnóstico histórico**: crecimiento y márgenes, FCF y su calidad, ROIC/ROE y tasa de reinversión, asignación del capital, múltiplos históricos y sus medianas.
- Detectar **red flags** con umbrales por defecto del módulo y override opcional por compañía.
- Proyectar 5 ejercicios a partir de hipótesis explícitas del usuario, en **tres escenarios** (pesimista / base / optimista).
- **Valorar por múltiplos de salida** (PER, EV/FCF, EV/EBITDA, EV/EBIT) y derivar precio objetivo, margen de seguridad, potencial, retorno anualizado y precio máximo de compra.
- Guardar **instantáneas** inmutables de un análisis bajo acción explícita del usuario, para poder auditar con el tiempo la calidad del propio criterio.
- Enlazar el veredicto con las posiciones reales de la cartera (RF-12).

**No-objetivos (fuera de alcance de este PRD)**

- **Compañías financieras (bancos, aseguradoras) y REITs.** El modelo se apoya en EBITDA, FCF, capital invertido y *enterprise value*, magnitudes que no son interpretables cuando la deuda es materia prima del negocio o el resultado contable está dominado por revalorizaciones de activos. Ver RN-13 y la deuda técnica de §11.
- Recomendaciones automáticas de compra/venta. El módulo calcula bajo las hipótesis del usuario y muestra el resultado; el juicio es del usuario. No emite señales ni puntúa "compra/no compra".
- Datos trimestrales, estimaciones de consenso, descarga automática desde SEC/EDGAR o API de fundamentales (→ §10).
- Contabilizar nada en la economía doméstica ni en `investments`: el módulo **no** crea movimientos, operaciones ni posiciones.
- Fiscalidad, dimensionado de posición y gestión de cartera (eso es [[prd/inversiones]]).

## 3. Modelo de datos (diseño)

**Esquema PostgreSQL separado `fundamentals`** (misma decisión que `investments`: aislamiento físico del contexto, sin claves foráneas hacia otros esquemas). Migración `V9__fundamentals.sql`; entidades JPA con `@Table(schema = "fundamentals")` y `ddl-auto=validate`.

**Precisión decimal**: importes y magnitudes contables `numeric(19,4)`; porcentajes, ratios y múltiplos `numeric(19,8)`; número de acciones `numeric(19,4)` (admite fracciones tras conversiones). En Java, `BigDecimal` en todo el dominio. El frontend **no calcula, solo formatea**.

**Convenio de signos** — el mismo que usan los proveedores de datos y la hoja original, para que la carga sea copia directa sin traducir signos: **salida de caja = negativo, entrada = positivo**. Amortizaciones, gastos financieros, impuestos, CapEx, recompras y dividendos pagados se introducen en negativo. Las fórmulas de §5 están escritas para ese convenio (por eso suman donde intuitivamente se restaría).

**Escala y divisa**: cada compañía declara su **divisa de reporte** y la **escala** en que se introducen las cifras (unidades / miles / millones). No hay conversión de divisa en ningún punto: el margen de seguridad es un porcentaje y convertir solo introduciría error de tipo de cambio (RN-4).

### `company` — compañía de la watchlist

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `name` | `varchar NOT NULL` | P. ej. "Alphabet Inc." |
| `ticker` | `varchar NOT NULL` | **Único**. Identidad de negocio de la ficha. |
| `isin` | `varchar`, nullable | **Único** cuando está informado (Postgres admite varios NULL). Llave de enlace con `investments.security` (RN-14). |
| `exchange` | `varchar`, nullable | Mercado de cotización. Llave para resolver el símbolo del proveedor de precios, reutilizando `YahooExchangeResolver` de `investments`. |
| `reporting_currency` | `varchar(3) NOT NULL` | Divisa de los estados financieros (ISO 4217). |
| `scale` | `varchar NOT NULL` | `UNITS` / `THOUSANDS` / `MILLIONS`: escala de las cifras de `fiscal_year`. Solo afecta a la presentación y a la coherencia con el precio por acción (RN-4). |
| `business_type` | `varchar NOT NULL` | `NON_FINANCIAL` (único plenamente soportado en v1), `FINANCIAL`, `REIT`. Ver RN-13. |
| `sector` | `varchar`, nullable | Metadato libre. |
| `threshold_roic_min` | `numeric(19,8)`, nullable | Override del umbral de ROIC pobre. Nulo = usar el valor por defecto del módulo (RN-11). |
| `threshold_net_debt_ebitda_max` | `numeric(19,8)`, nullable | Override del umbral de apalancamiento. Nulo = por defecto. |
| `created_at` | `timestamp NOT NULL` | |

### `fiscal_year` — estados financieros de un ejercicio (hechos)

Una fila por (`company_id`, `year`), **única**. Es la materia prima: se importa, se corrige y se reimporta sin tocar las hipótesis del usuario. Modelo **ancho** (una columna por partida canónica) en lugar de clave-valor: el motor de cálculo depende exactamente de este conjunto de partidas, así que añadir una implica cambiar las fórmulas de todos modos — la migración no es un coste extra, y a cambio se gana tipado, validación y consultas legibles.

Todas las columnas de importe son `numeric(19,4)` y **nullable** salvo `sales`: una compañía puede no publicar una partida (p. ej. no tener arrendamientos operativos) y un nulo se trata como 0 en las agregaciones, pero se distingue de un 0 introducido a propósito de cara a los avisos de calidad del dato (§8).

**Identificación**

| Campo | Notas |
|---|---|
| `id` `bigint` PK, `company_id` FK → `company`, `year` `int NOT NULL` | `UNIQUE (company_id, year)` |

**Cuenta de resultados**

| Campo | Notas |
|---|---|
| `sales` | Ventas / ingresos totales. **Obligatorio**: sin ventas no hay márgenes ni ratios. |
| `ebit` | Resultado operativo. |
| `depreciation_amortization` | Amortización del ejercicio (negativa). Se usa para reconstruir EBITDA. |
| `interest_expense` | Gasto financiero (negativo). |
| `interest_income` | Ingreso financiero (positivo). |
| `tax_expense` | Impuesto sobre beneficios (negativo). |
| `net_income` | Beneficio neto atribuible. |
| `minority_interests` | Intereses minoritarios. |
| `diluted_shares` | Acciones diluidas medias del ejercicio. |

**Balance**

| Campo | Notas |
|---|---|
| `cash_and_equivalents` | |
| `marketable_securities` | Inversiones financieras a corto y largo plazo. |
| `short_term_debt` / `long_term_debt` | |
| `operating_leases_current` / `operating_leases_non_current` | Arrendamientos operativos capitalizados. |
| `equity` | Fondos propios totales. |
| `inventories`, `accounts_receivable`, `accounts_payable`, `unearned_revenue` | Componentes del circulante operativo. |

**Flujo de caja**

| Campo | Notas |
|---|---|
| `capex` | Inversión en inmovilizado material (negativa). |
| `intangibles_capex` | Compra/venta neta de intangibles. |
| `ppe_disposals` | Venta de inmovilizado (positiva). |
| `depreciation_cf` | Depreciación reportada en el estado de flujos (positiva). Referencia del CapEx de mantenimiento (RN-6); si falta, se usa `|depreciation_amortization|`. |
| `acquisitions` / `divestitures` | Adquisiciones (negativa) y desinversiones (positiva). |
| `buybacks` | Recompra de acciones propias (negativa). |
| `dividends_paid` | Dividendos pagados (negativa). |
| `debt_issued` / `debt_repaid` | Emisión y amortización de deuda. |
| `stock_based_compensation` | Retribución en acciones. Red flag (RN-11). |
| `share_issuance` | Emisión de acciones. Red flag. |

**Partidas extraordinarias** (solo alimentan las red flags)

| Campo | Notas |
|---|---|
| `asset_writedowns`, `goodwill_impairment`, `merger_restructuring_charges`, `legal_settlements`, `other_unusual_items` | Cargos no recurrentes. |

**Mercado**

| Campo | Notas |
|---|---|
| `market_cap` | Capitalización al cierre del ejercicio, en la escala y divisa de la compañía. Necesaria para los múltiplos históricos y sus medianas (RN-9); sin ella, ese ejercicio no entra en las medianas. |

### `scenario` — juego de hipótesis

Tres filas por compañía (`BEAR` / `BASE` / `BULL`), creadas juntas al dar de alta la ficha. Un escenario es un juego **completo** de hipótesis, no un subconjunto: no hay palancas compartidas entre escenarios (RN-5). La operación "duplicar escenario" (§6) es lo que hace llevadero rellenar tres.

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `company_id` | FK → `company` | `UNIQUE (company_id, kind)` |
| `kind` | `varchar NOT NULL` | `BEAR` / `BASE` / `BULL`. El **base** es el que manda en el veredicto de cabecera y en el enlace con la cartera. |
| `target_per` | `numeric(19,8)` | Múltiplo objetivo de salida — PER. |
| `target_ev_fcf` | `numeric(19,8)` | Múltiplo objetivo — EV/FCF. |
| `target_ev_ebitda` | `numeric(19,8)` | Múltiplo objetivo — EV/EBITDA. |
| `target_ev_ebit` | `numeric(19,8)` | Múltiplo objetivo — EV/EBIT. |
| `required_annual_return` | `numeric(19,8) NOT NULL` | Retorno anual exigido, para el precio máximo de compra (RN-10). |
| `updated_at` | `timestamp NOT NULL` | |

### `scenario_year` — hipótesis año a año

Cinco filas por escenario (`offset` 1..5, siempre 5 ejercicios proyectados — RN-15).

| Campo | Tipo | Notas |
|---|---|---|
| `id` `bigint` PK, `scenario_id` FK, `offset` `int` | | `UNIQUE (scenario_id, offset)` |
| `sales_growth` | `numeric(19,8)` | Crecimiento de ventas del año. |
| `ebit_margin` | `numeric(19,8)` | Margen EBIT objetivo del año. |
| `tax_rate` | `numeric(19,8)` | Tasa impositiva efectiva. |
| `share_change` | `numeric(19,8)` | Variación del nº de acciones (negativa si hay recompra neta). |
| `maintenance_capex_pct` | `numeric(19,8)` | CapEx de mantenimiento como % de ventas. |
| `working_capital_pct` | `numeric(19,8)` | Circulante como % de ventas. |
| `expansion_capex_pct`, `acquisitions_pct`, `buybacks_pct`, `dividends_pct`, `debt_repayment_pct` | `numeric(19,8)` | Asignación de capital proyectada, como % del FCF del año (RN-7). |

### `valuation_snapshot` — instantánea inmutable

Creada **solo** por acción explícita del usuario. Congela a la vez las tres cosas que hacen reproducible un análisis: hipótesis, **datos financieros usados** y precio de la acción del momento (RN-12).

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `bigint` PK | |
| `company_id` | FK → `company` | |
| `taken_at` | `timestamp NOT NULL` | |
| `label` | `varchar`, nullable | Etiqueta libre del usuario ("tras resultados Q2"). |
| `share_price` | `numeric(19,4) NOT NULL` | Precio de la acción en el momento de la instantánea. |
| `target_price_base` | `numeric(19,4)` | Denormalizado: precio objetivo del escenario base al año 5. |
| `margin_of_safety_base` | `numeric(19,8)` | Denormalizado: margen de seguridad base. Ambos existen para poder listar el historial sin abrir el JSON. |
| `payload` | `jsonb NOT NULL` | Volcado completo e inmutable: `fiscal_year` usados, los tres escenarios con sus hipótesis y todos los resultados calculados. Mismo criterio que `investments.import_record.errors` — se lee siempre como bloque, no se consulta por dentro. |

**Nada materializado** (RN-2): ratios, medianas, proyecciones, múltiplos, precios objetivo y red flags se calculan siempre en la capa de lectura a partir de `fiscal_year` + `scenario`. Lo único almacenado son hechos, hipótesis e instantáneas congeladas.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | El usuario puede crear, listar, editar y eliminar compañías de la watchlist. |
| RF-2 | El usuario puede descargar una **plantilla canónica en blanco** (CSV/Excel) con las partidas de §3 en filas y los ejercicios en columnas, lista para rellenar a mano. |
| RF-3 | El usuario puede cargar los estados financieros de una compañía subiendo la plantilla canónica rellena o un volcado de un proveedor externo, que un adaptador traduce a la plantilla canónica. El resultado informa de filas importadas, ignoradas y no reconocidas, sin abortar por una partida suelta. |
| RF-4 | El usuario puede editar a mano cualquier cifra de un ejercicio ya cargado y añadir o borrar ejercicios sueltos. |
| RF-5 | El usuario ve el **diagnóstico histórico** por ejercicio: ventas, márgenes (EBITDA, EBIT, neto), BPA, acciones, y el crecimiento interanual de cada línea, más el CAGR del periodo disponible. |
| RF-6 | El usuario ve el **flujo de caja libre** por ejercicio con su desglose, su margen, el FCF por acción, la conversión en caja y los ratios de eficiencia (CapEx de mantenimiento y circulante sobre ventas), con la mediana del periodo. |
| RF-7 | El usuario ve la **asignación del capital** por ejercicio: qué porcentaje del FCF se destinó a CapEx de expansión, adquisiciones netas, recompras, dividendos y amortización de deuda. |
| RF-8 | El usuario ve los **retornos sobre el capital**: NOPAT, capital invertido, ROIC, ROE y tasa de reinversión por ejercicio, con sus medianas. |
| RF-9 | El usuario ve los **múltiplos históricos** (PER, EV/FCF, EV/EBITDA, EV/EBIT) por ejercicio y su mediana, junto a deuda neta y deuda neta/EBITDA. |
| RF-10 | El usuario ve las **red flags**: partidas sospechosas como porcentaje de ventas por ejercicio, y el recuento de ejercicios que incumplen cada umbral (ventas decrecientes, margen operativo decreciente, FCF negativo, ROIC pobre, apalancamiento elevado). |
| RF-11 | El usuario edita las hipótesis de los tres escenarios y obtiene, para cada uno: cuenta de resultados y FCF proyectados a 5 años, precio objetivo por método y por año, su promedio, margen de seguridad, potencial de revalorización, retorno anualizado a 5 años y **precio máximo de compra** para el retorno exigido. |
| RF-12 | Cuando una compañía de la watchlist coincide con una posición de la cartera ([[prd/inversiones]]), la tabla de posiciones muestra el **margen de seguridad** y el **precio objetivo/potencial** del escenario base, más un **aviso de valoración caducada** cuando la última actualización de datos o de hipótesis supera el umbral configurado. |
| RF-13 | El usuario puede guardar una **instantánea** del análisis en cualquier momento, consultarla después con las cifras de aquel día intactas y compararla con la situación actual. |
| RF-14 | El precio de la acción se obtiene automáticamente del proveedor de precios ya existente en `investments`, con posibilidad de introducirlo a mano (override) cuando el proveedor no cubre el mercado. |
| RF-15 | La watchlist muestra un listado comparativo de todas las compañías con su veredicto actual: precio, precio objetivo base, margen de seguridad, nº de red flags y antigüedad del análisis. |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | **Aislamiento de contextos**: el módulo no crea, modifica ni afecta a movimientos, transferencias, presupuestos, carteras ni operaciones de inversión. Solo **lee** hacia fuera: el proveedor de precios y, para el enlace de RF-12, la identidad del instrumento. |
| RN-2 | **Nada materializado**: todo indicador derivado (ratios, medianas, CAGR, proyecciones, múltiplos, precios objetivo, red flags) se calcula en la capa de lectura desde `fiscal_year` + `scenario`. Lo único persistido es hechos, hipótesis e instantáneas. Corregir un dato financiero recalcula automáticamente todo lo que dependa de él, salvo las instantáneas ya tomadas (RN-12). |
| RN-3 | **Convenio de signos**: salida de caja negativa, entrada positiva (§3). Las fórmulas de RN-5 a RN-10 lo asumen. Se valida en el dominio: una partida cuyo signo contradiga su naturaleza (ventas negativas, CapEx positivo, amortización positiva) se rechaza al cargar. |
| RN-4 | **Sin conversión de divisa**: los estados, el precio de la acción y todos los resultados viven en la divisa de reporte de la compañía. El precio obtenido del proveedor debe venir en esa misma divisa; si llega en otra (p. ej. peniques frente a libras), se normaliza si la equivalencia es conocida y, si no, se descarta y se pide el precio a mano (RF-14). La **escala** (`scale`) solo se aplica al reconciliar magnitudes agregadas con magnitudes por acción. |
| RN-5 | **Un escenario es un juego completo de hipótesis.** No existen palancas compartidas entre escenarios: cada uno lleva sus cinco años de hipótesis, sus cuatro múltiplos objetivo y su retorno exigido. El escenario **base** es el que alimenta el veredicto de cabecera, la watchlist (RF-15) y el enlace con la cartera (RF-12); el **pesimista** es el que responde "cuánto puedo perder si me equivoco". |
| RN-6 | **Magnitudes derivadas del histórico**, por ejercicio *t* (todas con el convenio de RN-3):<br>`EBITDA = EBIT − D&A`<br>`Interés neto = gasto financiero + ingreso financiero`<br>`EBT = EBIT + interés neto`; `tasa impositiva = −impuesto / EBT`<br>`BPA = beneficio neto / acciones diluidas`<br>`CapEx neto = capex + intangibles + venta de inmovilizado`<br>**`CapEx de mantenimiento`**: la parte del CapEx que solo sostiene el negocio existente se aproxima por la **depreciación del ejercicio, acotada por el CapEx realmente ejecutado** (si se invirtió menos que la depreciación, todo lo invertido es mantenimiento), más la inversión en intangibles. Es una heurística, no un dato reportado (ver §11).<br>`CapEx de expansión = CapEx neto − CapEx de mantenimiento`<br>`Circulante = existencias + clientes − proveedores − ingresos diferidos`; `Δcirculante = circulante_t − circulante_(t−1)`<br>**`FCF = EBITDA + CapEx de mantenimiento + interés neto + impuestos − Δcirculante`**<br>`Margen FCF = FCF / ventas`; `FCF por acción = FCF / acciones`; `conversión en caja = FCF / EBITDA` |
| RN-7 | **Asignación del capital**, por ejercicio y como porcentaje del FCF: CapEx de expansión, adquisiciones netas de desinversiones, recompras, dividendos y amortización neta de deuda (solo cuando es neta positiva). El total puede superar el 100 % — significa que el ejercicio consumió caja o deuda además del FCF generado, y eso es precisamente lo que la vista debe hacer visible. Con FCF ≤ 0 los porcentajes no se calculan (se muestra "—"), porque dividir por un FCF negativo produce signos sin sentido. |
| RN-8 | **Retornos sobre el capital**: `NOPAT = EBIT × (1 − tasa impositiva)`; `capital invertido = fondos propios + deuda a corto + deuda a largo + arrendamientos operativos (corto y largo) − inversiones financieras`; `ROIC = NOPAT / capital invertido`; `ROE = beneficio neto / fondos propios`; `tasa de reinversión = (|CapEx de expansión| + |adquisiciones| − desinversiones) / FCF`. |
| RN-9 | **Deuda neta, EV y múltiplos**: `deuda neta = (deuda corto + deuda largo) − (caja + inversiones financieras)` — **negativa significa caja neta**; `EV = capitalización + deuda neta`; `PER = capitalización / beneficio neto`; `EV/FCF`, `EV/EBITDA`, `EV/EBIT` con el EV del ejercicio. Un ejercicio sin capitalización informada queda fuera del múltiplo y de su mediana, no cuenta como cero. |
| RN-10 | **Valoración y veredicto.** Para cada escenario, cada año proyectado *e* y cada método, el precio objetivo es `(magnitud_e × múltiplo objetivo − deuda neta_e) / acciones_e`, con la magnitud correspondiente (beneficio neto para PER, FCF, EBITDA o EBIT para los tres EV). La deuda neta se resta **siempre y simétricamente**: con caja neta (deuda neta negativa) suma al valor, con deuda neta positiva resta (ver §11 — es una desviación deliberada respecto a la hoja de origen, que solo sumaba la caja). El **precio objetivo del escenario** es el promedio de los cuatro métodos disponibles (un método sin múltiplo objetivo informado no promedia). De ahí:<br>`margen de seguridad = (precio objetivo − precio actual) / precio objetivo`<br>`potencial de revalorización = (precio objetivo − precio actual) / precio actual`<br>`retorno anualizado = (precio objetivo del año 5 / precio actual)^(1/5) − 1`<br>**`precio máximo de compra = precio objetivo del año 5 / (1 + retorno exigido)^5`** |
| RN-11 | **Red flags**: umbrales **por defecto del módulo** (constantes de dominio: ROIC pobre por debajo del 10 %, deuda neta/EBITDA por encima de 2,5×) con **override opcional por compañía** (`threshold_*` de §3). La ficha marca visualmente todo umbral sobrescrito, para que al comparar dos compañías se vea si se están midiendo con la misma vara. Se cuentan ejercicios que incumplen: ventas decrecientes, margen operativo decreciente respecto al año anterior, FCF negativo, ROIC bajo el umbral y apalancamiento sobre el umbral. Además se muestran, como porcentaje de ventas, deterioros, desinversiones, retribución en acciones, emisión de acciones y cargos extraordinarios. |
| RN-12 | **Las instantáneas son inmutables y congelan tres cosas a la vez**: hipótesis, datos financieros usados y precio de la acción de ese día. Congelar solo las hipótesis haría que una recarga posterior de datos corregidos cambiase retroactivamente el resultado de una instantánea antigua, destruyendo justo lo que se quiere medir. Misma lógica que los snapshots de tipo de cambio de [[prd/inversiones]] (su RN-7a). Una instantánea no se edita: se borra o se toma otra. |
| RN-13 | **Compañías financieras y REITs fuera de alcance en v1.** Se pueden dar de alta y marcar con su `business_type`, pero la ficha muestra un aviso permanente de que las métricas basadas en EV, deuda neta, capital invertido y FCF no son interpretables para ese tipo de negocio. El módulo no las bloquea (el usuario puede querer registrar el histórico), pero tampoco finge que el veredicto es válido. |
| RN-14 | **Enlace con la cartera por identidad, sin FK**: una compañía se corresponde con un instrumento de `investments` cuando coinciden por ISIN, o por ticker cuando el ISIN no está informado en alguno de los dos lados. La correspondencia se resuelve en la capa de lectura; ningún esquema referencia al otro y la ausencia de correspondencia no es un error. |
| RN-15 | **Horizontes**: la proyección es **siempre de 5 ejercicios** (es lo que estructura la tabla de valoración y el retorno anualizado). El histórico es de **N ejercicios**, por defecto 5 y hasta 15; la UI ofrece cargarlo en bloques de 5 por comodidad, pero ninguna regla del modelo exige que N sea múltiplo de 5 ni contiguo. |
| RN-16 | **Toda métrica agregada declara su base.** Medianas, CAGR y recuentos se calculan sobre los ejercicios efectivamente disponibles y la UI muestra **sobre cuántos años**; con menos de 10 se añade un aviso. Una mediana de múltiplos sobre 5 años puede diferir sustancialmente de la de 10 si el periodo corto coincide con una expansión de múltiplo, y esa mediana es justo la referencia con la que se eligen los múltiplos objetivo — es decir, el sesgo se propaga directamente al precio objetivo. No bloquea nada: informa. |
| RN-17 | **Borrado**: una compañía con instantáneas no se puede eliminar (409), en coherencia con cuentas, categorías y carteras. Primero se borran sus instantáneas. Borrar un ejercicio sí es libre: es un hecho recargable. |

## 6. API (diseño)

Base: `/api/fundamentals`.

| Método | Ruta | Descripción |
|---|---|---|
| `GET/POST` | `/companies` · `PUT/DELETE /companies/{id}` | CRUD de la watchlist. `DELETE` con guarda RN-17 → 409. |
| `GET` | `/companies/{id}` | Ficha: datos de la compañía, ejercicios disponibles, precio actual y su fecha. |
| `GET` | `/watchlist` | Listado comparativo con el veredicto del escenario base de cada compañía (RF-15). |
| `GET` | `/financials/template?years=` | Descarga de la **plantilla canónica en blanco** (RF-2). |
| `GET` | `/companies/{id}/financials` | Ejercicios cargados (view CQRS), un objeto por año con las partidas de §3. |
| `PUT` | `/companies/{id}/financials/{year}` | Alta/edición de un ejercicio completo (RF-4). |
| `DELETE` | `/companies/{id}/financials/{year}` | Borra un ejercicio. |
| `POST` | `/companies/{id}/financials/import` | Import multipart de la plantilla canónica o de un volcado de proveedor (RF-3); devuelve `{imported, ignored, unmatched, errors, warnings}` con el mismo espíritu tolerante que `/api/imports` y el import Flex. |
| `GET/PUT` | `/companies/{id}/scenarios` · `/companies/{id}/scenarios/{kind}` | Lectura y edición de las hipótesis (RF-11). |
| `POST` | `/companies/{id}/scenarios/{kind}/duplicate-from/{source}` | Copia las hipótesis de un escenario sobre otro, para derivar pesimista/optimista del base tocando dos números (RN-5). |
| `GET` | `/companies/{id}/analysis?scenario=` | **Vista completa calculada** (read-model): diagnóstico histórico, FCF, asignación de capital, retornos, múltiplos y medianas, red flags, proyección y valoración del escenario pedido (base por defecto). Es la lectura que alimenta la ficha entera. |
| `GET/POST` | `/companies/{id}/snapshots` · `GET/DELETE /snapshots/{id}` | Historial de instantáneas (RF-13); `POST` toma una nueva con el estado actual. |
| `POST` | `/companies/{id}/price/refresh` | Refresca el precio contra el proveedor (RF-14). |
| `PUT` | `/companies/{id}/price` | Override manual del precio. |

## 7. UI/UX (diseño)

Entrada nueva **"Análisis"** en el grupo **Inversión** del menú lateral, junto a Panel general, Posiciones y Operaciones. Dos páginas lazy:

- `pages/analysis-watchlist` (ruta `/analysis`) — tabla comparativa de todas las compañías: nombre y ticker con su cuadrado de identificación (mismo patrón que la tabla de posiciones), precio actual, precio objetivo base, margen de seguridad con color por signo (`--pos`/`--neg`), nº de red flags y antigüedad del análisis. Ordenable por margen de seguridad: es la vista de "por dónde empiezo a mirar hoy".
- `pages/analysis-company` (ruta `/analysis/{id}`) — la ficha.

**Ficha: resumen arriba, detalle en pestañas.** La cabecera es lo que una hoja de cálculo no puede dar y concentra el veredicto, siempre visible:

- Precio actual con su fecha y origen (proveedor / manual).
- **Precio objetivo** del escenario base y su potencial.
- **Margen de seguridad de los tres escenarios** en una sola línea, para leer el rango de un vistazo — el pesimista destacado, porque es el que mide el riesgo.
- **Precio máximo de compra** para el retorno exigido, contrastado con el precio actual.
- Semáforo de red flags (nº de señales activas, enlace a su pestaña).
- Antigüedad de los datos y de las hipótesis; aviso si supera el umbral (RF-12).
- Selector de escenario, que gobierna todas las pestañas de proyección.
- Aviso permanente si `business_type` no es `NON_FINANCIAL` (RN-13).

Debajo, **control segmentado** de pestañas (mismo patrón visual que la página de Operaciones: contenedor `--surface-2`, segmento activo `--surface` + sombra, texto mono 12px):

1. **Resultados** — histórico y proyección de la cuenta de resultados, años en columnas, con crecimientos y márgenes; las celdas de hipótesis (crecimiento, margen EBIT, tasa impositiva, variación de acciones) **editables en línea**, como la matriz anual de [[prd/presupuestos]]. Panel lateral con CAGR y promedios del periodo histórico, que es el criterio con el que se eligen las hipótesis.
2. **Flujo de caja** — desglose del FCF, márgenes, ratios de eficiencia con su mediana, y la sección de **asignación de capital** (RF-7) con sus porcentajes proyectados también editables.
3. **Retornos** — NOPAT, capital invertido, ROIC, ROE y tasa de reinversión, con medianas.
4. **Valoración** — múltiplos históricos y medianas arriba; múltiplos objetivo editables; tabla de precio objetivo por método × año proyectado, con el promedio, margen de seguridad y potencial por año; retorno anualizado por método; y el precio máximo de compra con el retorno exigido editable.
5. **Red flags** — partidas sospechosas como % de ventas por ejercicio y recuento de incumplimientos por umbral, con los umbrales editables y marcados cuando están sobrescritos (RN-11).
6. **Gráficos** — Chart.js directamente, integrado con `ThemeService` como el resto de la app: evolución de ventas/BPA/FCF por acción, márgenes, FCF frente a ROIC, estructura de costes sobre ventas y evolución de múltiplos.
7. **Datos** — ejercicios cargados en crudo, edición manual, carga de plantilla y descarga de la plantilla en blanco.
8. **Instantáneas** — historial con fecha, etiqueta, precio de aquel día, precio objetivo y margen de seguridad de entonces; y la comparación con la situación actual, que es donde se ve si el criterio propio está mejorando.

Toda métrica agregada muestra su base de años (RN-16) mediante el mismo patrón de botón-tooltip ⓘ ya usado en la tabla de posiciones, y las tablas anchas van dentro de una tarjeta con `overflow-x: auto`.

**Enlace desde la cartera** (RF-12): la tabla de posiciones de `pages/investments-positions` gana tres elementos para las posiciones con análisis: margen de seguridad (color por signo), precio objetivo con potencial, e indicador de valoración caducada. Las posiciones sin análisis muestran "—" y un acceso para crear la ficha con el ISIN/ticker ya rellenos. La pantalla no cambia para quien no use el módulo.

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Alta de compañía con ticker (o ISIN) ya existente | `409 ConflictException`. |
| Ejercicio sin `sales` | `400 ValidationException`: sin ventas no hay márgenes ni ratios. |
| Partida con signo contrario a su naturaleza (ventas negativas, CapEx positivo, amortización positiva…) | `400 ValidationException` (RN-3). |
| Import: fila con partida no reconocida | Se ignora y se reporta en `unmatched`; el resto se importa (tolerante, como el import bancario y el Flex). |
| Import: valor no numérico o separador decimal incoherente | Fila reportada como error, el resto se importa. Comprobación específica de separador decimal, que es el fallo más frecuente al pegar datos de un proveedor. |
| Import: ejercicio ya cargado | Se **sobrescribe** y se reporta como actualizado. Los datos financieros son hechos recargables; corregir es el caso de uso normal, no un conflicto (a diferencia del import de operaciones, donde la idempotencia protege de duplicar hechos irrepetibles). |
| Proyección con menos de 2 ejercicios históricos | Se calcula igual, pero la ficha avisa: sin año anterior no hay variación de circulante ni crecimientos, y las medianas carecen de sentido. |
| Múltiplo objetivo no informado | Ese método no produce precio objetivo y no entra en el promedio (RN-10); no es un error. |
| Precio actual ausente | Margen de seguridad, potencial y retorno anualizado se muestran como "—". El precio objetivo sí se calcula: no depende del precio de mercado. |
| Escenario con retorno exigido ≤ −100 % o ejercicios proyectados incompletos | `400 ValidationException`. |
| Eliminar compañía con instantáneas | `409 ConflictException` (RN-17). |
| Precio del proveedor en divisa distinta de la de reporte | Se normaliza si la equivalencia es conocida; si no, se descarta con aviso y se pide override manual (RN-4). |

## 9. Casos límite y notas

- **La mediana de múltiplos sobre pocos años es el punto débil del método.** Es la referencia con la que se elige el múltiplo objetivo, y el múltiplo objetivo determina el precio objetivo; una mediana calculada sobre 5 años que caen dentro de una expansión de múltiplo arrastra todo el veredicto hacia arriba. Por eso RN-16 obliga a mostrar la base de años y a avisar por debajo de 10. El módulo no corrige el sesgo: lo hace visible.
- **El CapEx de mantenimiento es una estimación, no un dato.** Ninguna compañía publica cuánto de su inversión sostiene el negocio y cuánto lo hace crecer; la aproximación por depreciación acotada (RN-6) es una convención razonable y conservadora, pero en negocios con activos muy antiguos o muy intensivos en I+D puede quedarse corta o larga. Al depender el FCF de esta cifra, un error aquí se propaga al precio objetivo por dos vías (EV/FCF y la caja proyectada).
- **Interés neto proyectado**: en la proyección no hay estados financieros de los que leerlo, así que se estima aplicando el tipo medio implícito histórico (gasto financiero sobre deuda media, ingreso financiero sobre caja e inversiones financieras medias) a la deuda y la caja proyectadas por la asignación de capital (RN-7). Es una realimentación de un solo paso, no un cálculo circular: la caja del año *e* depende del FCF del año *e*, que usa el interés del año *e* estimado sobre los saldos de cierre del año *e−1*.
- **La asignación de capital cierra el circuito con la valoración.** No es una sección decorativa: el exceso o déficit de caja de cada año proyectado modifica la deuda neta, y la deuda neta entra en los cuatro precios objetivo (RN-10). Una compañía que se proyecta recomprando agresivamente vale más por acción por dos vías simultáneas (menos acciones y distinta deuda neta), y el modelo debe reflejar ambas.
- **Ejercicios fiscales que no coinciden con el año natural**: se registra el año fiscal tal como lo etiqueta la compañía, sin normalizar. Comparar entre compañías con cierres distintos es responsabilidad del usuario; el módulo no desplaza periodos.
- **Cambios de perímetro** (grandes adquisiciones, escisiones, cambios de criterio contable) rompen la comparabilidad de la serie histórica sin que el módulo pueda detectarlo. Los CAGR y las medianas seguirán calculándose sobre datos que no son homogéneos. Limitación conocida, sin maquinaria de ajuste en v1.
- **Compañías con pocos ejercicios publicados** (salidas a bolsa recientes): funcionan con lo que haya (RN-15/RN-16), con las medianas y los CAGR calculados sobre menos años y avisados como tales.
- **Precio en peniques frente a libras** (LSE) y equivalentes: mismo problema ya resuelto en [[prd/inversiones]] para el proveedor de precios; se reutiliza la normalización existente (RN-4).
- **La ficha no es una recomendación.** El precio objetivo es la consecuencia aritmética de las hipótesis introducidas; dos escenarios razonables pueden diferir en un 50 %. Por eso el veredicto de cabecera muestra los tres márgenes de seguridad a la vez y no un único número.

## 10. Backlog / mejoras futuras

- **DCF y DCF inverso**: descontar el FCF ya proyectado a una tasa exigida más valor terminal, y resolver a la inversa qué crecimiento implica el precio actual ("¿me creo lo que el mercado está descontando?"). Reutiliza íntegramente la proyección de RN-6; se añade como dos filas más en la tabla de precios objetivo, sin tocar el modelo de datos.
- **Valoración por poder de beneficio (EPV) y fórmulas de Graham**: rápidas, conservadoras y útiles como contraste del método por múltiplos.
- **Tabla de sensibilidad**: rejilla precio objetivo × dos palancas (crecimiento de ventas × múltiplo objetivo), para ver dónde está el riesgo real de la valoración.
- **Modelo alternativo para financieras y REITs** (RN-13): P/valor contable, ROE, ratios de capital, FFO/AFFO. Es otro modelo y merece su propio PRD.
- **Carga automática de fundamentales**: adaptador contra SEC/EDGAR o una API de fundamentales, sustituyendo el import manual sin tocar el modelo — la plantilla canónica ya actúa de frontera.
- **Comparativa entre compañías** de la watchlist sobre las mismas métricas (tabla o gráfico de dispersión ROIC × crecimiento × múltiplo).
- **Alertas de precio**: aviso cuando la cotización cruza el precio máximo de compra de una compañía analizada.
- **Tesis en texto** asociada a la compañía y congelada en cada instantánea.
- **Datos trimestrales** para acortar la latencia entre resultados y revisión del análisis.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Financieras y REITs | Fuera de alcance (RN-13): se registran y se avisa, pero el veredicto no es interpretable. | **Deuda técnica conocida y aceptada.** Cubrirlas exige un modelo de valoración distinto; se abordará como una versión posterior con su propio PRD. Empezar por no financieras cubre la mayoría del universo cotizado. |
| Mediana sobre menos de 10 años | Se calcula sobre lo disponible y se avisa (RN-16). | Evaluar, con uso real, si conviene exigir un mínimo de años para mostrar un veredicto en la watchlist, o ponderar la mediana por ciclo. |
| CapEx de mantenimiento heurístico | Aproximación por depreciación acotada (RN-6, §9). | Si el FCF resultante se separa sistemáticamente del que se obtiene por otras vías, considerar permitir un override manual por ejercicio. |
| PER ex-caja asimétrico en la hoja de origen | La hoja de la que procede la metodología suma la caja neta al valor pero **no** resta la deuda neta cuando la hay (y devuelve 0 con deuda neta exactamente nula). Aquí se especifica **simétrico** (RN-10), coherente con los otros tres métodos. | Desviación deliberada y documentada. Si al comparar resultados con la hoja aparecen diferencias en compañías endeudadas, el motivo es este y no un error. |
| Interés neto proyectado | Estimado con el tipo medio implícito histórico sobre saldos de cierre del año anterior (§9). | Revisar si en compañías con estructura de capital cambiante la aproximación distorsiona el FCF proyectado lo bastante como para justificar un modelo de deuda más fino. |
| Endpoint no oficial del proveedor de precios | Se hereda el `PriceProviderPort` de [[prd/inversiones]], con las mismas limitaciones (sin SLA, puede cambiar de formato). | Ninguna acción propia: el aislamiento por puerto ya permite sustituirlo. El override manual de precio (RF-14) es la mitigación desde el primer día. |
| Comparabilidad de la serie histórica | Sin detección de cambios de perímetro ni de criterio contable (§9). | Sin plan; se asume que el usuario conoce la compañía que está analizando. |
| Formato de volcado del proveedor externo | El adaptador de import se escribirá contra el formato del proveedor que el usuario usa hoy. | Igual que el ACL del Flex: el formato puede cambiar sin aviso. Mitigado por la plantilla canónica, que es la frontera estable — un cambio del proveedor solo afecta al adaptador. |

## 12. Fases de implementación

Desarrollo con **TDD obligatorio** (ver `CLAUDE.md`): cada hito se construye en ciclos red-green-refactor y se cierra con **un commit** (tests en verde + PRD actualizado). Un hito no empieza hasta que el anterior está commiteado.

### F1 — Compañías y datos financieros

| Hito | Contenido | Tests |
|---|---|---|
| H1.1 | Value objects del contexto: `Ticker`, `FiscalYear` (año), `Scale`, `BusinessType`, `Amount` con el convenio de signos de RN-3. | Unitarios de dominio. |
| H1.2 | Agregado `Company` (identidad ticker/ISIN, invariantes de divisa y escala, umbrales override RN-11). | Unitarios de dominio. |
| H1.3 | Agregado `FinancialStatement` (un ejercicio): partidas de §3, validación de signos (RN-3) y obligatoriedad de ventas (§8). | Unitarios de dominio. |
| H1.4 | Puertos de salida + `CompanyService` (CRUD, guarda de borrado RN-17). | Aplicación con puertos mockeados. |
| H1.5 | Migración `V9__fundamentals.sql` (esquema + tablas de §3) + entidades/mappers/adaptadores JPA. | `@DataJpaTest` (Testcontainers). |
| H1.6 | Web: `CompanyController` + DTOs; CRUD y ejercicios; el contexto entra en ArchUnit. | `@WebMvcTest` + `ArchitectureTest`. |
| H1.7 | Plantilla canónica: generación de la plantilla en blanco (RF-2) y `FinancialStatementParser` como ACL que traduce plantilla o volcado de proveedor a las partidas canónicas (RF-3), tolerante fila a fila. | Unitarios del parser con fixtures reales. |
| H1.8 | Caso de uso `ImportFinancials` + endpoint multipart, con sobrescritura por ejercicio (§8). | Aplicación mockeada + `@WebMvcTest`. |

### F2 — Motor de diagnóstico histórico

| Hito | Contenido | Tests |
|---|---|---|
| H2.1 | Servicio de dominio `HistoricalAnalyzer`: márgenes, crecimientos, CAGR y medianas sobre los años disponibles, declarando siempre la base (RN-16). | Unitarios de dominio. |
| H2.2 | `CashFlowCalculator`: CapEx de mantenimiento/expansión, circulante, FCF y ratios de eficiencia (RN-6). | Unitarios de dominio (incluidos los límites de la heurística de CapEx). |
| H2.3 | `CapitalAllocationCalculator` (RN-7), con el caso FCF ≤ 0. | Unitarios de dominio. |
| H2.4 | `ReturnsCalculator`: NOPAT, capital invertido, ROIC, ROE, tasa de reinversión (RN-8). | Unitarios de dominio. |
| H2.5 | `MultiplesCalculator`: deuda neta, EV, cuatro múltiplos y sus medianas, excluyendo ejercicios sin capitalización (RN-9). | Unitarios de dominio. |
| H2.6 | `RedFlagDetector`: umbrales por defecto + override por compañía, recuentos y partidas sobre ventas (RN-11). | Unitarios de dominio. |

### F3 — Escenarios, proyección y valoración

| Hito | Contenido | Tests |
|---|---|---|
| H3.1 | Agregados `Scenario` y `ScenarioYear` (juego completo de hipótesis, RN-5) + persistencia. | Unitarios de dominio + `@DataJpaTest`. |
| H3.2 | `ProjectionCalculator`: cascada de 5 ejercicios (ventas → EBIT → EBITDA → interés neto estimado → EBT → impuestos → beneficio → acciones → BPA → FCF), asignación de capital proyectada y deuda neta resultante (RN-6/RN-7, §9). | Unitarios de dominio. |
| H3.3 | `ValuationCalculator`: precio objetivo por método y año (deuda neta simétrica, RN-10), promedio, margen de seguridad, potencial, retorno anualizado y precio máximo de compra. | Unitarios de dominio (casos con caja neta, con deuda neta y con múltiplos ausentes). |
| H3.4 | Read-side CQRS `AnalysisQueryPort` + `CompanyAnalysisView` (la vista completa de §6) + adapter + endpoint `GET /companies/{id}/analysis`. | `@DataJpaTest` + `@WebMvcTest`. |
| H3.5 | Edición de escenarios y "duplicar escenario" (§6). | Aplicación + `@WebMvcTest`. |
| H3.6 | Precio: reutilización del `PriceProviderPort` de `investments` vía puerto propio, refresco y override manual (RF-14, RN-4). | Aplicación mockeada + `@WebMvcTest`. |

### F4 — UI: watchlist y ficha

| Hito | Contenido | Tests |
|---|---|---|
| H4.1 | Modelos + `api.service.ts`; entrada "Análisis" en el menú y rutas lazy. | Vitest. |
| H4.2 | `pages/analysis-watchlist`: tabla comparativa ordenable (RF-15). | Vitest + Playwright. |
| H4.3 | `pages/analysis-company`: cabecera de veredicto con selector de escenario y aviso de RN-13. | Vitest. |
| H4.4 | Pestañas Resultados y Flujo de caja, con edición en línea de las hipótesis. | Vitest + Playwright. |
| H4.5 | Pestañas Retornos, Valoración y Red flags (con umbrales editables y marcados). | Vitest. |
| H4.6 | Pestaña Datos: edición manual, carga de plantilla y descarga de plantilla en blanco. | Vitest + Playwright. |

### F5 — Instantáneas

| Hito | Contenido | Tests |
|---|---|---|
| H5.1 | Agregado `ValuationSnapshot` + puerto (solo escritura desde el dominio) y serialización del payload congelado (RN-12). | Unitarios de dominio. |
| H5.2 | Persistencia `jsonb` + adaptador; lectura CQRS paginada del historial. | `@DataJpaTest` (round-trip JSON). |
| H5.3 | Endpoints de instantáneas (§6) y guarda de borrado de compañía (RN-17). | `@WebMvcTest`. |
| H5.4 | Pestaña Instantáneas: historial y comparación con la situación actual. | Vitest + Playwright. |

### F6 — Enlace con la cartera

| Hito | Contenido | Tests |
|---|---|---|
| H6.1 | Resolución de correspondencia compañía ↔ instrumento por ISIN/ticker en la capa de lectura, sin FK (RN-14). | Unitarios + `@DataJpaTest`. |
| H6.2 | Columnas de margen de seguridad, precio objetivo/potencial y aviso de caducidad en la tabla de posiciones; acceso para crear la ficha desde una posición sin análisis (RF-12). Actualización del PRD de Inversiones. | Vitest + Playwright. |

### F7 — Gráficos

| Hito | Contenido | Tests |
|---|---|---|
| H7.1 | Pestaña Gráficos con Chart.js integrado en `ThemeService`: crecimiento (ventas/BPA/FCF por acción), márgenes, FCF frente a ROIC, estructura de costes y evolución de múltiplos. Mismo diferido de render (`scheduleRenderCharts`) que las páginas de inversión. | Vitest + Playwright (regresión de contenido de canvas). |

## 13. Referencias de código

Sin implementar. Al comenzar F1, esta sección recogerá — como en el resto de PRD — dominio, aplicación, infraestructura (persistencia, web, import) y frontend:

- **Backend**: `backend/src/main/java/com/xroig/finance/fundamentals/` (`domain/`, `application/`, `infrastructure/persistence|web|imports`).
- **Migración**: `backend/src/main/resources/db/migration/V9__fundamentals.sql`.
- **Frontend**: `frontend/src/app/pages/analysis-watchlist/`, `frontend/src/app/pages/analysis-company/`, modelos en `models.ts` y llamadas en `api.service.ts`.
