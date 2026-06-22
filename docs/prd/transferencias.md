# PRD — Transferencias

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.0 |
| Última actualización | 2026-06-22 |
| Dominio | Transferencias (`transfers`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de las transferencias (modelo, API, reglas de negocio o UI). Ver `docs/README.md`.

---

## 1. Propósito

Una **transferencia** mueve dinero de una cuenta a otra del propio usuario. No es ni ingreso ni gasto: es un traslado interno. Afecta a los saldos (resta en origen, suma en destino) pero **se excluye** de las agregaciones de ingresos/gastos para no distorsionar el análisis.

## 2. Objetivos y no-objetivos

**Objetivos**
- Crear, consultar, editar y eliminar transferencias entre dos cuentas.
- Filtrar por rango de fechas y por cuenta (origen o destino).
- Reflejar el efecto de la transferencia en los saldos calculados.

**No-objetivos (fuera de alcance de este PRD)**
- Transacciones de ingreso/gasto → PRD Movimientos.
- Importación de transferencias (`POST /api/transfers/import`) → PRD Importación de extractos.
- Cálculo de saldos → PRD Cuentas / Dashboard.

## 3. Modelo de datos

Tabla `transfers` (migración `V1__init.sql`), entidad `Transfer`:

| Campo | Tipo | Restricciones | Notas |
|---|---|---|---|
| `id` | `bigint` (identity) | PK | |
| `date` | `date` | `NOT NULL` | Fecha de la transferencia. |
| `amount` | `numeric(38,2)` | `NOT NULL` | Importe **positivo** (validado con `@Positive` en la API). |
| `description` | `varchar(255)` | Nullable | Texto libre opcional. |
| `from_account_id` | `bigint` → `accounts` | `NOT NULL` | Cuenta de origen. |
| `to_account_id` | `bigint` → `accounts` | `NOT NULL` | Cuenta de destino. |

Índices: por `from_account_id` y `to_account_id`. Una transferencia **no tiene categoría ni tipo**.

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Crear una transferencia indicando fecha, importe, cuenta de origen, cuenta de destino y descripción opcional. |
| RF-2 | Editar una transferencia existente. |
| RF-3 | Eliminar una transferencia. |
| RF-4 | Listar transferencias filtrando por rango de fechas y por cuenta (origen o destino), todos opcionales. |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | La cuenta de **origen y destino deben ser distintas**; si coinciden, "La cuenta de origen y la de destino deben ser distintas" (HTTP 400). |
| RN-2 | El importe debe ser **estrictamente positivo** (`@Positive`). |
| RN-3 | Ambas cuentas deben existir; si no, "Cuenta no válida" (HTTP 400). |
| RN-4 | Efecto en saldo: la transferencia **resta** del saldo de la cuenta de origen y **suma** al de la de destino (`TransferRepository.totalOutUntil` / `totalInUntil`, usados en `DashboardService.balanceUntil`). |
| RN-5 | Las transferencias **se excluyen** de las agregaciones de ingresos y gastos (no tienen `type` y no entran en `sumByType*` ni `sumByCategory*`). |
| RN-6 | Eliminar una transferencia no tiene restricciones de integridad: se borra directamente. |
| RN-7 | Una cuenta con transferencias asociadas (como origen o destino) no se puede eliminar (ver PRD Cuentas, RN-3). |

## 6. API

Base: `/api/transfers`.

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| `GET` | `/api/transfers?from=&to=&accountId=` | Busca transferencias; filtros opcionales. `accountId` casa con origen **o** destino. Sin `from`/`to` el rango es `1970-01-01`–`2999-12-31`. Orden: fecha desc, id desc. | `200` + array. |
| `POST` | `/api/transfers` | Crea una transferencia. | `201` + transferencia. |
| `PUT` | `/api/transfers/{id}` | Actualiza la transferencia. | `200` / `404` si no existe. |
| `DELETE` | `/api/transfers/{id}` | Elimina la transferencia. | `204`. |
| `POST` | `/api/transfers/import` | Importa transferencias desde fichero (multipart `file`). | Ver PRD Importación. |

**Cuerpo (`TransferRequest`, create/update):**
```json
{
  "date": "2026-06-15",
  "amount": 200.00,
  "description": "Traspaso a ahorro",
  "fromAccountId": 1,
  "toAccountId": 2
}
```

## 7. UI/UX

- La gestión de transferencias se hace **desde la pantalla de Movimientos** (`pages/transactions`): al crear/editar un movimiento se elige el tipo "Transferencia", que muestra los selectores de cuenta de origen y destino en lugar de categoría.
- En la lista unificada de movimientos, las transferencias aparecen mezcladas con las transacciones, ordenadas por fecha. Un filtro por categoría las oculta.
- Cambiar un movimiento de transferencia a transacción (o viceversa) elimina el registro original y crea el del otro tipo (ver PRD Movimientos, §7).
- **Legado**: existe un componente `pages/transfers/` (`TransfersPage`) con su propia pantalla, pero la ruta `transfers` redirige a `transactions` (`app.routes.ts`), por lo que ya **no se usa**. Es código a retirar (ver backlog).

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Origen = destino | `400` "La cuenta de origen y la de destino deben ser distintas" (también validado en el front antes de enviar). |
| Importe 0, negativo o no numérico | El front muestra "Importe no válido…"; el back rechaza con `@Positive`. |
| Cuenta inexistente | `400` "Cuenta no válida". |
| Editar transferencia inexistente | `404` "Transferencia no encontrada". |

## 9. Casos límite y notas

- No hay restricción de divisa: el importe se traslada tal cual entre cuentas (todo en euros).
- No hay comisión ni desfase de fechas entre cargo y abono: una transferencia es un único registro con una sola fecha e importe.
- Al excluirse de ingresos/gastos, mover dinero entre cuentas propias no infla artificialmente los totales del dashboard, pero **sí** cambia los saldos por cuenta.

## 10. Backlog / mejoras futuras

- Retirar el componente legado `pages/transfers/`.
- Soporte de comisión o de fechas distintas para cargo y abono.
- Transferencias entre divisas con tipo de cambio.
- Marcar transferencias recurrentes (p. ej. aporte mensual a ahorro).

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Componente legado | `pages/transfers/` sigue en el repo pero su ruta redirige a `transactions`: es código muerto. | Retirar el componente y sus ficheros. |
| Positividad del importe | `@Positive` solo en la API, sin `check` en BD (igual que movimientos). | Valorar un check de columna. |

## 12. Referencias de código

- Backend: `model/Transfer.java`, `controller/TransferController.java` (incluye `TransferRequest`), `repository/TransferRepository.java`.
- Efecto en saldo: `service/DashboardService.java` (`balanceUntil`), `repository/TransferRepository.java` (`totalInUntil`, `totalOutUntil`).
- Esquema: `db/migration/V1__init.sql`.
- Frontend: gestión en `pages/transactions/`; componente legado en `pages/transfers/`; modelos `Transfer` / `TransferRequest` en `models.ts`.
- Tests: `controller/TransferControllerTest.java` (CRUD con repos mockeados) y, contra Postgres real, `repository/TransferRepositoryTest.java` (el `search` casa una transferencia cuando la cuenta es origen **o** destino y ordena `fecha desc, id desc`; `totalInUntil`/`totalOutUntil` son direccionales y acotados por fecha, y suman 0 sin transferencias). El contrato HTTP se cubre en `controller/TransferControllerMvcTest.java` (slice `@WebMvcTest`): binding de query params con ventana de fechas por defecto, validación del record `TransferRequest` (`@NotNull`/`@Positive`→400) y los `ResponseStatusException` como `problem+json` (mismo origen/destino→400, cuenta no válida→400, no encontrada→404).
- Relacionado: PRD Movimientos, PRD Cuentas, PRD Importación de extractos.
