# Plan de testing del frontend — seguimiento entre sesiones

Documento vivo: **actualízalo al final de cada checkpoint** (marca casillas, añade hallazgos y ajusta "Estado actual / Próximo paso"). Una sesión nueva debe poder retomar leyendo solo este fichero. Hermano de `docs/testing-plan.md` (backend, ya cerrado al 99 %); este cubre el frontend, que hoy solo tiene un test trivial (`app.spec.ts`).

> ⚠️ **Regla de continuidad (preferencia del usuario):** antes de empezar **cualquier checkpoint nuevo**, pregunta primero al usuario si se continúa con el plan. No arranques el siguiente checkpoint sin su confirmación explícita.

## Objetivo y exigencia

Dos capas de test, en este orden:

1. **Vitest** — unitarios de componentes/servicios Angular (sustituye a Karma/Jasmine, que se elimina por completo).
2. **Playwright** — E2E contra la **app real** (backend Spring Boot + Postgres reales, con reset/seed determinista antes de cada suite — nada de mocks de red).

**Cobertura mínima exigida: 80–85 % global**, excluyendo del cómputo lo intrínsecamente no cubrible (`main.ts`, `**/*.spec.ts`, interfaces puras de `models.ts` sin código ejecutable). El umbral se **ratchetea** progresivamente por checkpoint (se sube según avanza la suite), no se fija al final de golpe.

## Decisiones ya tomadas (no reabrir sin motivo)

- **E2E contra backend+Postgres reales**, no contra mocks: es coherente con cómo ya se verifica manualmente el proyecto ("captura headless contra la app real", ver commits `ade0dfd`/`997b1ed`) y es el único enfoque que habría detectado el bug real de gráficos en blanco que motivó esta suite.
- **Karma se elimina**, no convive con Vitest.
- Los tests unitarios de `dashboard.ts`/`investments.ts` **mockean el constructor `Chart`** y verifican solo el mapeo de datos (labels/datasets/colores); **no prueban que el canvas pinte de verdad** — eso lo cubre el E2E de CP8 con una assertion explícita sobre el `<canvas>` real.
- **Angular se subió de 20.3.28 a 22.0.7 a mitad de este plan** (commits `ae96ea7`→21, `583cab4`→22, más `3f197ca` añadiendo `jsdom`), con Node en `24.18.0` (LTS Krypton) y TypeScript en `6.0.3`. Esto cambia CP1/CP7 de forma importante — ver notas ahí. La build de producción (`ng build`) y el `ng serve` real se verificaron tras la subida: correctos. El único fallo encontrado fue de test tooling (`matchMedia` no existe en jsdom), no de la migración de Angular en sí.

## Convenciones

- Tests unitarios junto al archivo que cubren (`*.spec.ts`, ya es el patrón Angular existente).
- Tests E2E en una carpeta `e2e/` en la raíz de `frontend/`, un spec por dominio.
- Mocks de `ApiService` vía spies de Vitest (`vi.fn()`), no `HttpClientTestingModule` salvo que se decida lo contrario al implementar.
- Un commit por checkpoint (mensaje en español, sin trailer de Claude, sin push — repo local).
- Informe de cobertura: `frontend/coverage/index.html` (Vitest + v8), equivalente al `target/site/jacoco/index.html` del backend.

---

## CP0 — Preparación

- [x] Confirmar contra la versión instalada el schematic/flags reales para el builder de test con runner Vitest (`@angular/build:unit-test`) — no asumir sintaxis sin verificarla.
- [x] Listar exclusiones de cobertura definitivas (`main.ts`, `**/*.spec.ts`; no hay `environments/**` en este proyecto; `models.ts` no aporta statements ejecutables, no necesita exclusión explícita).
- Commit: ninguno (solo investigación).

**Hallazgos — ronda 1, contra Angular 20.3.28 (superados por la ronda 2 tras subir a Angular 22, ver más abajo):**

- El builder `@angular/build:unit-test` con `runner: vitest` existía ya en 20.3.28 pero marcado `[EXPERIMENTAL]`, y su `schema.json` **no tenía ningún campo de umbral de cobertura** (`generateCoverageOption` en `builder.js` solo exponía `enabled/excludeAfterRemap/include/reporter`). Además invocaba a Vitest con `config: false`, ignorando a propósito cualquier `vitest.config.ts` del proyecto — así que ni siquiera un `vitest.config.ts` propio con `coverage.thresholds` se podía colar.

