# Plan de migración a arquitectura hexagonal + DDD — seguimiento entre sesiones

Documento vivo: **actualízalo al final de cada etapa** (marca casillas y ajusta "Estado actual / Próximo paso"). Una sesión nueva debe poder retomar leyendo solo este fichero.

> ⚠️ **Regla de continuidad (preferencia del usuario):** antes de empezar **cualquier etapa nueva**, pregunta primero al usuario si se continúa. No arranques la siguiente etapa sin su confirmación explícita.

## Objetivo y decisiones tomadas

Migrar el backend (hoy en capas clásicas controller → service → repository → entidad JPA) a **arquitectura hexagonal (puertos y adaptadores) con DDD**, sin cambiar el comportamiento (la suite de 311 tests al 99 % es la red de seguridad / caracterización).

Decisiones acordadas con el usuario:

| Decisión | Elección | Implicación |
|---|---|---|
| **Objetivo** | Aprender DDD/hexagonal | La pureza es el fin; el boilerplate (mappers, VOs, puertos) es aceptable y deseado. |
| **Pureza** | **DDD completo** | Modelo de dominio **puro** (POJOs/records, value objects, agregados) **separado** de las entidades JPA de persistencia. Mappers en el adaptador. |
| **Estructura** | Un módulo Maven, paquetes por *bounded context* y capa | Sin enforcement del compilador → la dirección de dependencias se vigila con **ArchUnit**. |
| **Alcance** | Toda la app, dominio a dominio | Migración incremental; la app y la suite quedan **verdes en cada etapa**. |

## Principios DDD que aplicamos (y que cambian respecto a hoy)

1. **Dominio puro, sin Spring ni JPA.** Las clases de `domain/` no importan `jakarta.persistence`, `org.springframework`, ni nada de infraestructura. ArchUnit lo verifica.
2. **Agregados con invariantes dentro.** Las reglas que hoy viven en los *controllers* (p. ej. `CategoryController` resolviendo ámbito/jerarquía, `TransactionController.applyRefund` validando la devolución) se mueven **dentro del agregado o de un domain service**. Un agregado no puede construirse/mutarse a un estado inválido.
3. **Referencias entre agregados por identidad, no por objeto.** Hoy `Transaction` tiene `@ManyToOne Account/Category`. En el dominio pasará a tener `AccountId`/`CategoryId` (value objects). Las invariantes que cruzan agregados (p. ej. "la categoría pertenece a la cuenta") se resuelven en el **application service** o en un **domain service**, no navegando objetos. La persistencia JPA sigue usando FKs/joins; el mapper traduce.
4. **Value Objects para los conceptos del lenguaje.** `Money` (BigDecimal + EUR), `DateRange`, identificadores tipados (`AccountId`, `CategoryId`, …), `MonthsMask` (la máscara de 12 bits de recurrencia con comportamiento), `CategoryScope` (global vs. ligada a cuenta).
5. **Persistencia separada del dominio.** Entidad de dominio `Account` (pura) ≠ entidad de persistencia `AccountJpaEntity` (anotada). Un `AccountJpaMapper` convierte en ambos sentidos. **Flyway no se toca**: las migraciones `V1..V6` y `ddl-auto=validate` siguen igual; la entidad JPA mapea a las mismas tablas.
6. **CQRS ligero para lecturas.** El dashboard y los listados de solo lectura **no** pasan por los agregados: tienen **puertos de consulta** (`...QueryPort`) y **read models** (DTOs de lectura) que el adaptador resuelve con SQL/JPQL directo. Reconstruir agregados para pintar gráficas sería caro y forzado.
7. **Errores de dominio como excepciones de dominio.** El dominio lanza excepciones propias (`DomainException` y subtipos: `NotFound`, `ConflictException`, `ValidationException`). Un adaptador (el `@RestControllerAdvice`) las traduce a HTTP (404/409/400). El dominio **no** conoce `HttpStatus` ni `ResponseStatusException` (hoy sí, dentro de los controllers).

## Estructura de paquetes objetivo

Un solo módulo, `com.xroig.finance`, organizado por **bounded context** y, dentro, por capa hexagonal:

```
com.xroig.finance
├── shared/
│   ├── domain/            Money, DateRange, DomainException (+ subtipos), base de IDs tipados
│   └── web/               GlobalExceptionHandler (traduce DomainException → HTTP), config web
├── accounts/
│   ├── domain/            Account (agregado), AccountId, AccountType, AccountRepository (puerto de salida)
│   ├── application/       AccountService (implementa los casos de uso), puertos de entrada (CreateAccount, …)
│   └── infrastructure/
│       ├── persistence/   AccountJpaEntity, AccountJpaRepository (Spring Data), AccountPersistenceAdapter (implementa el puerto), AccountJpaMapper
│       └── web/           AccountController (adaptador de entrada), AccountRequest/Response (DTOs web)
├── categories/            (idéntico patrón; agregado Category con jerarquía/ámbito)
├── transactions/          (agregado Transaction con semántica de devolución)
├── transfers/             (agregado Transfer)
├── budgets/               (agregados Budget y RecurringBudget; matriz anual como read model)
├── categorization/        (CategoryRule + servicio de dominio de recategorización)
├── imports/               (caso de uso de importación; parser como adaptador / ACL)
└── reporting/             (dashboard: solo lectura, puertos de consulta + read models)
```

Convención de nombres por agregado `X`:

| Rol | Clase | Capa |
|---|---|---|
| Agregado / raíz | `X` | `domain` |
| Identificador | `XId` (record envolviendo `Long`) | `domain` |
| Puerto de salida (repositorio) | `XRepository` (interfaz) | `domain` |
| Puerto(s) de entrada (caso de uso) | `CreateX`, `UpdateX`, `DeleteX`, `FindX…` | `application` (subpaquete `port`/`usecase`) |
| Servicio de aplicación | `XService` (implementa los casos de uso) | `application` |
| Entidad de persistencia | `XJpaEntity` | `infrastructure/persistence` |
| Repositorio Spring Data | `XJpaRepository extends JpaRepository<XJpaEntity, Long>` | `infrastructure/persistence` |
| Adaptador de persistencia | `XPersistenceAdapter implements XRepository` | `infrastructure/persistence` |
| Mapper dominio↔JPA | `XJpaMapper` | `infrastructure/persistence` |
| Adaptador de entrada (REST) | `XController` | `infrastructure/web` |
| DTOs web | `XRequest` / `XResponse` | `infrastructure/web` |

## Estrategia de tests durante la migración

La suite actual es la red de caracterización. Por capa:

- **`@DataJpaTest` de repositorio (T1–T4)** → se convierten en tests del **adaptador de persistencia**: prueban `XJpaRepository` + el mapeo a/desde el dominio contra Postgres real. Sobreviven casi tal cual (cambian de paquete y, a veces, afirman sobre el modelo de dominio en vez de la entidad). Las queries `@Query` se conservan.
- **Tests Mockito de service (E2x)** → se reescriben como **tests de dominio puro** (sin Mockito, sin Spring: el agregado y los domain services se testean directamente) y **tests de application service** (mockeando los puertos de salida).
- **Tests Mockito de controller (E3x)** → **se rehacen**: el controller deja de tocar repositorios; pasa a delegar en un puerto de entrada. El test del controller mockea el **caso de uso**, no el repositorio.
- **`@WebMvcTest` (M1–M5)** → siguen siendo tests de adaptador de entrada, pero con `@MockitoBean` del **application service / puerto de entrada** en lugar del repositorio. El contrato HTTP (status, JSON, `@Valid`→400, `problem+json`) se mantiene idéntico.
- **`DataSeederTest`** → sigue igual (arranque de contexto completo).
- **Nuevo: tests de dominio** para las invariantes que hoy estaban en controllers (devolución que no excede el pendiente, jerarquía de un solo nivel, ámbito de subcategoría, hoja para presupuestar, reconciliación de recurrencia…). Son POJOs, rápidos y sin mocks: el mayor valor del ejercicio.
- **Nuevo: ArchUnit** (`ArchitectureTest`) para fijar las reglas que el módulo único no fuerza:
  - `domain` no depende de `application`, `infrastructure`, Spring ni JPA.
  - `application` no depende de `infrastructure`.
  - los `infrastructure/web` solo hablan con puertos de entrada; los `infrastructure/persistence` implementan puertos de salida.
  - ningún `..domain..` importa `jakarta.persistence..` ni `org.springframework..`.

**Puerta de calidad por etapa:** suite completa verde + ArchUnit verde + cobertura global ≥ 99 % (no perder lo ganado) + PRD del dominio actualizado.

## Mapeo del código actual → objetivo (resumen)

