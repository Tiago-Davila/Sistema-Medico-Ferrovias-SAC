# Implementation Plan: Ficha médica de empleados

**Branch**: `001-ficha-medica-empleados` | **Date**: 2026-09-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-ficha-medica-empleados/spec.md`

## Summary

Una pantalla para que un operario administrativo registre, consulte, edite y
elimine eventos médicos del plantel. El backend es la autoridad de todas las
reglas; el frontend acelera la carga por teclado y nunca decide.

El eje técnico son dos tensiones que atraviesan todo el diseño:

1. **Escribir con reglas estrictas sobre datos históricos que no las cumplen.** Se
   resuelve con un esquema deliberadamente permisivo —nulos donde el negocio exige
   valor, ninguna FK contra el catálogo, ningún `CHECK` de negocio— y toda la
   obligatoriedad concentrada en la capa de servicio, activa solo al escribir.
2. **Velocidad de carga sobre una pantalla de dieciséis campos.** Se resuelve con
   validación acumulativa que devuelve todas las violaciones de una vez, guardado
   en un solo paso con advertencias en el cuerpo de la respuesta, y cero modales.

Las siete decisiones abiertas están resueltas y justificadas en
[research.md](research.md); el esquema resultante en
[data-model.md](data-model.md); la superficie de API en
[contracts/api.md](contracts/api.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript (frontend)

**Primary Dependencies**: Spring Boot, Spring Security, Spring JDBC
(`JdbcTemplate` como acceso principal), Flyway, springdoc-openapi · Next.js,
React, Tailwind, shadcn/ui, React Hook Form, Zod

**Storage**: SQL Server. Esquema propio de la aplicación, con DDL libre. El padrón
de empleados vive en una base **separada**, de solo consulta, que no se copia.

**Testing**: JUnit 5, Mockito, Testcontainers (SQL Server), ArchUnit

**Target Platform**: servidor Linux, navegador de escritorio

**Project Type**: aplicación web, backend + frontend

**Performance Goals**: SC-001, tarea del operario en 60 segundos o menos por
ficha. **No hay objetivo de tiempo de respuesta del sistema declarado**: el hueco
CHK051 sigue abierto y se resuelve durante la implementación si aparece.

**Constraints**: carga íntegra por teclado, sin mouse y sin modales. Ningún dato
clínico en logs, garantizado por prueba automática y no por disciplina.

**Scale/Scope**: una pantalla, cuatro historias, ~80.000 fichas históricas, un
perfil de usuario, decenas de operarios concurrentes como mucho.

## Constitution Check

*GATE: revisado antes de Phase 0 y de nuevo después del diseño de Phase 1.*

| Principio | Estado | Cómo lo cumple el diseño |
|---|---|---|
| I — Alcance cerrado | **Pasa** | El contrato de API no tiene endpoint, campo ni hueco reservado para nada de la lista de exclusiones. Sección explícita "Lo que este contrato no tiene". |
| II — Modelo propio y limpio | **Pasa** | Nombres completos en español en base, dominio, API y frontend. Cero abreviaturas legacy. Sin sincronización, sin lectura en vivo del sistema viejo, sin mappers legacy. |
| III — Comportamiento equivalente | **Pasa** | R1–R13 trazadas a requisitos y de ahí a tests. Los desvíos ya están documentados en el spec, no se agregan nuevos acá. |
| IV — Backend es la autoridad | **Pasa** | FR-018 ampliado cubre también las dependencias entre campos. Los DTO de entrada no tienen dónde recibir valores derivados (M4). |
| V — Simplicidad proporcional | **Pasa con dos justificaciones** | Ver *Complexity Tracking*. Sin microservicios, sin caché, sin eventos, sin colas, sin interfaces de una sola implementación. |
| VI — Datos de salud | **Pasa** | AD por LDAPS, autorización en backend, auditoría de toda escritura en tabla propia, y test automático de fuga de logs. |
| VII — Preguntar antes de suponer | **Pasa** | Los supuestos abiertos están marcados como tales más abajo, con la alternativa descartada. Ninguno se cerró en silencio. |

**Re-evaluación post-diseño**: sin cambios. Las dos justificaciones del Principio V
son las mismas antes y después.

## Project Structure

### Documentation (this feature)

```text
specs/001-ficha-medica-empleados/
├── plan.md              # Este archivo
├── research.md          # Las 7 decisiones, con alternativas descartadas
├── data-model.md        # Esquema definitivo
├── quickstart.md        # Cómo levantarlo y validarlo
├── contracts/
│   └── api.md           # Superficie de API, errores y advertencias
├── checklists/
│   ├── requirements.md
│   └── testabilidad.md
└── tasks.md             # Lo genera /speckit-tasks, no este comando
```

### Source Code (repository root)

```text
backend/sistema-medico/
├── src/main/java/com/ferrovias/sismedico/
│   ├── SistemaMedicoApplication.java
│   ├── fichas/
│   │   ├── FichaMedica.java              # dominio, sin anotaciones de validación
│   │   ├── FichaMedicaServicio.java      # transacción, orquestación
│   │   ├── ValidadorFichaMedica.java     # acumula violaciones, no cortocircuita
│   │   ├── CalculadorDiasPerdidos.java
│   │   ├── DetectorSolapamiento.java
│   │   ├── FichaMedicaRepositorio.java   # JdbcTemplate
│   │   └── web/
│   │       ├── FichaMedicaController.java
│   │       └── dto/                      # sin diasPerdidos: M4
│   ├── empleados/
│   │   ├── Empleado.java
│   │   └── PadronRepositorio.java        # solo lectura, base externa
│   ├── enfermedades/
│   │   ├── GrupoEnfermedad.java
│   │   ├── DetalleEnfermedad.java
│   │   └── CatalogoRepositorio.java
│   ├── auditoria/
│   │   ├── AuditoriaServicio.java
│   │   └── AuditoriaRepositorio.java
│   └── comun/
│       ├── RelojConfig.java              # único lugar con ZoneId
│       ├── ManejadorGlobalDeErrores.java # problem+json
│       ├── Violacion.java / Advertencia.java
│       └── SeguridadConfig.java          # LDAPS contra AD
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       ├── V1__esquema_inicial.sql
│       └── V2__catalogos_enfermedad.sql  # semilla versionada
└── src/test/java/com/ferrovias/sismedico/
    ├── unitarios/                        # reglas, sin base
    ├── integracion/                      # Testcontainers
    └── arquitectura/                     # ArchUnit: reloj y logs

