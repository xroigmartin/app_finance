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

**Decisión confirmada con el usuario (2026-07-25): precio de cierre (EOD), no tiempo real.** Esta no es una aplicación que se consulte de forma continua durante la sesión de mercado — el botón de refresco pide el último cierre disponible, no una cotización intradía. Esto simplifica el adaptador (un único endpoint EOD, no hay que elegir entre "último precio" vivo y cierre) y es más barato en cuota de API. Si en el futuro hiciera falta intradía (p. ej. para un caso de uso que aún no existe), sería un adaptador/método adicional, no un cambio de éste.

**NO entra ahora** (queda en el backlog, no lo toques en esta rama):
- Benchmarks (3.1) — depende de esto pero es tarea aparte.
- Scheduler/cron de refresco automático (F4-H4.3, "modo híbrido") — la mejora 1.1 solo pide botón de refresco bajo demanda.
- Revisar si la identidad de `Security` pasa a ISIN+exchange (nota abierta en PRD §9/§10) — no lo fuerces; si el proveedor elegido resuelve bien por ticker+exchange tal cual está hoy el modelo, no hace falta tocarlo.
- Flex Web Service (2.1) y cualquier cosa de otras ramas paralelas.

## 2. Diseño técnico backend

### 2.1 Proveedor externo

**Decisión revisada con el usuario (2026-07-25): Twelve Data, con API key** — se descarta el endpoint no oficial de Yahoo Finance que este plan proponía en su primera versión.

**Por qué se cambia:** el argumento original ("cero secreto que gestionar") es más débil de lo que parece — el proyecto ya gestiona secretos por variable de entorno (`FINANCE_DB_*` en `application.properties`), así que añadir `FINANCE_TWELVEDATA_API_KEY` no es infraestructura nueva, es una línea más del mismo patrón ya establecido. Además, desde 2024 el endpoint `chart` de Yahoo exige un mecanismo de cookie+crumb que caduca en minutos y cambia periódicamente para dificultar el scraping — sin SLA ni contrato, el riesgo de que el botón de refresco deje de funcionar en silencio es real y ya documentado (varios cambios de formato reportados en 2024-2026), no hipotético.

**Documentación oficial**: https://twelvedata.com/docs/introduction/overview — consultarla al implementar el adaptador (endpoints, parámetros, formato de respuesta, límites).

**Twelve Data**: API documentada y estable, free tier de **800 peticiones/día** — con un catálogo de ~10-20 instrumentos y refresco bajo demanda (no scheduler), el consumo real por refresco es de ese mismo orden, muy por debajo del límite. Cobertura confirmada de 50+ bolsas incluyendo LSE (cubre el caso ZEG/GBX, ver 2.3).

Endpoint EOD (cierre, no tiempo real — ver decisión de alcance en §1): `GET https://api.twelvedata.com/eod?symbol={ticker}&exchange={exchange}&apikey={apikey}` (alternativa: parámetro `mic_code` en vez de `exchange`, ver 2.2). Respuesta con metadata (`symbol`, `currency`, `exchange`, `mic_code`) + `close` + `datetime` — el campo `currency` es la clave para detectar GBX (ver 2.3).

Autenticación: `FINANCE_TWELVEDATA_API_KEY` (variable de entorno, mismo patrón `FINANCE_*` ya usado para las credenciales de BD).

Cliente HTTP: `RestClient` de Spring (Boot 4 lo trae de serie, sin nueva dependencia), con timeout corto (p. ej. 5s) para no bloquear el hilo del botón de refresco si el proveedor no responde.

### 2.2 Resolución de símbolo

A diferencia de Yahoo (sufijo pegado al ticker, `ZEG.L`), Twelve Data recibe el ticker y la bolsa como **parámetros separados**: `symbol={ticker}` + `exchange={nombre}` (nombre de bolsa en texto, p. ej. `LSE`) o alternativamente `mic_code={código}` (código MIC ISO 10383, p. ej. `XLON` para LSE). Esto simplifica la resolución: no hace falta construir un símbolo compuesto, "solo" traducir el código de `exchange` que usa IBKR (`AEB`, `SBF`, `NASDAQ`, `LSE`, `BVME`, `IBIS`…) al nombre/MIC que espera Twelve Data.

