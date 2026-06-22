# PRD — Dashboard

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.0 |
| Última actualización | 2026-06-22 |
| Dominio | Dashboard / agregaciones (`/api/dashboard`) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento del dashboard (agregaciones, endpoints o UI). Ver `docs/README.md`.

---

## 1. Propósito

El **dashboard** es la vista analítica de solo lectura: resume saldos, ingresos, gastos, ahorro, evolución temporal, desglose por categoría, comparativa entre cuentas y progreso de presupuestos. No crea ni modifica datos; agrega lo que producen los demás dominios.

## 2. Objetivos y no-objetivos

**Objetivos**
- Mostrar un resumen financiero del mes y del año seleccionados.
- Visualizar evolución mensual de ingresos/gastos, ahorro y patrimonio.
- Desglosar ingresos y gastos por categoría.
- Comparar cuentas entre sí y mostrar el progreso de los presupuestos del mes.

**No-objetivos (fuera de alcance de este PRD)**
- Edición de datos (movimientos, presupuestos, etc.) → sus respectivos PRDs.
- Definición de presupuestos → PRD Presupuestos.

## 3. Modelo de datos

El dashboard **no tiene tablas propias**. Lee de `transactions`, `transfers`, `accounts`, `categories` y `monthly_budgets`, y devuelve DTOs de solo lectura (`dto/DashboardDtos.java`):

| DTO | Contenido |
|---|---|
| `Summary` | Saldo total, ingresos/gastos/ahorro del mes y del año, deltas de saldo (mes/año) y porcentajes de crecimiento y de rentabilidad del ahorro, más la lista de saldos por cuenta. |
| `AccountBalance` | `id`, `name`, `type`, `balance` (a fin del mes seleccionado). |
| `CategoryAmount` | `category`, `color`, `amount` (suma por categoría principal). |
| `MonthlyPoint` | `month` (`YYYY-MM`), `income`, `expense`. |
| `BalancePoint` | `month`, `balance` (patrimonio a fin de ese mes). |
| `AccountSeries` / `AccountComparison` | Series mensuales de ingreso/gasto por cuenta, con etiquetas de mes. |
| `BudgetStatus` | Presupuesto vs. gastado vs. restante por categoría/cuenta del mes. |

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Resumen del mes/año seleccionados, global o de una cuenta. |
| RF-2 | Serie mensual de ingresos y gastos (N meses hasta un mes final). |
| RF-3 | Serie mensual del patrimonio (saldo total a fin de cada mes). |
| RF-4 | Desglose de gastos por categoría y de ingresos por categoría. |
| RF-5 | Comparativa de ingresos/gastos por cuenta a lo largo de N meses. |
| RF-6 | Estado de los presupuestos del mes (planificado, gastado, restante). |
| RF-7 | Todos los endpoints aceptan filtrar por cuenta (salvo la comparativa por cuenta, que siempre cubre todas). |

## 5. Reglas de negocio

| ID | Regla |
|---|---|
| RN-1 | Las **transferencias se excluyen** de los ingresos y gastos; el **patrimonio (saldo) sí** las incluye (resta en origen, suma en destino). |
| RN-1b | Las **devoluciones** (gastos con `refundOf`) netean con signo invertido en todas las cifras: **reducen el gasto** de su categoría y **suman al saldo**. El neteo está en las queries de `TransactionRepository`, así que summary, series, desgloses por categoría y "gastado" de presupuestos lo aplican automáticamente. |
| RN-2 | El saldo de una cuenta a una fecha = `saldo_inicial + neto de movimientos + transferencias entrantes − salientes` hasta esa fecha (`balanceUntil`). |
| RN-3 | En los desgloses por categoría, las **subcategorías se enrollan a su categoría principal** (`sumByCategory`). El join al padre es un `left join` explícito: las categorías de primer nivel (sin padre) también cuentan con sus movimientos directos. (Antes un join implícito sobre `t.category.parent.name` forzaba un *inner join* que las descartaba; corregido y cubierto por test de Nivel 2.) |
| RN-4 | `Summary` ofrece dos lecturas de rentabilidad: **crecimiento de saldo** (delta de saldo, incluye transferencias) y **rentabilidad del ahorro** (ingresos − gastos, excluye transferencias). Los porcentajes son `null` cuando el saldo de partida no es positivo. |
| RN-5 | El "gastado" de cada presupuesto se calcula sobre el **árbol de la categoría** (principal + subcategorías) y acotado a la cuenta del presupuesto (`sumByCategoryTreeAndPeriod`). |
| RN-6 | El nº de meses de las series se **acota a `[1, 36]`** (por defecto 12). |
| RN-7 | Sin `accountId`, las cifras son del **conjunto de todas las cuentas**; con `accountId`, de esa cuenta. La comparativa `by-account` ignora el filtro y siempre incluye todas las cuentas. |
| RN-8 | Sin `year`/`month`, se usan el año y mes **actuales**. |

