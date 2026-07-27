# Roadmap — mejoras pendientes

> Recordatorio consolidado de mejoras futuras, para retomarlas sin tener que rehacer el análisis. **No es la fuente de verdad**: cada dominio mantiene su propio backlog en el PRD correspondiente (§10 "Backlog / mejoras futuras" y §11 "Decisiones pendientes / deuda técnica", ver `docs/README.md`); cuando se implemente o descarte un ítem, actualiza el PRD del dominio, no (solo) este documento. Este fichero se revisa/reordena de vez en cuando, pero puede quedarse desactualizado respecto al PRD sin que sea grave.
>
> Última revisión: 2026-07-24.

## Inversiones (foco actual)

Documento detallado con dependencias y esfuerzo estimado: **`docs/investment/mejoras-modulo-inversiones.md`**. Resumen priorizado:

**P1 — sin dependencias, mayor impacto inmediato sobre lo ya construido**
1. **API externa de cotizaciones** (adaptador real de `PriceProviderPort`, p. ej. Yahoo Finance, + botón de refresco). Desbloquea valoración diaria (hoy es "a fecha del último import"), TWR/XIRR reales en vez de aproximados y benchmarks (P3.1). Ojo con el caso ZEG: la LSE cotiza en peniques, el Flex lo da en libras.
2. **Historial de imports** (persistir fecha/fichero/resumen ok-duplicadas-errores-warnings + vista de consulta). Barato y necesario antes de automatizar la descarga (P2.1): un import desatendido sin log es una caja negra.
3. **Posiciones cerradas / P&L realizado por año**. Los datos ya existen en `PositionCalculator`; es solo capa de lectura + UI. Antesala del informe fiscal (P4.2).

**P2 — automatización y robustez**
4. Flex Web Service (descarga automática con token IBKR) — depende de 2 (historial de imports).
5. Más acciones corporativas (fusiones, spin-offs, cambio de ticker, dividendo en acciones) — hoy cualquier cosa que no sea split se rechaza como fila inválida; riesgo latente, no funcionalidad puntual.
6. Análisis de costes (comisiones + FTT agregadas por año/instrumento) — dato ya disponible fila a fila.

**P3 — dependen de la API de precios (punto 1)**
7. Benchmarks (TWR vs índice de referencia).
8. Clasificación de activos por dimensiones (región/sector, estilo Portfolio Performance).

**P4 — fiscalidad**
9. Coste FIFO en paralelo al promedio actual (criterio fiscal español; conviven ambos métodos, no se sustituyen).
10. Informe fiscal de plusvalías — depende de 9 y se apoya en 3.

**P5 — solo si ocurre el evento que lo motiva (no planificar aún)**: lista de exclusión de apuntes importados borrados, archivar carteras, export CSV de posiciones/operaciones, parsers de otro broker, DRIP.

**Deuda técnica abierta** (`docs/prd/inversiones.md` §11): niveles de detalle del Flex para la FTT (provisional del formato actual, depende de reconfigurar el Flex Query cuando la app tenga su primera versión "real"); coste FIFO (ver P4.1 arriba).

**Recomendación**: empezar por P1.1 (API de cotizaciones) — es la que más desbloquea a la vez sin depender de nada previo.

## Resto de dominios

Listado condensado; el detalle vive en el §10 de cada PRD.

- **Movimientos** (`docs/prd/movimientos.md`): adjuntar etiquetas/notas a un movimiento, movimientos recurrentes/plantillas, edición en bloque (recategorizar varios a la vez).
- **Dashboard** (`docs/prd/dashboard.md`): rango de fechas libre (no solo mes/año), exportar resumen o gráficos, comparativa interanual, filtro por categoría.
- **Transferencias** (`docs/prd/transferencias.md`): retirar el componente legado `pages/transfers/`, comisión o fechas distintas para cargo/abono, transferencias entre divisas con tipo de cambio, marcar recurrentes.
- **Presupuestos** (`docs/prd/presupuestos.md`): presupuestos anuales/trimestrales, plantilla/duplicado de un año a otro, alertas al superar presupuesto, presupuesto agregado repartido entre cuentas.
- **Categorías** (`docs/prd/categorias.md`): más de un nivel de jerarquía (si surge necesidad real), reordenar/colorear subcategorías heredando del padre, mover en bloque subcategorías de cuenta, cascada explícita al cambiar el ámbito de una principal.
- **Reglas de categorización** (`docs/prd/reglas-categorizacion.md`): prioridad/orden explícito de reglas, reaplicar una regla a todos los movimientos bajo confirmación, patrones con comodines/regex, reglas por cuenta.
- **Cuentas** (`docs/prd/cuentas.md`): unicidad opcional del nombre, archivar en vez de eliminar, tipos de cuenta como catálogo configurable, divisa por cuenta.
- **Importación de extractos** (`docs/prd/importacion.md`): vista previa de filas antes de confirmar, mapeo manual de columnas, recordar última cuenta usada por fichero/banco, más formatos de fecha/importe.
