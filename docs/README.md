# Documentación — Mis Finanzas

Documentación de producto de la aplicación. Aquí viven los **PRD** (Product Requirements Documents): un documento por dominio funcional que describe qué hace la app, sus reglas de negocio, su API y su UI.

Esta carpeta es un vault de [Obsidian](https://obsidian.md): ábrela directamente como vault ("Open folder as vault" → selecciona `docs/`) para navegar por enlaces `[[wikilink]]`, grafo y tags. Cada PRD lleva frontmatter (`dominio`, `estado`, `tags`) para filtrar/buscar desde Obsidian; el contenido y las reglas de mantenimiento no cambian por ello.

## Regla de mantenimiento (obligatoria)

Los PRD son de **creación y actualización obligatoria**. Cualquier cambio de código que altere el comportamiento de un dominio (modelo de datos, reglas de negocio, endpoints de API o UI) debe actualizar el PRD correspondiente **en el mismo cambio**. Si un dominio aún no tiene PRD y se modifica, debe crearse.

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
| Análisis fundamental | [[prd/analisis-fundamental\|prd/analisis-fundamental.md]] | `fundamentals/` | `pages/analysis-watchlist/`, `pages/analysis-company/` | 📐 Diseño — PRD acordado, sin implementar |
| Observabilidad (logging) | [[prd/observabilidad\|prd/observabilidad.md]] | `shared/infrastructure/logging` (transversal, usado desde cualquier contexto) | — | 🚧 En curso — transversal, no es un dominio funcional |

## Roadmap

Recordatorio consolidado de mejoras futuras por dominio (priorizado, con foco actual en Inversiones): [[roadmap]]. No sustituye al §10/§11 de cada PRD, que sigue siendo la fuente de verdad.

## Otros documentos

Documentos de seguimiento (no son fuente de verdad funcional — eso son los PRD de arriba):

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

## Sistema de diseño (frontend)

La UI sigue el sistema de diseño «moderno, minimalista, técnico» documentado en `design_handoff_adaptacion_app/` (`DESIGN_SYSTEM.md` con los tokens OKLCH claro/oscuro y `ADAPTACION_ANGULAR.md` con el mapeo al código): tokens semánticos como CSS custom properties en `frontend/src/styles.scss` (`--bg`, `--surface`, `--text*`, `--accent*`, `--pos/--neg/--warn` + variantes soft, radios `--r-sm/md/lg`, `--shadow`), tipografía Space Grotesk (UI) + JetBrains Mono (toda cifra, con `tabular-nums`), tema claro/oscuro vía `data-theme` en `<html>` (`ThemeService`). Nunca usar colores literales en componentes: siempre referenciar tokens.

## Documentación de infraestructura

| Tema | Documento | Estado |
|---|---|---|
| Despliegue con Docker | [despliegue-docker.md](despliegue-docker.md) | ✅ Implementado |

## Estructura de un PRD

Cada PRD sigue la misma plantilla (ver `prd/cuentas.md` como referencia):

0. Frontmatter YAML (`dominio`, `estado: implementado|en-curso`, `tags: [prd, dominio/<slug>]`) para navegación/filtrado en Obsidian.
1. Cabecera de metadatos (estado, versión, última actualización, dominio).
2. Propósito.
3. Objetivos y no-objetivos.
4. Modelo de datos.
5. Requisitos funcionales.
6. Reglas de negocio.
7. API.
8. UI/UX.
9. Validaciones y errores.
10. Casos límite y notas.
11. Backlog / mejoras futuras.
12. Decisiones pendientes / deuda técnica.
13. Referencias de código.

Idioma: español, en coherencia con el resto del producto.