**Hallazgos — ronda 2, contra Angular 22.0.7 (estado real actual, verificado en `node_modules/@angular/build@22.0.7/src/builders/unit-test/schema.json` ya instalado, no solo en el registro):**

- El schema del builder creció mucho: ahora incluye `coverage`, `coverageInclude`, `coverageExclude`, `coverageReporters`, `coverageWatermarks` y, la pieza que faltaba, **`coverageThresholds`** (objeto con `statements/branches/functions/lines` + `perFile`), cuya descripción dice literalmente: *"If thresholds are not met, the builder will exit with an error."* Es un gate real y nativo — **ya no hace falta el script propio que se había planteado para CP7**.
- Ojo con el **rename de propiedades entre v20 y v22**: `codeCoverage`→`coverage`, `codeCoverageExclude`→`coverageExclude`, `codeCoverageReporters`→`coverageReporters`. Cualquier ejemplo o doc que se consulte sobre la v20 usa los nombres viejos.
- Sigue marcado `[EXPERIMENTAL]` en `builders.json` incluso en 22.0.7 — más maduro, pero formalmente Angular no lo da por estable todavía.
- `ng update` **ya migró `angular.json` solo** al subir a Angular 22: el target `test` pasó de `@angular/build:karma` a `@angular/build:unit-test` con `runner: "vitest"` y `buildTarget: ":build:testing"` (una configuración `testing` nueva añadida a `architect.build.configurations` con los polyfills de zone.js). Es decir, **buena parte de CP1 ya está hecha por el propio `ng update`**, sin que lo hiciéramos nosotros a propósito.
- `ng update` instaló `vitest` pero **no `jsdom`** (sin él, `ng test` falla con *"A DOM environment is required"*) — ya instalado y comiteado (`3f197ca`).
- Los paquetes `karma`, `karma-chrome-launcher`, `karma-coverage`, `karma-jasmine`, `karma-jasmine-html-reporter` siguen en `package.json` como huérfanos (nadie los usa ya) — pendiente desinstalarlos en CP1.
- Al ejecutar `npm test` de verdad (con Node 24.18.0), el único test existente falla con `TypeError: matchMedia is not a function` — jsdom no implementa `window.matchMedia`, que usa `ThemeService`. No es un problema de la migración de Angular; es exactamente el hueco de entorno de test que toca tapar en CP1 con un `setupFiles`.

## CP1 — Migración del runner: Karma → Vitest

- [x] Cambiar builder `test` en `angular.json` a Vitest — **ya hecho por `ng update` al subir a Angular 22**, no hace falta tocarlo.
- [x] Instalar `jsdom` (`3f197ca`); `vitest` ya lo instaló `ng update`.
- [x] Desinstalar `karma`, `karma-chrome-launcher`, `karma-jasmine`, `karma-jasmine-html-reporter`, `karma-coverage`. **Además** se desinstalaron `@types/jasmine` y `jasmine-core`, huérfanos también: `ng update` ya había cambiado `tsconfig.spec.json` de `"types": ["jasmine"]` a `"types": ["vitest/globals"]` al subir a Angular 22, así que no los usaba nadie.
- [x] Añadido `src/test-setup.ts` (mock de `window.matchMedia`, que jsdom no implementa) referenciado en `architect.test.options.setupFiles`; incluido también en `tsconfig.spec.json`.
- [x] `app.spec.ts` pasa sin tocarlo — Jasmine/Vitest comparten la API `describe/it/expect` y el builder expone `globals: true`.
- [x] Fijado `"coverage": true` + `coverageThresholds` + `coverageExclude` en `angular.json`. **Corrección sobre lo previsto en CP0**: sí hace falta instalar `@vitest/coverage-v8` como paquete aparte (el builder solo activa/desactiva y configura la cobertura; el provider v8 en sí no viene incluido) — sin él falla con *"Code coverage requires either @vitest/coverage-v8 or @vitest/coverage-istanbul to be installed"*.
- [x] **`coverageInclude: ["src/app/**/*.ts"]`** — decisión no listada originalmente en el plan: sin esto, v8 solo cuenta los ficheros realmente importados por los tests que existen (con un solo test, eran 2 ficheros: `app.ts` y `theme.service.ts`, dando un falso "46 %"). Con `coverageInclude` forzando todo `src/app`, los ficheros nunca importados cuentan como 0 % — así el "global" del umbral es real y comparable al criterio de JaCoCo en el backend (cuenta todo el código, no solo lo tocado).
- [x] `npm test` verde con un solo test. **Baseline real medida**: 1 % statements (13/1296), 0.71 % branches (4/560), 0.69 % functions (3/433), 0.99 % lines (11/1103). Umbral fijado a `{statements:1, branches:0, functions:0, lines:0}` — por debajo de la baseline real para que el gate exista y sea real desde ya (falla si alguien borra el único test), y se ratchetea al alza en cada checkpoint siguiente.
- Commit: "termina la migración del runner de tests unitarios a Vitest (jsdom, mock de matchMedia, limpieza de Karma, umbral nativo)".

