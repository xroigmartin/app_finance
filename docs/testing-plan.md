# Plan de testing del backend — seguimiento entre sesiones

Documento vivo: **actualízalo al final de cada etapa** (marca casillas y ajusta "Estado actual / Próximo paso"). Una sesión nueva debe poder retomar leyendo solo este fichero.

> ⚠️ **Regla de continuidad (preferencia del usuario):** antes de empezar **cualquier etapa nueva**, pregunta primero al usuario si se continúa con el plan. No arranques la siguiente etapa sin su confirmación explícita.

## Objetivo y exigencia

Tres niveles de tests, **en orden y con puerta de cobertura ≥ 90 % antes de pasar al siguiente**:

1. **JUnit + Mockito** (unit puro + services/controllers con repos mockeados).
2. **Testcontainers (`@DataJpaTest` con Postgres)** — queries `@Query` y constraints reales.
3. **MockMvc (`@WebMvcTest`)** — contrato HTTP, validaciones `@Valid`, `GlobalExceptionHandler`.

Cobertura medida con **JaCoCo** (`mvn test` genera `target/site/jacoco/index.html`). Listón: **90 %** (por encima del 80 % estándar, a propósito).

## Convenciones

- Tests en `src/test/java` con paquete espejo. AssertJ (`assertThat`, `assertThatThrownBy`).
- Helper `com.xroig.finance.Fixtures` con builders (`account`, `category`, `expense`, …).
- Un commit por etapa (mensaje en español). Sin push (repo local).

---

## Nivel 1 — JUnit + Mockito

- [x] **E0 · Infra**: JaCoCo en `pom.xml`; `mvn test` verde (el informe se generará al haber tests).
- [x] **E1 · Unit puro**: `ImportFileParser` (24), `RecategorizationService.matches` (5), `RecurringBudget` (4) + `Fixtures`. 33 tests verdes.
- [x] **E2a · Service**: `RecurringBudgetService` (upsert/reconciliación, parseAmounts, bitmask, get/delete). 15 tests verdes.
- [x] **E2b · Service**: `ImportService` (resolveType/Category/Date, buildDescription, dedup, errores por fila, transfers). 25 tests verdes.
- [x] **E2c · Service**: `RecategorizationService.applyRule` (5) + `BudgetService.annual` (6). 11 tests verdes.
- [x] **E2d · Service**: `DashboardService` (summary, balanceUntil, series, byCategory, budgetStatus). 10 tests verdes.
- [x] **E3a · Controller**: `CategoryController` (padre/subcategoría/ámbito/recurrencia/borrado). 20 tests verdes.
- [x] **E3b · Controller**: `TransactionController` (normal + devoluciones). 16 tests verdes.
- [x] **E3c · Controller**: `BudgetController` (duplicados, hoja, copy). 15 tests verdes.
- [x] **E3d · Controller**: `AccountController` (6) + `TransferController` (7) + `CategoryRuleController` (5). 18 tests verdes.
- [x] **E3e · Transversal**: `GlobalExceptionHandler`. 6 tests verdes.
- [x] **E4 · Puerta 90 %**: JaCoCo **90,1 % global** (5391/5983) y **98,3 % sin bootstrap/config** (5391/5487). Añadidos `DashboardController` (9) y `RecurringBudgetController` (3) que estaban a 0 %, y huecos de rama en `CategoryController` (→ 98,8 %). 184 tests verdes. Lo único a 0 % es bootstrap no cubrible por Mockito (`DataSeeder` 442 instr., `WebConfig` 34, `FinanceApplication` 8) — aplazado a un test de contexto/integración en niveles posteriores.

## Nivel 2 — Testcontainers (`@DataJpaTest` Postgres)

- [x] **T0 · Infra**: dependencias Testcontainers (`testcontainers-postgresql` + `testcontainers-junit-jupiter`, Boot 4 gestiona la versión 2.0.2) + `spring-boot-data-jpa-test` + `spring-boot-testcontainers`; clase base `PostgresTestBase` (`@DataJpaTest` + `@ServiceConnection`, contenedor `postgres:17-alpine` estático compartido) y `SchemaSmokeTest` verde (Flyway aplica las 6 migraciones, `ddl-auto=validate` pasa).
  - **Notas de Boot 4**: las slices de test viven en módulos nuevos (`spring-boot-data-jpa-test` → `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`; `AutoConfigureTestDatabase` en `org.springframework.boot.jdbc.test.autoconfigure`), no en los paquetes 3.x. Testcontainers 2.0 renombró los artefactos a `testcontainers-<módulo>`.
