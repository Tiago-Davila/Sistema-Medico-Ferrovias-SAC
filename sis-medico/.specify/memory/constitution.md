<!--
SYNC IMPACT REPORT
==================
Cambio de versión: 1.0.0 -> 2.0.0
Tipo de cambio: MAJOR — reescritura completa. La v1.0.0 partía de tres supuestos
que no aplican: migración literal del sistema legacy, alcance de módulo completo,
y una base de producción existente e intocable como destino de los datos. Los tres
quedan derogados.

Principios removidos (incompatibles hacia atrás):
  - I. La base Ferrovías es intocable — no hay acceso a la base de producción;
    el proyecto trabaja sobre esquema propio.
  - II. Reparto estricto entre las dos bases — ya no hay dos bases. Desaparecen
    el reparto de tablas legacy, la prohibición de duplicar datos, la regla de
    transacción local única y la prohibición de MSDTC.

Principios redefinidos:
  - V. Preservación verificable del comportamiento legacy
    -> III. Comportamiento equivalente al sistema actual
    (se elimina el requisito de trazar cada regla a archivo:línea del código
     legacy; las reglas pasan a ser requisitos funcionales reimplementados)
  - VII. Nombres de dominio legibles, base intacta
    -> absorbido por II. Modelo de datos propio y limpio
    (se endurece: los nombres legacy ya no existen tampoco en la base)
  - IV. Los datos históricos no cumplen las reglas actuales
    -> absorbido por II. Modelo de datos propio y limpio, ahora condicionado a
       la decisión de importar registros reales sin filtrar

Principios retenidos (renumerados):
  - III. El backend es la autoridad -> IV. El backend es la autoridad
    (expandido: los cálculos derivados se resuelven en el backend)
  - VI. Datos de salud -> VI. Datos de salud
    (expandido: anonimización de los datos importados al entorno de prueba)

Principios agregados:
  - I. El alcance está cerrado
  - V. Simplicidad proporcional
  - VII. Preguntar antes de suponer (era guía de flujo en v1.0.0, asciende a
    principio)

Secciones reemplazadas:
  - "Stack y Restricciones Técnicas" -> "Alcance Funcional, Entorno y Stack"
  - "Flujo de Desarrollo y Puertas de Calidad" -> reescrita sobre los nuevos
    puntos de control

TODOs diferidos: ninguno dentro de este documento.

CONFLICTO RESUELTO (2026-09-08): el archivo CLAUDE.md en la raíz del repositorio
describía la arquitectura derogada por esta versión (dos bases, prohibición de
DDL, paquete legacy/, tablas fv_*, vocabulario "novedad", bloque ART en
pantalla). Fue reescrito para alinearse con esta constitución.
-->

# Constitución del Sistema de Fichas Médicas de Ferrovías SAC

Aplicación web para cargar y consultar fichas médicas de empleados de Ferrovías
SAC. Reemplaza funcionalmente **una sola pantalla** de un sistema viejo escrito en
IdeaFix (C propietario, 1996).

Es un sistema nuevo que hace lo mismo que hace el viejo en esa pantalla. No es una
migración, no es una reescritura línea por línea, y no hereda estructura técnica.
Del sistema viejo se toma el comportamiento, no la forma.

Los principios de este documento son **no negociables** y aplican a todas las
features, sin excepción por urgencia, tamaño o conveniencia.

## Core Principles

### I. El alcance está cerrado

Solo se implementa lo que está en la lista de campos de la sección *Alcance
Funcional*. **MUST NOT** agregarse funcionalidad que no fue pedida, por razonable
que parezca.

Queda explícitamente afuera:

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
- Los perfiles funcionales médico y psicólogo: hay un **único perfil de operario**.

Si una tarea parece requerir algo de esa lista, el requerimiento está mal
planteado. El trabajo se detiene y se pregunta.

**Rationale**: la pantalla de referencia arrastra treinta años de funcionalidad
acumulada, la mayor parte de la cual no es parte de este encargo. Sin un límite
escrito, cada campo visible en una captura de pantalla se convierte en trabajo
implícito.

### II. Modelo de datos propio y limpio

La aplicación diseña su propio esquema, con nombres completos y legibles en
español, tipos apropiados y restricciones declaradas donde corresponde.

- **MUST NOT** replicarse el esquema legacy. Las abreviaturas de seis letras del
  sistema viejo (`fecacc`, `feccit`, `enivel1`, `apelli`) no existen en este
  proyecto: ni en la base, ni en el código, ni en la API, ni en el frontend.
- Tampoco se hereda su vocabulario: la entidad principal es la **ficha médica**,
  no la "novedad".
