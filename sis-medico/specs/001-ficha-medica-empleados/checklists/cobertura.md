# Cobertura de criterios: Ficha médica de empleados

**Purpose**: Verificar que cada criterio de aceptación de las cuatro historias y
cada criterio de éxito tenga al menos un test que lo cubra, contra la tabla de
mapeo de [plan.md](../plan.md). Es el resultado de T078.

**Fecha de la verificación**: 2026-09-10
**Alcance**: 38 criterios de aceptación (US1-1 a US4-5) y 10 criterios de éxito
(SC-001 a SC-010).

**Cómo se hizo**: cada test nombra en un comentario los criterios que cubre. La
tabla se armó recorriendo los archivos de test y juntando esas marcas, no de
memoria ni de la intención del plan.

**Marcador**: `[x]` significa que existe al menos un test automatizado que ejerce
el criterio. `[ ]` significa que no lo hay, y la fila dice por qué.

## Resultado

| | Cantidad |
|---|---|
| Criterios con test automatizado | **46 de 48** |
| Sin test automatizado, por diseño | **2** — SC-001 y SC-005 |
| Sin test automatizado, por omisión | **0** |

Los dos que faltan son de medición con usuarios reales y no se automatizan por
naturaleza, no por olvido. Están como T076 y T077 y **siguen pendientes**: son la
única parte de la feature que no se puede completar sin operarios frente a la
pantalla.

## US1 — Registrar una ficha médica nueva

- [x] US1-1 Buscar un legajo y ver apellido y nombre — `ConsultaDeEmpleadoTest`
- [x] US1-2 Legajo inexistente informado explícitamente — `ConsultaDeEmpleadoTest`
- [x] US1-3 Alta válida persiste con auditoría — `AltaFichaTest`
- [x] US1-4 Fecha de evento futura rechazada — `ValidadorFichaMedicaTest`
- [x] US1-5 Sin ninguna de las dos fechas de fin — `ValidadorFichaMedicaTest`
- [x] US1-6 Con las dos fechas de fin — `ValidadorFichaMedicaTest`
- [x] US1-7 Grupo y detalle obligatorios — `ValidadorFichaMedicaTest`
- [x] US1-8 Detalle fuera del grupo — `ValidadorFichaMedicaTest`, `CatalogoRepositorioTest`
- [x] US1-9 In itinere obligatorio con estado accidentado — `ValidadorFichaMedicaTest`
- [x] US1-10 Derivación de "estaba en servicio" — `DependenciasDeCamposTest`
- [x] US1-11 Limpieza por estado del paciente — `DependenciasDeCamposTest`
- [x] US1-12 Evento antiguo: guarda y advierte — `AdvertenciaAntiguedadTest`, `ValidadorFichaMedicaTest`
- [x] US1-13 Duplicado por (legajo, fecha de evento) — `AltaFichaTest`, `FichaMedicaRepositorioTest`
- [x] US1-14 Solapamiento rechazado, con la ficha en conflicto — `AltaFichaTest`
- [x] US1-15 Extremos compartidos no solapan — `AltaFichaTest`, `DetectorSolapamientoTest`
- [x] US1-16 Histórica de período abierto bloquea con mensaje accionable — `AltaFichaTest`, `DetectorSolapamientoTest`

## US2 — Consultar y navegar las fichas

- [x] US2-1 Listado del empleado, descendente — `ListadoFichasTest`
- [x] US2-2 Histórica sin grupo ni detalle se abre entera y se señala — `LecturaToleranteTest`
- [x] US2-3 Navegar no exige completar nada — `LecturaToleranteTest`
- [x] US2-4 Empleado sin fichas, dicho explícitamente — `ListadoFichasTest`
- [x] US2-5 Días perdidos calculados y no editables — `DiasPerdidosTest`
- [x] US2-6 Sin fecha de alta, días perdidos vacíos — `DiasPerdidosTest`

## US3 — Editar una ficha para registrar el alta