frontend/
├── app/
│   └── fichas/page.tsx                   # la única pantalla
├── components/fichas/
│   ├── BuscadorLegajo.tsx
│   ├── ListaFichas.tsx
│   ├── FormularioFicha.tsx
│   ├── SelectorCatalogo.tsx              # acepta código tipeado
│   └── ConfirmacionEnLinea.tsx           # no es modal
└── lib/
    ├── api.ts
    └── esquemas.ts                       # Zod, espejo del backend
```

**Structure Decision**: backend y frontend separados, que es lo que el stack de la
constitución implica. El backend se organiza por área de dominio —fichas,
empleados, enfermedades, auditoría— y no por capa técnica, para que el paquete
`fichas/` contenga todo lo que hace falta leer para entender una regla.

**No hay paquete `legacy/`, y no debe haberlo.** Esa estructura pertenecía al
diseño derogado por la constitución v2.0.0.

### Cambios necesarios sobre lo que ya existe

El proyecto actual es el andamio sin tocar de Spring Initializr y **diverge del
stack de la constitución**:

| Qué | Estado actual | Acción |
|---|---|---|
| Paquete | `com.sis_medico.demo` | Renombrar a `com.ferrovias.sismedico` |
| `rootProject.name` | `demo` | `sistema-medico` |
| Acceso a datos | `spring-boot-starter-data-jpa` | Reemplazar por `spring-boot-starter-jdbc` |
| Driver | ausente | Agregar `mssql-jdbc` |
| Migraciones | ausente | Agregar `flyway-core` y `flyway-sqlserver` |
| OpenAPI | ausente | Agregar `springdoc-openapi` |
| AD | ausente | Agregar soporte LDAP de Spring Security |
| Tests | solo starter-test | Agregar Testcontainers (SQL Server) y ArchUnit |

## Las siete decisiones

Resumen. La justificación completa y las alternativas descartadas están en
[research.md](research.md).

| # | Decisión | En una línea |
|---|---|---|
| D1 | Clave primaria | **Sustituta** `id BIGINT` + índice único filtrado sobre `(legajo, fecha_evento) WHERE eliminada_en IS NULL`. La natural es inviable porque FR-003d hace editable el legajo y FR-039 restringe la unicidad a las fichas vivas. |
| D2 | Concurrencia | **Optimista** con columna `version BIGINT` que incrementa el servicio. `UPDATE ... WHERE id = ? AND version = ?`; cero filas afectadas → **409**. |
| D3 | FK contra el padrón | **No hay**, y no puede haberla: el padrón está en otra base y SQL Server no admite FK entre bases. El rechazo de legajo inexistente vive en el servicio. |
| D4 | Días perdidos | **Columna calculada no persistida**. Consultable por SQL e imposible de desincronizar, las dos cosas a la vez. |
| D5 | Auditoría | **Columnas y tabla de historial**. Las columnas cumplen FR-032, el historial cumple FR-033, que las columnas solas no pueden cumplir. |
| D6 | Observaciones | Columna `NVARCHAR(MAX)`, tope de 500 en el servicio. Único arreglo que respeta FR-006b y FR-006c sin truncar histórico de longitud desconocida. |
| D7 | Zona horaria | Propiedad `app.zona-horaria` explícita → bean `Clock` inyectado. ArchUnit prohíbe `LocalDate.now()` fuera de la configuración. |

## Los puntos donde no se improvisa

### Nulabilidad

`grupo_enfermedad_id` y `detalle_enfermedad_id` son `NULL` en la base y **sin FK
contra el catálogo**. Ningún `NOT NULL` ni `CHECK` reproduce una regla de negocio.
El modelo de persistencia no lleva anotaciones de validación. Toda la
obligatoriedad de FR-004b la aplica `ValidadorFichaMedica` al escribir. Detalle
por columna en [data-model.md](data-model.md).

### Validación acumulativa

`ValidadorFichaMedica` recorre **todas** las reglas y devuelve
`List<Violacion>(campo, codigo, mensaje)`. No hay `return` temprano. El manejador
global lo traduce a **422** con todas las violaciones. Un test carga una ficha con
siete errores simultáneos y exige las siete en la respuesta: es lo que impide que
alguien introduzca un cortocircuito más adelante.

### Advertencias contra errores

**Guardado en un solo paso.** `200`/`201` con `advertencias: []` en el cuerpo,
siempre presente. No hay confirmación en dos pasos: exigiría un modal o un segundo
viaje, y las dos cosas pelean contra la carga por teclado y contra SC-001. La
envoltura es idéntica en `POST` y `PUT`, así que el frontend mira un solo lugar.

### Cálculos en el backend

Los DTO de entrada **no tienen campo** `diasPerdidos`. No es que se ignore lo que
mande el cliente: no existe dónde recibirlo. `estabaEnServicio` sí viaja, pero el
servicio lo sobrescribe cuando `inItinere` está marcado (FR-024) sin mirar lo
enviado. El frontend puede anticipar ambos para mostrarlos mientras el operario
escribe.

### Logs sin datos clínicos

Cuatro mecanismos, ninguno confiado a la disciplina (detalle en M3 de
[research.md](research.md)):

1. `toString()` redactado en los tipos que llevan texto clínico.
2. `toString()` del registro de ficha limitado a `id`, `legajo` y fechas.
3. Logger de `JdbcTemplate` clavado en `INFO`, con comprobación que **falla el
   arranque** si está en `DEBUG` o `TRACE` fuera de test. El log de parámetros de
   SQL es la fuga más fácil de pasar por alto.
4. **Test de fuga**: ejecuta alta, modificación y baja con observaciones
   centinela, captura todo el log a nivel `TRACE` y falla si algún centinela
   aparece. Es lo que verifica SC-007.

### Carga por teclado

| Requisito | Resolución |
|---|---|
| Orden de tabulación | `tabIndex` explícito siguiendo el orden visual de la pantalla vieja, no el orden del DOM |
| Foco automático | Al resolver el legajo, el foco salta solo a *fecha del evento* |
| Catálogos | `SelectorCatalogo` acepta el **código tipeado** y resuelve sin abrir lista; la lista es opcional, para quien no se acuerda el código |
| Guardado | Atajo de teclado documentado, sin pasar por el mouse |
| Encadenado | Tras guardar, formulario limpio y foco en el buscador de legajo (FR-041) |
| Sin modales | La confirmación de borrado de FR-038 es una **fila de confirmación en línea**, operable por teclado |

**Tensión que hay que registrar**: FR-038 exige confirmar antes de eliminar y el
enunciado prohíbe los modales. Se resuelve con confirmación en línea dentro del
flujo, no con un diálogo. Es la única lectura que satisface las dos cosas.

## Importación de datos

Tres cargas separadas, con mecanismos distintos.

| Carga | Mecanismo | Nota |
|---|---|---|
| Catálogos de enfermedad | Datos semilla versionados con el esquema, `V2__catalogos_enfermedad.sql` | Pocos registros, estables. Viajan con el código. |
| Padrón de empleados | **No se importa** | Ver conflicto abajo. |
| Fichas históricas | Script de importación aparte, anonimizado | **Sin filtrar.** Ver abajo. |

### El padrón no se importa — resuelto el 2026-09-09

Hubo un conflicto: el pedido de este plan decía *"Padrón de empleados: volumen
mayor, se importa una vez del sistema actual"*, mientras FR-003b y FR-003c decían
lo contrario. **El cliente resolvió que el padrón se consulta en la base externa y
no se importa**, con lo que FR-003b y FR-003c quedan firmes y el pedido original
queda descartado.

En consecuencia: **no hay tabla `empleado`**, D3 se mantiene sin clave foránea, y
el `PadronRepositorio` es de solo lectura contra la base externa, sin copia ni
caché persistente.

Consecuencia que hay que seguir aceptando de frente: si el padrón da de baja un
legajo con fichas cargadas, queda una referencia colgada y el sistema no puede
impedirlo.

### Fichas históricas: se cargan sin filtrar

No es una preferencia, lo exige FR-036b: el juego de datos de prueba **tiene que
contener** casos inconsistentes reales —sin grupo ni detalle, sin ninguna de las
dos fechas de fin— porque sin ellos FR-028 a FR-031, FR-014c y SC-002 no son
verificables. Filtrarlos dejaría sin ejercitar toda la tolerancia de lectura, que
es la mitad del riesgo de esta feature.

Anonimización según FR-036c: legajos y nombres ficticios; fechas, estados y
códigos de enfermedad tal cual.

Durante esta importación se **mide el largo máximo real de observaciones**, dato
que hoy no tenemos y que permitiría después acotar la columna de D6.

## Estrategia de pruebas

### Dos velocidades

| Suite | Qué prueba | Cómo corre |
|---|---|---|
| **Unitaria** | Todas las reglas de negocio, el cálculo de días perdidos, las dependencias entre campos, la acumulación de violaciones | Sin base. `./gradlew test`. Segundos. |
| **Integración** | Persistencia, índice único filtrado, consulta de solapamiento, bloqueo optimista, auditoría en la misma transacción, visibilidad del borrado lógico, lectura tolerante | Testcontainers. Tarea Gradle aparte. |
| **Arquitectura** | Prohibición de `LocalDate.now()`, fuga de datos clínicos en logs | ArchUnit + test de fuga. Con la suite unitaria. |

### La imagen de SQL Server es pesada

Pesa más de 1 GB y tarda decenas de segundos en estar lista. Sin cuidado, el ciclo
de trabajo se vuelve inusable. Medidas concretas:

- **Un solo contenedor para toda la suite**: `static` y compartido, no uno por
  clase de test.
- **Reuso entre corridas** con `testcontainers.reuse.enable=true`, para que el
  arranque se pague una vez por sesión de trabajo y no una vez por ejecución.
- **Suites separadas en Gradle**: `test` corre solo lo unitario y no toca Docker;
  `integrationTest` corre lo de base. El desarrollo iterativo usa la primera.
- **Esquema por Flyway sobre el contenedor**, no por scripts sueltos, para que lo
  que se prueba sea el mismo esquema que se despliega.
- **Padrón externo en el mismo contenedor**, como segunda base, para poder probar
  la consulta entre bases de verdad en vez de simularla.

### Mapeo de criterios de aceptación a tests

`U` unitario · `I` integración · `A` arquitectura · `M` medición manual

| Criterio | Tipo | Qué verifica |
|---|---|---|
| US1-1, US1-2 | U + I | Resolución de legajo contra el padrón; inexistente rechaza |
| US1-3 | I | Alta válida persiste con auditoría |
| US1-4 | U | `EVENTO_FUTURO`, contra el `Clock` fijado |
| US1-5, US1-6 | U | `FECHAS_FIN_EXCLUYENTES` en los dos sentidos |
| US1-7 | U | `GRUPO_REQUERIDO` / `DETALLE_REQUERIDO` por separado |
| US1-8 | U + I | `DETALLE_FUERA_DE_GRUPO` contra el catálogo real |
| US1-9 | U | `IN_ITINERE_REQUERIDO` solo con estado accidentado |
| US1-10, US1-11 | U | Derivaciones de FR-024, FR-023 y FR-025 |
| US1-12 | U + I | Guarda **y** devuelve `EVENTO_ANTIGUO` en el cuerpo |
| US1-13 | I | Índice único filtrado rechaza el duplicado |
| US1-14, US1-15 | I | Solapamiento; extremos compartidos **no** solapan |
| US1-16 | I | Ficha histórica de período abierto bloquea, con mensaje accionable |
| US2-1 | I | Orden descendente, sin paginado |
| US2-2, US2-3 | I | Histórica incompleta se abre, se marca y no exige nada al navegar |
| US2-4 | I | Empleado sin fichas |
| US2-5, US2-6 | I | Columna calculada: valor y `NULL` |
| US3-1 | I | Alta reemplaza citación, recalcula, audita modificación |
| US3-2 | U | Excluyencia también al editar |
| US3-3, US3-4, US3-5 | U | Bordes de alta: igual, anterior, más de 999 |
| US3-6 | I | Alta nueva que genera solapamiento |
| US3-7 | U + I | Cambio de estado limpia campos **en lo guardado** |
| US3-8 | U | Histórica incompleta exige completarse al guardar |
| US3-9 | I | Bloqueo optimista: segundo guardado da 409 |
| US3-10, US3-11 | I | Reasignación de legajo, validada contra el destino |
| US4-1 | Frontend | Confirmación en línea, por teclado |
| US4-2 | I | Borrado lógico con asiento de auditoría |
| US4-3, US4-4 | I | Eliminada fuera de validaciones y de listados |
| US4-5 | I | Datos conservados y recuperables |
| SC-001 | M | Cronometrado con operarios, contra la pantalla actual |
| SC-002 | I | Recorrido completo del dataset histórico sin error |
| SC-003 | U + I | Cobertura agregada de todas las reglas bloqueantes |
| SC-004 | I | Toda escritura deja asiento |
| SC-005 | M | Prueba con usuarios sobre mensajes de rechazo |
| SC-006 | I | Días perdidos = diferencia, incluidos negativos y mayores a 999 |
| SC-007 | A | Test de fuga de logs |
| SC-008 | I | Dos operarios, cero pérdidas silenciosas |
| SC-009 | U | Contenido del mensaje de solapamiento |
| SC-010 | I | Recuperabilidad de eliminadas |

Las 38 líneas de criterios de aceptación de las cuatro historias y los 10
criterios de éxito quedan cubiertos. Los dos que no se automatizan —SC-001 y
SC-005— son de medición con usuarios por naturaleza, no por omisión.

## Lo que este plan deliberadamente no hace

Sin punto de extensión, interfaz ni abstracción preparada para reportes, bloque de
aseguradora, consultas médicas, seguimiento, parte diario ni perfiles adicionales.
Sin caché, sin cola de mensajes, sin capa de eventos, sin separación en servicios.
Sin repositorio genérico ni interfaz de una sola implementación.

Si mañana entra alguna de esas cosas, se agrega entonces. Preparar el terreno hoy
es complejidad sin uso, y el Principio V la prohíbe.

## Riesgos y supuestos

### Supuestos que hay que verificar antes de codificar

| Supuesto | Alternativa si es falso |
|---|---|
| El padrón está en la **misma instancia** de SQL Server, accesible por nombre de tres partes | Segunda instancia → `DataSource` aparte y dos round-trips. Cambia configuración, no diseño. |
| El legajo es **entero** sin ceros a la izquierda (FR-004f) | Si el padrón lo guarda como texto con relleno, la columna pasa a `NVARCHAR` y FR-004f queda mal. **La autoridad es la base externa.** |
| El padrón **no se importa** (FR-003b) | Si el cliente confirma que sí se importa, aparece tabla `empleado`, D3 cambia a FK real y hay que enmendar FR-003b y FR-003c. |

### Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| **CHK040 sigue abierto**: padrón externo caído | Sin él no se crea ni edita ninguna ficha: es dependencia dura de FR-001 | El plan asume `503` y consulta degradada sin apellido ni nombre. Necesita confirmación del cliente. |
| **FR-014c bloquea legajos enteros** | Una ficha histórica sin fecha de fin deja al legajo sin poder cargar nada nuevo | FR-014d hace el bloqueo accionable. **Medir cuántos legajos alcanza antes de la puesta en marcha**: si son muchos, el arranque queda dominado por esa corrección. |
| Arranque de Testcontainers | Ciclo de trabajo lento, tentación de saltear los tests de base | Suites separadas, contenedor único, reuso entre corridas |
| Observaciones históricas más largas de lo previsto | Truncar datos clínicos es irreversible | `NVARCHAR(MAX)` por decisión D6; se mide el máximo real durante la importación |
| Fuga de datos clínicos por log de parámetros SQL | Incumple el Principio VI | Logger clavado, comprobación al arranque y test de fuga automático |
| Divergencia entre validación de Zod y la del backend | El operario ve un error distinto del que aplica el servidor | El backend es la autoridad (FR-018). Los esquemas Zod se derivan del contrato de OpenAPI, no se escriben a mano dos veces. |

## Complexity Tracking

Dos agregados que exceden lo mínimo y necesitan justificación contra el
Principio V.

| Adición | Por qué hace falta | Alternativa simple, y por qué se descartó |
|---|---|---|
| Tabla `ficha_medica_auditoria` | FR-033 exige registrar **toda** escritura. Las columnas de FR-032 guardan solo la última: la segunda modificación pisa el rastro de la primera | Solo columnas: incumple FR-033. Lo que se agrega es una tabla y un `INSERT` en la misma transacción, sin abstracción ni disparadores |
| Flyway | El esquema es propio y evoluciona; los catálogos viajan como semilla versionada junto al código | Scripts SQL sueltos: no dan reproducibilidad entre el contenedor de tests y el despliegue, que es justo lo que la estrategia de pruebas necesita |

Ninguna de las dos introduce una capa, una interfaz ni un punto de extensión.
