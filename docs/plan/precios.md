# Plan de implementación — API externa de cotizaciones (1.1)

**Worktree:** `/home/xroig/workspace/app_finance-precios` · **Rama:** `inv-precios`
**Origen:** `docs/investment/mejoras-modulo-inversiones.md` §1.1, PRD `docs/prd/inversiones.md` §10/§11 (H4.1).
**Estado del código de partida:** `PriceProviderPort` (dominio) está definido desde v1 pero **sin ningún adaptador ni caller** (0 usos fuera de su propia definición) — literalmente el puerto vacío que el PRD promete rellenar. `PriceQuoteRepository`/`PriceQuotePersistenceAdapter` ya existen y ya soportan upsert por `(security_id, quote_date)` — los usa hoy `FlexReportParser` al importar `Open Positions`.

## 1. Objetivo y alcance

**Entra:**
- Adaptador real de `PriceProviderPort.latestQuotes(Security)` contra una API externa.
- Caso de uso de aplicación que recorre el catálogo de instrumentos, pide cotización nueva a cada uno y hace upsert en `price_quote`.
- Endpoint REST + botón "Actualizar precios" en el frontend (bajo demanda, sin scheduler).
- Resolución de símbolo desde `Security.ticker`/`exchange` (ya existen desde v1).
- Conversión correcta cuando el proveedor devuelve peniques (GBX) en vez de libras (GBP) — caso ZEG/LSE, generalizado por divisa, no por instrumento concreto.

**NO entra ahora** (queda en el backlog, no lo toques en esta rama):
- Benchmarks (3.1) — depende de esto pero es tarea aparte.
- Scheduler/cron de refresco automático (F4-H4.3, "modo híbrido") — la mejora 1.1 solo pide botón de refresco bajo demanda.
- Revisar si la identidad de `Security` pasa a ISIN+exchange (nota abierta en PRD §9/§10) — no lo fuerces; si el proveedor elegido resuelve bien por ticker+exchange tal cual está hoy el modelo, no hace falta tocarlo.
- Flex Web Service (2.1) y cualquier cosa de otras ramas paralelas.

## 2. Diseño técnico backend

### 2.1 Proveedor externo

**Recomendación: Yahoo Finance (endpoint no oficial `chart`)** en vez de un proveedor con API key (Alpha Vantage, Twelve Data...). Motivo: cero secreto que gestionar (nada de `FINANCE_PRICE_API_KEY`), cobertura amplia de mercados europeos vía sufijo de ticker, y es justo lo que ya nombra el PRD como opción por defecto ("Yahoo Finance u otra"). Contrapartida a documentar como deuda técnica: es un endpoint no oficial, sin SLA, puede cambiar de forma sin aviso — el diseño ya aísla esto en un único adaptador (`PriceProviderPort`), así que cambiar de proveedor el día que Yahoo falle es sustituir una clase, no rediseñar.

Endpoint: `GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=5d&interval=1d`. Devuelve `chart.result[0].meta.regularMarketPrice` + `meta.regularMarketTime` + `meta.currency` — este último es la clave para detectar GBX (ver 2.3).

Cliente HTTP: `RestClient` de Spring (Boot 4 lo trae de serie, sin nueva dependencia), con timeout corto (p. ej. 5s) para no bloquear el hilo del botón de refresco si Yahoo no responde.

### 2.2 Resolución de símbolo

Nueva clase de dominio (o método privado del adaptador; si tiene invariantes propias, mejor un `record`/servicio en `domain/`) que traduce `Security.ticker` + `Security.exchange` (código IBKR: `AEB`, `SBF`, `NASDAQ`, `LSE`, `BVME`…) a símbolo Yahoo, vía tabla de sufijos:

| `exchange` (IBKR) | Sufijo Yahoo | Ejemplo |
|---|---|---|
| `NASDAQ`/`NYSE`/vacío | (ninguno) | `AAPL` |
| `LSE` | `.L` | `ZEG.L` |
| `AEB` | `.AS` | `IWDA.AS` |
| `SBF` | `.PA` | `MC.PA` |
| `BVME` | `.MI` | — |
| `IBIS`/`FWB` | `.DE` | — |

Empieza con los mercados que ya aparecen en la cartera real (revisa qué `exchange` hay hoy en `investments.security` antes de fijar la tabla — no la inventes de memoria) y deja la tabla fácilmente ampliable (no hardcodees el resto "porque sí"). Si `exchange` es nulo o no está en la tabla, `latestQuotes` devuelve lista vacía (contrato ya definido: "empty when the provider has none") en vez de lanzar — un instrumento sin mapeo simplemente no se refresca, no rompe el resto.

### 2.3 Caso GBX/peniques (generalizado, no solo ZEG)

