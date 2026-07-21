# Documentación — Mis Finanzas

Documentación de producto de la aplicación. Aquí viven los **PRD** (Product Requirements Documents): un documento por dominio funcional que describe qué hace la app, sus reglas de negocio, su API y su UI.

## Regla de mantenimiento (obligatoria)

Los PRD son de **creación y actualización obligatoria**. Cualquier cambio de código que altere el comportamiento de un dominio (modelo de datos, reglas de negocio, endpoints de API o UI) debe actualizar el PRD correspondiente **en el mismo cambio**. Si un dominio aún no tiene PRD y se modifica, debe crearse.

## PRD por dominio

| Dominio | Documento | Estado |
|---|---|---|
| Cuentas | [prd/cuentas.md](prd/cuentas.md) | ✅ Implementado |
| Categorías y subcategorías | [prd/categorias.md](prd/categorias.md) | ✅ Implementado |
| Movimientos | [prd/movimientos.md](prd/movimientos.md) | ✅ Implementado |
| Transferencias | [prd/transferencias.md](prd/transferencias.md) | ✅ Implementado |
| Presupuestos | [prd/presupuestos.md](prd/presupuestos.md) | ✅ Implementado |
| Dashboard | [prd/dashboard.md](prd/dashboard.md) | ✅ Implementado |
| Importación de extractos | [prd/importacion.md](prd/importacion.md) | ✅ Implementado |
| Reglas de categorización | [prd/reglas-categorizacion.md](prd/reglas-categorizacion.md) | ✅ Implementado |
| Inversiones | [prd/inversiones.md](prd/inversiones.md) | ✅ Implementado (F1–F3: import Flex, posiciones/valoración multidivisa, rentas, alta manual y rentabilidad TWR/XIRR con UI; F4 automatización en backlog) |
| Observabilidad (logging) | [prd/observabilidad.md](prd/observabilidad.md) | 🚧 En curso — transversal, no es un dominio funcional |

## Sistema de diseño (frontend)

La UI sigue el sistema de diseño «moderno, minimalista, técnico» documentado en `design_handoff_adaptacion_app/` (`DESIGN_SYSTEM.md` con los tokens OKLCH claro/oscuro y `ADAPTACION_ANGULAR.md` con el mapeo al código): tokens semánticos como CSS custom properties en `frontend/src/styles.scss` (`--bg`, `--surface`, `--text*`, `--accent*`, `--pos/--neg/--warn` + variantes soft, radios `--r-sm/md/lg`, `--shadow`), tipografía Space Grotesk (UI) + JetBrains Mono (toda cifra, con `tabular-nums`), tema claro/oscuro vía `data-theme` en `<html>` (`ThemeService`). Nunca usar colores literales en componentes: siempre referenciar tokens.

## Estructura de un PRD

Cada PRD sigue la misma plantilla (ver `prd/cuentas.md` como referencia):

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
