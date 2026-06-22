# PRD — Cuentas

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.0 |
| Última actualización | 2026-06-22 |
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

Tabla `accounts` (migración `V1__init.sql`), entidad `Account`:

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

Página `pages/accounts` (componente `AccountsPage`).

- Tabla con columnas **Nombre**, **Tipo**, **Saldo inicial** (formateado en EUR) y acciones **Editar** / **Eliminar**.
- Botón **＋ Nueva cuenta** que abre un formulario en línea (compartido entre alta y edición).
- Campo **Tipo**: selector con valores `Banco`, `Efectivo`, `Tarjeta`, `Inversión`, `Otro` (por defecto `Banco`). Nota: el backend acepta cualquier texto no vacío; la lista cerrada es solo de la UI.
- Campo **Saldo inicial**: entrada de texto con `inputmode="decimal"` y patrón `-?\d{1,12}([.,]\d{1,2})?`; se normaliza con `parseAmount` antes de enviar.
- Eliminar pide confirmación (`confirm`). Si el backend responde 409 se muestra "La cuenta tiene movimientos asociados y no se puede eliminar".

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Nombre vacío | Rechazado: `@NotBlank` (back) + `required` (front). |
| Tipo vacío | Rechazado: `@NotBlank`. |
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

- Backend: `model/Account.java`, `controller/AccountController.java`, `repository/AccountRepository.java`.
- Cálculo de saldo: `service/DashboardService.java` (`balanceUntil`).
- Esquema: `db/migration/V1__init.sql`.
- Tests: `controller/AccountControllerTest.java` (lógica con repos mockeados) y `controller/AccountControllerMvcTest.java` (contrato HTTP con el slice `@WebMvcTest`: listado/alta como JSON, `@Valid`→400 con nombre en blanco, guardas de borrado→409 y el `DataIntegrityViolationException`→409 `problem+json` del `GlobalExceptionHandler`). Es además el test de referencia del Nivel 3 (patrón `MockMvcTester` + `@MockitoBean`).
- Frontend: `pages/accounts/` (`accounts.ts`, `accounts.html`), modelo `Account` en `models.ts`.