| Hoy | Pasa a |
|---|---|
| `model/Account.java` (JPA) | `accounts/domain/Account.java` (puro) + `accounts/infrastructure/persistence/AccountJpaEntity.java` |
| `repository/AccountRepository.java` (Spring Data) | puerto `accounts/domain/AccountRepository.java` + `AccountJpaRepository` + `AccountPersistenceAdapter` |
| `controller/AccountController.java` | `accounts/infrastructure/web/AccountController.java` (fino) + `accounts/application/AccountService.java` |
| Reglas en `CategoryController` (ámbito, jerarquía, recurrencia) | invariantes en `categories/domain/Category.java` + domain services |
| `TransactionController.applyRefund` | invariante en `transactions/domain/Transaction.java` (devolución) |
| `service/BudgetService` (matriz anual) | read model en `budgets` (CQRS) + agregado `Budget` para escritura |
| `service/RecurringBudgetService` (reconciliación) | comportamiento en el agregado `RecurringBudget` (máscara, importes con vigencia) |
| `service/RecategorizationService` | domain service en `categorization` |
| `service/ImportService` + `ImportFileParser` | caso de uso en `imports/application` + parser como adaptador (ACL) |
| `service/DashboardService` + `DashboardController` | `reporting`: puertos de consulta + read models + controller de lectura |
| `controller/GlobalExceptionHandler` | `shared/web` traduciendo `DomainException` → HTTP |
| `config/DataSeeder` | se queda en `infrastructure`/bootstrap (orquesta casos de uso o adaptadores de persistencia) |

---

## Etapas

### H0 · Cimientos
- [x] **H0a · Shared kernel**: `shared/domain` con `Money` (VO en EUR, normalizado a 2 decimales HALF_UP, igualdad por valor y aritmética), `DateRange` (rango inclusivo que rechaza el invertido), `DomainId` (contrato de IDs tipados) y la jerarquía `DomainException` (`NotFound`/`Conflict`/`Validation`, base abstracta). Tests de dominio: `MoneyTest` (6), `DateRangeTest` (3).
- [x] **H0b · ArchUnit**: dependencia `archunit-junit5` 1.3.0 (parsea bytecode de Java 25 sin problema) + `ArchitectureTest` (3 reglas: dominio puro de frameworks; dominio sin depender de application/infrastructure; application sin depender de infrastructure). Reglas **scoping a los nuevos paquetes** `..domain../..application../..infrastructure..`, así la estructura legacy (`model`/`controller`/`service`/`repository`/`dto`/`config`, sin esos segmentos) queda excluida sin esfuerzo. `archunit.properties` con `failOnEmptyShould=false` para las reglas que aún no casan clases.
- [x] **H0c · Advice de dominio**: `shared/web/DomainExceptionHandler` (`@RestControllerAdvice`) que mapea `NotFound→404`, `Conflict→409`, `Validation→400` y la base `DomainException→400`, como `application/problem+json`. Convive con el `controller/GlobalExceptionHandler` legado (tipos disjuntos). Test `DomainExceptionHandlerTest` (4). **Nota/desviación**: no pre-creo el árbol vacío de los 8 contextos (sería ruido de `package-info` sin código); cada contexto se crea al migrarse (H1+). Solo existe `shared/` porque ya tiene contenido.

### H1 · Piloto: Accounts (valida el patrón end-to-end) ✅
- [x] Dominio `Account` puro (con la regla de saldo = inicial + neto de movimientos expresada como concepto de dominio, `balanceWith`), `AccountId`, puerto `AccountRepository`. **Desviación**: `type` se mantiene como `String` libre (no se introduce `AccountType` enum) para no rechazar los valores arbitrarios que ya admite la BD/el seeder (p. ej. «Banco»); es un VO candidato si algún día se cierra el catálogo.
- [x] Persistencia: `AccountJpaEntity` + `AccountJpaRepository` + `AccountJpaMapper` + `AccountPersistenceAdapter`. **Nota estrangulador**: `AccountJpaEntity` y el legado `model/Account` mapean la **misma** tabla `accounts` a la vez (Hibernate lo permite; `DataSeederTest` arranca el contexto completo y lo confirma). El legado sigue vivo porque `Transaction`/`Transfer`/`Category`/`Budget` aún lo referencian por `@ManyToOne`; desaparecerá cuando esos contextos migren.
- [x] Aplicación: casos de uso (`FindAccounts`/`CreateAccount`/`UpdateAccount`/`DeleteAccount`) + `AccountService`. La guarda de borrado consulta el puerto de salida `AccountUsage` (`hasMovements`/`hasTransfers`), resuelto por `AccountUsageAdapter` contra los repos legados.
- [x] Web: `AccountController` fino → puertos de entrada; DTOs `AccountRequest`/`AccountResponse`. Se elimina el `controller/AccountController` legado.
- [x] Tests: dominio (`AccountTest`), aplicación (`AccountServiceTest`, puertos mockeados), adaptador de persistencia (`AccountPersistenceAdapterTest`, `@DataJpaTest` con round-trip del mapper + guarda de borrado contra filas reales) y contrato HTTP (`AccountControllerMvcTest`, `@WebMvcTest` con puertos de entrada como `@MockitoBean`). ArchUnit endurecido (nueva regla: `infrastructure.web` no depende de `infrastructure.persistence`). PRD Cuentas actualizado (v1.1). **Suite verde: 347 tests, cobertura global 99,04 %.**