- Del sistema existente se toman únicamente **los valores**: el catálogo de grupos
  y detalles de enfermedad, y el catálogo de estados. Se importan **una sola vez**
  al esquema nuevo.
- **MUST NOT** existir sincronización posterior, lectura en vivo de las tablas
  viejas, adaptadores legacy, ni capa de mapeo hacia el sistema anterior.
- Si se opta por importar registros reales sin filtrar, esos registros pueden
  violar reglas que hoy son obligatorias porque se cargaron cuando esas reglas no
  existían. En ese caso las validaciones se aplican **al escribir y nunca al
  leer**: un registro inconsistente se muestra completo, se señala en la interfaz
  si corresponde, y **MUST NOT** lanzar excepción.

**Rationale**: el esquema legacy codifica límites técnicos de 1996 y un
vocabulario que ya nadie interpreta bien. Copiarlo importa la deuda sin ninguno de
los datos que la justificaban. Y rechazar al leer convierte historia clínica
importada en errores 500 sobre datos que nadie va a poder corregir.

### III. Comportamiento equivalente al sistema actual

Las reglas de validación del sistema viejo son **requisitos funcionales** de este
sistema, no herencia técnica.

- Se reimplementan de forma limpia y directa, sin arrastrar la estructura del
  código original.
- Cuando una regla del sistema viejo se decide cambiar o eliminar, se documenta
  como **decisión explícita con su justificación** en la spec de la feature.
- Ninguna regla desaparece por olvido. Una regla del sistema de referencia que no
  está implementada ni documentada como descartada es un hallazgo de revisión.

**Rationale**: el sistema viejo es la única especificación de comportamiento que
existe, pero su código no es un modelo a seguir. La distinción importa: se copia
qué valida, no cómo lo valida.

### IV. El backend es la autoridad

Toda regla de negocio vive en servicios de Spring.

- Los cálculos derivados, como los **días perdidos**, se resuelven en el backend.
  El frontend los muestra, no los computa como fuente de verdad.
- React **MAY** validar para mejorar la experiencia del usuario, nunca como único
  control.
- Cada validación del frontend **MUST** tener su equivalente en el backend. Nunca
  al revés.
- Una regla que solo existe en el frontend se trata como defecto, no como decisión
  de diseño.

**Rationale**: el cliente HTTP es controlable por el usuario. Una regla que solo
corre en React no es una regla, es una sugerencia — y en un sistema con datos de
salud, la integridad no puede depender del navegador.

### V. Simplicidad proporcional

Esto es una aplicación de carga de datos sobre una pantalla, con reglas de
validación y un par de catálogos. El diseño tiene que reflejar eso.

**MUST NOT** introducirse:

- Microservicios.
- Capas de abstracción especulativas.
- Interfaces con una sola implementación puestas por si acaso.
- Patrones de integración para sistemas que no existen.
- Caché sin un problema de performance medido.

Cualquier estructura que exceda lo que el alcance requiere se justifica por
escrito en el commit, o se revierte.

**Rationale**: la complejidad agregada por anticipación al cambio es la que después
impide el cambio. Con un alcance de una pantalla, cada capa extra cuesta más de lo
que jamás va a ahorrar.

### VI. Datos de salud

El sistema maneja diagnósticos médicos de empleados identificados. En
consecuencia:

- Autenticación contra Active Directory corporativo.
- Autorización resuelta en el backend, nunca solo por ocultamiento en la interfaz.
- Toda escritura queda auditada con usuario, momento y registro afectado.
- Los logs de aplicación **MUST NOT** contener diagnósticos, observaciones
  clínicas ni descripciones de enfermedad — en ningún nivel de log, ni en mensajes
  de excepción, ni en trazas de SQL con parámetros.
- Los datos reales importados al entorno de prueba se **anonimizan**: legajos y
  nombres ficticios. Las fechas, los estados y los códigos de enfermedad pueden
  conservarse tal cual, que es lo que hace falta para probar el comportamiento.

**Rationale**: un diagnóstico filtrado a un archivo de log sale del perímetro de
control de acceso de la aplicación y llega a operaciones, backups y agregadores de
logs, donde nadie lo autorizó. Y un entorno de desarrollo no tiene los controles
de un entorno productivo: si los datos reales llegan ahí identificados, el
incidente ya ocurrió.

### VII. Preguntar antes de suponer

Quedan decisiones abiertas del cliente. Si una tarea requiere asumir algo sobre el
comportamiento esperado, el significado de un campo o una regla de negocio, se
pregunta en vez de inventar.

- Las preguntas abiertas se registran en la spec de la feature y **MUST NOT**
  cerrarse por cuenta propia.
