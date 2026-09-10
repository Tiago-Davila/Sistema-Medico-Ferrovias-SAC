# Quickstart: Ficha médica de empleados

**Fecha**: 2026-09-09

Cómo levantar la aplicación y comprobar que la feature funciona de punta a punta.
El diseño está en [plan.md](plan.md); las decisiones en [research.md](research.md).

## Prerrequisitos

- JDK 21
- Docker en marcha (para SQL Server, local y de tests)
- Node para el frontend
- Acceso de red al padrón de empleados, o su sustituto local (ver abajo)

## Puesta en marcha

Levantar SQL Server local y aplicar el esquema:

```bash
docker compose up -d sqlserver
```

```bash
cd backend/sistema-medico && ./gradlew flywayMigrate
```

Arrancar el backend:

```bash
cd backend/sistema-medico && ./gradlew bootRun
```

Arrancar el frontend:

```bash
cd frontend && npm run dev
```

La documentación de la API queda en `/swagger-ui.html`.

### Configuración que no se hereda del entorno

`application.yml` tiene que fijar explícitamente, sin depender del sistema
operativo del servidor (decisión D7):

```yaml
app:
  zona-horaria: America/Argentina/Buenos_Aires
```

Y el logger de JDBC clavado, porque el arranque falla si está por debajo de
`INFO` fuera de test (mecanismo M3):

```yaml
logging:
  level:
    org.springframework.jdbc.core.JdbcTemplate: INFO
```

## Datos para trabajar

Tres cargas separadas, por diseño:

1. **Catálogos** de grupos y detalles de enfermedad: ya vienen en la migración
   `V2__catalogos_enfermedad.sql`. No hay que hacer nada.
2. **Padrón de empleados**: vive en una base externa que esta aplicación no
   administra ni copia. Para desarrollo local se usa una segunda base en el mismo
   contenedor, con una vista de solo lectura de la forma que describe
   [data-model.md](data-model.md).
3. **Fichas históricas**: se importan anonimizadas y **sin filtrar**, con el script
   de importación. Tienen que entrar las inconsistentes, porque son las que
   ejercitan la lectura tolerante.

El juego de datos es utilizable solo si contiene, como mínimo (FR-036b):

- fichas sin grupo ni detalle de enfermedad, anteriores a 2016
- fichas sin ninguna de las dos fechas de fin
- alguna ficha con más de 999 días entre evento y alta
- alguna ficha con fecha de alta anterior a la del evento
- alguna ficha con un código de enfermedad que no está en el catálogo

Sin esos casos, media docena de requisitos quedan sin poder verificarse.

## Pruebas

Reglas de negocio, sin base, segundos:

```bash
cd backend/sistema-medico && ./gradlew test
```

Persistencia, solapamiento, unicidad y concurrencia, con SQL Server real:

```bash
cd backend/sistema-medico && ./gradlew integrationTest
```

La imagen de SQL Server es pesada y tarda en arrancar. Para que se pague una sola
vez por sesión de trabajo, habilitar el reuso de contenedores en
`~/.testcontainers.properties`:

```bash
echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
```

La pantalla, con un runner propio:

```bash
cd frontend && npm test
```

Son pocos tests y a propósito. Casi toda regla de esta feature vive en el backend
(FR-018) y se prueba donde se aplica; acá se prueba lo único que no se puede ver
desde Java: que la confirmación de borrado se pida y se opere con el teclado sin
ser un modal (US4-1, FR-038).

La cobertura criterio por criterio está en
[checklists/cobertura.md](checklists/cobertura.md).

## Validación de punta a punta

Recorrido mínimo que demuestra que la feature anda. Cada paso apunta al criterio
que cubre; el mapeo completo está en [plan.md](plan.md).

### 1. Identificar al empleado

Buscar un legajo que exista. Tienen que aparecer apellido y nombre resueltos
contra el padrón, sin haberse guardado nada. Buscar después uno inexistente: se
informa y la carga no se habilita. *(US1-1, US1-2)*

### 2. Cargar una ficha, entera con el teclado

Sin tocar el mouse en ningún paso: tabular por los campos, elegir grupo y detalle
**tipeando el código**, guardar con el atajo. Al terminar, el foco vuelve al
buscador de legajo, listo para la siguiente. *(US1-3, FR-040, FR-041, SC-001)*

### 3. Comprobar que las violaciones vienen todas juntas

Guardar una ficha con varios errores a la vez: fecha de evento futura, sin
ninguna de las dos fechas de fin, sin grupo y sin detalle. La respuesta tiene que
ser **422 con las cuatro violaciones**, cada una con su campo, no la primera
sola. *(M1, US1-4 a US1-7)*

### 4. Comprobar que una advertencia no es un error

Cargar una ficha con más de 45 días de antigüedad. Tiene que **guardarse**, con
`201`, y traer `EVENTO_ANTIGUO` en `advertencias` dentro del cuerpo. Sin diálogo,
sin segundo paso. *(M2, US1-12)*

### 5. Solapamiento, incluidos los bordes

Con una ficha del 1 al 10 de marzo: otra que se superpone se rechaza indicando
con cuál choca; otra con evento **el 10 de marzo** se acepta, porque compartir un
extremo no es solapamiento. *(US1-14, US1-15)*