### H2 · Categories (jerarquía + ámbito) ✅
- [x] Agregado `Category` con un solo nivel de subcategorías y herencia de tipo como invariantes de dominio; `CategoryScope` VO (global vs. ligada a cuenta, referencia la cuenta por `AccountId`). La **inheritance de ámbito** de la subcategoría se decide en el `CategoryService` (no en el agregado) porque condiciona una validación cruzada de cuenta; documentado en el servicio. `type` reutiliza el enum puro `model/TransactionType` (compromiso transitorio: se moverá a `shared/domain` en H3).
- [x] Aplicación: casos de uso + `CategoryService` que **preserva el orden exacto** de las guardas del controlador legado (padre/ámbito/recurrencia/movimientos-de-otra-cuenta/borrado), traduciendo a `DomainException`. **Lectura CQRS**: read model `CategoryView` (con `account`/`parent` anidados, reproduce el JSON heredado) + `CategoryQueryPort`, resuelto por `CategoryQueryAdapter` desde el grafo JPA.
- [x] Persistencia: `CategoryJpaEntity` con asociaciones `@ManyToOne` cuenta/padre resueltas vía `getReferenceById` (escritura por id, lectura del grafo en la misma sesión); adaptadores de comando, lectura, guardas (`CategoryReferencesAdapter`) y existencia de cuenta (`AccountExistenceAdapter`). Web `CategoryController` fino + `CategoryRequest`. Se elimina el `controller/CategoryController` legado.
- [x] Tests: dominio (`CategoryTest`), aplicación (`CategoryServiceTest`, todas las ramas), adaptador de persistencia (`@DataJpaTest`: round-trip + ensamblado del read model) + unitarios de los adaptadores de guardas/existencia, y contrato HTTP (`CategoryControllerMvcTest`). PRD Categorías v1.2. **Suite verde: 378 tests, cobertura global 99,14 %.**

### H3 · Transactions (devoluciones) ✅
- [x] **Pre-paso**: `TransactionType` movido a `shared/domain` (shared kernel); todos los imports y los `@Query` JPQL actualizados. Commit aparte.
- [x] Agregado `Transaction` con la **devolución** como invariante (hereda tipo/cuenta/categoría del original por `TransactionId`, no excede el pendiente, no es devolución de devolución ni de un ingreso; importe positivo). `applyRefund` movido al dominio (`refundOf`/`changeToRefund`); el «no es su propia devolución» y la existencia del original quedan en el servicio (necesitan identidad/consulta). Puertos de salida `TransactionRepository` (con `refundedAmountFor`), `AccountExistence`, `CategoryCatalog`.
- [x] Aplicación: casos de uso + `TransactionService` (orden de `apply`/`applyRefund` preservado). Lectura **CQRS**: `TransactionView` (account/category anidados, refundOf por id) + `TransactionQueryPort` (search con ventana de fechas/filtros; recent). El endpoint `/import` sigue delegando en el `ImportService` legado (migra en H7).
- [x] Persistencia (`TransactionJpaEntity` + adaptadores de comando/lectura + `AccountExistenceAdapter`/`CategoryCatalogAdapter`) + web (`TransactionController` fino + `TransactionRequest`). Se elimina el controlador legado. **Nota**: colisión de nombre de bean entre los dos `AccountExistenceAdapter` (categories/transactions) resuelta con nombres `@Component` explícitos.
- [x] Tests: dominio, aplicación (todas las ramas), adaptadores (`@DataJpaTest` + unitarios) y contrato HTTP. PRD Movimientos v1.1. **Suite verde: 405 tests, cobertura global 99,29 %.**