- [x] **T1**: `TransactionRepository` — neteo de devoluciones, roll-up de subcategorías, `extract(month/year)`, scoping por cuenta. 9 tests verdes. **Cazó un bug real**: `sumByCategory` descartaba las categorías de primer nivel por un *inner join* implícito al padre (`fix` aplicado: `left join` explícito + PRD dashboard). Ajuste de infra: `PostgresTestBase` pasó al patrón **singleton container** (el `@Container` estático paraba el contenedor en el `afterAll` de la primera clase y dejaba el contexto cacheado apuntando a un puerto muerto).
- [x] **T2**: recurrencias — `RecurringBudgetReconciliationTest` (servicio importado en el slice `@DataJpaTest`). 6 tests verdes. Demuestra contra Postgres real lo que Mockito no podía: editar el importe manteniendo el mes de vigencia **no** viola `uq_amount_vigencia` (la reconciliación en sitio evita el insert-antes-de-delete del flush de Hibernate), las constraints `uq_amount_vigencia`/`chk_recurring_months`/`category_id` único se aplican de verdad (`DataIntegrityViolationException`), y borrar la recurrencia hace cascada al histórico de importes. Patrón: `EntityManager.flush()/clear()` entre upserts para recargar de BD y forzar el SQL.
- [x] **T3**: `ConstraintViolationsTest` — provoca las violaciones en el esquema real y pasa la `DataIntegrityViolationException` por el `GlobalExceptionHandler` real (lo que E3e no podía: confirma que los marcadores del handler son los **nombres reales** de las constraints). 4 tests verdes. Cubre `ux_categories_name_scope` (duplicado en mismo ámbito/padre → 409; mismo nombre en otra cuenta o bajo otro padre → permitido) y `uq_monthly_budgets_account_category_period` (duplicado de periodo → 409; otro mes → permitido). `uq_amount_vigencia` y `recurring_budgets_category_id` ya cubiertas en T2. **Hallazgo**: `accounts.name` **no** tiene unique (el `unique` de V1 era de `categories`, sustituido en V3 por el índice de ámbito), así que cuentas con nombre repetido son legales.
- [x] **T4 · Puerta 90 %** de la capa de repositorio. **Matiz**: los repositorios son interfaces Spring Data (sin bytecode propio: los `@Query` viven en anotaciones y los proxies los genera el contenedor), así que JaCoCo reporta **0 instrucciones** para el paquete `repository` — la cobertura por instrucción no aplica a esta capa. La puerta se reinterpreta como **toda query custom ejercitada contra Postgres real**, y se cumple: los **9 `@Query` de `TransactionRepository`** (T1 + `search` con sus filtros opcionales y orden `date desc, id desc`), `CategoryRepository.findVisibleForAccount`/`findByParentId` (`CategoryRepositoryTest`), `TransferRepository.search`/`totalInUntil`/`totalOutUntil` (`TransferRepositoryTest`) y `RecurringBudgetRepository.findActiveByAccountWithAmounts`/`findAllActiveWithAmounts` (`RecurringBudgetRepositoryTest`); `findByCategoryIdWithAmounts` ya quedaba ejercitado contra BD real en el test de reconciliación T2. Las constraints las cubren T2/T3. **JaCoCo global 90,2 %** (5387/5971). 214 tests verdes.

## Nivel 3 — MockMvc (`@WebMvcTest`)

- [x] **M0 · Infra**: patrón `@WebMvcTest` + beans mockeados, validado con `AccountControllerMvcTest` (6 tests verdes) sobre el controller CRUD más simple. Demuestra lo que los tests Mockito de E3 no tocaban: routing, (de)serialización JSON, validación `@Valid`→400 y que el `@RestControllerAdvice` (`GlobalExceptionHandler`) participa en el slice mapeando `DataIntegrityViolationException`→409 `application/problem+json`.
  - **Notas de Boot 4**: el slice `@WebMvcTest` vive en el módulo nuevo `spring-boot-webmvc-test` (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`), añadido como dependencia de test (igual que `spring-boot-data-jpa-test` en T0). `@MockBean`/`@SpyBean` están eliminados: se usa **`@MockitoBean`** (`org.springframework.test.context.bean.override.mockito.MockitoBean`). Aserciones con **`MockMvcTester`** (API AssertJ, Spring 7) en vez del `MockMvc.perform(...).andExpect(...)` clásico, para no salir del idioma AssertJ del resto de la suite.
- [ ] **M1..Mn**: contrato HTTP por controller (status, JSON, `@Valid` → 400, advice → 409).
- [ ] **M · Puerta 90 %** global de la aplicación.

---

## Estado actual / Próximo paso

- **Estado**: **Nivel 2 cerrado** (T0–T4) y **M0 hecho** (infra Nivel 3). 220 tests verdes en la suite completa. `AccountControllerMvcTest` fija el patrón `@WebMvcTest` + `@MockitoBean` + `MockMvcTester`.
- **Próximo paso**: **M1..Mn** — contrato HTTP por controller (status, JSON, `@Valid`→400, advice→409) reutilizando el patrón de M0, empezando por los controllers con más superficie HTTP (`TransactionController`, `CategoryController`, `BudgetController`). Es continuación de la misma etapa (Nivel 3 ya arrancado).
