# Plan de implementación — Historial de imports (Inversiones)

**Rama de desarrollo:** `inv-historial-imports` (worktree `app_finance-imports`).
**Origen:** `docs/investment/mejoras-modulo-inversiones.md` §1.2 (P1.2). Precursor obligatorio de la 2.1 (Flex Web Service).

## 1. Objetivo y alcance

Hoy `FlexImportService.importReport` devuelve un `FlexImportResult` (imported/duplicated/errors/warnings) que solo vive en la respuesta HTTP: al cerrar `FlexImportDialog` desaparece. Si un import de hace un mes dejó 3 warnings, no queda rastro de qué faltó.

**Entra en alcance:**
- Persistir cada intento de import Flex (uno por llamada a `POST /portfolios/{id}/import`, tanto si importó filas como si no) con: fecha/hora, nombre del fichero, periodo cubierto por el informe (`fromDate`/`toDate`, que `FlexReportParser` **ya calcula** en `FlexReport` pero `FlexImportService` descarta hoy), y el resumen completo (contadores + detalle de errores/warnings).
- Vista de consulta paginada por cartera, con detalle de errores/warnings por import.
- Guardar el registro también cuando `imported == 0` (import "vacío" o solo duplicadas) — es información útil ("reimporté el mismo fichero, 0 nuevas, correcto").

**No entra en alcance** (queda para 2.1 Flex Web Service): descarga automática, scheduler, gestión de token IBKR. Este trabajo solo deja el terreno preparado (log persistente que un proceso desatendido pueda escribir).

## 2. Diseño técnico backend

### 2.1 Dominio (`investments/domain/`)

Nuevo agregado `ImportRecord` (fichero `ImportRecord.java`), siguiendo el patrón de los agregados existentes del contexto (record/clase inmutable + factory estático, sin Spring/JPA):

```java
public record ImportRecord(
    Long id,                      // null hasta persistir
    PortfolioId portfolioId,
    Instant importedAt,
    String fileName,              // nullable: MultipartFile.getOriginalFilename() puede venir null
    LocalDate fromDate,           // nullable: FlexReport.fromDate() es opcional en el propio Flex
    LocalDate toDate,             // NOT NULL: FlexReport.toDate() es obligatorio (FlexReportParser lo exige)
    int imported,
    int duplicated,
    List<ImportRowIssue> errors,  // reutiliza la forma de FlexRowError (section, reference, message)
    List<String> warnings
) {
    public static ImportRecord of(PortfolioId portfolioId, Instant importedAt, String fileName,
                                   LocalDate fromDate, LocalDate toDate, FlexImportResult result) { ... }
}
```

`ImportRowIssue` es un VO nuevo en `domain/` con los mismos 3 campos que `application.FlexRowError` (`section`, `reference`, `message`) — **no reutilizamos `FlexRowError` directamente en el dominio** porque vive en `application/` y el dominio no depende hacia arriba; el mapeo `FlexRowError → ImportRowIssue` se hace en `FlexImportService` al construir el `ImportRecord`.

Sin invariantes de negocio complejas (es un registro de log, no una entidad con reglas); basta validar no-nulos de `portfolioId`/`importedAt`/`toDate` en el constructor compacto, igual que otros VOs simples del contexto.

Puerto de salida: `ImportRecordRepository` (interfaz en `domain/`):

```java
public interface ImportRecordRepository {
    ImportRecord save(ImportRecord record);
}
```

(Sin `findById`/`delete`: es un log de solo-escritura desde el dominio; la lectura paginada es responsabilidad del lado CQRS, no de este puerto — mismo patrón que `InvestmentTransactionRepository.save` vs. `InvestmentQueryPort` para lectura.)

### 2.2 Aplicación (`investments/application/`)