### H4 · Transfers ✅
- [x] Agregado `Transfer` puro con **origen ≠ destino** como invariante (re-comprobada en `reassign`), importe positivo y fecha obligatoria; ambos extremos referenciados por `AccountId`. `TransferId` y puertos de salida `TransferRepository` + `AccountExistence`.
- [x] Aplicación: casos de uso (`FindTransfers`/`CreateTransfer`/`UpdateTransfer`/`DeleteTransfer`) + `TransferService`, que **preserva el orden legado** (el agregado valida origen ≠ destino antes de comprobar la existencia de las cuentas). Lectura **CQRS**: read model `TransferView` (con `fromAccount`/`toAccount` anidados) + `TransferQueryPort` (search con ventana de fechas y filtro por cuenta en cualquiera de los dos extremos).
- [x] Persistencia (`TransferJpaEntity` + `TransferJpaMapper` + adaptadores de comando/lectura + `AccountExistenceAdapter` con bean `transfersAccountExistenceAdapter`) + web (`TransferController` fino + `TransferRequest`). Se elimina el `controller/TransferController` legado. **Nota estrangulador**: `model/Transfer` y `repository/TransferRepository` (con `totalInUntil`/`totalOutUntil`/`existsByFromAccountIdOrToAccountId`) siguen vivos porque los usan `DashboardService` (H8) y la guarda de borrado de cuentas; ambas entidades mapean la misma tabla `transfers`. El endpoint `/import` sigue delegando en el `ImportService` legado (H7).
- [x] Tests: dominio (`TransferTest`), aplicación (`TransferServiceTest`, todas las ramas), adaptador de persistencia (`TransferPersistenceAdapterTest` + `AccountExistenceAdapterTest`) y contrato HTTP (`TransferControllerMvcTest`). Sigue vigente `repository/TransferRepositoryTest` (queries del repo legado en uso). PRD Transferencias v1.1. **Suite verde: 427 tests, cobertura global 99,36 %.**

### H5 · Budgets + Recurrencias (el dominio más rico)

#### H5a · Budget (escritura) + matriz anual (lectura CQRS) ✅
- [x] Agregado `Budget` puro (presupuesto por cuenta/categoría/año/mes) con **mes 1..12** e **importe positivo** como invariantes; `copyTo` (copia sin identidad a otro hueco) e `isAt` (igualdad de hueco) como comportamiento. `BudgetId` y puertos de salida `BudgetRepository` (con `existsAt`/`findByYearMonth`), `CategoryCatalog` (hoja + ámbito de cuenta) y `AccountExistence`.
- [x] Aplicación: casos de uso (`FindBudgets`/`CreateBudget`/`UpdateBudget`/`DeleteBudget`/`CopyBudgets`) + `BudgetService`, que **preserva el orden legado** de guardas (alta: duplicado→cuenta→categoría→hoja→ámbito; edición: no-encontrado→atajo «mismo hueco»→duplicado). **Read model** de la matriz anual (CQRS): `AnnualBudgetView` + `BudgetView` + `BudgetQueryPort`, con la fusión manual+recurrencia y la agregación padre/hijo en el adaptador de lectura (sin reconstruir agregados).
- [x] Persistencia (`BudgetJpaEntity` sobre `monthly_budgets` + mapper + adaptador de comando + `BudgetQueryAdapter` con el ensamblado de la matriz + `CategoryCatalogAdapter`/`AccountExistenceAdapter` con beans nombrados) + web (`BudgetController` fino + `BudgetRequest`/`CopyRequest`). Se eliminan el `controller/BudgetController`, `service/BudgetService` y `dto/BudgetDtos` legados. **Nota estrangulador**: `model/Budget` + `repository/BudgetRepository` siguen vivos (los usan `DashboardService` (H8) y la guarda de borrado de categorías); las recurrencias siguen legadas y el adaptador de lectura las consume de forma transitoria (vía `repository/RecurringBudgetRepository` y `repository/TransactionRepository.sumByExactCategoryAndMonthOfYear`).
- [x] Tests: dominio (`BudgetTest`), aplicación (`BudgetServiceTest`, todas las ramas), adaptadores de persistencia (`BudgetPersistenceAdapterTest` round-trip/`existsAt`/`findByYearMonth`, `BudgetQueryAdapterMatrixTest` reemplaza al `BudgetServiceTest` legado sembrando datos reales, `CategoryCatalogAdapterTest`, `AccountExistenceAdapterTest`) y contrato HTTP (`BudgetControllerMvcTest`). PRD Presupuestos v1.3. **Suite verde: 447 tests, cobertura global 99,35 %.**

