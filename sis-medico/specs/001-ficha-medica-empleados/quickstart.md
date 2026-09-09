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

Editar el legajo de una ficha. Pasa al empleado correcto sin eliminarse. Si el
empleado de destino ya tiene una ficha en esa fecha, se rechaza. *(US3-10,
US3-11)*

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
cd backend/sistema-medico && ./gradlew test --tests '*FugaDeLogsTest'
```

## Si algo no anda

| Síntoma | Causa probable |
|---|---|
| El arranque falla quejándose del nivel de log | El logger de `JdbcTemplate` quedó en `DEBUG` o `TRACE`. Es a propósito (M3). |
| Fecha del evento de hoy rechazada por futura | `app.zona-horaria` sin configurar y el servidor en UTC. Ver D7. |
| Un legajo no permite cargar nada | Probablemente tenga una ficha histórica sin fecha de fin. El mensaje dice cuál completar (FR-014d). |
| `integrationTest` tarda muchísimo | Falta habilitar el reuso de contenedores. |
| Un empleado nuevo no aparece | El alta de personal ocurre fuera de esta aplicación (FR-003b). |