**No es un caso de uso aparte.** La persistencia se engancha como paso final de `FlexImportService.importReport`, dentro de la misma transacción `@Transactional` ya existente — si el import falla a medio camino y la transacción hace rollback, no debe quedar un registro de historial "fantasma" de un import que no ocurrió; si el import se completa (aunque sea con errores de fila tolerados, que no abortan), sí se loguea.

Cambio en `FlexImportService.importReport` (después de construir `FlexImportResult`, antes de `return`):

```java
FlexImportResult result = new FlexImportResult(imported, duplicated, List.copyOf(errors), positionWarnings(portfolio));
importRecords.save(ImportRecord.of(portfolio.id(), Instant.now(), file.getOriginalFilename(),
        report.fromDate(), report.toDate(), result));
return result;
```

Nueva dependencia inyectada: `ImportRecordRepository importRecords` en el constructor de `FlexImportService`.

**Lectura (CQRS):** nuevo query port `ImportRecordQueryPort` en `application/`:

```java
public interface ImportRecordQueryPort {
    Page<ImportRecordView> history(long portfolioId, int page, int size);
}
```

`ImportRecordView` (record en `application/`): `id, importedAt, fileName, fromDate, toDate, imported, duplicated, errors (List<FlexRowError>), warnings (List<String>)` — se reutiliza `FlexRowError` aquí porque esta vista sí vive en `application/` (el mismo record que ya usa `FlexImportResult`, evita duplicar forma). Se listan **todas** las filas del import (sin paginar dentro del import — el volumen de un solo Flex anual es de decenas, no miles) y se pagina la lista de imports.

### 2.3 Infraestructura — persistencia (`investments/infrastructure/persistence/`)

**Migración `V8__import_record.sql`** (número reservado, sin coordinar con las otras dos ramas paralelas):

```sql
CREATE TABLE investments.import_record (
    id                BIGSERIAL PRIMARY KEY,
    portfolio_id      BIGINT      NOT NULL REFERENCES investments.portfolio (id),
    imported_at       TIMESTAMP   NOT NULL,
    file_name         VARCHAR,
    from_date         DATE,
    to_date           DATE        NOT NULL,
    imported_count    INT         NOT NULL,
    duplicated_count  INT         NOT NULL,
    errors            JSONB       NOT NULL DEFAULT '[]',
    warnings          JSONB       NOT NULL DEFAULT '[]'
);

CREATE INDEX idx_import_record_portfolio ON investments.import_record (portfolio_id, imported_at DESC);
```

**Decisión: `errors`/`warnings` como `JSONB`, no tabla hija.** Justificación: (a) el resto del contexto `investments` no usa `@OneToMany`/colecciones JPA en ningún sitio — las entidades son planas con FKs como columnas simples (`getReferenceById`), y una tabla hija `import_record_error` introduciría el primer `@OneToMany` del módulo sin necesidad real; (b) errors/warnings nunca se consultan ni filtran a nivel SQL — se leen siempre como bloque completo junto a su import padre, el caso de uso exacto para el que `jsonb` está pensado; (c) volumen pequeño y acotado (decenas de filas por import como mucho). Postgres 17 + `jsonb` con Hibernate 7 vía `@JdbcTypeCode(SqlTypes.JSON)` sobre un `String` (serializado/deserializado a mano con Jackson en el mapper, igual de simple que cualquier otro campo) evita ceremonia.

`ImportRecordJpaEntity` (`@Table(schema = "investments", name = "import_record")`), `ImportRecordJpaRepository extends JpaRepository<ImportRecordJpaEntity, Long>` con método derivado `Page<ImportRecordJpaEntity> findByPortfolioIdOrderByImportedAtDesc(Long portfolioId, Pageable pageable)` (Spring Data pagina de extremo a extremo, igual que ya hace `InvestmentTransactionJpaRepository` — revisar su query concreta antes de implementar para no reinventar el patrón de paginación del contexto), `ImportRecordJpaMapper` (domain↔entity, serializa `List<ImportRowIssue>`/`List<String>` a JSON con `ObjectMapper` inyectado — hay uno ya configurado como bean en el proyecto, reutilizarlo en vez de crear uno nuevo), `ImportRecordPersistenceAdapter implements ImportRecordRepository` (solo `save`), y el propio adapter o uno nuevo `ImportRecordQueryAdapter implements ImportRecordQueryPort` (puede vivir en la misma clase que el adapter de escritura o aparte — decidir en el momento según cómo estén organizados los otros adapters de escritura+lectura del contexto; si `InvestmentQueryAdapter` ya es una clase separada de los adapters de escritura, seguir ese mismo split).