#### H5b · RecurringBudget (escritura) ✅
- [x] Agregado `RecurringBudget` puro con `MonthsMask` (VO de 12 bits con comportamiento), historial de `RecurrenceAmount` (importe + `YearMonth` de vigencia) e **invariante de un importe por mes de vigencia**; `plannedAmount(year,month)`/`amountAt(ym)` como comportamiento (lo reusa la matriz). La **reconciliación** del historial es comportamiento del agregado (`reconcile`); la transición **in situ** Hibernate-safe (no violar `uq_amount_vigencia` al editar el importe manteniendo el mes) vive en el `RecurringBudgetJpaMapper.applyTo`, documentado como reparto consciente dominio↔persistencia.
- [x] Aplicación: `RecurringBudgetService` (get/upsert/delete) que **preserva el orden legado** de guardas (404 inexistente → 400 global/subcategorías/mes/formato), reusa el puerto `CategoryCatalog` de budgets; puertos de entrada `FindRecurrence`/`UpsertRecurrence`/`DeleteRecurrence`; lectura **CQRS** `RecurringBudgetQueryPort` + read model `RecurringBudgetView` (con los ids de los importes). Persistencia (`RecurringBudgetJpaEntity` + `RecurrenceAmountJpaEntity` + mapper + adaptadores de comando/lectura) y sub-recurso web (`RecurringBudgetController` + `RecurringBudgetRequest`).
- [x] **Repuntado** `BudgetQueryAdapter` a su propio puerto de dominio (`RecurringBudgetRepository.findActiveByAccount`, agregados que reusan `plannedAmount`) y `CategoryReferencesAdapter.hasRecurrence` al nuevo repo. **Retirado todo el legado** de recurrencias: `model/RecurringBudget(+Amount)`, `repository/RecurringBudgetRepository`, `service/RecurringBudgetService`, `controller/RecurringBudgetController`, `dto/RecurringBudgetDtos` y sus tests; helpers `recurrence`/`amount` de `Fixtures` eliminados. **Nota infra**: para acotar las conexiones al crecer los slices `@DataJpaTest` se fija `hikari.maximum-pool-size=2` en `PostgresTestBase` (evita «too many clients already»).
- [x] Tests: dominio (`MonthsMaskTest`, `RecurringBudgetTest`), aplicación (`RecurringBudgetServiceTest`, todas las ramas), adaptadores (`RecurringBudgetPersistenceAdapterTest` round-trip/reconciliación/constraints/scoping/cascada, `RecurringBudgetQueryAdapterTest`) y contrato HTTP (`RecurringBudgetControllerMvcTest`). PRD Presupuestos v1.4. **Suite verde: 454 tests, cobertura global 99,25 %.**

### H6 · Categorization (reglas + recategorización) ✅
- [x] Agregado `CategoryRule` puro (patrón no vacío recortado como invariante; referencia la categoría destino por `CategoryId`) con `matches` delegando en el **domain service** `PatternMatcher` (patrones con `|`, insensible a mayúsculas/acentos), extraído de `RecategorizationService`. La normalización se movió al shared kernel `shared/domain/TextNormalizer` (fuente única); `ImportFileParser.normalizeHeader` delega ahí.
- [x] Aplicación: casos de uso (`FindRules`/`CreateRule`/`UpdateRule`/`DeleteRule`) + `CategoryRuleService` que **preserva el orden legado** de guardas (alta: categoría destino→400; edición: 404 inexistente antes de 400 categoría). El caso de uso "aplicar regla" recategoriza los movimientos de respaldo ("Otros gastos/ingresos") que coinciden, con el ámbito de cuenta del destino, vía los puertos de salida `RuleCategoryCatalog` (categoría destino + fallback por tipo) y `TransactionRecategorizer` (ACL sobre los movimientos). Lectura **CQRS**: read model `CategoryRuleView` (categoría anidada reusando `CategoryView`) + `CategoryRuleQueryPort`; resultado `RuleSaved` (`{rule, recategorized}`).
- [x] Persistencia (`CategoryRuleJpaEntity` sobre `category_rules` + mapper + adaptador de comando + `CategoryRuleQueryAdapter` + `RuleCategoryCatalogAdapter` + `TransactionRecategorizerAdapter`) + web (`CategoryRuleController` fino + `CategoryRuleRequest`). Se **repuntó** `CategoryReferencesAdapter.hasRule` al nuevo `CategoryRuleJpaRepository` y se **retiró** el legado migrado: `service/RecategorizationService`, `controller/CategoryRuleController` y sus tests. **Nota estrangulador**: `model/CategoryRule` + `repository/CategoryRuleRepository` siguen vivos **solo** para el `ImportService` legado (H7), que ahora casa con `PatternMatcher.matches`; mapean la misma tabla.
- [x] Tests: dominio (`CategoryRuleTest`, `PatternMatcherTest`), aplicación (`CategoryRuleServiceTest`, todas las ramas), adaptadores (`CategoryRulePersistenceAdapterTest` round-trip + read model + `existsByCategory`, `RuleCategoryCatalogAdapterTest`, `TransactionRecategorizerAdapterTest`), contrato HTTP (`CategoryRuleControllerMvcTest`) y `TextNormalizerTest`. PRD Reglas de categorización v2.0. **Suite verde: 478 tests, cobertura global 99,22 %.**