**No hardcodear esta tabla de memoria.** Antes de fijarla:
1. Consulta qué valores de `exchange` hay hoy realmente en `investments.security` (no inventar mercados que no están en la cartera).
2. Consulta el endpoint de referencia `GET https://api.twelvedata.com/exchanges?apikey=...` (o la documentación de https://twelvedata.com/exchanges) para confirmar el nombre/MIC exacto que Twelve Data espera para cada uno de esos mercados — verificado en la investigación previa a este plan: Twelve Data cubre LSE explícitamente, pero el nombre/código exacto a usar en el parámetro debe confirmarse en el momento de implementar, no asumirse.

Nueva clase de dominio (o método privado del adaptador; si tiene invariantes propias, mejor un `record`/servicio en `domain/`) con la tabla de mapeo `exchange` IBKR → parámetro Twelve Data, fácilmente ampliable (no hardcodees mercados que la cartera no tiene "porque sí"). Si `exchange` es nulo o no está en la tabla, `latestQuotes` devuelve lista vacía (contrato ya definido: "empty when the provider has none") en vez de lanzar — un instrumento sin mapeo simplemente no se refresca, no rompe el resto.

### 2.3 Caso GBX/peniques (generalizado, no solo ZEG)

Confirmado en la investigación previa a este plan: **Twelve Data también cotiza los valores de la LSE en GBX (peniques)** — es una convención del propio mercado (LSE), no una particularidad de Yahoo, así que el problema persiste igual con el nuevo proveedor. `Security.currency` en este dominio es `"GBP"` (la divisa real del instrumento, en libras — así se guardó desde el import Flex).

**Antes de fijar el literal exacto**, verificar empíricamente (llamada real al endpoint `/eod` con un ticker LSE, p. ej. el propio ZEG) qué valor devuelve Twelve Data en el campo `currency` de la respuesta — no asumir que es `"GBp"` como Yahoo; podría ser `"GBX"`, `"GBp"` u otro literal. Regla en el adaptador, una vez confirmado el literal: si `currency` (respuesta) indica peniques **y** `Security.currency` es `"GBP"`, dividir el precio entre 100 antes de construir el `PriceQuote`. Si algún día aparece un instrumento realmente en peniques con `Security.currency = "GBX"` (no ocurre hoy), no dividir — la regla es específicamente "proveedor en peniques, dominio en libras", no "proveedor en peniques" a secas.

Cualquier otro mismatch de divisa entre lo que devuelve Twelve Data y `Security.currency` (no debería ocurrir si el símbolo está bien resuelto) → tratar la cotización como no fiable y descartarla (lista vacía para ese security), nunca guardar un precio en divisa equivocada.

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
| M1 — Resolución de símbolo | `domain/TwelveDataExchangeResolverTest.java` (o el nombre que decidas): casos `LSE`→parámetro esperado, `NASDAQ`→parámetro esperado, exchange desconocido→vacío/`Optional.empty()`, ticker nulo→vacío. | Clase de resolución `exchange`/`mic_code` pura, sin HTTP. |
| M2 — Conversión GBX→GBP | Test unitario (mismo fichero o `TwelveDataPriceQuoteAdapterTest` con el cliente HTTP mockeado/fake) que fija el literal de divisa-peniques confirmado empíricamente (§2.3) + `Security.currency="GBP"` y espera precio /100; y el caso contrario (`currency="USD"`) sin dividir. | Lógica de conversión en el adaptador. |
| M3 — Adaptador completo con fallos tolerados | Test del adaptador con un fake `RestClient`/servidor HTTP simulado: caso feliz (una cotización EOD), caso "símbolo no encontrado" (404/JSON de error → lista vacía, no excepción), caso timeout (idem), caso "cuota diaria agotada" (respuesta 429 → lista vacía, no excepción). | `TwelveDataPriceProvider implements PriceProviderPort`. |
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

- **Cuota diaria (800 peticiones/día, free tier)**: con refresco bajo demanda y un catálogo pequeño de instrumentos, el consumo normal está muy por debajo del límite, pero si el usuario pulsa el botón repetidamente en poco tiempo (o crece mucho el catálogo) podría agotarse. Mitigación: `PriceRefreshResult` debe distinguir el fallo "cuota agotada" (429) de otros fallos, para que el resumen en el frontend sea claro sobre por qué no se actualizó algo, y no reintentar automáticamente.
- **Gestión de la API key**: `FINANCE_TWELVEDATA_API_KEY` sigue el patrón `FINANCE_*` ya usado para las credenciales de BD — sin infraestructura nueva, pero sí un secreto real que no debe acabar en el repo (documentar en README/`.env.example` si existe, nunca hardcodeado).
- **Tabla de mapeo `exchange`→parámetro Twelve Data incompleta**: cualquier mercado que la cartera aún no tenga no está mapeado; instrumentos de esos mercados no se refrescan hasta ampliar la tabla (fallo silencioso y seguro, no error).
- **Ambigüedad del literal de divisa en peniques**: si Twelve Data cambia el literal exacto que usa para GBX/peniques, la detección se rompe. Vale la pena loguear (nivel WARN) cualquier divisa de respuesta que no coincida con `Security.currency` y no sea el caso GBX conocido, para detectar mercados nuevos con el mismo problema.
- **Sin SLA formal en el free tier**: menos frágil que el endpoint no oficial de Yahoo (API documentada y versionada), pero sigue siendo un free tier — si el proveedor falla o cambia condiciones, el mismo aislamiento por puerto (`PriceProviderPort`) permite sustituirlo sin rediseñar el resto.

## 7. Puntos de fricción con las otras dos ramas paralelas

- **`SecurityController.java`**: si "posiciones cerradas" o "historial de imports" también tocan controllers del módulo, no debería haber solape de fichero (cada una tiene su propio controller natural), pero revísalo al mergear.
- **`docs/prd/inversiones.md`**: las tres ramas lo tocan → conflicto de merge seguro, textual y fácil de resolver a mano; no dejar el merge para el final de las tres, mergear una rama cada vez como ya se decidió.
- **`app.routes.ts` / menú lateral**: esta tarea **no** añade ninguna pantalla nueva (el botón vive en el toolbar ya existente), así que no debería tocar rutas ni menú — si en algún momento del desarrollo sientes la tentación de añadir una pantalla de "estado de precios", para: no es parte del alcance 1.1.
- **Migraciones Flyway**: esta rama no crea ninguna (§2.5) — cero riesgo de colisión de número con las otras dos.