### 2.4 Infraestructura — web (`investments/infrastructure/web/`)

Nuevo endpoint en `PortfolioController` (mismo controlador que ya expone `POST .../import`, coherente con que el historial es "del import de esa cartera"):

```
GET /api/investments/portfolios/{id}/import-history?page=0&size=25
```

Respuesta: `Page<ImportRecordView>` serializado tal cual (mismo patrón que el resto de vistas CQRS del contexto — "las views CQRS se serializan tal cual", PRD §13). Defaults `page=0`/`size=25`, igual que `GET /transactions`.

## 3. Diseño frontend

**Ubicación: cuarta y última pestaña "Importaciones" en `pages/investments-operations`**, orden confirmado con el usuario (2026-07-25): **Operaciones / Dividendos / Cerradas / Importaciones** (`activeTab: 'operaciones' | 'dividendos' | 'cerradas' | 'importaciones'` — el valor `'cerradas'` lo añade la rama paralela de posiciones cerradas, en 3ª posición; esta rama va última). Encaja mejor aquí que en el Panel general: es información operativa/de auditoría del import, no un KPI de cartera. Protocolo de integración: la rama que mergee primero añade su pestaña en la posición que le toque dentro de las que existan; la que mergee segunda hace rebase/merge sobre la primera y añade la suya al final, sin reordenar las ya mergeadas.

Contenido de la pestaña:
- Tabla paginada (reutiliza `app-pagination`, igual que la pestaña Operaciones — mismo componente, 5/10/25/50/100 por página): fecha/hora del import, fichero, periodo cubierto (`fromDate`–`toDate`, o solo `toDate` si `fromDate` es null), importadas, duplicadas, nº errores, nº warnings.
- Fila expandible (o botón "ver detalle") para desplegar la lista de errores/warnings de ese import concreto, reutilizando el mismo bloque visual `<ul class="errors">`/`<ul class="warnings">` que ya existe en `flex-import-dialog.ts` (extraer a un pequeño componente compartido si el CSS/markup coincide al 90%, o duplicar si diverge — decidir al implementar, no hacer abstracción prematura si solo hay 2 usos).
- Al completar un import con éxito (`FlexImportDialog.done` ya emite hoy), la pestaña de Importaciones debe recargar si está activa — mismo mecanismo que ya dispara `loadTransactions()`/`loadIncome()` en `investments-operations.ts`.

**Modelos nuevos en `models.ts`:** `ImportRecordView { id, importedAt, fileName, fromDate, toDate, imported, duplicated, errors: FlexRowError[], warnings: string[] }` y `Page<ImportRecordView>` (o reutilizar el `Page<T>` genérico si ya existe uno en el frontend — comprobar si `models.ts` ya tiene un tipo `Page<T>` para las Operaciones paginadas, y reutilizarlo).

**`api.service.ts`:** `getImportHistory(portfolioId: number, page: number, size: number): Observable<Page<ImportRecordView>>` → `GET /investments/portfolios/{portfolioId}/import-history`.

