# PRD — Cuentas

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.1 |
| Última actualización | 2026-07-16 |
| Dominio | Cuentas (`accounts`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de las cuentas (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

---

## 1. Propósito

Una **cuenta** representa un contenedor de dinero del usuario (cuenta bancaria, efectivo, tarjeta, inversión, etc.). Es la entidad raíz sobre la que se registran movimientos y transferencias, y respecto a la que se calculan saldos, presupuestos y agregaciones del dashboard.

Sin al menos una cuenta no es posible registrar movimientos ni importar extractos: las importaciones exigen que la cuenta ya exista.

## 2. Objetivos y no-objetivos

**Objetivos**
- Permitir crear, consultar, editar y eliminar cuentas.
- Clasificar cada cuenta por un tipo descriptivo.
- Servir de base para el cálculo de saldos y para el filtrado por cuenta en el resto de la app.

**No-objetivos (fuera de alcance de este PRD)**
- Cálculo y visualización de saldos históricos y evolución → PRD Dashboard.
- Movimientos y transferencias asociados → PRDs Movimientos / Transferencias.
- Multiusuario, divisas distintas del euro, conciliación bancaria automática.

## 3. Modelo de datos

Tabla `accounts` (migración `V1__init.sql`). Desde la migración a arquitectura hexagonal (etapa H1), el modelo de dominio puro `accounts/domain/Account` (con `AccountId` y `Money` como value objects) está **separado** de la entidad de persistencia `accounts/infrastructure/persistence/AccountJpaEntity`, que es la que mapea esta tabla; un `AccountJpaMapper` convierte entre ambos. El esquema y las columnas no cambian:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | Generado por la BD. |
| `name` | `varchar(255)` | `NOT NULL`, no vacío | **No hay unicidad**: pueden existir dos cuentas con el mismo nombre. |
| `type` | `varchar(255)` | `NOT NULL`, no vacío | Texto libre en el backend; la UI ofrece una lista cerrada (ver §6). |
| `initial_balance` | `numeric(38,2)` | `NOT NULL`, por defecto `0` | Saldo de partida. Importe en euros con 2 decimales. |

**El saldo actual no se almacena**: es un valor calculado (ver §5, RN-1).

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | El usuario puede listar todas sus cuentas. |
| RF-2 | El usuario puede crear una cuenta indicando nombre, tipo y saldo inicial. |
| RF-3 | El usuario puede editar el nombre, el tipo y el saldo inicial de una cuenta existente. |
| RF-4 | El usuario puede eliminar una cuenta que no tenga movimientos ni transferencias asociados. |
| RF-5 | El saldo inicial admite importe con coma o punto decimal y hasta dos decimales (p. ej. `1234,56`). |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | El saldo de una cuenta se **calcula**, nunca se almacena: `saldo = saldo_inicial + neto de movimientos + transferencias entrantes − transferencias salientes`, acumulado hasta una fecha. Ver `DashboardService.balanceUntil`. |
| RN-2 | Una cuenta con **movimientos** asociados no se puede eliminar (HTTP 409). |
| RN-3 | Una cuenta con **transferencias** (como origen o destino) no se puede eliminar (HTTP 409). |
| RN-4 | Editar el saldo inicial recalcula automáticamente todos los saldos históricos derivados, ya que ningún saldo está materializado. |
| RN-5 | El nombre de la cuenta no es único; la identidad es el `id`. |

## 6. API

Base: `/api/accounts`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/accounts` | Lista todas las cuentas. | `200` + array de cuentas. |
| `POST` | `/api/accounts` | Crea una cuenta. El `id` enviado se ignora. | `201` + cuenta creada. |
| `PUT` | `/api/accounts/{id}` | Actualiza nombre, tipo y saldo inicial. | `200` + cuenta / `404` si no existe. |
| `DELETE` | `/api/accounts/{id}` | Elimina la cuenta. | `204` / `409` si tiene movimientos o transferencias. |

No existe `GET /api/accounts/{id}` individual.

**Cuerpo (create/update):**
```json
{ "name": "Cuenta corriente", "type": "Banco", "initialBalance": 1500.00 }
```

## 7. UI/UX

Página `pages/accounts` (componente `AccountsPage`), rediseñada según el sistema de diseño (`Cuentas.dc.html` del handoff).

- **Hero de patrimonio** (tarjeta): patrimonio neto (cifra mono 30px/600), badge pill de **variación mensual** (▲/▼ % sobre `--pos-soft`/`--neg-soft`), cifras de **Activos** y **Pasivos**, y **barra de distribución** de 7px (pill, segmentos por grupo de activo) con leyenda en mono.
- **Tarjetas agrupadas por tipo** (sustituyen a la tabla), con subtotal por grupo en la cabecera: `Banco` → Cuentas corrientes (`--accent`), `Efectivo` (`--pos`), `Inversión` (`--warn`), `Otro` → Otras cuentas (neutro) y `Tarjeta` → Tarjetas y crédito (`--neg`, **pasivo**). Cada tarjeta: avatar 38px (inicial sobre el soft del grupo), nombre + meta (tipo · saldo inicial), **saldo actual** en mono (negativo en `--neg`) y botón Eliminar; clic en la tarjeta abre la edición.
- **Fuente de los saldos**: el **saldo actual** por cuenta (inicial + movimientos) se lee del read-side de reporting `GET /api/dashboard/summary` (campo `accounts[]`, saldo a fin del mes en curso), en `forkJoin` con `GET /api/accounts`; no se añadió un endpoint nuevo. La variación mensual reutiliza `monthGrowthPct`. Si el summary falla, la página degrada: sin hero y con «—» como saldo.
- **Activos/pasivos**: activos = suma de saldos de los grupos de activo; pasivos = suma del grupo Tarjeta; patrimonio neto = activos + pasivos. La barra de distribución reparte solo los grupos de activo con saldo positivo.
- Botón **+ Nueva cuenta**; el formulario de alta/edición se abre en un **diálogo modal** (`<dialog>` nativo con `showModal()`, como en Movimientos), compartido entre alta y edición.
- Campo **Tipo**: selector con valores `Banco`, `Efectivo`, `Tarjeta`, `Inversión`, `Otro` (por defecto `Banco`). Nota: el backend acepta cualquier texto no vacío; la lista cerrada es solo de la UI (y determina el grupo visual).
- Campo **Saldo inicial**: entrada de texto con `inputmode="decimal"` y patrón `-?\d{1,12}([.,]\d{1,2})?`; se normaliza con `parseAmount` antes de enviar.
- Eliminar pide confirmación (`confirm`). Si el backend responde 409 se muestra "La cuenta tiene movimientos asociados y no se puede eliminar".

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Nombre vacío | Rechazado en dos niveles: `@NotBlank` en el DTO web `AccountRequest` (→ `400`) y, como invariante de dominio, `Account` lanza `ValidationException` (→ `400` vía `DomainExceptionHandler`). En el front, `required`. |
| Tipo vacío | Rechazado igual que el nombre (`@NotBlank` en el DTO + invariante de dominio). |
| Saldo inicial no numérico / formato inválido | El front muestra "Saldo inicial no válido…" y no envía la petición. |
| Editar cuenta inexistente | `404 Cuenta no encontrada`. |
| Eliminar cuenta con movimientos | `409` con mensaje de movimientos asociados. |
| Eliminar cuenta con transferencias | `409` con mensaje de transferencias asociadas. |

## 9. Casos límite y notas

- El saldo inicial puede ser negativo (p. ej. una tarjeta con deuda); el patrón de la UI lo admite.
- Al ser el saldo siempre calculado, no hay riesgo de descuadre entre saldo almacenado y movimientos.
- Las importaciones de extractos **no** crean cuentas: la cuenta destino debe existir previamente (ver PRD Importación).

## 10. Backlog / mejoras futuras

- Unicidad opcional del nombre de cuenta.
- Archivar cuentas (en lugar de eliminar) cuando ya tienen historial.
- Tipos de cuenta como catálogo configurable en vez de lista fija en la UI.
- Soporte de divisa por cuenta.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Unicidad del nombre | No hay restricción: dos cuentas pueden llamarse igual; la identidad es solo el `id`. | Decidir si el nombre de cuenta debe ser único. |
| Tipo de cuenta | `type` es texto libre en el backend (`@NotBlank`); la lista cerrada (`Banco`, `Efectivo`, …) vive solo en la UI. | La API acepta cualquier cadena. Decidir si se valida contra un catálogo/enum o se deja abierto. |
| Lectura individual | No existe `GET /api/accounts/{id}`. | Aceptable mientras el front cargue la lista completa; añadir si aparece un consumidor que lo necesite. |

## 12. Referencias de código

Cuentas es el **piloto de la arquitectura hexagonal + DDD** (etapa H1 de `docs/migration-ddd-hexagonal.md`). Estructura por capas del contexto `accounts`:

- **Dominio** (puro, sin Spring ni JPA): `accounts/domain/Account.java` (agregado con sus invariantes y el concepto de saldo calculado `balanceWith`), `accounts/domain/AccountId.java`, y los puertos de salida `accounts/domain/AccountRepository.java` y `accounts/domain/AccountUsage.java` (guarda de borrado).
- **Aplicación**: puertos de entrada (casos de uso) en `accounts/application/port/` (`FindAccounts`, `CreateAccount`, `UpdateAccount`, `DeleteAccount`) y el servicio `accounts/application/AccountService.java` que los implementa.
- **Infraestructura · persistencia**: `AccountJpaEntity`, `AccountJpaRepository` (Spring Data), `AccountJpaMapper` y los adaptadores `AccountPersistenceAdapter` (implementa `AccountRepository`) y `AccountUsageAdapter` (resuelve la guarda contra los stores legados de movimientos/transferencias).
- **Infraestructura · web**: `accounts/infrastructure/web/AccountController.java` (adaptador fino que solo delega en los puertos de entrada) y los DTOs `AccountRequest`/`AccountResponse`.
- Cálculo de saldo (lectura): `service/DashboardService.java` (`balanceUntil`) — aún en capas clásicas; el concepto de dominio equivalente vive en `Account.balanceWith`.
- Esquema: `db/migration/V1__init.sql` (sin cambios; durante la migración `AccountJpaEntity` y el legado `model/Account` mapean la misma tabla).
- Tests: dominio `accounts/domain/AccountTest`; aplicación `accounts/application/AccountServiceTest` (puertos mockeados); persistencia `accounts/infrastructure/persistence/AccountPersistenceAdapterTest` (`@DataJpaTest` contra Postgres real: round-trip del mapper y guarda de borrado); contrato HTTP `accounts/infrastructure/web/AccountControllerMvcTest` (`@WebMvcTest` con los puertos de entrada como `@MockitoBean`: listado/alta/edición como JSON, `@Valid`→400, `404`/`409` de dominio y `DataIntegrityViolationException`→409 `problem+json`).
- Frontend: `pages/accounts/` (`accounts.ts`, `accounts.html`), modelo `Account` en `models.ts` (sin cambios).
