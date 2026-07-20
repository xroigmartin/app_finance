# Plan de testing del frontend — seguimiento entre sesiones

Documento vivo: **actualízalo al final de cada checkpoint** (marca casillas, añade hallazgos y ajusta "Estado actual / Próximo paso"). Una sesión nueva debe poder retomar leyendo solo este fichero. Hermano de `docs/testing-plan.md` (backend, ya cerrado al 99 %); este cubre el frontend, que hoy solo tiene un test trivial (`app.spec.ts`).

> ⚠️ **Regla de continuidad (preferencia del usuario):** antes de empezar **cualquier checkpoint nuevo**, pregunta primero al usuario si se continúa con el plan. No arranques el siguiente checkpoint sin su confirmación explícita.

## Objetivo y exigencia

Dos capas de test, en este orden:

1. **Vitest** — unitarios de componentes/servicios Angular (sustituye a Karma/Jasmine, que se elimina por completo).
2. **Playwright** — E2E contra la **app real** (backend Spring Boot + Postgres reales, con reset/seed determinista antes de cada suite — nada de mocks de red).

**Cobertura mínima exigida: 80–85 % global** (Vitest + `@vitest/coverage-v8`), excluyendo del cómputo lo intrínsecamente no cubrible (`main.ts`, `environments/**`, `**/*.spec.ts`, interfaces puras de `models.ts` sin código ejecutable). El umbral se **ratchetea** progresivamente por checkpoint (se sube según avanza la suite), no se fija al final de golpe.

## Decisiones ya tomadas (no reabrir sin motivo)

- **E2E contra backend+Postgres reales**, no contra mocks: es coherente con cómo ya se verifica manualmente el proyecto ("captura headless contra la app real", ver commits `ade0dfd`/`997b1ed`) y es el único enfoque que habría detectado el bug real de gráficos en blanco que motivó esta suite.
- **Karma se elimina**, no convive con Vitest.
- Los tests unitarios de `dashboard.ts`/`investments.ts` **mockean el constructor `Chart`** y verifican solo el mapeo de datos (labels/datasets/colores); **no prueban que el canvas pinte de verdad** — eso lo cubre el E2E de CP8 con una assertion explícita sobre el `<canvas>` real.

## Convenciones

- Tests unitarios junto al archivo que cubren (`*.spec.ts`, ya es el patrón Angular existente).
- Tests E2E en una carpeta `e2e/` en la raíz de `frontend/`, un spec por dominio.
- Mocks de `ApiService` vía spies de Vitest (`vi.fn()`), no `HttpClientTestingModule` salvo que se decida lo contrario al implementar.
- Un commit por checkpoint (mensaje en español, sin trailer de Claude, sin push — repo local).
- Informe de cobertura: `frontend/coverage/index.html` (Vitest + v8), equivalente al `target/site/jacoco/index.html` del backend.

---

## CP0 — Preparación

- [ ] Confirmar contra la versión instalada (`@angular/cli` 20.3.28 / `@angular/build`) el schematic/flags reales para el builder de test con runner Vitest (`@angular/build:unit-test`, developer preview) — no asumir sintaxis sin verificarla.
- [ ] Listar exclusiones de cobertura definitivas (`main.ts`, `environments/**`, `models.ts` si no aporta statements ejecutables).
- Commit: ninguno (solo investigación).

## CP1 — Migración del runner: Karma → Vitest

- [ ] Cambiar builder `test` en `angular.json` a Vitest; instalar `vitest` + `@vitest/coverage-v8`.
- [ ] Desinstalar `karma`, `karma-chrome-launcher`, `karma-jasmine`, `karma-jasmine-html-reporter`, `karma-coverage`.
- [ ] Migrar `app.spec.ts` a Vitest (TestBed se mantiene, solo cambia el runner).
- [ ] Configurar `vitest.config` con umbral de cobertura bajo (la baseline real de hoy, ~0 %) y exclusiones de CP0.
- [ ] `npm test` verde con un solo test.
- Commit: "migra runner de tests unitarios de Karma a Vitest".

## CP2 — Andamiaje E2E: Playwright + entorno de datos determinista

- [ ] Instalar `@playwright/test`, `playwright.config.ts`, proyecto headless Chromium.
- [ ] `globalSetup` de Playwright: reset de BD (`docker compose down -v db && up -d db`, esperar Flyway/backend listos) + siembra de un dataset fijo **vía la API real** (no INSERT SQL, para no saltarse invariantes de dominio): cuentas, categorías, movimientos, cartera de inversión con títulos/operaciones.
- [ ] Test de humo: la app carga y navega al dashboard.
- Commit: "añade infraestructura E2E con Playwright y seed determinista contra backend real".

## CP3 — Unit tests: núcleo transversal

- [ ] `api.service.ts` (310 líneas): cada método HTTP, mapeo de parámetros, manejo de error.
- [ ] `theme.service.ts`: toggle, persistencia en `localStorage`, `chartText/chartGrid/chartPos/...`.
- [ ] `amount.ts`, `app.routes.ts`, `app.ts`.
- [ ] Ratchet del umbral de cobertura al nivel alcanzado.
- Commit: "tests unitarios de ApiService, ThemeService y utilidades del núcleo".

## CP4 — Unit tests: páginas simples

- [ ] `transfers.ts` (94 líneas), `accounts.ts` (168), `budgets.ts` (197): carga, filtros, CRUD contra `ApiService` mockeado, getters derivados.
- [ ] Ratchet del umbral.
- Commit: "tests unitarios de Transferencias, Cuentas y Presupuestos".

## CP5 — Unit tests: páginas complejas

- [ ] `categories.ts` (357: jerarquía padre/subcategoría, recurrencia, chips por ámbito).
- [ ] `transactions.ts` (381: filtros, import dialog, devoluciones).
- [ ] Ratchet del umbral.
- Commit: "tests unitarios de Categorías y Movimientos".

## CP6 — Unit tests: Dashboard e Inversión

- [ ] `dashboard.ts` y `investments.ts`: mock del constructor `Chart`, verificación de la lógica de mapeo de datos que se le pasa (labels, datasets, colores por signo/posición) — no el renderizado de píxeles.
- [ ] Ratchet del umbral hacia el objetivo 80–85 %.
- Commit: "tests unitarios de Dashboard e Inversión".

## CP7 — Unit tests: diálogos + puerta de cobertura definitiva

- [ ] `import-dialog.ts`, `flex-import-dialog.ts`, `investment-transaction-dialog.ts`: validación de formulario, alta/edición, errores de API.
- [ ] Fijar el umbral definitivo de Vitest en **80–85 %** (gate real: `npm test` falla por debajo).
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

- **Estado**: plan aprobado, sin empezar. Ningún checkpoint iniciado.
- **Próximo paso**: confirmar con el usuario y arrancar por CP0.