Yahoo devuelve `meta.currency = "GBp"` (con "p" minúscula) para instrumentos que cotizan en peniques en la LSE, mientras que `Security.currency` en este dominio es `"GBP"` (la divisa real del instrumento, en libras — así se guardó desde el import Flex). Regla en el adaptador: si `meta.currency` (respuesta) es `"GBp"`/`"GBX"` **y** `Security.currency` es `"GBP"`, dividir el precio entre 100 antes de construir el `PriceQuote`. Si algún día aparece un instrumento realmente en peniques con `Security.currency = "GBX"` (no ocurre hoy), no dividir — la regla es específicamente "proveedor en peniques, dominio en libras", no "proveedor en peniques" a secas.

Cualquier otro mismatch de divisa entre lo que devuelve Yahoo y `Security.currency` (no debería ocurrir si el símbolo está bien resuelto) → tratar la cotización como no fiable y descartarla (lista vacía para ese security), nunca guardar un precio en divisa equivocada.

### 2.4 Caso de uso y manejo de errores

Nuevo puerto de entrada `application/port/RefreshPrices.java` + servicio `application/PriceRefreshService.java` (sigue el patrón `XService implements Port` ya usado en `PortfolioService`/`SecurityService`). Recorre `SecurityRepository.findAll()` (revisa si ese método existe; si no, añádelo — es lectura simple, no rompe nada), para cada uno llama `PriceProviderPort.latestQuotes(security)`, hace upsert de lo que venga con `PriceQuoteRepository.upsert(...)`, y agrega un resultado tipo `PriceRefreshResult` (mismo espíritu que `FlexImportResult`: cuántos securities actualizados, cuáles fallaron y por qué — "sin mapeo de mercado", "proveedor sin datos", "error de red"). Un fallo puntual de un instrumento **no** aborta el resto (mismo principio tolerante que el import Flex, §8 del PRD).

No hace falta transacción especial: cada upsert es independiente y ya es atómico a nivel de fila.

### 2.5 Sin migración

`price_quote` ya soporta exactamente lo que necesita esta tarea (upsert por security+fecha). **No se crea ninguna migración Flyway en esta rama.** Si en el futuro se quisiera distinguir el origen de una cotización (Flex vs API), sería una columna `source` nullable — pero no es necesaria para el alcance de esta tarea y se deja fuera explícitamente. Esto también significa que esta rama **no compite por ningún número de migración** con las otras dos tareas en paralelo (la siguiente libre, V8, queda para "historial de imports").

### 2.6 Endpoint REST

```
POST /api/investments/prices/refresh
```
Sin cuerpo. Respuesta `200` con el resumen (`PriceRefreshResponse`: `updated`, `failed: [{securityId, ticker, reason}]`). Global (no por cartera): `Security` es un catálogo compartido entre carteras (§6 PRD, `GET/POST /securities` ya es global), así que refrescar precios no tiene sentido acotado a una cartera concreta.

Controller: añade el método a `SecurityController.java` (ya gestiona el catálogo de `Security`) en vez de crear un controller nuevo — evita otro fichero para una sola ruta y mantiene el catálogo y su refresco juntos.

## 3. Diseño frontend

Botón "Actualizar precios" en `components/investment-toolbar.ts` (barra compartida por ambas páginas de Inversión, según PRD §7 — así el refresco está disponible desde Panel general y Operaciones sin duplicar botón). Al pulsar: llama a un nuevo método `refreshPrices()` en `api.service.ts` → `POST /investments/prices/refresh`, muestra estado de carga (spinner/disabled mientras corre) y, al terminar, un resumen breve (p. ej. "12 instrumentos actualizados, 2 sin cotización disponible") — reutiliza el patrón de aviso ya usado tras un import Flex si existe uno reutilizable (`flex-import-dialog.ts` seguramente ya tiene un componente de resumen ok/error; compártelo o clónalo con ligereza, no lo inventes desde cero). Tras un refresco con éxito, recarga lo que dependa de precios (posiciones, valoración, rentabilidad) igual que ya hace el flujo tras un import Flex — mira cómo `InvestmentContextService`/las páginas reaccionan hoy tras `importFlexReport()` y replica ese mismo disparo de recarga.

Nuevo modelo en `models.ts`: `PriceRefreshResult { updated: number; failed: { securityId: number; ticker: string; reason: string }[] }`.

## 4. Plan TDD por hitos