- [x] US3-1 Citación reemplazada por alta, con recálculo y auditoría — `EdicionTest`, `BordesDeFechaTest`
- [x] US3-2 Excluyencia de fechas también al editar — `BordesDeFechaTest`
- [x] US3-3 Alta igual al evento: cero días — `BordesDeFechaTest`
- [x] US3-4 Alta anterior al evento rechazada — `BordesDeFechaTest`
- [x] US3-5 Más de 999 días rechazado — `BordesDeFechaTest`
- [x] US3-6 Alta nueva que genera solapamiento — `EdicionTest`
- [x] US3-7 Cambio de estado limpia campos en lo guardado — `EdicionTest`
- [x] US3-8 Histórica incompleta exige completarse al guardar — `EdicionTest`
- [x] US3-9 Segundo guardado concurrente rechazado sin pisar — `ConcurrenciaTest`
- [x] US3-10 Reasignación de legajo mueve la ficha — `ReasignacionLegajoTest`
- [x] US3-11 Reasignación que choca contra el destino — `ReasignacionLegajoTest`

## US4 — Eliminar una ficha cargada por error

- [x] US4-1 Se pide confirmación antes de eliminar — `ConfirmacionEnLinea.test.tsx`
- [x] US4-2 Baja con asiento de auditoría — `EliminacionTest`
- [x] US4-3 La eliminada no bloquea por solapamiento — `EliminacionTest`
- [x] US4-4 La eliminada no aparece por ningún medio — `EliminacionTest`
- [x] US4-5 Los datos se conservan y son recuperables — `EliminacionTest`

**Nota sobre US4-1.** Es el único criterio que el backend no puede verificar: la
API elimina cuando se lo piden y no tiene manera de saber si alguien preguntó
antes. Se cubre con un test de componente, que además fija **cómo** se pregunta
—operable por teclado, sin atrapar el foco— porque FR-038 y la prohibición de
modales están en tensión y ese es el lugar donde alguien la resolvería con un
diálogo.

## Criterios de éxito

- [ ] SC-001 Carga completa en 60 segundos o menos — **medición con usuarios (T076), pendiente**
- [x] SC-002 Recorrido completo del dataset histórico sin error — `LecturaToleranteTest`
- [x] SC-003 Cero fichas guardadas violan R1 a R8 — `ReglasBloqueantesTest`
- [x] SC-004 Toda escritura deja asiento — `AltaFichaTest`, `AuditoriaTest`, `EdicionTest`, `EliminacionTest`
- [ ] SC-005 90 % de rechazos corregidos sin ayuda — **medición con usuarios (T077), pendiente**
- [x] SC-006 Días perdidos = diferencia, incluidos negativos y mayores a 999 — `DiasPerdidosTest`, `CalculadorDiasPerdidosTest`
- [x] SC-007 Sin datos clínicos en logs — `FugaDeLogsTest`
- [x] SC-008 Dos operarios, cero pérdidas silenciosas — `ConcurrenciaTest`
- [x] SC-009 Contenido del mensaje de solapamiento — `AltaFichaTest`, `DetectorSolapamientoTest`
- [x] SC-010 Recuperabilidad de las eliminadas — `EliminacionTest`

## Lo que esta tabla no dice

Que un criterio tenga test no significa que el sistema esté probado en
producción. En particular:

- **SC-002 y SC-003 corren sobre 686 fichas, no sobre 80.000.** El juego de datos
  disponible es un extracto de dos meses de 2026 más 16 fichas fabricadas para
  cubrir los casos que el extracto no trae. Cuando llegue el volcado del archivo
  completo hay que volver a correrlos: es ahí donde están las fichas anteriores a
  2016 que motivan toda la tolerancia de lectura.
- **SC-004 se verifica sobre las escrituras de los tests, no sobre "el 100 % de
  las escrituras desde la puesta en marcha".** La formulación del criterio es una
  propiedad operativa; lo que se automatiza es que ningún camino de escritura del
  código actual pueda saltearse la auditoría.
- **El frontend tiene un solo archivo de test, y es a propósito.** Casi toda regla
  de esta feature vive en el backend (FR-018) y se prueba donde se aplica.
  Reescribirlas en TypeScript daría la impresión de doble cobertura cuando en
  realidad serían dos copias que pueden divergir.