- Una suposición razonable que resulta equivocada acá cuesta más que la demora.

**Rationale**: no hay documentación del sistema de referencia ni acceso a quienes
lo escribieron. Una suposición no marcada es indistinguible de un requisito
confirmado seis semanas después, cuando ya hay código encima.

## Alcance Funcional, Entorno y Stack

**Alcance funcional**: una pantalla. Carga, consulta y edición de fichas médicas
por legajo, con estos campos y nada más.

| Bloque | Campos |
|---|---|
| Identificación | legajo, con apellido y nombre traídos del padrón |
| Evento | fecha, estado del paciente, in itinere, estaba en servicio, hora del accidente, atendido por servicio médico, envío de médico a domicilio, justificado |
| Fechas | fecha de citación, fecha de alta, días perdidos (calculado) |
| Clasificación | categoría de enfermedad, detalle de enfermedad |
| Texto | observaciones |
| Auditoría | quién y cuándo creó, quién y cuándo modificó |

**Entorno**: el desarrollo corre contra una base de datos propia, de prueba, con
esquema diseñado por nosotros. Contiene una parte de datos reales importados y el
resto generado para testing.

Las condiciones de despliegue en producción **todavía no están definidas**. La
empresa ya expresó una vez que su base de producción no admite cambios de esquema.
El esquema que se diseñe acá es propio del proyecto y **MUST NOT** asumirse como
definitivo ni como el destino final de los datos. Toda decisión que dependa de la
forma del despliegue productivo se marca como abierta bajo el Principio VII.

**Stack**:

- **Backend**: Java 21, Spring Boot, Spring Security, Spring JDBC. OpenAPI para la
  documentación de la API. JUnit, Mockito y Testcontainers para pruebas.
- **Frontend**: Next.js, React, TypeScript, Tailwind, shadcn/ui, React Hook Form,
  Zod.
- **Base de datos**: SQL Server, esquema propio de la aplicación.
- **Autenticación**: Active Directory vía LDAPS, u OIDC si la empresa dispone de
  un Identity Provider.

## Flujo de Desarrollo y Puertas de Calidad

Toda propuesta de cambio verifica explícitamente el cumplimiento de los siete
principios. Los puntos de control mínimos:

1. Nada fuera de la lista de campos, y nada de la lista de exclusiones del
   Principio I.
2. Cero abreviaturas legacy en base, código, API y frontend; la entidad se llama
   ficha médica.
3. Cero lectura en vivo, sincronización o adaptadores hacia el sistema viejo.
4. Cada regla del sistema de referencia está implementada, o documentada como
   descartada con justificación.
5. Cada validación del frontend tiene su par en el backend; los días perdidos se
   calculan en el backend.
6. Ninguna abstracción sin uso concreto en el alcance actual.
7. Ningún dato clínico en logs; datos importados al entorno de prueba
   anonimizados.
8. Toda suposición sobre comportamiento no confirmado está marcada como pregunta
   abierta, no resuelta silenciosamente.

## Governance

Esta constitución **prevalece sobre cualquier otra práctica, convención o
documento** del repositorio. Ante conflicto entre este documento y cualquier otra
guía, manda este documento, y la otra guía se corrige.

**Procedimiento de enmienda**: toda modificación requiere (a) propuesta escrita con
la justificación del cambio, (b) aprobación del responsable técnico del proyecto
y, cuando la enmienda afecte a los principios I o VI, aprobación del cliente —
porque el alcance y el tratamiento de datos de salud son compromisos con la
empresa, no decisiones técnicas —, y (c) un plan de migración para el código ya
escrito que dependa del principio modificado. Las enmiendas se registran en el
Sync Impact Report al inicio de este archivo.

**Política de versionado** (semántico):

- **MAJOR**: se remueve o redefine un principio de forma incompatible hacia atrás.
- **MINOR**: se agrega un principio o una sección, o se expande materialmente una
  guía existente.
- **PATCH**: aclaraciones, redacción, correcciones sin cambio semántico.

**Revisión de cumplimiento**: cada revisión de código verifica los ocho puntos de
control de la sección anterior. Un incumplimiento detectado bloquea la integración
hasta que se corrige o se documenta como enmienda aprobada.

**Provisionalidad del despliegue**: mientras las condiciones de producción sigan
sin definirse, ninguna decisión de este documento sobre persistencia o despliegue
se considera final. Cuando el cliente las defina, esta constitución se revisa
antes de escribir código que dependa de ellas.

**Version**: 2.0.0 | **Ratified**: 2026-09-08 | **Last Amended**: 2026-09-08