## CP2 — Andamiaje E2E: Playwright + entorno de datos determinista

- [x] Instalar `@playwright/test`, `playwright.config.ts`, proyecto headless Chromium (`npx playwright install chromium`, descargado y funcionando).
- [x] `globalSetup`/`globalTeardown` de Playwright + siembra de un dataset fijo vía la API real (no INSERT SQL): cuentas, movimientos en dos meses, cartera de inversión con un título y una posición.
- [x] Test de humo: la app carga, muestra el dashboard con datos reales del seed, navega a Inversión y ve la cartera sembrada.
- Commit: "añade infraestructura E2E con Playwright y seed determinista contra backend real".

**Cambio de diseño importante sobre lo previsto (no se ejecutó el reset literal del plan original):** el `docker-compose.yml` de este repo solo tiene **un** servicio `db` (puerto 5432, volumen `finance-data`) — es el mismo Postgres de desarrollo que ya tenía datos reales (cartera "Interactive Broker", movimientos reales, etc.). Hacer `docker compose down -v db` ahí habría borrado esos datos en cada ejecución de la suite E2E. Se decidió con el usuario **aislar por completo el entorno E2E**:

- **`docker-compose.e2e.yml`** nuevo en la raíz, fichero de compose *separado* (no un segundo servicio en el mismo `docker-compose.yml`) con `name: finance-e2e` explícito — así Docker Compose no comparte proyecto/red con el `docker-compose.yml` de dev (se comprobó en vivo: sin el `name:` explícito, ambos ficheros caían por defecto en el mismo nombre de proyecto derivado del directorio y Compose los veía como "contenedores huérfanos" el uno del otro). Servicio `db-e2e`, puerto **5434**, volumen propio `finance-e2e-data`.
- **Backend e2e**: proceso `mvn spring-boot:run` propio lanzado por `e2e/global-setup.ts` con `FINANCE_DB_PORT=5434` y `SERVER_PORT=8081` (nunca toca el backend de dev en 8080). Se mata por grupo de proceso en `global-teardown.ts` (mismo patrón que `app.sh` con `mvn`).
- **Frontend e2e**: el propio `webServer` de Playwright arranca `ng serve --port 4201 --proxy-config proxy.conf.e2e.json` (nuevo fichero, apunta a `:8081`), aislado del `ng serve` de dev en 4200.
- **Reset real**: `global-setup.ts` hace `docker compose -f docker-compose.e2e.yml down -v && up -d --wait` antes de cada ejecución; `global-teardown.ts` hace `down -v` otra vez al terminar, dejando cero rastro. Verificado en vivo dos veces seguidas (con y sin el `name:` explícito) y confirmado con `docker ps`/`docker volume ls` que `finance-db`/`finance-data` (el stack de dev) no se tocan en ningún momento.
- Fixture en `e2e/fixtures/seed.ts`: crea 2 cuentas, usa las categorías globales por defecto que ya siembra el `DataSeeder` del backend (Nómina/Vivienda/Alimentación), 5 movimientos en 2 meses, 1 cartera con 1 depósito + 1 compra de un valor de prueba.

