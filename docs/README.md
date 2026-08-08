# Documentación — Mis Finanzas

Documentación de producto de la aplicación. Aquí viven los **PRD** (Product Requirements Documents): un documento por dominio funcional que describe **qué hace la app y por qué**, en lenguaje de negocio y sin detalle de implementación. El **cómo** (modelo físico, arquitectura, API y plan de trabajo) vive en su documento de diseño técnico en `plan/`. Ver [Estructura de la documentación de un dominio](#estructura-de-la-documentación-de-un-dominio).

Esta carpeta es un vault de [Obsidian](https://obsidian.md): ábrela directamente como vault ("Open folder as vault" → selecciona `docs/`) para navegar por enlaces `[[wikilink]]`, grafo y tags. Cada PRD lleva frontmatter (`dominio`, `estado`, `tags`) para filtrar/buscar desde Obsidian; el contenido y las reglas de mantenimiento no cambian por ello.

## Regla de mantenimiento (obligatoria)

Los PRD son de **creación y actualización obligatoria**. Cualquier cambio de código que altere el comportamiento de un dominio (reglas de negocio, requisitos, validaciones o experiencia de usuario) debe actualizar el PRD correspondiente **en el mismo cambio**. Si un dominio aún no tiene PRD y se modifica, debe crearse.

La misma regla aplica al **documento de diseño técnico** del dominio: un cambio que altere el modelo físico, la arquitectura, la API o el plan de fases lo actualiza en el mismo cambio. Los dos documentos se mantienen a la vez, cada uno con lo suyo — un cambio puede tocar solo uno de ellos (renombrar un endpoint no cambia el PRD; añadir una regla de negocio no siempre cambia el diseño).

## PRD por dominio

Antes de explorar el código de un dominio, **lee primero su PRD**: es la fuente de verdad de las reglas de negocio, así que el código solo hace falta consultarlo para localizar la implementación exacta, no para re-derivar reglas ya documentadas. Las columnas «Backend»/«Frontend» indican dónde vive esa implementación.

| Dominio | PRD | Backend | Frontend | Estado |
|---|---|---|---|---|
| Cuentas | [[prd/cuentas\|prd/cuentas.md]] | `accounts/` | `pages/accounts/` | ✅ Implementado |
| Categorías y subcategorías | [[prd/categorias\|prd/categorias.md]] | `categories/` | `pages/categories/` | ✅ Implementado |
| Movimientos | [[prd/movimientos\|prd/movimientos.md]] | `transactions/` | `pages/transactions/` | ✅ Implementado |
| Transferencias | [[prd/transferencias\|prd/transferencias.md]] | `transfers/` | `pages/transfers/` | ✅ Implementado |
| Presupuestos | [[prd/presupuestos\|prd/presupuestos.md]] | `budgets/` | `pages/budgets/` | ✅ Implementado |
| Dashboard | [[prd/dashboard\|prd/dashboard.md]] | `reporting/` | `pages/dashboard/` | ✅ Implementado |
| Importación de extractos | [[prd/importacion\|prd/importacion.md]] | `imports/` | `components/import-dialog.ts` (usado desde `pages/transactions/`, `pages/transfers/`) | ✅ Implementado |
| Reglas de categorización | [[prd/reglas-categorizacion\|prd/reglas-categorizacion.md]] | `categorization/` | `pages/categories/` | ✅ Implementado |
| Inversiones | [[prd/inversiones\|prd/inversiones.md]] | `investments/` | `pages/investments-dashboard/`, `pages/investments-operations/`, `pages/investments-positions/`, `components/flex-import-dialog.ts`, `components/investment-toolbar.ts`, `components/investment-transaction-dialog.ts` | ✅ Implementado (F1–F3: import Flex, posiciones/valoración multidivisa, rentas, alta manual y rentabilidad TWR/XIRR con UI; F4 automatización en backlog) |
| Análisis fundamental | [[prd/analisis-fundamental\|prd/analisis-fundamental.md]] | `fundamentals/` | `pages/analysis-watchlist/`, `pages/analysis-company/` | 📐 Diseño — acordado, sin implementar. **Documento de referencia del formato vigente**; diseño técnico en [[plan/analisis-fundamental]] |
| Observabilidad (logging) | [[prd/observabilidad\|prd/observabilidad.md]] | `shared/infrastructure/logging` (transversal, usado desde cualquier contexto) | — | 🚧 En curso — transversal, no es un dominio funcional |

## Roadmap

Recordatorio consolidado de mejoras futuras por dominio (priorizado, con foco actual en Inversiones): [[roadmap]]. No sustituye al §10/§11 de cada PRD, que sigue siendo la fuente de verdad.

## Otros documentos

Diseño técnico y seguimiento. **No son fuente de verdad funcional** — eso son los PRD de arriba —, pero los documentos de `plan/` **sí lo son de las decisiones técnicas** de su dominio (modelo físico, arquitectura, API, fases):

| Documento | Qué es |
|---|---|
| [[migration-ddd-hexagonal]] | Historial de la migración a hexagonal + DDD, por etapas |
| [[testing-plan]] | Seguimiento del plan de testing backend |
| [[testing-plan-frontend]] | Seguimiento del plan de testing frontend |
| [[plan/inversiones]] | Seguimiento de implementación del módulo Inversiones (referencia a [[prd/inversiones]]) |
| [[plan/historial-imports]] | Plan de implementación — historial de imports de Inversiones |
| [[plan/posiciones-cerradas]] | Plan de implementación — posiciones cerradas / P&L realizado |
| [[plan/precios]] | Plan de implementación — API externa de cotizaciones |
| [[plan/recurrencias-presupuestos]] | Plan de implementación — recurrencias de presupuesto |
| [[plan/analisis-fundamental]] | Diseño técnico — análisis fundamental (modelo físico, API y fases; el funcional está en [[prd/analisis-fundamental]]) |

## Sistema de diseño (frontend)

La UI sigue el sistema de diseño «moderno, minimalista, técnico» documentado en `design_handoff_adaptacion_app/` (`DESIGN_SYSTEM.md` con los tokens OKLCH claro/oscuro y `ADAPTACION_ANGULAR.md` con el mapeo al código): tokens semánticos como CSS custom properties en `frontend/src/styles.scss` (`--bg`, `--surface`, `--text*`, `--accent*`, `--pos/--neg/--warn` + variantes soft, radios `--r-sm/md/lg`, `--shadow`), tipografía Space Grotesk (UI) + JetBrains Mono (toda cifra, con `tabular-nums`), tema claro/oscuro vía `data-theme` en `<html>` (`ThemeService`). Nunca usar colores literales en componentes: siempre referenciar tokens.

## Documentación de infraestructura

| Tema | Documento | Estado |
|---|---|---|
| Despliegue con Docker | [despliegue-docker.md](despliegue-docker.md) | ✅ Implementado |

## Estructura de la documentación de un dominio

Cada dominio se documenta en **dos ficheros con responsabilidades separadas**:

| | `prd/<dominio>.md` | `plan/<dominio>.md` |
|---|---|---|
| **Responde a** | Qué hace el producto y por qué | Cómo se construye |
| **Lector objetivo** | Cualquiera, sin conocer el código | Quien va a implementarlo |
| **Es fuente de verdad de** | Reglas de negocio, requisitos, validaciones, experiencia de usuario | Modelo físico, arquitectura, API, plan de fases |
| **Nunca contiene** | Tablas, tipos de columna, SQL, migraciones, rutas HTTP, nombres de paquete o de clase, frameworks de test | Justificaciones de negocio que no estén ya en el PRD |

La prueba para saber si algo va en el PRD: **si la frase deja de ser cierta al reescribir el módulo en otro lenguaje o con otra base de datos, no es funcional y no pertenece al PRD.** Un margen de seguridad se calcula igual en Java que en Python; una columna `numeric(19,4)` no.

Referencia del formato vigente: [[prd/analisis-fundamental]] y [[plan/analisis-fundamental]].

### Estructura del PRD

0. Frontmatter YAML (`dominio`, `estado: implementado | en-curso | en-diseño`, `tags: [prd, dominio/<slug>]`) para navegación y filtrado en Obsidian.
1. Cabecera de metadatos (estado, versión, última actualización, dominio) y nota de mantenimiento obligatorio.
2. **Resumen** — qué es el módulo en un párrafo.
3. **Problema y contexto** — qué duele hoy y por qué merece construirse. Sin esto, un PRD es una lista de funcionalidades sin criterio para priorizarlas.
4. **Usuarios y casos de uso** — quién lo usa y para qué, con su frecuencia.
5. **Objetivos y no-objetivos** — cada no-objetivo **con su motivo**, no solo enunciado.
6. **Glosario** — obligatorio en dominios con vocabulario especializado (inversión, fiscalidad, contabilidad). En lenguaje llano, asumiendo lector no experto.
7. **Modelo conceptual** — las entidades del dominio en lenguaje de negocio: qué representa cada una, quién la crea, cómo se relacionan. Nunca tablas ni tipos.
8. **Requisitos funcionales** — numerados `RF-n`, agrupados por bloque temático.
9. **Reglas de negocio** — numeradas `RN-n`. Aquí sí van las fórmulas si el dominio es de cálculo: una fórmula financiera es una regla de negocio, no un detalle de implementación.
10. **Experiencia de usuario** — qué ve y qué puede hacer el usuario. Sin nombres de componente ni de fichero.
11. **Validaciones y mensajes** — qué ocurre cuando algo va mal, desde el punto de vista del usuario.
12. **Casos límite y limitaciones conocidas** — incluidas las limitaciones del propio modelo, dichas con honestidad.
13. **Evolución prevista** — backlog priorizado.
14. **Decisiones tomadas y deuda conocida** — decisión, motivo y consecuencia asumida.

### Estructura del documento de diseño técnico

0. Frontmatter YAML (`dominio`, `estado`, `tags: [plan, dominio/<slug>]`).
1. **Contexto y arquitectura** — bounded context, esquema, dependencias con otros contextos y cómo se aíslan.
2. **Modelo físico** — tablas, columnas, tipos, índices y unicidades.
3. **Componentes de dominio** — value objects, agregados, servicios y puertos, con la regla de negocio que implementa cada uno.
4. **Anticorrupción / integraciones**, si las hay.
5. **API** — tabla de endpoints.
6. **Frontend** — páginas, rutas y patrones reutilizados.
7. **Plan de implementación** — fases y hitos, cada uno con su contenido y sus tests. Un hito = un commit.
8. **Deuda técnica prevista**.

Cada documento enlaza al otro al principio y al final.

### Diagramas

Se usa **Mermaid** (Obsidian lo renderiza de forma nativa) siempre que un diagrama explique mejor que un párrafo: flujo de extremo a extremo del dominio, modelo conceptual (`erDiagram`), ciclo de vida de un dato, o el encadenamiento de un cálculo con varios pasos.

Criterio: **un diagrama que se puede sustituir por una frase sobra**. No se diagraman cosas que el lector ya entiende, ni se dibuja la arquitectura hexagonal en cada dominio — es la misma en todos.

### Idioma y migración

Idioma: **español**, en coherencia con el resto del producto.

Los PRD anteriores a este formato mezclan contenido funcional y técnico en un solo documento (esquema de tablas, endpoints, referencias de código). **Se migran cuando haya que tocarlos por otro motivo**, no en una pasada masiva: hoy son la fuente de verdad de módulos ya implementados y reescribirlos en bloque tiene más riesgo que valor. Al migrar uno, lo técnico se extrae a su `plan/<dominio>.md` — varios dominios ya tienen uno donde acumularlo.
