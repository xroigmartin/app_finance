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

### H1 · Piloto: Accounts (valida el patrón end-to-end)
- [ ] Dominio `Account` puro (con la regla de saldo = inicial + neto de movimientos expresada como concepto de dominio), `AccountId`, puerto `AccountRepository`.
- [ ] Persistencia: `AccountJpaEntity` + `AccountJpaRepository` + `AccountJpaMapper` + `AccountPersistenceAdapter`.
- [ ] Aplicación: casos de uso (crear/editar/borrar/listar) + `AccountService`. Guardas de borrado (movimientos/transferencias) como caso de uso que consulta puertos.
- [ ] Web: `AccountController` fino → puertos de entrada; DTOs web.
- [ ] Tests: reubicar el `@DataJpaTest` como test de adaptador; rehacer `@WebMvcTest`/Mockito contra el caso de uso; añadir tests de dominio. ArchUnit endurecido para `accounts`. PRD Cuentas actualizado. **Suite verde.**

### H2 · Categories (jerarquía + ámbito)
- [ ] Agregado `Category` con: un solo nivel de subcategorías, herencia de tipo/ámbito de la subcategoría, reglas de reasignación de ámbito y de recurrencia incompatible (hoy en `CategoryController`). `CategoryScope` VO.
- [ ] Persistencia + aplicación + web + tests (incl. tests de dominio de las invariantes que hoy prueba `CategoryControllerTest` por la vía HTTP). PRD Categorías. **Suite verde.**

### H3 · Transactions (devoluciones)
- [ ] Agregado `Transaction` con la semántica de **devolución** como invariante (referencia al gasto original por `TransactionId`, no excede el pendiente, no es devolución de devolución, solo de gastos). Neteo de signo como comportamiento.
- [ ] Persistencia + aplicación + web + tests (mover `applyRefund` a dominio). PRD Movimientos. **Suite verde.**

### H4 · Transfers
- [ ] Agregado `Transfer` (origen ≠ destino como invariante; efecto en saldo documentado). Persistencia + aplicación + web + tests. PRD Transferencias. **Suite verde.**

### H5 · Budgets + Recurrencias (el dominio más rico)
- [ ] Agregado `Budget` (presupuesto por hoja/cuenta/periodo, único). Agregado `RecurringBudget` con `MonthsMask` y los importes con vigencia (`validoDesde`) y la **reconciliación in situ** como comportamiento del agregado (hoy en `RecurringBudgetService`).
- [ ] **Read model** de la matriz anual (CQRS): puerto de consulta + ensamblado de hojas/padres + fusión manual+recurrencia, sin reconstruir agregados para leer.
- [ ] Persistencia + aplicación + web + tests (los de `BudgetService`/`RecurringBudgetService` pasan a dominio + read model). PRD Presupuestos. **Suite verde.**

### H6 · Categorization (reglas + recategorización)
- [ ] Agregado `CategoryRule`; **domain service** de matching (patrones con `|`, insensible a mayúsculas/acentos) extraído de `RecategorizationService`. Caso de uso "aplicar regla" que recategoriza movimientos de "Otros". Persistencia + web + tests. PRD Reglas de categorización. **Suite verde.**

### H7 · Imports (orquestación / ACL)
- [ ] Caso de uso de importación (CSV/Excel) en `imports/application`, con el `ImportFileParser` como **adaptador / anti-corruption layer** que traduce filas de banco al lenguaje del dominio. Reusa los casos de uso de Transactions/Transfers/Categories. Tests de parser (puros) + caso de uso. PRD Importación. **Suite verde.**

### H8 · Reporting (dashboard, solo lectura)
- [ ] `reporting`: puertos de consulta (`SummaryQuery`, `MonthlySeriesQuery`, `ByCategoryQuery`, `BudgetStatusQuery`, …) + read models, resueltos por adaptadores con las queries de agregación existentes. `DashboardController` de lectura. Tests. PRD Dashboard. **Suite verde.**

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

- **Estado**: **H0 hecho** (cimientos). `shared/domain` (Money, DateRange, DomainId, jerarquía DomainException), `shared/web/DomainExceptionHandler` y ArchUnit con 3 reglas verdes. 327 tests verdes (16 nuevos). Backend de negocio aún en capas clásicas; nada migrado todavía.
- **Próximo paso**: **H1 · Piloto Accounts** — migrar el dominio más simple end-to-end (domain `Account` + `AccountId` + puerto `AccountRepository`; persistencia `AccountJpaEntity`+adaptador+mapper; aplicación + casos de uso; `AccountController` fino) para validar el patrón y dejarlo de plantilla. **Preguntar antes de arrancar** (ver regla de continuidad arriba).
