# Plan de testing del backend — seguimiento entre sesiones

Documento vivo: **actualízalo al final de cada etapa** (marca casillas y ajusta "Estado actual / Próximo paso"). Una sesión nueva debe poder retomar leyendo solo este fichero.

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
- [ ] **E1 · Unit puro**: `ImportFileParser` (parseAmount/parseDate/normalizeHeader/parse), `RecategorizationService.matches`, entidad `RecurringBudget` (appliesToMonth/amountAt). + `Fixtures`.
- [ ] **E2a · Service**: `RecurringBudgetService` (upsert/reconciliación, parseAmounts, bitmask, get/delete).
- [ ] **E2b · Service**: `ImportService` (resolveType/Category/Date, buildDescription, dedup, errores por fila, transfers).
- [ ] **E2c · Service**: `RecategorizationService.applyRule` + `BudgetService.annual`.
- [ ] **E2d · Service**: `DashboardService` (summary, balanceUntil, series, byCategory).
- [ ] **E3a · Controller**: `CategoryController` (padre/subcategoría/ámbito/recurrencia/borrado).
- [ ] **E3b · Controller**: `TransactionController` (normal + devoluciones).
- [ ] **E3c · Controller**: `BudgetController` (duplicados, hoja, copy).
- [ ] **E3d · Controller**: `AccountController` + `TransferController` + `CategoryRuleController`.
- [ ] **E3e · Transversal**: `GlobalExceptionHandler`.
- [ ] **E4 · Puerta 90 %**: medir JaCoCo, rellenar huecos hasta ≥ 90 % de lo cubrible por este nivel.

## Nivel 2 — Testcontainers (`@DataJpaTest` Postgres)

- [ ] **T0 · Infra**: dependencias Testcontainers (postgresql + junit-jupiter), clase base con contenedor.
- [ ] **T1**: `TransactionRepository` — neteo de devoluciones en las 7 sumas, roll-up de subcategorías, `extract(month)`.
- [ ] **T2**: recurrencias — reconciliación contra `uq_amount_vigencia` (el bug que arreglamos).
- [ ] **T3**: constraints UNIQUE que alimentan `GlobalExceptionHandler`.
- [ ] **T4 · Puerta 90 %** de la capa de repositorio.

## Nivel 3 — MockMvc (`@WebMvcTest`)

- [ ] **M0 · Infra**: patrón `@WebMvcTest` + beans mockeados.
- [ ] **M1..Mn**: contrato HTTP por controller (status, JSON, `@Valid` → 400, advice → 409).
- [ ] **M · Puerta 90 %** global de la aplicación.

---

## Estado actual / Próximo paso

- **Estado**: E0 hecha (JaCoCo cableado, `mvn test` verde).
- **Próximo paso**: E1 — unit puro (`ImportFileParser`, `RecategorizationService.matches`, `RecurringBudget`) + helper `Fixtures`.