### H7 · Imports (orquestación / ACL) ✅
- [x] Caso de uso de importación (CSV/Excel) en `imports/application` (`ImportService` que implementa `ImportTransactions`/`ImportTransfers`), con `ImportFileParser` como **adaptador / anti-corruption layer** (`ImportFileReader`) que traduce filas de banco a `ImportRow` (VO de dominio con los formatos de importe/fecha del banco). La orquestación **reutiliza los casos de uso** de Transactions/Transfers/Categories: el dominio define puertos de salida propios (`AccountDirectory`, `CategoryDirectory`, `RuleDirectory`, `MovementWriter`, `TransferWriter`) que **adaptadores *bridge*** implementan delegando en `CreateTransaction`/`CreateTransfer`/`CreateCategory` + los `QueryPort` de lectura (dedup) de cada contexto; así `imports` no toca persistencia. La coincidencia de reglas reusa `categorization/domain/PatternMatcher`. Los formatos/lecturas inválidos del parser se traducen a `ValidationException` (→ 400).
- [x] **Retirado el legado** migrado: `service/ImportService`, `service/ImportFileParser`, `dto/ImportDtos`, **y el último resto de recurrencias/reglas legadas** `model/CategoryRule` + `repository/CategoryRuleRepository` (solo los usaba el `ImportService` legado) y sus tests; eliminado el helper `Fixtures.rule`. Los endpoints `/import` siguen en `TransactionController`/`TransferController` pero delegan en los puertos de entrada `ImportTransactions`/`ImportTransfers`.
- [x] Tests: dominio (`ImportRowTest`), aplicación (`ImportServiceTest`, todas las ramas con los puertos mockeados), adaptadores (`ImportFileParserTest` del parser ACL + `ImportBridgeAdaptersTest` de los 5 bridges) y los 2 `@WebMvcTest` actualizados (`@MockitoBean` del puerto de entrada). PRD Importación v1.1. **Suite verde: 481 tests, cobertura global 99,22 %.**

### H8 · Reporting (dashboard, solo lectura) ✅
- [x] `reporting` **solo lectura (CQRS)**: la matemática de agregación (saldos = inicial + neto + transferencias in/out; ahorro; deltas; % de crecimiento/rentabilidad; serie de patrimonio; comparativa por cuenta; roll-up de presupuestos) vive en el caso de uso `ReportingService` (`reporting/application`), que implementa el puerto de entrada **facade** `DashboardReports` y lee las cifras crudas por **puertos de consulta de salida** (`MovementAggregateQuery`, `TransferAggregateQuery`, `AccountCatalogQuery`, `BudgetCatalogQuery`); nunca pasa por los agregados de escritura. Read models `*View` (Summary/CategoryAmount/MonthlyPoint/BalancePoint/AccountComparison/BudgetStatus) **fieles al JSON heredado** (mismos nombres de campo). Web `reporting/infrastructure/web/DashboardController` (mismas rutas `/api/dashboard/*`, fallback a fecha actual + clamp de `months` a 1..36).
- [x] Adaptadores de consulta (`reporting/infrastructure/persistence/*QueryAdapter`) que **reutilizan transitoriamente** las queries de agregación de los repos legados `repository.{Transaction,Transfer,Account,Budget}Repository` (mismo lazo estrangulador que `BudgetQueryAdapter`). Se **retiró** el legado del dashboard: `service/DashboardService`, `controller/DashboardController`, `dto/DashboardDtos` y sus 3 tests. Los paquetes `service/` y `dto/` quedan vacíos; `controller/` solo conserva `GlobalExceptionHandler` (legado, H9).
- [x] Tests: aplicación (`ReportingServiceTest`, todas las ramas con puertos mockeados), adaptadores (`ReportingQueryAdaptersTest`), unit del controller (`DashboardControllerTest`) y contrato HTTP (`DashboardControllerMvcTest`). PRD Dashboard v1.1. **Suite verde: 486 tests, cobertura global 99,24 %.**

