# PRD — Observabilidad (logging)

| Campo | Valor |
|---|---|
| Estado | 🚧 En curso — formateador OTel, enrutado de dos niveles, logging de sistema y el caso piloto de negocio (import de Inversiones) listos; falta la correlación de trazas (Micrometer Tracing) |
| Versión | 0.3 |
| Última actualización | 2026-07-21 |
| Dominio | Transversal (no es un bounded context de negocio; vive en `shared/infrastructure/logging` y se usa desde cualquier contexto) |
| Responsable | Equipo Mis Finanzas |

> Mantenimiento obligatorio: este PRD debe actualizarse en el mismo cambio de código que modifique el comportamiento del logging (formato, enrutado, campos, retención). Ver `docs/README.md`.

---

## 1. Propósito

Origen: al diagnosticar un fallo real de import de Inversiones (reversas de IBKR violando el convenio de signos §3 del PRD de Inversiones, ver `docs/prd/inversiones.md` §11) hubo que abrir el XML original del Flex —fuera de git por contener datos financieros reales (`docs/investment/`, ver `.gitignore`)— porque la app no dejaba ningún rastro del fallo. El sistema de logging existe para que **cualquier fallo, de sistema o de una acción de negocio, se pueda diagnosticar leyendo los logs de la propia app, sin depender de abrir el fichero fuente con datos reales**.

## 2. Objetivos y no-objetivos

**Objetivos**
- Dos niveles de logging, físicamente separados: **sistema** (arranque, excepciones no controladas, conexión a BD) y **negocio** (import de inversiones/gastos, alta manual de movimientos, alta de cuentas...), cada uno con su propio fichero.
- Formato JSON estructurado desde el día uno, con el shape del **Log Data Model de OpenTelemetry** (`Timestamp`/`SeverityText`/`SeverityNumber`/`Body`/`TraceId`/`SpanId`/`Attributes`/`Resource`), para que integrar SigNoz en el futuro sea solo apuntar un OTel Collector a los ficheros — sin tocar código de negocio.
- El log de negocio lleva el contexto necesario para explicar por qué falló una operación (tipo, importe, fecha, identificadores de negocio), pero **nunca** identificadores de cuenta reales de un proveedor externo (IBAN, número de cuenta de bróker) ni el nombre del titular.
- Preparar ya la correlación de trazas (`trace_id`/`span_id` por petición HTTP, vía Micrometer Tracing con puente a OTel) aunque no haya ningún exporter/collector configurado todavía.

**No-objetivos (fuera de alcance de este PRD)**
- Exportar a un backend real (SigNoz u otro) — hoy no hay ningún exporter OTLP configurado; los logs solo se escriben a fichero local. Ver §10.
- Métricas de aplicación (contadores, histogramas) — solo logs y, en preparación, trazas.
- Un histórico de imports persistido en BD y consultable desde la UI — eso es la mejora **"1.2 Historial de imports"** de `docs/investment/mejoras-modulo-inversiones.md`, complementaria y no sustituida por este PRD: el log de negocio es la red de seguridad de bajo nivel que existe desde ya para *cualquier* acción, tenga o no una vista de histórico dedicada.

## 3. Modelo (diseño)

### 3.1 Shape JSON (una línea por evento)

```json
{
  "Timestamp": "2026-07-21T10:42:03.512+02:00",
  "SeverityText": "WARN",
  "SeverityNumber": 13,
  "Body": "import_row_rejected",
  "TraceId": "6c4f3f1a9e2b4d7c8a5f0e3d2c1b9a8f",
  "SpanId": "3a2b1c0d9e8f7a6b",
  "Attributes": {
    "logger": "business.investments",
    "type": "TAX",
    "external_id": "CT-3702536094",
    "amount": "0.46"
  },
  "Resource": { "service.name": "finance-backend" }
}
```

- `TraceId`/`SpanId`: se omiten (no aparecen en el JSON) cuando no hay contexto de traza activo — no se fabrica un id falso.
- `Attributes`: siempre incluye `logger` (nombre del logger SLF4J); el resto son los pares clave-valor añadidos con la API fluida de SLF4J 2.x (`log.atWarn().addKeyValue(...)`).
- `SeverityNumber`: mapeo fijo por nivel Logback → TRACE=1, DEBUG=5, INFO=9, WARN=13, ERROR=17 (convención de OpenTelemetry).
- `Resource.service.name`: constante `"finance-backend"` (mismo valor que `spring.application.name`).

### 3.2 Componentes