**H-imp.7 (post-implementación, a petición del usuario tras probar MT-1 desde el Panel general):** el diálogo `FlexImportDialog` ya mostraba el resumen/errores/*warnings* de un import de forma inmediata (comportamiento previo a este RF), pero eso solo cubre el momento del import — el historial persistido (RF-11) vive en otra pestaña, y de otra página si el import se lanzó desde el Panel general. Para tender el puente sin forzar navegación no solicitada: cuando `result.errors.length > 0 || result.warnings.length > 0`, el diálogo muestra un botón **"Ver detalle en Importaciones →"** que cierra el diálogo y navega a `/investments/operations?tab=importaciones` (`FlexImportDialog.goToImportHistory()`); si el usuario no lo pulsa, "Cerrar" se comporta exactamente igual que antes (se queda en la página en la que estaba). `InvestmentsOperationsPage` consume el query param en `ngOnInit` (suscripción a `ActivatedRoute.queryParamMap`), activa la pestaña y lo limpia de la URL (`router.navigate([], { queryParams: {}, replaceUrl: true })`) para que no quede pegado en recargas futuras. No hace falta pasar el `portfolioId` por la URL: la cartera es estado compartido (`InvestmentContextService`), así que el historial que aparece es el de la cartera con la que se acaba de importar.

## 4. Plan TDD por hitos

Cada hito cierra con commit propio (tests en verde + PRD actualizado), como manda `CLAUDE.md`.

**H-imp.1 — Dominio.**
Rojo: `ImportRecordTest` (unit) — `of()` construye correctamente desde un `FlexImportResult`; rechaza `portfolioId`/`toDate` nulos. `ImportRowIssueTest` si tiene alguna validación propia (probablemente no, VO trivial — valorar si merece test dedicado o se cubre indirectamente).
Verde: `ImportRecord`, `ImportRowIssue`, `ImportRecordRepository` (interfaz, sin implementación).

**H-imp.2 — Persistencia.**
Rojo: `ImportRecordPersistenceAdapterTest` (`@DataJpaTest` sobre Testcontainers) — guarda un `ImportRecord` con errores/warnings no vacíos y lo relee, comprueba round-trip del JSON (orden y contenido de `errors`/`warnings` preservado), comprueba que `to_date NOT NULL` se respeta a nivel BD.
Verde: migración `V8__import_record.sql`, `ImportRecordJpaEntity`, `ImportRecordJpaRepository`, `ImportRecordJpaMapper`, `ImportRecordPersistenceAdapter`.

**H-imp.3 — Enganche en el import existente.**
Rojo: en `FlexImportServiceTest` (aplicación, puertos mockeados) — nuevo test "un import persiste un ImportRecord con los mismos contadores que el FlexImportResult devuelto" y "un import con 0 filas nuevas (todo duplicado) también persiste su ImportRecord" (verifica `Mockito.verify(importRecords).save(...)` con los campos esperados, incluido `fromDate`/`toDate` tomados del `FlexReport`).
Verde: inyectar `ImportRecordRepository` en `FlexImportService` y añadir la llamada `.save(...)` antes del `return`.

**H-imp.4 — Lectura CQRS + endpoint.**
Rojo: `ImportRecordQueryAdapterTest` (`@DataJpaTest`) — con 3 imports guardados para una cartera, `history(portfolioId, 0, 2)` devuelve página de 2 ordenada por `importedAt` descendente, `totalElements=3`, `totalPages=2`. `PortfolioControllerTest` (`@WebMvcTest`) — `GET .../import-history?page=0&size=10` devuelve 200 con el `Page<ImportRecordView>` esperado (puerto mockeado).
Verde: `ImportRecordQueryPort`, `ImportRecordView`, adapter de lectura, endpoint en `PortfolioController`.

**H-imp.5 — Frontend: modelos + servicio.**
Rojo: `api.service.spec.ts` — `getImportHistory()` llama a la URL correcta con `page`/`size` como query params y mapea la respuesta.
Verde: `ImportRecordView`/`Page<T>` en `models.ts`, método en `api.service.ts`.

**H-imp.6 — Frontend: pestaña Importaciones.**
Rojo: `investments-operations.spec.ts` (Vitest) — al activar la pestaña "importaciones" se llama a `getImportHistory`, se renderiza la tabla con los datos devueltos, el `app-pagination` dispara una nueva carga al cambiar de página. Playwright: extender `e2e/investments-operations.spec.ts` con un caso que importa un Flex de fixture, cambia a la pestaña Importaciones y comprueba que aparece la fila con los contadores correctos.
Verde: pestaña nueva en `investments-operations.ts`/`.html`, tabla + expansión de detalle.

**H-imp.7 — Frontend: enlace desde el diálogo al historial (post-implementación, ver §3).**
Rojo: `flex-import-dialog.spec.ts` — `goToImportHistory()` cierra el diálogo (`visible = false`) y navega con `Router.navigate(['/investments/operations'], { queryParams: { tab: 'importaciones' } })`. `investments-operations.spec.ts` — con `?tab=importaciones` en la ruta, `ngOnInit` activa la pestaña, carga el historial y limpia el query param; sin el query param no fuerza ninguna pestaña. Playwright: `e2e/investments-dashboard.spec.ts` — un import con errores lanzado desde el Panel general, clic en "Ver detalle en Importaciones →", aterriza en Operaciones con la fila y el contador de errores visibles.
Verde: `goToImportHistory()` + botón condicional en `flex-import-dialog.ts`; suscripción a `ActivatedRoute.queryParamMap` en `investments-operations.ts`.

## 5. Actualización de PRD requerida

`docs/prd/inversiones.md`:
- §4 (RF): nuevo requisito funcional, p. ej. "RF-11: El usuario puede consultar el historial de imports Flex de una cartera (fecha, fichero, periodo cubierto, resumen ok/duplicadas/errores/warnings)".
- §3 (modelo de datos): añadir tabla `import_record` con su esquema, junto a las 5 existentes.
- §6 (API): añadir `GET /portfolios/{id}/import-history`.
- §7 (UI/UX): documentar la tercera pestaña "Importaciones" en `investments-operations`.
- §10 (backlog): quitar "Historial de imports" del backlog si estaba anotado ahí (revisar; en este PRD no aparece explícitamente en §10, solo en el roadmap externo — puede que no requiera cambio aquí, pero si el hito se referencia en §12/§13 sí hay que añadirlo).
- §12 (fases): nuevo hito, probablemente dentro de una "F4" ampliada o una fase propia — decidir encaje al implementar, coherente con que F4 ya es "Automatización (backlog)" y este es su precursor, no backlog puro.
- §13 (referencias de código): añadir el agregado, los adapters y el endpoint una vez implementado.
- Bump de "Última actualización" y versión.

También tocar `docs/investment/mejoras-modulo-inversiones.md` §1.2: marcar como implementada o mover el texto a "hecho" (documento vivo, no PRD formal, pero mantiene la trazabilidad del roadmap).

## 6. Cómo prepara 2.1 (Flex Web Service) — sin implementarlo

Con este trabajo, un import disparado por un proceso desatendido (futuro scheduler + token IBKR) queda **auditable**: cualquier fallo o warning silencioso se puede consultar después en la pestaña Importaciones sin depender de que alguien estuviera mirando la respuesta HTTP en el momento. El caso de uso `ImportFlexReport` no cambia de forma (mismo puerto de entrada), así que 2.1 solo tendrá que invocarlo con un `MultipartFile` sintético desde el fichero descargado — el logging ya estará resuelto.

## 7. Puntos de fricción con las otras dos ramas paralelas

- **`docs/prd/inversiones.md`**: las tres ramas (precios, historial de imports, posiciones cerradas) tocan este mismo fichero en secciones distintas (RF, modelo, API, UI, backlog, referencias de código) → conflicto de merge garantizado pero de texto, trivial de resolver a mano al integrar una rama cada vez (no las tres juntas).
- **`PortfolioController.java`**: si la rama de "posiciones cerradas" también añade un endpoint ahí (`GET .../closed-positions` o similar), ambas ramas tocan el mismo fichero en puntos distintos — conflicto de línea baja probabilidad si los métodos nuevos no quedan adyacentes, pero revisar al mergear.
- **`investments-operations.ts`/`.html`**: **choque confirmado** — "posiciones cerradas" también añade una pestaña a esta misma página. Orden acordado con el usuario: Operaciones / Dividendos / Cerradas / Importaciones (esta rama, última). Ver protocolo de integración en la sección de diseño frontend (§3) arriba.
- **Numeración de migración**: `V8` está reservada para esta rama. Si "precios" acaba necesitando una migración (p. ej. columna `source` en `price_quote` para distinguir Flex vs. API externa), le corresponde `V9`, no `V8`. "Posiciones cerradas" no debería necesitar migración (es capa de lectura sobre datos existentes).
- **Menú lateral / rutas**: esta rama no añade páginas ni rutas nuevas (la pestaña vive dentro de una página ya enlazada), así que no debería tocar `app.routes.ts` ni el componente de menú — menor riesgo de choque en ese frente que las otras dos si alguna añade una página nueva.

## 8. Plan de pruebas de validación manual

Complementa la suite automatizada (H-imp.1–H-imp.7, todos verdes: 758 tests backend, 365 Vitest, 28 Playwright). Pensado para ejecutarse a mano contra el stack real (`./app.sh start`), navegador en `http://localhost:4200`. Marca cada caso al ejecutarlo; si algo falla, anota el hallazgo antes de seguir.

**Nota sobre MT-1/MT-3 (aclaración surgida al ejecutarlas la primera vez):** el resumen que aparece **dentro del propio diálogo** de import (importadas/duplicadas/errores/warnings, con el detalle de errores en línea) es comportamiento **previo** a este RF-11, no la pestaña Importaciones. Para ver la fila persistida hay que cerrar el diálogo (o pulsar el nuevo enlace de MT-15) e ir a Operaciones → Importaciones.

**Preparación:**
- Stack arrancado (`./app.sh status` para confirmar `db`/`backend`/`frontend` arriba).
- Fichero `backend/src/test/resources/investments/flex/flex-sample.xml` (o `frontend/e2e/fixtures/flex-sample.xml`, idéntico) a mano para subir desde el navegador.
- Una copia del mismo fichero **sin el atributo `fromDate`** en `<FlexStatement>` (para MT-5) — duplícalo y edita esa línea a mano.
- Una cartera nueva con divisa base EUR (crear desde "Nueva cartera" en Operaciones).

| # | Caso | Pasos | Resultado esperado |
|---|---|---|---|
| MT-1 | Import feliz | Importar `flex-sample.xml` en una cartera nueva. Ir a la pestaña Importaciones. | Aparece una fila: fecha/hora actual, fichero `flex-sample.xml`, periodo `05/01/2024 – 31/12/2024`, importadas 11, duplicadas 0, errores 3, avisos 0. |
| MT-2 | Reimport (todo duplicado) | Importar el mismo fichero otra vez en la misma cartera. | Se añade una **segunda** fila (más reciente, arriba): importadas 0, duplicadas 11, errores 3 — no desaparece ni se fusiona con la anterior. Esto es justo lo que motivó el RF-11 (antes esta información se perdía). |
| MT-3 | Detalle de errores | En la fila de MT-1, pulsar "Ver detalle". | Se despliega una fila con `<ul class="errors">` de 3 líneas (fila de opción no soportada, etc.), texto legible. Pulsar de nuevo colapsa. |
| MT-4 | Detalle de avisos | Editar la copia de `flex-sample.xml` para añadir una venta de un instrumento sin posición previa (o usar `frontend/e2e/fixtures/flex-sample-warning.xml`, ya preparado para esto) e importarla. | La fila muestra avisos ≥ 1; "Ver detalle" muestra `<ul class="warnings">` con el texto "venta sin posición suficiente". **Ojo**: esto solo se puede provocar vía import — el alta manual de una operación rechaza la misma situación con un error 400 (RN-4, lado duro), a diferencia del import (RN-4, lado blando). No es un bug, es la regla de negocio; no lo confundas con un fallo si lo pruebas primero a mano. |
| MT-5 | `fromDate` nulo | Importar la copia sin `fromDate`. | La columna Periodo muestra solo la fecha de fin (`31/12/2024`), sin guion ni "–" colgando. |
| MT-6 | `fileName` nulo | Con `curl`/Postman, `POST /api/investments/portfolios/{id}/import` con un `multipart/form-data` cuya parte de fichero no lleve `filename`. | El import se procesa igual; en la pestaña Importaciones esa fila muestra "—" en la columna Fichero en vez de vacío o `null`. |
| MT-7 | Aislamiento por cartera | Repetir MT-1 en una segunda cartera distinta. | El historial de cada cartera solo muestra sus propios imports; cambiar de cartera en el selector superior recarga la tabla con los del historial correcto. |
| MT-8 | Estado vacío | Crear una tercera cartera y entrar directamente en Importaciones sin haber importado nada. | Tabla vacía con el mensaje "Sin imports registrados todavía." (no un error, no un spinner colgado). |
| MT-9 | Carga perezosa | Abrir la página con la pestaña Operaciones activa (por defecto) y mirar la pestaña Red del navegador. Cambiar de cartera sin tocar la pestaña Importaciones. | **No** debe verse ninguna llamada a `import-history` hasta la primera vez que se activa esa pestaña. |
| MT-10 | Recarga en caliente tras import | Con la pestaña Importaciones activa, pulsar "Importar Flex" y completar un import (con o sin filas nuevas). | Al cerrar el diálogo, la tabla de Importaciones se refresca sola con la nueva fila, sin recargar la página a mano — incluye el caso "todo duplicado" (antes de este trabajo ese caso no refrescaba nada). |
| MT-11 | Paginación | Generar más de 25 imports en una cartera (repetir MT-1/MT-2 las veces que haga falta, o vía `curl` en bucle) y cambiar el tamaño de página (5/10/25/50/100). | La tabla, el indicador "Página X de Y" y los botones Anterior/Siguiente se comportan igual que en Operaciones; cambiar el tamaño no deja una página "fantasma" con el tamaño antiguo (mismo bug ya cubierto por regresión en Operaciones/Movimientos). |
| MT-12 | Tema oscuro | Activar el tema oscuro (interruptor de la barra lateral) con la pestaña Importaciones abierta y el detalle desplegado. | Contraste correcto en la tabla y en las listas de errores/avisos; nada ilegible ni con fondo blanco residual. |
| MT-13 | API directa | `curl http://localhost:8080/api/investments/portfolios/{id}/import-history?page=0&size=10` | JSON `{content, page, size, totalElements, totalPages}` con la forma de `ImportRecordView`; probar también sin `page`/`size` (defaults 0/25). |
| MT-14 | Regresión de pestañas hermanas | Con imports ya registrados, navegar libremente entre Operaciones/Dividendos/Importaciones varias veces seguidas. | Ninguna pestaña pierde su estado (filtros, año de dividendos, página de historial) de forma inesperada; los gráficos de Dividendos se siguen pintando. |
| MT-15 | Enlace al historial desde el diálogo (H-imp.7) | Desde el **Panel general** (no Operaciones), importar `flex-sample.xml` (deja 3 errores). En el diálogo, pulsar **"Ver detalle en Importaciones →"**. | El diálogo se cierra y la app navega a Operaciones con la pestaña **Importaciones** ya activa, mostrando la fila del import recién hecho (misma cartera, sin selección manual). Repetir el import sin errores/warnings (p. ej. reimportando el mismo fichero hasta que solo queden errores tolerados — o comprobar directamente que el botón **no aparece** cuando `errors`/`warnings` están vacíos) y comprobar que "Cerrar" se comporta como antes: el diálogo se cierra y la página no cambia. |