### 6. Lectura tolerante

Abrir las fichas históricas del punto anterior. Todas se muestran enteras y
marcadas como incompletas; ninguna produce error. En particular:

- días perdidos **negativos** y **mayores a 999**, sin topear
- código de enfermedad huérfano mostrado sin descripción
- booleanos vacíos, distintos de "no"
- observaciones de más de 500 caracteres, completas

*(US2-2, US2-3, SC-002, SC-006)*

### 7. Registrar el alta

Sobre una ficha con solo citación, cargar la fecha de alta y limpiar la citación.
Los días perdidos se recalculan solos y queda registrado quién modificó y cuándo.
*(US3-1)*

### 8. Corregir un legajo equivocado

**Se verifica contra la API, no desde la pantalla.** La reasignación existe en el
backend (FR-003d, FR-003e): al cambiar el legajo, la unicidad y el solapamiento se
evalúan contra el empleado de **destino**. La pantalla, en cambio, no expone
ningún control para cambiarlo: manda siempre el legajo de la ficha abierta, no el
que quedó tipeado en el buscador. Mover fichas clínicas de un empleado a otro como
efecto colateral de tipear otro número sería demasiado fácil de hacer sin querer.

```bash
curl -X PUT localhost:8080/api/fichas/{id} \
  -H 'Content-Type: application/json' \
  -d '{"version": 0, "legajo": <destino>, ...}'
```

La ficha pasa al empleado correcto sin eliminarse. Si el empleado de destino ya
tiene una ficha en esa fecha, o si los períodos se pisan, se rechaza con **422**.
*(US3-10, US3-11; automatizado en `ReasignacionLegajoTest`)*

**Queda como pregunta para el cliente**: si el operario tiene que poder corregir
el legajo por sí mismo desde la pantalla, hace falta un control explícito, con su
propia confirmación. No se agregó por cuenta propia.

### 9. Edición concurrente

Abrir la misma ficha en dos sesiones, guardar en las dos. La segunda tiene que dar
**409** e informar que la ficha cambió, sin pisar lo de la primera. *(US3-9,
SC-008)*

### 10. Eliminación

Eliminar una ficha con la confirmación **en línea**, sin modal. Deja de aparecer,
deja de bloquear por solapamiento, y sus datos siguen conservados. *(US4-1 a
US4-5, SC-010)*

### 11. Verificar que no se filtran datos clínicos

Esto no se comprueba a ojo: lo hace el test de fuga, que corre el alta, la
modificación y la baja con observaciones centinela, captura todo el log a nivel
`TRACE` y falla si alguna aparece. *(M3, SC-007)*

```bash
cd backend/sistema-medico && ./gradlew integrationTest --tests '*FugaDeLogsTest'
```

## Las dos mediciones que faltan

SC-001 y SC-005 son los únicos dos criterios de éxito sin test automatizado, y no
por omisión: se miden con operarios frente a la pantalla y no hay forma de
simularlos. Son las tareas T076 y T077, y **siguen pendientes**. Los resultados se
registran acá abajo cuando se hagan.

### T076 — SC-001: tiempo de carga

Cronometrar a un operario entrenado cargando una ficha completa **solo con el
teclado**, sobre al menos **10 cargas reales**, y comparar contra el tiempo que le
lleva la misma carga en la pantalla de terminal actual. El objetivo es 60 segundos
o menos, y no ser más lento que el sistema que se reemplaza.

Qué anotar por carga: segundos de punta a punta, si tuvo que tocar el mouse, y en
qué campo se trabó si se trabó. Lo último es lo más útil: si el orden de
tabulación está mal, se ve ahí y en ningún otro lado.

| Carga | Segundos (sistema nuevo) | Segundos (pantalla actual) | ¿Tocó el mouse? | Dónde se trabó |
|---|---|---|---|---|
| | | | | |

### T077 — SC-005: rechazos que el operario corrige solo

Ante un guardado rechazado, medir en qué porcentaje de los casos el operario
identifica y corrige el campo señalado **sin ayuda externa**. El objetivo es 90 %.

Conviene provocar los rechazos a propósito, uno por regla, y anotar cuáles se
entienden y cuáles no. Un mensaje que nadie entiende es un mensaje mal escrito, no
un operario mal entrenado.

| Código de violación | Casos | Corregidos sin ayuda | Qué no se entendió |
|---|---|---|---|
| | | | |

## Si algo no anda

| Síntoma | Causa probable |
|---|---|
| El arranque falla quejándose del nivel de log | El logger de `JdbcTemplate` quedó en `DEBUG` o `TRACE`. Es a propósito (M3). |
| Fecha del evento de hoy rechazada por futura | `app.zona-horaria` sin configurar y el servidor en UTC. Ver D7. |
| Un legajo no permite cargar nada | Probablemente tenga una ficha histórica sin fecha de fin. El mensaje dice cuál completar (FR-014d). |
| `integrationTest` tarda muchísimo | Falta habilitar el reuso de contenedores. |
| Un empleado nuevo no aparece | El alta de personal ocurre fuera de esta aplicación (FR-003b). |