| Hito | Test rojo primero | Implementación mínima en verde |
|---|---|---|
| M1 — Resolución de símbolo | `domain/YahooSymbolResolverTest.java` (o el nombre que decidas): casos `LSE`→`.L`, `NASDAQ`→sin sufijo, exchange desconocido→vacío/`Optional.empty()`, ticker nulo→vacío. | Clase de resolución de símbolo pura, sin HTTP. |
| M2 — Conversión GBX→GBP | Test unitario (mismo fichero o `YahooPriceQuoteAdapterTest` con el cliente HTTP mockeado/fake) que fija `meta.currency="GBp"` + `Security.currency="GBP"` y espera precio /100; y el caso contrario (`currency="USD"`) sin dividir. | Lógica de conversión en el adaptador. |
| M3 — Adaptador completo con fallos tolerados | Test del adaptador con un fake `RestClient`/servidor HTTP simulado: caso feliz (una cotización), caso "símbolo no encontrado" (404/JSON vacío → lista vacía, no excepción), caso timeout (idem). | `YahooFinancePriceProvider implements PriceProviderPort`. |
| M4 — Caso de uso `PriceRefreshService` | `application/PriceRefreshServiceTest.java` con `SecurityRepository`/`PriceProviderPort`/`PriceQuoteRepository` mockeados: valida que un fallo de un security no aborta el resto y que el resultado agrega updated/failed correctamente. | `PriceRefreshService implements RefreshPrices`. |
| M5 — Endpoint | `infrastructure/web/SecurityControllerMvcTest.java` (ampliar) con `@WebMvcTest`: `POST /api/investments/prices/refresh` → 200 + payload esperado, puerto de entrada mockeado. | Método nuevo en `SecurityController`. |
| M6 — Frontend | `investment-toolbar.spec.ts` (Vitest): botón dispara `refreshPrices()`, estado de carga, resumen mostrado; si aplica, ampliar el Playwright de `investments-dashboard`/`investments-operations` con el flujo de refresco. | Botón + método en `api.service.ts` + manejo de estado en el componente. |

Cada hito termina con commit propio (tests verdes + PRD actualizado en el mismo commit del último hito que toque esa sección, o incremental si prefieres cerrar el PRD por bloques).

## 5. Actualización de PRD requerida

`docs/prd/inversiones.md`:
- §2 "No-objetivos": quitar "Cotizaciones desde APIs externas... → backlog" (ya no es no-objetivo, es lo que se acaba de construir).
- §6 API: añadir la fila `POST /prices/refresh`.
- §10 Backlog: quitar la entrada "API externa de cotizaciones" (implementada); dejar Benchmarks y el resto.
- §11 Deuda técnica: la fila "Serie de valoración dispersa" pasa a resuelta (o se matiza: TWR/XIRR ahora sobre serie diaria real, no solo fechas de import) — actualízala, no la borres sin más, dejando trazabilidad de cuándo se resolvió (como ya hace el PRD con otras filas resueltas en §11).
- §12/§13: añade el hito (p. ej. H4.1, tal como ya lo nombra §12 F4) con su contenido real y amplía §13 "Referencias de código" con el adaptador nuevo, siguiendo el estilo denso ya usado para el resto del módulo.
- Bump "Última actualización" y considera si el estado de cabecera (F4 "en backlog") debe matizarse (F4 parcialmente implementado: H4.1 sí, H4.2/H4.3 no).

## 6. Riesgos / deuda técnica

- **Fuente no oficial**: el endpoint `chart` de Yahoo no es una API pública soportada; puede cambiar de forma o bloquear por rate-limit sin aviso. Mitigación: el puerto ya aísla esto a una sola clase adaptadora; si falla, el fallback es simplemente "no se actualiza el precio, se sigue viendo el último conocido" (nunca rompe la valoración existente).
- **Tabla de sufijos de exchange incompleta**: cualquier mercado que la cartera aún no tenga no está mapeado; instrumentos de esos mercados no se refrescan hasta ampliar la tabla (fallo silencioso y seguro, no error).
- **Ambigüedad `GBp`/`GBX`**: si Yahoo cambia el literal exacto que usa para peniques, la detección se rompe. Vale la pena loguear (nivel WARN) cualquier divisa de respuesta que no coincida con `Security.currency` y no sea el caso GBX conocido, para detectar mercados nuevos con el mismo problema (p. ej. algunas plazas usan también fracciones no-decimales infrecuentes).

## 7. Puntos de fricción con las otras dos ramas paralelas

- **`SecurityController.java`**: si "posiciones cerradas" o "historial de imports" también tocan controllers del módulo, no debería haber solape de fichero (cada una tiene su propio controller natural), pero revísalo al mergear.
- **`docs/prd/inversiones.md`**: las tres ramas lo tocan → conflicto de merge seguro, textual y fácil de resolver a mano; no dejar el merge para el final de las tres, mergear una rama cada vez como ya se decidió.
- **`app.routes.ts` / menú lateral**: esta tarea **no** añade ninguna pantalla nueva (el botón vive en el toolbar ya existente), así que no debería tocar rutas ni menú — si en algún momento del desarrollo sientes la tentación de añadir una pantalla de "estado de precios", para: no es parte del alcance 1.1.
- **Migraciones Flyway**: esta rama no crea ninguna (§2.5) — cero riesgo de colisión de número con las otras dos.
