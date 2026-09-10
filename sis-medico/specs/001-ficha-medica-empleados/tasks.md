---

description: "Task list for Ficha médica de empleados"
---

# Tasks: Ficha médica de empleados

**Input**: Design documents from `/specs/001-ficha-medica-empleados/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/api.md](contracts/api.md)

**Tests**: Sí. La estrategia de pruebas del plan mapea cada criterio de aceptación a un tipo de test, y cuatro de esos tests son guardarraíles que van **antes** del código que vigilan.

**Organization**: Agrupadas por historia de usuario. Cada historia queda entregable por sí sola.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede correr en paralelo. No comparte archivos con otra tarea de la misma tanda.
- **[Story]**: US1 a US4. Los bloques previos no llevan etiqueta de historia.
- **BLOQUEADA**: espera una definición pendiente. Lista completa al final.

## Path Conventions

**Nota del 2026-09-10**: el backend quedó organizado **por capa técnica**
(`models/`, `service/`, `repositories/`, `controllers/`, `dtos/`, `exceptions/`,
`comun/`), que es lo que manda `CLAUDE.md`, y no por área de dominio como
proponía [plan.md](plan.md). Las rutas que las tareas de abajo nombran como
`fichas/…` o `empleados/…` hay que leerlas contra esa estructura: por ejemplo
`fichas/FichaMedicaRepositorio.java` vive en `repositories/`. La divergencia es
de ubicación de archivos, no de diseño: no hay paquete `legacy/`, ni interfaces
de una sola implementación, ni capas nuevas.

- Backend: `backend/sistema-medico/src/main/java/com/ferrovias/sismedico/`
- Migraciones: `backend/sistema-medico/src/main/resources/db/migration/`
- Tests: `backend/sistema-medico/src/test/java/com/ferrovias/sismedico/`
- Frontend: `frontend/`

---

## Phase 0: Decisiones pendientes y verificación de supuestos

**Purpose**: Nada de esto es código, y todo condiciona código. Van primero porque
resolverlas después obliga a rehacer esquema y repositorios.

- [x] T001 ~~Obtener del cliente la definición sobre el padrón de empleados.~~ **RESUELTA el 2026-09-09: el padrón se consulta en la base externa, no se importa.** Confirma FR-003b y FR-003c tal como estaban. D3 queda firme: no hay clave foránea, y no puede haberla. **T033 y T039 quedan desbloqueadas; la tabla `empleado` no existe y no se crea.**
- [ ] T002 [P] Verificar contra la base real si el padrón vive en la **misma instancia** de SQL Server y es alcanzable por nombre de tres partes. Registrar en `specs/001-ficha-medica-empleados/research.md`. Si está en otra instancia hace falta un `DataSource` aparte.
- [ ] T003 [P] Verificar el tipo real del legajo en el padrón: entero, o texto con ceros a la izquierda significativos. Registrar en `specs/001-ficha-medica-empleados/research.md`. **FR-004f asume entero; la autoridad es la base externa.** Condiciona la columna `legajo` de T015.
- [ ] T004 [P] Confirmar con el cliente el comportamiento ante padrón no disponible (hueco CHK040): el plan asume `503` para alta y edición, con consulta degradada sin apellido ni nombre. Registrar en `spec.md`, sección Assumptions.

**Checkpoint**: T001 resuelto o las tareas bloqueadas quedan fuera del sprint.

---

## Phase 1: Preparación del proyecto

**Purpose**: El proyecto actual es el andamio crudo de Spring Initializr y diverge
del stack de la constitución. Corregirlo con código ya escrito cuesta el triple.

### Andamio y dependencias

- [x] T005 Renombrar el paquete `com.sis_medico.demo` a `com.ferrovias.sismedico` y `DemoApplication` a `SistemaMedicoApplication` en `backend/sistema-medico/src/main/java/` y `src/test/java/`. **Va antes que cualquier clase nueva.**
- [x] T006 Cambiar `rootProject.name` de `demo` a `sistema-medico` en `backend/sistema-medico/settings.gradle`.
- [x] T007 Reemplazar `spring-boot-starter-data-jpa` por `spring-boot-starter-jdbc` en `backend/sistema-medico/build.gradle`. La constitución fija `JdbcTemplate` como acceso principal; JPA solo con justificación demostrable, que acá no existe.
- [x] T008 Agregar el driver `com.microsoft.sqlserver:mssql-jdbc` en `backend/sistema-medico/build.gradle`.
- [x] T009 Agregar `flyway-core` y `flyway-sqlserver` en `backend/sistema-medico/build.gradle`.
- [x] T010 Agregar `springdoc-openapi` en `backend/sistema-medico/build.gradle`. Es la fuente de la que T046 deriva los esquemas Zod.
- [x] T011 Agregar el soporte LDAP de Spring Security en `backend/sistema-medico/build.gradle` (FR-034).
- [x] T012 Agregar `testcontainers` con el módulo `mssqlserver` en `backend/sistema-medico/build.gradle`.
- [x] T013 Agregar `archunit-junit5` en `backend/sistema-medico/build.gradle`.

### Reloj — va antes que cualquier regla que toque fechas

- [x] T014 Crear `comun/RelojConfig.java` con un bean `java.time.Clock` construido desde la propiedad `app.zona-horaria`, y fijar `app.zona-horaria: America/Argentina/Buenos_Aires` en `application.yml`. **D7, FR-018b. Ninguna regla de fecha se escribe antes de esto**: con el servidor en UTC, una carga a las 21:00 hora argentina sería "mañana" y FR-007 rechazaría cargas legítimas.

### Esquema — va antes que cualquier repositorio

- [x] T015 Crear `db/migration/V1__esquema_inicial.sql` con `ficha_medica`, el índice único filtrado `WHERE eliminada_en IS NULL` (D1), el índice de listado, y `ficha_medica_auditoria` (D5). Copiar el DDL de [data-model.md](data-model.md) sin desviarse. **Restricciones que hay que respetar al pie: `grupo_enfermedad_id` y `detalle_enfermedad_id` son `NULL` y sin FK contra el catálogo; ningún `NOT NULL` ni `CHECK` que reproduzca una regla de negocio; `observaciones` es `NVARCHAR(MAX)` (D6); `dias_perdidos` es columna calculada no persistida (D4).** El tipo de `legajo` depende de T003.
- [x] T016 [P] Crear `db/migration/V2__catalogos_enfermedad.sql` con las tablas `grupo_enfermedad` y `detalle_enfermedad` y sus datos semilla versionados. La FK entre detalle y grupo **sí** va: es interna al catálogo. La que no va es desde la ficha hacia el catálogo (FR-028c).

### Infraestructura transversal

- [x] T017 Crear la infraestructura de Testcontainers en `src/test/java/com/ferrovias/sismedico/integracion/BaseIntegracion.java` y `build.gradle`: contenedor SQL Server **estático único** compartido por toda la suite, reuso entre corridas (`testcontainers.reuse.enable`), tarea Gradle `integrationTest` separada de `test`, esquema aplicado por Flyway sobre el contenedor, y el padrón externo como **segunda base en el mismo contenedor**. **Va antes que el primer test de integración**; la imagen pesa más de 1 GB y sin esto el ciclo de trabajo se vuelve inusable.
- [x] T018 [P] Crear `comun/SeguridadConfig.java` con autenticación LDAPS contra Active Directory y rechazo de toda petición no autenticada (FR-034, FR-036e). Un único perfil de operario, con acceso a cualquier legajo (FR-036d): sin segmentación por sección ni por área.
- [x] T019 [P] Crear `comun/ManejadorGlobalDeErrores.java` que traduzca `FichaInvalidaException` a **422 `application/problem+json`** con el arreglo `violaciones` completo, el conflicto optimista a **409** y el padrón caído a **503**, según [contracts/api.md](contracts/api.md).
- [x] T020 Crear el script de importación del dataset histórico anonimizado en `backend/sistema-medico/src/test/resources/datos/` más su cargador. **Se carga sin filtrar** (FR-036b): tiene que contener fichas sin grupo ni detalle, fichas sin ninguna de las dos fechas de fin, alguna con más de 999 días entre evento y alta, alguna con alta anterior al evento, y alguna con código de enfermedad huérfano. Anonimizado según FR-036c: legajos y nombres ficticios, fechas y códigos tal cual. **Va antes que los tests de tolerancia de lectura de US2**; sin estos casos esos tests no prueban nada.

**Checkpoint**: compila, arranca, tiene esquema y sabe qué hora es.

---

## Phase 2: Guardarraíles

**Purpose**: Impedir que se introduzca el problema, no documentarlo después.
Escritos acá, fallan hasta que exista el código que vigilan — eso es lo esperado y
es lo que los vuelve útiles.

- [x] T021 [P] Crear `arquitectura/RelojArchUnitTest.java` que prohíba `LocalDate.now()`, `LocalDateTime.now()` e `Instant.now()` sin argumento en todo el código de producción, salvo en `comun/RelojConfig.java`. **D7.** Pasa desde el primer día y falla apenas alguien escriba una regla con el reloj del sistema.
- [x] T022 [P] Crear `arquitectura/PersistenciaArchUnitTest.java` que prohíba anotaciones de Bean Validation (`@NotNull`, `@NotBlank`, `@Size` y similares) sobre el modelo de dominio y de persistencia en `fichas/`. **La obligatoriedad de FR-004b vive en el validador del servicio, nunca en el modelo**, porque las fichas históricas tienen que poder leerse violando esas reglas (FR-028, FR-030).
- [x] T023 [P] Crear `comun/ComprobacionNivelDeLogs.java` que **falle el arranque** de la aplicación si el logger de `org.springframework.jdbc.core.JdbcTemplate` está en `DEBUG` o `TRACE` fuera del perfil de test. **M3.** El log de parámetros de SQL es la vía de fuga de datos clínicos más fácil de pasar por alto.
- [x] T024 [P] Crear `arquitectura/FugaDeLogsTest.java`: ejecuta alta, modificación y baja de una ficha con observaciones y descripciones centinela únicas, captura todo el log con un appender en memoria a nivel `TRACE`, y falla si algún centinela aparece. **M3, verifica SC-007.** Queda en rojo hasta que US1, US3 y US4 existan.
- [x] T025 [P] Crear `unitarios/ValidacionAcumulativaTest.java`: carga una ficha con **siete violaciones simultáneas** —evento futuro, sin ninguna de las dos fechas de fin, sin grupo, sin detalle, in itinere sin definir con estado accidentado, observaciones de más de 500 caracteres, y un campo de sí/no sin responder— y exige que la respuesta traiga **las siete**, cada una con su campo. **M1.** Es lo que impide que alguien introduzca un `return` temprano en el validador.

**Checkpoint**: T021, T022 y T023 en verde. T024 y T025 en rojo, a propósito.

---

## Phase 3: User Story 1 — Registrar una ficha médica nueva (Priority: P1) 🎯 MVP

**Goal**: El operario busca un legajo, confirma la identidad, carga una ficha entera con el teclado y la guarda. Si algo está mal, recibe todos los problemas juntos.

**Independent Test**: Con US2 a US4 sin existir, cargar una ficha para un legajo real y verificar que persiste con su auditoría, y que cada regla bloqueante rechaza con un mensaje que identifica el campo.

### Dominio y reglas

- [x] T026 [P] [US1] Crear `fichas/FichaMedica.java` como record de dominio con los campos de FR-004. **Sin anotaciones de validación de ningún tipo** (T022 lo vigila). `toString()` limitado a `id`, `legajo` y fechas (M3).
- [x] T027 [P] [US1] Crear `fichas/Observaciones.java` como tipo con `toString()` redactado del estilo `Observaciones[longitud=123]`, sin contenido (M3).
- [x] T028 [P] [US1] Crear `comun/Violacion.java` y `comun/Advertencia.java` como records `(campo, codigo, mensaje)`, con los códigos de [contracts/api.md](contracts/api.md).
- [x] T029 [US1] Crear `fichas/CalculadorDiasPerdidos.java` (FR-020, FR-020b, FR-021). Deriva de las dos fechas, **sin topear al leer**: negativos y valores mayores a 999 se devuelven tal cual (FR-020c). Depende de T014.
- [x] T030 [US1] Crear `fichas/ValidadorFichaMedica.java` con las reglas FR-007 a FR-016 y la obligatoriedad de FR-004b y FR-004c. **Acumula en una `List<Violacion>` y no corta en la primera: ningún `return` temprano.** Depende de T014 para todo lo que toque fechas. Cubre US1-4 a US1-9.
- [x] T031 [US1] Crear `fichas/DetectorSolapamiento.java` con la consulta de [data-model.md](data-model.md) (FR-014, FR-014b, FR-014c, FR-015, FR-014d). Extremo derecho **excluido**; el `hoy` para fichas históricas abiertas viene del `Clock`, no de `GETDATE()`. Depende de T014, T015.
- [x] T032 [P] [US1] Crear `enfermedades/CatalogoRepositorio.java` con `JdbcTemplate` para validar que el detalle pertenezca al grupo (FR-010). **Clase concreta, sin interfaz.** Depende de T016.
- [x] T033 [US1] Crear `empleados/PadronRepositorio.java` de solo lectura contra la base externa del padrón (FR-001, FR-002, FR-003b). **Clase concreta, sin interfaz.** **MUST NOT copiar, importar ni cachear el padrón**: se consulta donde está, en cada búsqueda. La ficha guarda solo el legajo; apellido, nombre, sección y categoría laboral se resuelven al consultar (FR-003c). Depende de T002 para saber si es la misma instancia.

### Persistencia y servicio

- [x] T034 [US1] Crear `fichas/FichaMedicaRepositorio.java` con `JdbcTemplate`: inserción y lectura por id. **Clase concreta, sin interfaz.** El `version` nace en 0 (D2). Depende de T015.
- [x] T035 [P] [US1] Crear `auditoria/AuditoriaRepositorio.java` y `auditoria/AuditoriaServicio.java` que inserten el asiento `ALTA` (D5, FR-033). Guarda `ficha_id`, `operacion`, `usuario` y `momento`; **no guarda el contenido de la ficha**. Depende de T015.
- [x] T036 [US1] Crear `fichas/FichaMedicaServicio.java` con el método de alta: `@Transactional` que valida, persiste y audita en la misma transacción, y devuelve las advertencias de FR-019. **Toda la validación vive acá, nunca en el controlador.** Depende de T030, T031, T032, T034, T035.

### API

- [x] T037 [P] [US1] Crear los DTO de entrada y salida en `backend/sistema-medico/src/main/java/com/ferrovias/sismedico/fichas/web/dto/`. **Sin campo `diasPerdidos`: no existe dónde recibirlo** (M4). Sin `version` en el alta.
- [x] T038 [US1] Crear `fichas/web/FichaMedicaController.java` con `POST /api/fichas`, que devuelve **201** con la envoltura `{ datos, advertencias }`, siempre presente aunque vacía (M2). **Guardado en un solo paso: una advertencia nunca cambia el código de estado y no hay confirmación en dos pasos.** Depende de T036, T019, T037.
- [x] T039 [P] [US1] Agregar `GET /api/empleados/{legajo}` en `empleados/web/EmpleadoController.java` (FR-001, US1-1, US1-2). Devuelve **404** si el legajo no existe en el padrón y **503** si el padrón no responde. Depende de T033.
- [x] T040 [P] [US1] Agregar `GET /api/enfermedades/grupos` y `GET /api/enfermedades/grupos/{id}/detalles` en `enfermedades/web/CatalogoController.java`, para la selección por código tipeado. Depende de T032.

### Tests de US1

- [x] T041 [P] [US1] Crear `unitarios/ValidadorFichaMedicaTest.java` con las reglas sin base: evento futuro, excluyencia de fechas, grupo y detalle faltantes, detalle fuera de grupo, más de 999 días, alta y citación anteriores al evento, in itinere obligatorio. Cubre US1-4 a US1-9. Verifica que T025 pase a verde.
- [x] T042 [P] [US1] Crear `unitarios/DependenciasDeCamposTest.java` para FR-023, FR-024 y FR-025. Cubre US1-10 y US1-11.
- [x] T043 [US1] Crear `integracion/AltaFichaTest.java`: alta válida con auditoría (US1-3), duplicado rechazado por el índice único filtrado (US1-13), solapamiento (US1-14), extremos compartidos que **no** solapan (US1-15), y ficha histórica de período abierto que bloquea con mensaje accionable (US1-16). Depende de T017, T020, T038.
- [x] T044 [P] [US1] Crear `integracion/AdvertenciaAntiguedadTest.java`: una ficha de más de 45 días **se guarda** con 201 y trae `EVENTO_ANTIGUO` en el cuerpo (US1-12, FR-019). Verifica que la advertencia no sea un error.

### Frontend de US1

- [x] T045 [P] [US1] Crear `frontend/lib/esquemas.ts` **derivando los esquemas Zod del contrato OpenAPI** que expone T010. No se escriben a mano duplicando las reglas del backend: el backend es la autoridad (FR-018).
- [x] T046 [US1] Crear `frontend/components/fichas/BuscadorLegajo.tsx`. Al resolver el legajo, el foco salta **solo** a *fecha del evento* (FR-040).
- [x] T047 [US1] Crear `frontend/components/fichas/FormularioFicha.tsx` con `tabIndex` explícito siguiendo el orden visual de la pantalla vieja, no el orden del DOM (FR-040).
- [x] T048 [P] [US1] Crear `frontend/components/fichas/SelectorCatalogo.tsx` que acepte el **código tipeado** y resuelva sin abrir lista. La lista es opcional, para quien no recuerda el código.
- [x] T049 [US1] Agregar guardado por atajo de teclado y encadenado en `frontend/app/fichas/page.tsx`: al guardar, formulario limpio y foco en el buscador de legajo (FR-041). **Sin diálogos modales.**
- [x] T050 [US1] Mostrar cada violación junto a su control y las advertencias en un aviso no bloqueante en `frontend/components/fichas/FormularioFicha.tsx`. Consume `violaciones[].campo` de la respuesta 422.

**Checkpoint**: el operario carga una ficha de punta a punta sin tocar el mouse. T024 y T025 pasan a verde en la parte del alta.

---

## Phase 4: User Story 2 — Consultar y navegar las fichas (Priority: P2)

**Goal**: El operario ve las fichas cargadas de un empleado y las recorre. Las históricas incompletas se ven enteras y no rompen nada.

**Independent Test**: Con el dataset de T020 cargado, listar y abrir todas las fichas de un legajo, incluidas las inconsistentes, sin un solo error.

- [x] T051 [US2] Agregar al `fichas/FichaMedicaRepositorio.java` el listado por legajo ordenado por `fecha_evento` **descendente, sin paginado ni filtros** (FR-031b, FR-031c), excluyendo eliminadas.
- [x] T052 [US2] Crear `fichas/EvaluadorInconsistencia.java` que calcule `incompleta` y `motivosInconsistencia` **al leer, sin aplicar ninguna validación** (FR-029, FR-030). Motivos: `SIN_CLASIFICACION`, `CODIGO_HUERFANO`, `SIN_FECHA_FIN`, `DIAS_PERDIDOS_NEGATIVOS`, `OBSERVACIONES_EXCEDIDAS`, `CAMPO_SIN_RESPONDER`.
- [x] T053 [US2] Agregar `GET /api/empleados/{legajo}/fichas` en `empleados/web/EmpleadoController.java` y `GET /api/fichas/{id}` en `fichas/web/FichaMedicaController.java`, devolviendo el registro **tal como está almacenado** (FR-028): días perdidos negativos o mayores a 999 sin topear, códigos huérfanos con `descripcion: null`, booleanos en `null` distintos de `false`, observaciones de más de 500 caracteres completas. Depende de T051, T052.
- [x] T054 [P] [US2] Crear `integracion/LecturaToleranteTest.java` recorriendo el dataset histórico completo sin error (US2-2, US2-3, SC-002). Depende de T020, T053.
- [x] T055 [P] [US2] Crear `integracion/DiasPerdidosTest.java`: valor calculado con alta, `NULL` sin alta, negativo y mayor a 999 en históricas (US2-5, US2-6, SC-006). Verifica la columna calculada de D4.
- [x] T056 [P] [US2] Crear `integracion/ListadoFichasTest.java`: orden descendente, sin paginado, y empleado sin fichas (US2-1, US2-4).
- [x] T057 [US2] Crear `frontend/components/fichas/ListaFichas.tsx` con navegación por teclado entre fichas, sin exigir completar nada al navegar (US2-3).
- [x] T058 [P] [US2] Agregar la marca visual de ficha incompleta en `frontend/components/fichas/ListaFichas.tsx` y en el formulario, consumiendo `motivosInconsistencia` (FR-029).

**Checkpoint**: consulta y navegación completas sobre datos reales inconsistentes.

---

## Phase 5: User Story 3 — Editar una ficha para registrar el alta (Priority: P3)

**Goal**: El operario reemplaza la citación por el alta, corrige cualquier campo incluido el legajo, y dos operarios no se pisan.

**Independent Test**: Tomar una ficha con solo citación, cargarle el alta, verificar recálculo y auditoría; y comprobar que un segundo guardado concurrente da 409.

- [x] T059 [US3] Agregar al `fichas/FichaMedicaRepositorio.java` el `UPDATE ... WHERE id = ? AND version = ?` que verifique **una fila afectada**, e incremente `version` (D2).
- [x] T060 [US3] Agregar el método de modificación a `fichas/FichaMedicaServicio.java`: valida todo de nuevo (FR-017), persiste, audita `MODIFICACION`, y lanza el conflicto optimista si afectó cero filas. Depende de T059, T035.
- [x] T061 [US3] Implementar la limpieza de campos al cambiar el estado del paciente en `fichas/FichaMedicaServicio.java` (FR-026, FR-026b, FR-027). **`limpiar` significa distinto según el campo**: envío de médico a domicilio queda en `no`, in itinere queda sin valor definido, hora del accidente queda vacía.
- [x] T062 [US3] Implementar la reasignación de legajo en `fichas/FichaMedicaServicio.java` (FR-003d, FR-003e): unicidad y solapamiento se evalúan contra el empleado **de destino**, no el de origen. Depende de T031.
- [x] T063 [US3] Agregar `PUT /api/fichas/{id}` en `fichas/web/FichaMedicaController.java`, con `version` obligatorio en el cuerpo y **409** ante conflicto (FR-037). Misma envoltura `{ datos, advertencias }` que el alta. Depende de T060.
- [x] T064 [P] [US3] Crear `unitarios/BordesDeFechaTest.java`: excluyencia al editar, alta igual al evento con cero días, alta anterior al evento, y más de 999 días (US3-2 a US3-5).
- [x] T065 [P] [US3] Crear `integracion/ConcurrenciaTest.java`: dos guardados sobre la misma ficha, el segundo da 409 y no pisa al primero (US3-9, SC-008).
- [x] T066 [P] [US3] Crear `integracion/EdicionTest.java`: citación reemplazada por alta con recálculo y auditoría (US3-1), cambio de estado que limpia campos **en lo guardado** (US3-7), histórica incompleta que exige completarse al guardar (US3-8), y solapamiento generado al agregar el alta (US3-6). Depende de T020.
- [x] T067 [P] [US3] Crear `integracion/ReasignacionLegajoTest.java`: corregir el legajo mueve la ficha (US3-10) y choca contra el destino cuando corresponde (US3-11).
- [x] T068 [US3] Agregar la edición en `frontend/app/fichas/page.tsx`, enviando el `version` recibido y mostrando el aviso de conflicto ante 409. **Sin modal.**

**Checkpoint**: ciclo de vida completo de la ficha, con concurrencia resuelta.

---

## Phase 6: User Story 4 — Eliminar una ficha cargada por error (Priority: P4)

**Goal**: El operario elimina una ficha espuria. Los datos se conservan y la clave queda liberada.

**Independent Test**: Eliminar una ficha y verificar que desaparece de la consulta, deja de bloquear por solapamiento, y sus datos siguen en la base.

- [x] T069 [US4] Agregar el borrado lógico al `fichas/FichaMedicaRepositorio.java`: setea `eliminada_por` y `eliminada_en` con el `Clock`, sin borrar la fila (FR-039b). El predicado `eliminada_en IS NULL` ya excluye la ficha de unicidad, solapamiento y listados.
- [x] T070 [US4] Agregar el método de baja a `fichas/FichaMedicaServicio.java` con asiento de auditoría `BAJA` (FR-033) y verificación de `version`. Depende de T069, T035.
- [x] T071 [US4] Agregar `DELETE /api/fichas/{id}` en `fichas/web/FichaMedicaController.java`, con `version` como parámetro de consulta y respuesta **204** (FR-038). Depende de T070.
- [x] T072 [P] [US4] Crear `integracion/EliminacionTest.java`: baja con auditoría (US4-2), la eliminada no aparece en consultas (US4-4), no bloquea solapamiento ni unicidad (US4-3), y sus datos siguen conservados y recuperables (US4-5, SC-010).
- [x] T073 [US4] Crear `frontend/components/fichas/ConfirmacionEnLinea.tsx` (FR-038). **Fila de confirmación dentro del flujo, operable por teclado. No es un diálogo modal**: FR-038 exige confirmar y el diseño prohíbe modales que corten la secuencia de carga.

**Checkpoint**: las cuatro historias entregadas. T024 pasa a verde por completo.

---

## Phase 7: Mediciones, validación con usuarios y cierre

**Purpose**: Lo que el plan pide y no es código. Sin esto, dos decisiones quedan sin el dato que las cierra.

- [x] T074 Medir el **largo máximo real** del campo observaciones durante la importación histórica y registrarlo en `specs/001-ficha-medica-empleados/research.md`, decisión D6. Es el dato que falta para poder acotar la columna, hoy `NVARCHAR(MAX)` por precaución. Depende de T020.
- [x] T075 Medir **cuántos legajos tienen una ficha histórica sin ninguna de las dos fechas de fin**, o sea cuántos quedan bloqueados por FR-014c, y registrarlo en `specs/001-ficha-medica-empleados/research.md`. **Si son muchos, el arranque del sistema queda dominado por esa corrección y hay que saberlo antes de la puesta en marcha.** Depende de T020.
- [ ] T076 [P] Medir SC-001 con operarios reales: tiempo de carga de una ficha completa por teclado, sobre al menos 10 cargas, comparado contra la pantalla de terminal actual. Registrar en `specs/001-ficha-medica-empleados/quickstart.md`. **No es automatizable y no se omite por eso.**
- [ ] T077 [P] Medir SC-005 con usuarios reales: porcentaje de rechazos que el operario corrige sin ayuda externa, objetivo 90 %. Registrar en `specs/001-ficha-medica-empleados/quickstart.md`, junto al resultado de T076. **No es automatizable y no se omite por eso.**
- [x] T078 Verificar que las 38 líneas de criterios de aceptación y los 10 criterios de éxito tengan al menos un test que las cubra, contra la tabla de mapeo de [plan.md](plan.md). Los únicos sin test automatizado deben ser SC-001 y SC-005.

---

## Dependencies

### Orden entre bloques

```
Phase 0 (decisiones)  →  Phase 1 (preparación)  →  Phase 2 (guardarraíles)  →  US1  →  US2  →  US3  →  US4  →  Phase 7
```

Phase 2 va antes de US1 a propósito: T024 y T025 quedan en rojo hasta que exista
el código que vigilan. Escribirlos después no impediría nada.

### Precedencias duras dentro de Phase 1

- T005 antes que **toda** clase nueva. Renombrar el paquete con código escrito cuesta el triple.
- T014 (reloj) antes que T029, T030, T031 y cualquier regla que toque fechas.
- T015 (esquema) antes que T031, T032, T034, T035 y cualquier repositorio.
- T017 (Testcontainers) antes que T043 y cualquier test de integración.
- T020 (dataset) antes que T054, T055, T066, T074 y T075.

### Dependencias entre historias

US1 es autónoma salvo por T033/T039, que esperan T001. US2, US3 y US4 dependen de
US1 porque operan sobre fichas que US1 crea.

---

## Parallel Execution

- **Phase 0**: T002, T003 y T004 en paralelo. T001 es conversación con el cliente y va aparte.
- **Phase 1**: T008 a T013 tocan `build.gradle`, así que **no** son paralelas entre sí pese a ser triviales. T016, T018 y T019 sí.
- **Phase 2**: T021 a T025 son cinco archivos distintos, todas en paralelo.
- **US1**: T026, T027, T028 en paralelo. Después T037, T039, T040 en paralelo. Los tests T041, T042, T044 en paralelo. Frontend T045 y T048 en paralelo.
- **US2**: T054, T055, T056 y T058 en paralelo.
- **US3**: T064 a T067 en paralelo.
- **Phase 7**: T076 y T077 en paralelo.

---

## Implementation Strategy

**MVP**: Phase 0 + Phase 1 + Phase 2 + US1. Con eso el operario carga fichas de
punta a punta por teclado, con todas las reglas aplicadas y auditadas, aunque no
pueda todavía consultar el histórico, editar ni eliminar.

**Incremento siguiente**: US2 vuelve utilizable el histórico importado y es donde
se paga la lectura tolerante. US3 cierra el ciclo de vida y es lo que hace que los
días perdidos existan de verdad. US4 es la menos frecuente y la única prescindible
durante un tiempo.

---

## Tareas bloqueadas por decisiones pendientes

| Tarea | Espera |
|---|---|
| T015 — tipo de la columna `legajo` | **T003.** FR-004f asume entero sin ceros a la izquierda. Si el padrón lo guarda como texto con relleno, la columna pasa a `NVARCHAR` y FR-004f queda mal. |
| Comportamiento ante padrón caído | **T004**, hueco CHK040. El plan asume `503` y consulta degradada; sin confirmar, ningún test puede fijar el comportamiento esperado. |

### Resuelto el 2026-09-09

**T001 cerrada: el padrón se consulta en la base externa, no se importa.**

Consecuencias, todas ya aplicadas:

- FR-003b y FR-003c quedan como estaban. El pedido original del plan —"el padrón
  se importa una vez del sistema actual"— queda descartado explícitamente.
- **D3 queda firme**: no hay clave foránea contra el padrón, y no puede haberla,
  porque SQL Server no admite FK entre bases.
- **La tabla `empleado` no existe y no se crea.** No hay tarea para ella.
- T033 y T039 quedan desbloqueadas.
- Sigue en pie la consecuencia que D3 ya anotaba: si el padrón da de baja un
  legajo con fichas cargadas, queda una referencia colgada y el sistema no puede
  impedirlo. Al leer, FR-028 manda mostrar la ficha igual.

---

## Estado al 2026-09-10

**Hechas**: Phase 1, Phase 2, y las cuatro historias completas (US1 a US4). Más de
Phase 7: T074, T075 y T078.

**Pendientes, y por qué**:

| Tarea | Por qué sigue abierta |
|---|---|
| T002 — instancia del padrón | Necesita acceso a la base real de Ferrovías. El código asume misma instancia y consulta por nombre de dos partes, configurable en `app.padron.esquema`; si estuviera en otra instancia hace falta un `DataSource` aparte. |
| T003 — tipo del legajo | Necesita la definición de columnas del padrón real. La columna quedó en `INT`, como asume FR-004f. |
| T004 — padrón caído (CHK040) | Necesita respuesta del cliente. El comportamiento implementado es el que el plan asume: `503` para alta y edición, consulta de fichas ya cargadas sin degradar. |
| T076 — SC-001 cronometrado | Medición con operarios reales sobre al menos 10 cargas, contra la pantalla de terminal actual. No es automatizable. |
| T077 — SC-005 con usuarios | Medición con usuarios reales del porcentaje de rechazos que se corrigen sin ayuda. No es automatizable. |

Las tres primeras no bloquearon la implementación: cada una tiene un supuesto
explícito, anotado en `research.md`, y ninguna cambia el diseño si resulta falsa.
Las dos últimas son las únicas dos líneas de la tabla de mapeo de
[plan.md](plan.md) que no tienen test automatizado, y es por naturaleza. El
detalle está en [checklists/cobertura.md](checklists/cobertura.md).