## CP3 — Unit tests: núcleo transversal

- [x] `api.service.ts` (310 líneas): un test por método (URL, verbo, query params opcionales presentes/ausentes, body, `FormData` en las importaciones). Caso especial cubierto: `getRecurrence` traga el error (404 sin recurrencia) y emite `null` — es lógica real, no solo passthrough. Resultado: 95.9% statements / 72.2% branches en este fichero (quedan combinaciones de params opcionales sin agotar en algún método casi duplicado; se acepta el margen, no compensa test-a-test para los últimos puntos de rama).
- [x] `theme.service.ts`: `initial()` (SO claro/oscuro, valor guardado válido/ inválido en localStorage), `toggle()` (alterna, persiste, aplica `data-theme`), los 7 getters de color de gráfico en ambos temas. `matchMedia` mockeado por test con `vi.spyOn`.
- [x] `amount.ts`: número directo, coma/punto decimal, espacios, cadena vacía/inválida → `NaN`.
- [x] `app.routes.ts`: los tres redirects (`''`→dashboard, `transfers`→transactions, `**`→dashboard) y que cada `loadComponent` resuelve a la clase de componente esperada (comparando la clase importada directamente, no por `.name`: el bundler renombra clases duplicadas en el grafo de módulos — p. ej. `DashboardPage2` — así que comparar por nombre de cadena es frágil).
- [x] `app.ts`: estado inicial de `collapsed` según `localStorage`, `toggle()` alterna y persiste, `toggleTheme()` delega en `ThemeService` (inyectado con `TestBed.inject`, no accediendo al campo `protected theme` del componente).
- [x] Ratchet del umbral de cobertura al nivel alcanzado: **18% statements / 14% branches / 18% functions / 16% lines** (medido: 18.16/14.28/18.93/16.72).
- Commit: "tests unitarios de ApiService, ThemeService y utilidades del núcleo".

## CP4 — Unit tests: páginas simples

- [x] `transfers.ts`, `accounts.ts`, `budgets.ts`: carga, filtros/validación, CRUD contra `ApiService` mockeado (`{ provide: ApiService, useValue: ... }`, sin `HttpClientTestingModule`), getters derivados. Los diálogos nativos (`<dialog>` de Cuentas) se stubean asignando un `ElementRef` falso a la propiedad `@ViewChild` directamente, sin renderizar la plantilla completa — coherente con la filosofía del plan de testear lógica, no rendering (eso es cosa del E2E).
- [x] `accounts.ts` 100% statements, `budgets.ts` 99% statements tras el fix de abajo.
- [x] Ratchet del umbral: **35% statements / 27% branches / 34% functions / 33% lines** (medido: 35/27.14/34.64/33.99).
- Commit: "tests unitarios de Transferencias, Cuentas y Presupuestos".

**Bug real encontrado y corregido (TDD real, no solo test-writing) en `budgets.ts#onCellEdit`:** en las dos ramas de error (importe inválido, y fallo al guardar), el código fijaba `this.error = '...'` y **luego** llamaba a `this.load()` — pero `load()` empieza con `this.error = ''` como primera línea, así que se borraba a sí mismo en el mismo tick, síncrono, sin ningún `await` de por medio. El usuario nunca llegaba a ver esos mensajes de error en la pantalla de Presupuestos (ni con importe no válido ni con fallo de guardado). Se detectó porque el test escrito para ese comportamiento (rojo) fallaba con `error` vacío en vez del mensaje esperado. Arreglo: invertir el orden (`this.load(); this.error = '...';`) en ambos sitios — `load()` sigue limpiando el error al arrancar una carga normal, pero ya no pisa el mensaje que se acaba de fijar justo después.

## CP5 — Unit tests: páginas complejas

- [x] `categories.ts` (jerarquía padre/subcategoría, ámbito global/cuenta, recurrencia, reglas de categorización): 98.2% statements.
- [x] `transactions.ts` (fusión y orden de movimientos+transferencias, cálculo de pendiente de devolución, categorías válidas por tipo/ámbito, conversión transacción↔transferencia al editar, borrado en cascada de devoluciones): 97.9% statements.
- [x] Ratchet del umbral: **61% statements / 56% branches / 62% functions / 61% lines** (medido: 61.29/56.42/62.81/61.67 — salto grande porque estas dos páginas son las más grandes del proyecto).
- Commit: "tests unitarios de Categorías y Movimientos".

