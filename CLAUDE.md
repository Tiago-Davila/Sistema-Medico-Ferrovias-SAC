# CLAUDE.md

Instrucciones para agentes que trabajan en este repositorio.

La autoridad es `sis-medico/.specify/memory/constitution.md`. Este archivo es la
guía operativa del día a día: lo que se repite en todas las tareas y lo que es
fácil de errar. Ante divergencia entre los dos, manda la constitución y este
archivo se corrige.

## Qué es este proyecto

Una aplicación web para cargar y consultar fichas médicas de empleados de
Ferrovías SAC. Reemplaza funcionalmente **una sola pantalla** de un sistema viejo
escrito en IdeaFix (C propietario, 1996).

Es un sistema **nuevo** que hace lo mismo que hace el viejo en esa pantalla. No es
una migración, no es una reescritura línea por línea, y no hereda estructura
técnica. Del sistema viejo se toma el comportamiento, no la forma.

El desarrollo corre contra una base propia, de prueba, con esquema diseñado por
nosotros. Las condiciones de despliegue en producción **todavía no están
definidas**: el esquema de acá es del proyecto, no el destino final de los datos.

## Restricciones duras

Violar cualquiera de estas rompe el acuerdo con el cliente o el diseño del
sistema.

### 1. El alcance está cerrado

Una pantalla. Estos campos y nada más:

| Bloque | Campos |
|---|---|
| Identificación | legajo, con apellido y nombre traídos del padrón |
| Evento | fecha, estado del paciente, in itinere, estaba en servicio, hora del accidente, atendido por servicio médico, envío de médico a domicilio, justificado |
| Fechas | fecha de citación, fecha de alta, días perdidos (calculado) |
| Clasificación | categoría de enfermedad, detalle de enfermedad |
| Texto | observaciones |
| Auditoría | quién y cuándo creó, quién y cuándo modificó |

No agregar funcionalidad que no fue pedida, por razonable que parezca. La lista de
exclusiones está más abajo, en **Fuera de alcance**.

### 2. Nada de legacy en el código

No se replica el esquema viejo. Las abreviaturas de seis letras (`fecacc`,
`feccit`, `enivel1`, `apelli`, `diaspe`) **no existen en este proyecto**: ni en la
base, ni en el código, ni en la API, ni en el frontend.

Tampoco se hereda el vocabulario. La entidad principal es la **ficha médica**, no
la "novedad".

Del sistema existente se toman únicamente **los valores**: el catálogo de grupos y
detalles de enfermedad, y el catálogo de estados. Se importan una sola vez. No hay
sincronización posterior, ni lectura en vivo de las tablas viejas, ni adaptadores
legacy, ni capa de mapeo hacia el sistema anterior.

Si estás escribiendo un mapper `legacy <-> dominio`, algo salió mal.

### 3. El backend es la autoridad

Toda regla de negocio vive en servicios de Spring. Los cálculos derivados —**días
perdidos** en particular— se resuelven en el backend; el frontend los muestra, no
los computa como fuente de verdad.

React valida solo para mejorar la experiencia. Cada validación del frontend tiene
su equivalente en el backend. Nunca al revés.

### 4. Simplicidad proporcional

Esto es carga de datos sobre una pantalla, con validaciones y dos catálogos. El
diseño tiene que reflejar eso.

No microservicios. No capas de abstracción especulativas. No interfaces con una
sola implementación puestas por si acaso. No patrones de integración para sistemas
que no existen. No caché sin un problema de performance medido.

### 5. Validar al escribir, tolerar al leer

Aplica **si** se importan registros reales sin filtrar: pueden violar reglas que
hoy son obligatorias, porque se cargaron cuando esas reglas no existían.

- Las validaciones corren al guardar, jamás al cargar.
- Un registro inconsistente se muestra completo, se señala en la interfaz si
  corresponde, y nunca lanza excepción.

Ejemplo real del sistema de referencia: hay fichas con categoría y detalle de
enfermedad vacíos, anteriores a la clasificación de 2016, aunque hoy ambos campos
sean obligatorios para guardar.

## Trampas del dominio

**"Categoría" significa dos cosas distintas.** En el padrón es la categoría
laboral del empleado. En esta pantalla es el grupo de enfermedad. En el dominio
son `categoriaLaboral` y `categoriaEnfermedad`. Nunca "categoria" a secas.

**Un empleado no puede tener dos fichas con la misma fecha.** La combinación
(legajo, fecha del evento) identifica unívocamente a la ficha médica. Es una regla
de negocio del sistema de referencia, no un accidente del esquema viejo.

**El catálogo de estados tiene dos valores reales.** Accidentado y enfermedad. El
sistema de referencia tiene ramas de código para otros dos estados que nunca se
ejecutaron en más de 80.000 registros. Es código muerto: no implementarlas.

**Los días perdidos son calculados, no cargados.** Se derivan de las fechas. No
hay campo editable.

## Convenciones

### Nombres

Español completo y descriptivo, en toda la pila: base, dominio, API y frontend.
`fechaAccidente`, `fechaCitacion`, `fechaAlta`, `diasPerdidos`, `apellido`,
`categoriaEnfermedad`, `detalleEnfermedad`.

No hay una capa donde las abreviaturas viejas sean aceptables. Ese era el diseño
anterior y ya no aplica.

### Comentarios en Java

Nada de bloques Javadoc (`/** ... */`) ni de `@param`/`@return`/`@throws`. Como
mucho, una línea `//` arriba del método, en una sola oración, diciendo qué hace.
Si el nombre del método ya lo dice, no lleva comentario.