| Pieza | Qué hace | Dónde vive |
|---|---|---|
| `OtelJsonLogFormatter` | Renderiza un `ILoggingEvent` de Logback como la línea JSON de §3.1. | `shared/infrastructure/logging/OtelJsonLogFormatter.java` |
| `OtelJsonEncoder` | `Encoder` de Logback que envuelve el formatter; deliberadamente **no** usa el `StructuredLogEncoder` de Spring Boot porque ese requiere un `Environment` de Spring ya arrancado (no disponible al cargar `logback.xml`, y no necesario aquí — evita esa dependencia). | `shared/infrastructure/logging/OtelJsonEncoder.java` |
| `logback.xml` | Enrutado de dos niveles: logger `business` (y sus hijos `business.<contexto>`) con `additivity=false` → consola + `business.log`; `root` → consola + `system.log`. Ruta configurable con la variable de entorno `FINANCE_LOG_PATH` (por defecto `logs`, relativo al directorio de trabajo del proceso). Rotación diaria/10MB, retención 14 días. | `src/main/resources/logback.xml` |

### 3.3 Convención de nombres de logger de negocio

`business.<contexto>` (p. ej. `business.investments`, `business.transactions`), un logger por bounded context que emite eventos de negocio. Logback los enruta todos a `business.log` por el prefijo común `business`, y permite filtrar por contexto si hace falta (`grep '"logger":"business.investments"'`).

## 4. Requisitos funcionales

| ID | Requisito | Estado |
|---|---|---|
| RF-1 | Todo log (de sistema o de negocio) se escribe como una línea JSON con el shape de §3.1. | ✅ |
| RF-2 | Los logs de negocio se escriben en un fichero distinto de los de sistema, sin duplicarse entre ambos. | ✅ |
| RF-3 | La ruta de los ficheros de log es configurable por entorno (`FINANCE_LOG_PATH`), nunca versionada en git. | ✅ |
| RF-4 | `DomainExceptionHandler`/`DataIntegrityExceptionHandler` dejan constancia en `system.log` (WARN, `exception`+`detail`) de cada excepción de dominio o de integridad traducida a una respuesta HTTP 4xx. | ✅ |
| RF-5 | Cada fila rechazada de un import de Inversiones (Flex) queda en `business.log` con tipo, `external_id`, fecha, importe/divisa, identidad del instrumento, descripción y motivo del rechazo. | ✅ (`FlexImportService.logRejectedRow`) |
| RF-6 | Cada petición HTTP genera un `trace_id`/`span_id` (Micrometer Tracing, puente OTel, sin exporter configurado) que aparece en todo log emitido durante esa petición. | ⬜ Pendiente (siguiente hito) |

## 5. Reglas de negocio (contenido y redacción)

| ID | Regla |
|---|---|
| RN-1 | El log de negocio solo registra **fallos** (o avisos), nunca el detalle fila a fila de una operación exitosa — evita volcar el histórico financiero completo a un fichero en cada import. |
| RN-2 | Nunca se loguea un identificador de cuenta real de un proveedor externo (IBAN, número de cuenta de bróker) ni el nombre del titular, en ningún contexto. |
| RN-3 | El texto libre (`description` de una fila importada) se incluye en `Attributes` solo cuando es contenido institucional/no personal (p. ej. el texto que el propio bróker pone en un dividendo). En contextos donde ese texto libre puede llevar datos personales (comercio, ubicación — gastos familiares importados de un extracto bancario), se omite explícitamente con `description_omitted=true` en vez de truncarlo (truncar a medias podría dejar justo el fragmento sensible). |
| RN-4 | El dominio (`domain/`) nunca loguea — coherente con que no depende de ningún framework. El logging de negocio vive en `application/` (los `XService`, que ya orquestan el caso de uso y tienen el contexto completo de la operación); el logging de sistema vive en `infrastructure/` (arranque, adaptadores web). |

## 6. API

No aplica — el logging no expone API propia. (Si en el futuro se persiste un histórico de imports consultable, ver la mejora "1.2 Historial de imports" del backlog de Inversiones, que sí tendría su propio endpoint.)

## 7. UI/UX

No aplica — los logs se consultan directamente en fichero (o, en el futuro, en SigNoz una vez integrado).

## 8. Validaciones y errores

| Caso | Comportamiento |
|---|---|
| No hay contexto de traza activo (código ejecutado fuera de una petición HTTP, p. ej. arranque) | `TraceId`/`SpanId` se omiten del JSON en vez de escribir un id inválido/falso. |
| Un logger de negocio sin pares clave-valor | `Attributes` sigue presente, con solo la clave `logger`. |

## 9. Casos límite y notas