## 6. API

Base: `/api/dashboard`. Todos son `GET` de solo lectura.

| Ruta | Parámetros | Devuelve |
|---|---|---|
| `/summary` | `year?`, `month?`, `accountId?` | `Summary`. |
| `/monthly` | `months=12`, `year?`, `month?`, `accountId?` | `MonthlyPoint[]` (ingreso/gasto por mes). |
| `/monthly-balance` | `months=12`, `year?`, `month?`, `accountId?` | `BalancePoint[]` (patrimonio por mes). |
| `/expenses-by-category` | `year?`, `month?`, `accountId?` | `CategoryAmount[]`. |
| `/income-by-category` | `year?`, `month?`, `accountId?` | `CategoryAmount[]`. |
| `/by-account` | `months=12`, `year?`, `month?` | `AccountComparison` (todas las cuentas). |
| `/budgets` | `year?`, `month?`, `accountId?` | `BudgetStatus[]` (ordenado por gastado desc). |

`months` se interpreta como "los N meses que terminan en el mes final (year/month)".

## 7. UI/UX

Página `pages/dashboard` (componente `DashboardPage`), con **Chart.js** directamente (sin wrapper).

- Selector de **cuenta** y de **mes/año**; al cambiarlos se recargan todas las cifras y gráficos.
- **Tarjetas de resumen**: saldo total, ingresos/gastos/ahorro del mes y del año, con sus porcentajes.
- **Gráficos**: barras de ingresos/gastos mensuales; doughnut de gastos por categoría; doughnut de ingresos por categoría; línea de ahorro mensual; línea de evolución del patrimonio; barras comparativas de ingresos por cuenta y de gastos por cuenta.
- **Progreso de presupuestos**: lista con barra de porcentaje gastado por categoría.
- **Últimos movimientos**: las 10 transacciones recientes (`/api/transactions/recent`).
- **Tema**: los gráficos leen colores de `ThemeService` (`chartText()` / `chartGrid()`) y se redibujan mediante un `effect` al cambiar de tema claro/oscuro.

## 8. Validaciones y errores

- Es de solo lectura: no hay validaciones de escritura.
- `months` fuera de rango se acota silenciosamente a `[1, 36]`.
- Parámetros ausentes toman valores por defecto (mes/año actuales, 12 meses, todas las cuentas).
- Sin datos, las series devuelven ceros y los desgloses listas vacías (no errores).

## 9. Casos límite y notas

- Mover dinero entre cuentas propias no altera ingresos/gastos del dashboard, pero **sí** cambia el patrimonio por cuenta.
- Los porcentajes de rentabilidad se omiten (`null`) cuando el saldo de partida es ≤ 0, para no dividir por base no positiva.
- La distinción entre "crecimiento de saldo" y "rentabilidad del ahorro" es deliberada: la primera incluye transferencias y la segunda no.

## 10. Backlog / mejoras futuras

- Rango de fechas libre (no solo mes/año).
- Exportar el resumen o los gráficos.
- Comparativa interanual (mismo mes de años distintos).
- Filtro por categoría en el dashboard.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| `by-account` ignora `accountId` | A diferencia del resto de endpoints, la comparativa por cuenta siempre cubre todas las cuentas. | Inconsistencia menor de API; documentada como intencional, revisar si confunde. |
| "Gastado" de presupuesto duplicado | El cálculo del gastado vive aquí (`sumByCategoryTreeAndPeriod`) y de otra forma en presupuestos. | Misma observación que en el PRD Presupuestos: valorar unificar. |

## 12. Referencias de código

- Backend: `controller/DashboardController.java`, `service/DashboardService.java`, `dto/DashboardDtos.java`.
- Consultas de agregación: `repository/TransactionRepository.java` (`sumByTypeAndPeriod`, `sumByCategory`, `sumByCategoryTreeAndPeriod`, `netTotalByAccountUntil`), `repository/TransferRepository.java` (`totalInUntil`, `totalOutUntil`).
- Tests: `service/DashboardServiceTest.java` (matemática de agregación con repos mockeados), `controller/DashboardControllerTest.java` (lógica con mocks) y `controller/DashboardControllerMvcTest.java` (contrato HTTP con el slice `@WebMvcTest`: binding con fallback a la fecha actual, clamp de `months` a 1..36, serialización de listas/DTO y param numérico mal formado→400, verificando la delegación al `DashboardService`).
- Frontend: `pages/dashboard/` (`dashboard.ts`, `dashboard.html`), `theme.service.ts`, modelos en `models.ts`.
- Relacionado: PRD Cuentas, PRD Movimientos, PRD Transferencias, PRD Presupuestos, PRD Categorías.
