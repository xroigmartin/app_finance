# Mis Finanzas

Aplicación de gestión de finanzas personales con dashboard, movimientos, cuentas y categorías.

- **Backend**: Spring Boot 4 (Java 25) + JPA + PostgreSQL, con migraciones gestionadas por Flyway
- **Frontend**: Angular 20 + Chart.js

## Arranque

Base de datos (PostgreSQL 17 en Docker, puerto 5432):

```bash
docker compose up -d
```

Backend (puerto 8080; Flyway aplica las migraciones de `backend/src/main/resources/db/migration` al arrancar):

```bash
cd backend
mvn spring-boot:run
```

Frontend (puerto 4200, con proxy a `/api`):

```bash
cd frontend
npm install
npx ng serve
```

Abrir <http://localhost:4200>.

## Funcionalidades

- **Dashboard**: balance total, ingresos/gastos/ahorro del mes, evolución de 12 meses (barras), gastos por categoría del mes (donut), saldos por cuenta y últimos movimientos.
- **Movimientos**: listado con filtro por fechas, alta, edición y borrado. Las categorías se filtran según el tipo (ingreso/gasto).
- **Transferencias**: movimientos entre cuentas propias; afectan a los saldos pero no cuentan como ingreso ni gasto.
- **Importación masiva** desde CSV o Excel (`.xls` y `.xlsx`) en las páginas de Movimientos y Transferencias, compatible con las exportaciones de la web del banco. Se importan las filas válidas y se reportan los errores fila a fila.
  - Movimientos: obligatorias `fecha` e `importe`; opcionales `cuenta` (si falta se usa la cuenta por defecto elegida en la página), `categoria` (si falta, "Otros gastos"/"Otros ingresos"), `tipo` (ingreso/gasto; sin él, importe negativo = gasto) y `descripcion`/`movimiento` (+ `mas datos` como detalle adicional). Las categorías desconocidas se crean automáticamente; las cuentas deben existir.
  - Transferencias: columnas `fecha`, `importe`, `origen`, `destino` (+ opcional `descripcion`).
  - Tolerante con formatos bancarios: detecta la fila de cabecera aunque haya texto antes (preámbulos del banco), fechas `dd/MM/yyyy` o `yyyy-MM-dd`, importes `1.234,56` o `1234.56`, CSV con `,` o `;`, cabeceras con o sin acentos. Si "Más datos" contiene "Fecha de operación: …", esa fecha sustituye a la de la columna `fecha`.
- **Reglas de categorización automática** (página de Categorías): patrones de texto (alternativas separadas por `|`, sin distinguir mayúsculas/acentos) que asignan categoría a los movimientos importados sin columna `categoria`. Ej.: `consum|lidl|spar` → Alimentación. Sin regla aplicable, van a "Otros gastos/ingresos".
- **Presupuestos**: importe por categoría de gasto y mes (pueden variar de un mes a otro), con navegación por meses, botón "Copiar mes anterior" y barras de progreso del gasto (también visibles en el dashboard para el mes en curso).
- **Cuentas**: CRUD con saldo inicial; el saldo actual se calcula con los movimientos. No se pueden borrar cuentas con movimientos.
- **Categorías**: CRUD con color personalizable, separadas por tipo. No se pueden borrar categorías con movimientos.

## Configuración

| Variable | Por defecto | Descripción |
|---|---|---|
| `FINANCE_DB_HOST` | `localhost` | Host de PostgreSQL |
| `FINANCE_DB_PORT` | `5432` | Puerto de PostgreSQL |
| `FINANCE_DB_NAME` | `finance` | Nombre de la base de datos |
| `FINANCE_DB_USER` | `finance` | Usuario |
| `FINANCE_DB_PASSWORD` | `finance` | Contraseña |

El esquema lo crea Flyway (`V1__init.sql`); Hibernate solo lo valida (`ddl-auto=validate`). Para cambios de esquema, añade un nuevo script `V2__...sql` en `backend/src/main/resources/db/migration`.

En el primer arranque, con la base de datos vacía, se siembran las **categorías por defecto** (`Nómina`, `Vivienda`, `Alimentación`…) para que la app sea usable de inmediato; no se siembran cuentas ni movimientos de ejemplo. `./app.sh start` arranca todos los servicios.

## API

- `GET/POST /api/transactions` (filtros opcionales `from`, `to`, `accountId`, `categoryId`), `PUT/DELETE /api/transactions/{id}`, `GET /api/transactions/recent`
- `GET/POST /api/accounts`, `PUT/DELETE /api/accounts/{id}`
- `GET/POST /api/categories`, `PUT/DELETE /api/categories/{id}`
- `GET/POST /api/transfers`, `PUT/DELETE /api/transfers/{id}`
- `POST /api/transactions/import?accountId=`, `POST /api/transfers/import` (multipart, campo `file`)
- `GET/POST /api/category-rules`, `PUT/DELETE /api/category-rules/{id}`
- `GET /api/budgets?year=&month=`, `POST /api/budgets`, `PUT/DELETE /api/budgets/{id}`, `POST /api/budgets/copy`
- `GET /api/dashboard/summary`, `GET /api/dashboard/monthly?months=12`, `GET /api/dashboard/expenses-by-category?year=&month=`, `GET /api/dashboard/budgets?year=&month=`
