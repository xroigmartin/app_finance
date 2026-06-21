# PRD — Importación de extractos

| Campo | Valor |
|---|---|
| Estado | Implementado |
| Versión | 1.0 |
| Última actualización | 2026-06-16 |
| Dominio | Importación (CSV/Excel → movimientos y transferencias) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento de la importación (parser, mapeo de columnas, deduplicación o UI). Ver `docs/README.md`.

---

## 1. Propósito

La **importación** permite cargar en bloque movimientos (transacciones) y transferencias desde extractos bancarios en CSV o Excel. Es deliberadamente **tolerante**: detecta la fila de cabecera tras los preámbulos del banco, admite varios formatos de fecha e importe y distintos separadores. Las filas válidas se guardan; las inválidas se omiten y se reportan, sin abortar toda la importación.

## 2. Objetivos y no-objetivos

**Objetivos**
- Importar movimientos a una cuenta a partir de un fichero del banco.
- Importar transferencias entre cuentas a partir de un fichero con origen y destino.
- Tolerar variaciones de formato habituales en extractos.
- Evitar duplicados al reimportar el mismo fichero y reportar el resultado por filas.

**No-objetivos (fuera de alcance de este PRD)**
- Definición de movimientos/transferencias → PRDs Movimientos / Transferencias.
- Reglas de auto-categorización → PRD Reglas de categorización.
- Conexión automática con bancos (open banking): la carga es por fichero.

## 3. Modelo de datos

La importación **no tiene tablas propias**: crea `transactions` y `transfers`. Resultado (`dto/ImportDtos.java`):

| DTO | Contenido |
|---|---|
| `ImportResult` | `imported` (filas guardadas), `duplicated` (omitidas por ya existir), `errors` (lista de errores por fila). |
| `RowError` | `row` (nº de fila en el fichero, contando la cabecera), `message` (motivo). |

## 4. Requisitos funcionales

| ID | Requisito |
|---|---|
| RF-1 | Importar movimientos desde `.csv`/`.txt`/`.xls`/`.xlsx`, indicando opcionalmente una cuenta por defecto. |
| RF-2 | Importar transferencias desde fichero con columnas de origen y destino. |
| RF-3 | Detectar la fila de cabecera aunque haya filas de preámbulo del banco. |
| RF-4 | Aceptar fechas en formatos `ISO`, `dd/MM/yyyy`, `dd-MM-yyyy`, `dd/MM/yy`. |
| RF-5 | Aceptar importes `1234.56`, `1.234,56`, `1234,56`, `-12 €`. |
| RF-6 | Detectar el separador CSV (`,` o `;`) automáticamente. |
| RF-7 | Auto-categorizar movimientos sin categoría (reglas → respaldo). |
| RF-8 | Omitir duplicados al reimportar y devolver un resumen (importadas/omitidas/errores). |

## 5. Reglas de negocio

### Parser (común a movimientos y transferencias)

| ID | Regla |
|---|---|
| RN-1 | Las cabeceras se **normalizan** (minúsculas, sin acentos, sin BOM) para casar columnas de forma laxa. |
| RN-2 | La fila de cabecera es la primera que contiene a la vez una columna `fecha`/`date` y una `importe`/`amount`; lo anterior es preámbulo y se descarta. |
| RN-3 | Fechas: se prueban en orden `ISO`, `dd/MM/yyyy`, `dd-MM-yyyy`, `dd/MM/yy`; si ninguna casa, error de fila. |
| RN-4 | Importes: se eliminan `€`, `$` y espacios; el separador decimal se infiere por la última `,`/`.`. El importe se guarda en **valor absoluto**. |
| RN-5 | Separador CSV: `;` si en la primera línea hay más `;` que `,`; si no, `,`. |

### Movimientos (`/api/transactions/import`)

| ID | Regla |
|---|---|
| RN-6 | Columnas: `fecha` e `importe` obligatorias; opcionales `cuenta`, `categoria`, `tipo`, `descripcion`/`concepto`/`movimiento`, `mas datos`/`observaciones`. |
| RN-7 | **Tipo**: si hay columna `tipo` (`ingreso/income/i`, `gasto/expense/g/e`) se usa; si no, importe negativo = gasto, positivo = ingreso. |
| RN-8 | **Cuenta**: si hay columna `cuenta` se busca por nombre (sin distinguir mayúsculas); si no, se usa la **cuenta por defecto** del formulario; si tampoco, error de fila. Las cuentas **deben existir** (no se crean). |
| RN-9 | **Categoría con columna**: se busca por nombre entre las visibles para la cuenta (una categoría de la cuenta gana a una global homónima); si no existe, **se crea global** con el tipo de la fila y color `#64748b`. |
| RN-10 | **Categoría sin columna**: se aplica la **primera regla** de categorización que coincida (del tipo correcto y visible para la cuenta); si ninguna coincide, va a la categoría de respaldo "Otros gastos" / "Otros ingresos". |
| RN-11 | **Fecha de operación**: si la columna `mas datos`/`observaciones` empieza por "Fecha de operación: …", esa fecha **prevalece** sobre la columna `fecha`, y ese texto no se añade a la descripción. |
| RN-12 | La **descripción** se compone de `descripcion`/`concepto`/`movimiento` y se le anexa `mas datos` con " — " (salvo que sea la fecha de operación). |