Dentro del cuerpo, una línea corta se justifica solo cuando el código hace algo
contraintuitivo a propósito: `// Intencional: aunque parezca un bug, así lo pide
el sistema de referencia.` Nada de citar números de requisito (`FR-xxx`,
`SC-xxx`, `D-xxx`) en el código — esa trazabilidad vive en la spec, bajo
`specs/`.

### Reglas heredadas del sistema de referencia

Las validaciones del sistema viejo son **requisitos funcionales**, no herencia
técnica. Se reimplementan de forma limpia y directa, sin arrastrar la estructura
del código original. No se copian comentarios con `archivo:línea` del código C.

Cuando una regla se decide cambiar o eliminar, se documenta como decisión
explícita con su justificación en la spec de la feature. Ninguna regla desaparece
por olvido.

### Estructura

Por capa técnica, no por feature:

```
backend/sistema-medico/src/main/java/com/ferrovias/sismedico/
├── models/          entidades y value objects del dominio
├── controllers/     endpoints REST
├── service/         reglas de negocio y validaciones
├── dtos/            formas de entrada/salida de la API
├── repositories/     acceso a datos con JdbcTemplate
├── exceptions/      excepciones de dominio
└── comun/           configuración transversal (seguridad, reloj, manejo de errores)
```

No hay paquete `legacy/`, y no debe haberlo.

## Datos de salud

El sistema maneja diagnósticos médicos de empleados identificados.

- Autenticación contra Active Directory corporativo, autorización en el backend.
- Toda escritura queda auditada: usuario, momento, registro afectado.
- **Los logs nunca contienen diagnósticos, observaciones clínicas ni descripciones
  de enfermedad.** Ni en INFO, ni en DEBUG, ni en mensajes de excepción, ni en
  trazas de SQL con parámetros.
- Los datos reales importados al entorno de prueba se **anonimizan**: legajos y
  nombres ficticios. Fechas, estados y códigos de enfermedad se conservan tal
  cual, que es lo que hace falta para probar el comportamiento.

## Fuera de alcance

No implementar, aunque aparezca en la pantalla de referencia:

- El bloque ART completo: aseguradora, número de siniestro, observaciones ART.
- Todo tipo de reporte, incluidos el parte diario y el reporte de ficha médica.
- El campo de inclusión en el parte del día siguiente.
- La novedad centralizada.
- Consultas médicas y seguimiento médico.
- Justificación de horas.
- Catálogos y seguimiento COVID.
- Padrón de médicos.
- Exclusión de licencias.
- El envío automático de correo.
- Los perfiles funcionales médico y psicólogo. Hay un **único perfil de operario**.

Si una tarea parece requerir algo de esta lista, el requerimiento está mal
planteado. Parar y preguntar.

## Estado actual del repo

Actualizado el 2026-09-10. Las cuatro historias de la feature 001 están
implementadas de punta a punta.

- `sis-medico/backend/sistema-medico/` — Spring Boot sobre Gradle, paquete
  `com.ferrovias.sismedico`, organizado por capa técnica. Acceso a datos con
  `JdbcTemplate`, esquema por Flyway, seguridad LDAP contra AD, contrato de
  OpenAPI por springdoc.
- `sis-medico/frontend/` — Next.js con React, Tailwind, React Hook Form y Zod.
  Una sola pantalla, en `app/fichas/`.
- Esquema y catálogos en `src/main/resources/db/migration/`: `V1` la ficha médica
  y su auditoría, `V2` los catálogos de enfermedad con su semilla.

**Lo que sigue abierto, y espera al cliente**: en qué instancia vive el padrón
(T002), el tipo real de la columna legajo (T003), el comportamiento ante padrón
caído (T004), y las dos mediciones con operarios reales, SC-001 y SC-005 (T076 y
T077). Están en `sis-medico/specs/001-ficha-medica-empleados/tasks.md` con el
supuesto que se aplicó en cada caso. **Ninguna se cierra por cuenta propia.**

## Comandos

Backend, desde `sis-medico/backend/sistema-medico/`:

```bash
./gradlew bootRun
```

Reglas de negocio y guardarraíles de arquitectura, sin Docker, segundos:

```bash
./gradlew test
```

Persistencia, solapamiento, concurrencia y lectura tolerante, con SQL Server real:

```bash
./gradlew integrationTest
```

Son dos suites a propósito. La imagen de SQL Server pesa más de 2 GB, así que
`test` no la toca y es la que se usa mientras se escribe código. Para pagar el
arranque una sola vez por sesión:

```bash
echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
```

Frontend, desde `sis-medico/frontend/`:

```bash
npm run dev
npm test          # vitest
npm run typecheck
npm run lint
```

El contrato de Zod **no se escribe a mano**: sale del OpenAPI del backend. Cuando
cambia un DTO o un endpoint, la cadena es correr `integrationTest` —que vuelca
`frontend/lib/contrato-openapi.json`— y después:

```bash
cd sis-medico/frontend && npm run generar-contrato
```

Los tests de integración corren contra una base descartable con Testcontainers,
nunca contra datos productivos.

## Cuando algo no cierra

Este proyecto tiene información faltante y decisiones pendientes del cliente. Si
una tarea requiere asumir algo sobre el comportamiento esperado, el significado de
un campo o una regla de negocio, **preguntar en vez de inventar**. Una suposición
razonable que resulta equivocada acá cuesta más que la demora.

Las preguntas abiertas se registran en la spec de la feature, bajo `specs/`. No
cerrarlas por cuenta propia.