- **500 no controlados**: no se ha añadido un `@ExceptionHandler(Exception.class)` propio — habría cambiado el contrato de la API (el cuerpo de un 500) sin que se haya pedido, y Spring/Tomcat ya loguean por su cuenta (a ERROR, con traza) cualquier excepción no capturada que llegue al `DispatcherServlet`; al usar SLF4J igual que el resto de la app, esos logs ya caen en `system.log` por el enrutado por defecto de `root` (§3.2), sin necesidad de tocar nada. **No verificado con una prueba propia** (es comportamiento del framework, no código nuestro); pendiente de confirmar con un smoke test manual (arrancar la app, forzar un 500, mirar `system.log`).

- `LoggerContext` construido a mano (fuera del arranque normal de Spring Boot/SLF4J) no trae `MDCAdapter` propio — se descubrió al testear el enrutado end-to-end (`LogbackTwoTierRoutingTest`) y hay que asignarlo explícitamente (`ctx.setMDCAdapter(new LogbackMDCAdapter())`) para que `getMDCPropertyMap()` no lance NPE. Solo afecta a tests que instancian su propio `LoggerContext`; en la app real, Spring Boot lo arranca correctamente.
- `logback.xml` (no `logback-spring.xml`): no usa ninguna etiqueta específica de Spring (`springProperty`/`springProfile`), así que carga antes del contexto de Spring y es testable con un `JoranConfigurator` desnudo, sin arrancar la aplicación.

## 10. Backlog / mejoras futuras

- **Exporter OTLP + SigNoz**: cuando exista un OTel Collector, añadir `management.otlp.tracing.endpoint=...` (Spring Boot autoconfigura el exporter de trazas) y/o un `filelog receiver` del Collector apuntando a `system.log`/`business.log`. Sin cambios de código en los call sites, porque ya emiten el shape OTel y ya llevan `trace_id`/`span_id`.
- **Trazas en jobs programados**: el futuro Flex Web Service (F4, import automático sin petición HTTP) necesitará crear su propio span manualmente (`Tracer.nextSpan()`) para tener `trace_id` — hoy todo pasa por HTTP, así que no aplica todavía.
- Instrumentar el resto de acciones de negocio mencionadas en el diseño (alta manual de movimientos, alta de cuentas, import de extractos bancarios) más allá del caso piloto de Inversiones.

## 11. Decisiones pendientes / deuda técnica

| Tema | Situación actual | Decisión pendiente / deuda |
|---|---|---|
| Retención de `system.log`/`business.log` | 14 días / 10MB por fichero rotado, igual para ambos (valor de partida razonable, no discutido a fondo). | Revisar si el log de negocio merece retención más larga que el de sistema (es el que sirve de rastro de auditoría, no solo de depuración). |
| Ruta por defecto (`FINANCE_LOG_PATH=logs`) | Relativa al directorio de trabajo del proceso (`backend/logs/` con `mvn spring-boot:run`), independiente de `.run/` (que usa `app.sh` para PID/consola). | Decidir si `app.sh` debe exportar `FINANCE_LOG_PATH` apuntando a `.run/` para tenerlo todo junto, o si se deja como directorio propio. |

## 12. Referencias de código

- `shared/infrastructure/logging/OtelJsonLogFormatter.java` (+ `OtelJsonLogFormatterTest`): renderiza el shape JSON de §3.1 a partir de un `ILoggingEvent`.
- `shared/infrastructure/logging/OtelJsonEncoder.java`: adaptador `Encoder` de Logback sobre el formatter.
- `src/main/resources/logback.xml` (+ `LogbackTwoTierRoutingTest`): enrutado de dos niveles con ficheros rotados.
- `.gitignore`: `logs/` nunca se versiona.
- `shared/web/DomainExceptionHandler.java` / `shared/web/DataIntegrityExceptionHandler.java` (+ sus tests, con un `ListAppender` de Logback para capturar el evento): logging de sistema (WARN) de cada excepción de dominio/integridad mapeada a 4xx.
- `investments/application/FlexImportService.java` (método `logRejectedRow`, logger `business.investments`): caso piloto del log de negocio — cada fila de un import Flex que viola una invariante de dominio (p. ej. el convenio de signos §3 del PRD de Inversiones) queda en `business.log` con tipo, `external_id`, fecha, importe/divisa, ISIN/ticker si hay instrumento, descripción y motivo, sin el `accountId` real de IBKR. Probado en `FlexImportServiceTest.invalidRow_isLoggedToBusinessLogWithDiagnosticContext` (reproduce el caso real de la reversa de ASML que motivó este PRD).