### H9 · Cierre transversal
- [ ] Retirar el `GlobalExceptionHandler` y los `ResponseStatusException` legados; todo error pasa por `DomainException`→advice. Eliminar restos de acceso directo a repositorios desde web. `DataSeeder` reescrito sobre casos de uso/adaptadores.
- [ ] **ArchUnit estricto global** (sin exclusiones legacy). Re-medir JaCoCo (objetivo: mantener ≥ 99 %). Actualizar la sección **Architecture** de `CLAUDE.md` y el `docs/README.md`. **Suite verde.**

---

## Riesgos y coste (sé consciente)

- **Coste de tests:** los tests Mockito de controller y los `@WebMvcTest` se rehacen (≈ 6 controllers). El comportamiento verificado se reaprovecha, pero es trabajo real. Los `@DataJpaTest` y `DataSeederTest` casi no cambian.
- **Boilerplate por diseño:** con DDD completo, cada agregado trae entidad de dominio + entidad JPA + mapper + puerto + adaptador + casos de uso + DTOs web. Es el precio de la pureza elegida; en una app con mucho CRUD fino, parte de ese código será "ceremonia". Asumido al elegir "aprender DDD / DDD completo".
- **Referencias por ID** cambian el modelo: invariantes que cruzaban agregados (categoría↔cuenta) se mueven al application/domain service. Hay que tener cuidado de no perder ninguna regla (los tests de caracterización ayudan).
- **Flyway intacto:** no hay cambio de esquema; si alguna entidad JPA dejara de validar contra el esquema, es un fallo de mapeo, no una migración nueva.

## Estado actual / Próximo paso

- **Estado**: **H0–H8 hechos**. H8 migró el **dashboard** al contexto `reporting` (solo lectura, CQRS): caso de uso `ReportingService` (puerto de entrada `DashboardReports`) con toda la matemática de agregación, leyendo por puertos de consulta de salida (`MovementAggregateQuery`/`TransferAggregateQuery`/`AccountCatalogQuery`/`BudgetCatalogQuery`) resueltos por adaptadores que **reutilizan transitoriamente** las queries de los repos legados; read models `*View` fieles al JSON; web `DashboardController` de lectura. Se **retiró** el legado del dashboard (`service/DashboardService`, `controller/DashboardController`, `dto/DashboardDtos` + 3 tests); `service/` y `dto/` quedan vacíos. ArchUnit con 4 reglas verdes. **486 tests verdes; cobertura global 99,24 %.** Migrados: `accounts`, `categories`, `transactions`, `transfers`, `budgets`, `categorization`, `imports`, `reporting`. **Todos los contextos migrados**; solo queda el cierre transversal (H9).
- **Estrangulador restante** (lo que retira H9): los repos/entidades legados `repository.{Account,Transaction,Transfer,Budget,Category}Repository` + `model.{Account,Transaction,Transfer,Budget,Category}` siguen vivos porque los usan **(a)** `config/DataSeeder`, **(b)** guardas de borrado (`AccountUsageAdapter`, `CategoryReferencesAdapter`), **(c)** `budgets/.../BudgetQueryAdapter` y **(d)** los adaptadores de consulta de `reporting`. También sigue el `controller/GlobalExceptionHandler` legado (conviviendo con `shared/web/DomainExceptionHandler`).
- **Próximo paso**: **H9 · Cierre transversal** — retirar `GlobalExceptionHandler`/`ResponseStatusException` legados (todo error vía `DomainException`→advice); reescribir `DataSeeder` sobre casos de uso/adaptadores; repuntar los adaptadores que aún tocan `repository.*` legado (reporting + budgets + guardas) a los repos JPA migrados y **eliminar** los `repository.*`/`model.*` legados; **ArchUnit estricto global** (sin exclusiones legacy, quitar `failOnEmptyShould=false` donde ya no haga falta); re-medir JaCoCo (≥99 %); actualizar la sección **Architecture** de `CLAUDE.md` y `docs/README.md`. **Preguntar antes de arrancar** (ver regla de continuidad arriba).