**Hallazgo de infraestructura de test (no un bug de producto):** `CategoriesPage` y `TransactionsPage` usan la API `viewChild()` de señales de Angular (no el decorador `@ViewChild` clásico) para sus `<dialog>`. A diferencia de lo asumido en CP4 con `AccountsPage`, aquí el componente **sí renderiza su plantilla al crearse en el test** (con `changeDetection: ChangeDetectionStrategy.Eager`, aplicado por el codemod de la subida a Angular 22, el árbol se pinta sin esperar a un `fixture.detectChanges()` explícito) — así que el `<dialog>` real de jsdom queda resuelto y sus métodos imperativos `showModal()`/`close()` se invocan de verdad. jsdom no los implementa (`TypeError: ... is not a function`). Se añadió un stub mínimo de ambos en `src/test-setup.ts` (mismo patrón que el mock de `matchMedia`), reutilizable por cualquier página con `<dialog>` a partir de ahora.

## CP6 — Unit tests: Dashboard e Inversión

- [ ] `dashboard.ts` y `investments.ts`: mock del constructor `Chart`, verificación de la lógica de mapeo de datos que se le pasa (labels, datasets, colores por signo/posición) — no el renderizado de píxeles.
- [ ] Ratchet del umbral hacia el objetivo 80–85 %.
- Commit: "tests unitarios de Dashboard e Inversión".

## CP7 — Unit tests: diálogos + puerta de cobertura definitiva

- [ ] `import-dialog.ts`, `flex-import-dialog.ts`, `investment-transaction-dialog.ts`: validación de formulario, alta/edición, errores de API.
- [ ] Subir `coverageThresholds` en `angular.json` (`architect.test.options`) al valor definitivo **80–85 %** en `statements/branches/functions/lines`. Es un gate nativo del builder (confirmado en CP0: "if thresholds are not met, the builder will exit with an error") — `npm test` falla solo, sin script propio de por medio.
- Commit: "tests unitarios de diálogos CRUD y fija el umbral de cobertura al 80-85 %".

## CP8 — E2E: recorridos críticos por dominio

- [ ] Un spec Playwright por dominio: dashboard, inversión, movimientos, categorías, presupuestos, cuentas, transferencias — navegación, alta/edición/borrado vía UI real, cambio de tema, sidebar colapsable.
- [ ] **Regresión del bug original**: assertion explícita en dashboard e inversión de que cada `<canvas>` tiene contenido real (bounding box > 0, o inspección de que la instancia Chart.js registrada tiene datasets no vacíos).
- Commit: "añade suite E2E de recorridos críticos por dominio".

## CP9 — Documentación

- [ ] Actualizar `CLAUDE.md`: la sección de frontend ya no dice "Karma/Jasmine; only app.spec.ts exists"; documentar `npm test` (Vitest), `npm run test:e2e` (Playwright), el mecanismo de seed/reset y el umbral de cobertura exigido.
- Commit: "actualiza CLAUDE.md con la nueva infraestructura de tests".

## Nota sobre TDD

El `CLAUDE.md` del proyecto exige TDD estricto para todo desarrollo nuevo. Este trabajo, al escribir tests sobre código de producción ya existente, es **retrofit de tests de caracterización**, no red-green-refactor clásico (no hay fase roja que diseñe el comportamiento; el comportamiento ya está fijado). Cualquier bug real que aparezca por el camino (p. ej. el de los gráficos en blanco) sí se corrige con TDD real: primero el test que lo reproduce en rojo, luego el fix.

---

## Estado actual / Próximo paso

- **Estado**: **CP0–CP5 cerrados.** 217 tests unitarios verdes, cobertura real 61.29/56.42/62.81/61.67% (statements/branches/functions/lines), umbral fijado a ese nivel. Ya solo quedan por cubrir dashboard/inversión (con gráficos) y los diálogos.
- **Próximo paso**: confirmar con el usuario y arrancar CP6 (unit tests de Dashboard e Inversión, con el mock de Chart.js).