### Transferencias (`/api/transfers/import`)

| ID | Regla |
|---|---|
| RN-13 | Columnas: `fecha`, `importe`, `origen`/`desde`/`from`, `destino`/`hasta`/`to` obligatorias; `descripcion` opcional. |
| RN-14 | Si el fichero no tiene columnas de origen y destino, se rechaza con `400` y se sugiere importarlo como movimientos. |
| RN-15 | Origen y destino se buscan por nombre; deben existir y ser **distintos** (error de fila si coinciden). |

### Deduplicación

| ID | Regla |
|---|---|
| RN-16 | Se omite una fila si ya existe en BD un registro con la misma **clave** dentro del rango de fechas del fichero. Clave de movimiento: `cuenta·fecha·tipo·importe·descripción`; de transferencia: `origen·destino·fecha·importe·descripción` (importe y texto normalizados). |
| RN-17 | La deduplicación es por **conteo**: reimportar el mismo fichero lo omite entero, pero un duplicado genuinamente nuevo (dos compras idénticas el mismo día, presentes dos veces en el fichero pero una en BD) **sí** entra. |

## 6. API

| Método | Ruta | Parámetros | Devuelve |
|---|---|---|---|
| `POST` | `/api/transactions/import` | multipart `file`, `accountId?` (cuenta por defecto) | `ImportResult`. |
| `POST` | `/api/transfers/import` | multipart `file` | `ImportResult`. |

Formatos aceptados: `.csv`, `.txt`, `.xlsx`, `.xls`. Otro formato → `400` "Formato no soportado…".

## 7. UI/UX

Componente reutilizable `components/import-dialog.ts` (`ImportDialog`), con `@Input kind: 'transactions' | 'transfers'`.

- Se abre desde la pantalla de **Movimientos** (botón de importar).
- **Movimientos**: pide (1) cuenta de destino y (2) fichero; muestra una ayuda con las columnas esperadas.
- **Transferencias**: pide solo el fichero (las cuentas salen de las columnas origen/destino).
- Zona de archivo que acepta `.csv,.txt,.xlsx,.xls`.
- Tras importar, muestra el **resultado**: nº de filas importadas, nº omitidas por estar ya registradas y la lista de filas con error (con nº de fila y motivo).
- El botón de importar se deshabilita sin fichero (y sin cuenta, en el caso de movimientos).

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| Formato de fichero no soportado | `400` "Formato no soportado. Usa un fichero .csv o .xlsx". |
| Fichero de transferencias sin origen/destino | `400` con sugerencia de importar como movimientos. |
| Fila con fecha/importe inválidos o columna obligatoria ausente | Fila omitida y añadida a `errors` con su nº y motivo; el resto se importa. |
| Cuenta (o cuenta de origen/destino) inexistente | Error de fila "Cuenta no encontrada: …". |
| Movimiento sin `cuenta` y sin cuenta por defecto | Error de fila "Falta la columna cuenta y no se indicó cuenta por defecto". |

## 9. Casos límite y notas

- Las **cuentas no se crean** durante la importación (deben existir); las **categorías sí** se crean al vuelo (globales) si no existen.
- La numeración de filas de error cuenta desde la cabecera (`i + 2`), para que coincida con la fila que ve el usuario en la hoja.
- El rango de fechas para deduplicar lo marca el propio fichero (mínima y máxima fecha de las filas parseadas).
- La lógica de coincidencia de reglas es la misma que la del dominio de reglas (`RecategorizationService.matches`), de modo que importar y recategorizar se comportan igual.

## 10. Backlog / mejoras futuras

- Vista previa de filas antes de confirmar la importación.
- Mapeo manual de columnas cuando los nombres no casan.
- Recordar la última cuenta usada por fichero/banco.
- Soporte de más formatos de fecha/locale y de importes con paréntesis para negativos.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Categorías autocreadas globales | Al importar a una cuenta concreta, una categoría desconocida se crea **global**, no acotada a esa cuenta. | Decidir si debería crearse acotada a la cuenta de destino. |
| Orden de reglas no determinista | La primera regla que coincide gana, pero el orden es el de `findAll()` (sin garantía). | Definir un orden/prioridad explícito (deuda compartida con el PRD Reglas de categorización). |
| Importación a ciegas | No hay vista previa: las filas se guardan al confirmar. | Añadir previsualización antes de persistir (ver backlog). |

## 12. Referencias de código

- Backend: `service/ImportService.java`, `service/ImportFileParser.java`, `dto/ImportDtos.java`.
- Endpoints: `controller/TransactionController.java` (`/import`), `controller/TransferController.java` (`/import`).
- Auto-categorización: `service/RecategorizationService.java` (`matches`), reglas en `category_rules`.
- Frontend: `components/import-dialog.ts`; se invoca desde `pages/transactions/`. Modelo `ImportResult` en `models.ts`.
- Relacionado: PRD Movimientos, PRD Transferencias, PRD Reglas de categorización, PRD Cuentas, PRD Categorías.
