# Modelo de datos: Ficha médica de empleados

**Fecha**: 2026-09-09
**Decisiones que lo sustentan**: [research.md](research.md)

Nombres definitivos, en español completo. Ninguna abreviatura del sistema viejo
aparece acá (Principio II de la constitución).

## Regla de nulabilidad

**El esquema es deliberadamente permisivo. Las reglas viven en el servicio.**

Toda columna que pueda faltar en una ficha histórica se declara `NULL`, aunque la
regla de negocio actual la exija al guardar. Esto incluye `grupo_enfermedad_id` y
`detalle_enfermedad_id`, que las fichas anteriores a 2016 no tienen.

Prohibido, sin excepción:

- `NOT NULL` en columnas que el histórico puede no traer.
- `CHECK` que reproduzca una regla de negocio.
- Clave foránea contra el catálogo de enfermedades (FR-028c admite códigos
  huérfanos).
- Anotaciones de validación sobre el modelo de persistencia.

La obligatoriedad de FR-004b la hace cumplir `ValidadorFichaMedica`, y solo al
escribir.

## `ficha_medica`

```sql
CREATE TABLE ficha_medica (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    version                 BIGINT         NOT NULL CONSTRAINT DF_ficha_version DEFAULT 0,

    legajo                  INT            NOT NULL,
    fecha_evento            DATE           NOT NULL,

    estado_paciente         CHAR(1)            NULL,
    in_itinere              BIT                NULL,
    estaba_en_servicio      BIT                NULL,
    hora_accidente          TINYINT            NULL,
    atendido_servicio_medico BIT               NULL,
    envio_medico_domicilio  BIT                NULL,
    justificado             BIT                NULL,

    fecha_citacion          DATE               NULL,
    fecha_alta              DATE               NULL,
    dias_perdidos           AS DATEDIFF(day, fecha_evento, fecha_alta),

    grupo_enfermedad_id     INT                NULL,
    detalle_enfermedad_id   INT                NULL,
    observaciones           NVARCHAR(MAX)      NULL,

    creada_por              NVARCHAR(100)  NOT NULL,
    creada_en               DATETIME2(0)   NOT NULL,
    modificada_por          NVARCHAR(100)      NULL,
    modificada_en           DATETIME2(0)       NULL,
    eliminada_por           NVARCHAR(100)      NULL,
    eliminada_en            DATETIME2(0)       NULL,

    CONSTRAINT PK_ficha_medica PRIMARY KEY (id)
);

-- D1: unicidad solo entre fichas vivas (FR-013 + FR-039)
CREATE UNIQUE INDEX UX_ficha_legajo_fecha
    ON ficha_medica (legajo, fecha_evento)
    WHERE eliminada_en IS NULL;

-- Listado por empleado, descendente (FR-031b), excluyendo eliminadas (FR-039c)
CREATE INDEX IX_ficha_legajo_fecha_desc
    ON ficha_medica (legajo, fecha_evento DESC)
    WHERE eliminada_en IS NULL;
```

### Notas por columna

| Columna | Por qué así |
|---|---|
| `id` | Sustituta. El legajo es editable (FR-003d), así que la clave no puede depender de él. Ver D1. |
| `version` | Bloqueo optimista (D2). La incrementa el servicio, no el motor. |
| `legajo` | `INT` por FR-004f, sin ceros a la izquierda. Sin FK: el padrón está en otra base (D3). |
| `fecha_evento` | `NOT NULL` porque forma la identidad de negocio y ninguna ficha histórica carece de ella. |
| `estado_paciente` | `CHAR(1)`, `NULL` por tolerancia de lectura. Valores válidos al escribir: `A` accidentado, `E` enfermedad. Sin `CHECK`: la restricción vive en el servicio. |
| `in_itinere` | `BIT NULL`. Es el único booleano con tres estados legítimos (FR-004e). |
| `estaba_en_servicio`, `atendido_servicio_medico`, `envio_medico_domicilio`, `justificado` | `BIT NULL` en la base por FR-004d, aunque FR-004c los exija respondidos al guardar. La nulabilidad es para el histórico, no para las fichas nuevas. |
| `hora_accidente` | `TINYINT` (0–23). Solo la hora, sin minutos (FR-004g). |
| `dias_perdidos` | Columna calculada no persistida (D4). Consultable por SQL, imposible de desincronizar, y devuelve negativos y valores mayores a 999 tal como FR-020 y FR-020c exigen. |
| `grupo_enfermedad_id`, `detalle_enfermedad_id` | `NULL` y **sin FK**. Las fichas previas a 2016 no los tienen, y FR-028c admite códigos huérfanos. |
| `observaciones` | `NVARCHAR(MAX)` (D6). El tope de 500 lo aplica el servicio. |
| `eliminada_por` / `eliminada_en` | Borrado lógico (FR-039b). `eliminada_en IS NULL` es el predicado de "viva" en todo el sistema. |

## `ficha_medica_auditoria`

```sql
CREATE TABLE ficha_medica_auditoria (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    ficha_id    BIGINT        NOT NULL,
    operacion   VARCHAR(12)   NOT NULL,   -- ALTA | MODIFICACION | BAJA
    usuario     NVARCHAR(100) NOT NULL,
    momento     DATETIME2(0)  NOT NULL,   -- UTC

    CONSTRAINT PK_ficha_auditoria PRIMARY KEY (id),
    CONSTRAINT FK_ficha_auditoria_ficha FOREIGN KEY (ficha_id)
        REFERENCES ficha_medica (id)
);

CREATE INDEX IX_ficha_auditoria_ficha ON ficha_medica_auditoria (ficha_id, momento);
```

Solo inserción. Un renglón por escritura, en la misma transacción que la
escritura (D5). **No guarda el contenido de la ficha**: quién, cuándo, sobre qué
registro y qué operación. Acá sí hay FK, porque ambas tablas viven en la base
propia.

## Catálogos de enfermedad

```sql
CREATE TABLE grupo_enfermedad (
    id          INT           NOT NULL,
    descripcion NVARCHAR(100) NOT NULL,
    CONSTRAINT PK_grupo_enfermedad PRIMARY KEY (id)
);

CREATE TABLE detalle_enfermedad (
    id                  INT           NOT NULL,
    grupo_enfermedad_id INT           NOT NULL,
    descripcion         NVARCHAR(100) NOT NULL,
    CONSTRAINT PK_detalle_enfermedad PRIMARY KEY (id),
    CONSTRAINT FK_detalle_grupo FOREIGN KEY (grupo_enfermedad_id)
        REFERENCES grupo_enfermedad (id)
);
```

Datos semilla versionados con el esquema. La FK entre detalle y grupo sí existe:
es interna al catálogo y la controlamos nosotros. Lo que no existe es la FK
*desde la ficha hacia el catálogo*, que es la que rompería la lectura tolerante.

FR-010 —el detalle tiene que pertenecer al grupo— se valida en el servicio contra
esta tabla, al escribir.

## Empleado

**No hay tabla.** Vive en la base del padrón, fuera de esta aplicación (FR-003b),
y se consulta de solo lectura. La ficha guarda el `legajo` y nada más: apellido,
nombre, sección y categoría laboral se resuelven al consultar (FR-003c).

Vista de solo lectura esperada en la base externa:

| Campo | Uso |
|---|---|
| legajo | clave de búsqueda |
| apellido, nombre | confirmación de identidad (FR-001) |
| sección, categoría laboral | contexto en pantalla |

## Estados de una ficha

```
        ┌──────────┐  editar   ┌──────────┐
  alta →│  viva    │──────────→│  viva    │
        └────┬─────┘           └────┬─────┘
             │ eliminar             │
             ▼                      │
        ┌──────────┐                │
        │ eliminada│←───────────────┘
        └──────────┘
```

- **Viva**: `eliminada_en IS NULL`. Participa de unicidad, solapamiento y
  listados.
- **Eliminada**: `eliminada_en IS NOT NULL`. Invisible al operario (FR-039c),
  fuera de toda validación (FR-039), conservada y recuperable (FR-039b).

No hay transición de vuelta desde la aplicación: FR-039d excluye la pantalla de
restauración del alcance.

## Consulta de solapamiento (FR-014, FR-014b, FR-014c)

El período de una ficha es `[fecha_evento, fin)`, con el extremo derecho
**excluido** (FR-014b):

```
fin = COALESCE(fecha_citacion, fecha_alta, <hoy>)
```

El `<hoy>` cubre las fichas históricas sin ninguna de las dos fechas, cuyo período
se trata como abierto hasta el día actual (FR-014c). Lo provee el servicio desde
el `Clock` de D7, nunca `GETDATE()`, para que el reloj sea uno solo y los tests
puedan fijarlo.

Dos fichas se solapan si y solo si el inicio de cada una es **estrictamente
anterior** al fin de la otra:

```sql
SELECT id, fecha_evento
FROM ficha_medica
WHERE legajo = :legajo
  AND eliminada_en IS NULL
  AND id <> :idActual            -- al editar, no chocar consigo misma
  AND :inicioNueva < COALESCE(fecha_citacion, fecha_alta, :hoy)
  AND fecha_evento < :finNueva;
```

Al cambiar el legajo (FR-003e), `:legajo` es el **de destino**.

## Trazabilidad campo a campo

| Campo del spec | Columna | Requisito |
|---|---|---|
| legajo del empleado | `legajo` | FR-004b, FR-004f |
| fecha del evento | `fecha_evento` | FR-004b |
| estado del paciente | `estado_paciente` | FR-005 |
| in itinere | `in_itinere` | FR-004e, FR-016 |
| estaba en servicio | `estaba_en_servicio` | FR-004c, FR-024 |
| hora del accidente | `hora_accidente` | FR-004g |
| atendido por servicio médico | `atendido_servicio_medico` | FR-004b |
| envío de médico a domicilio | `envio_medico_domicilio` | FR-004c, FR-023 |
| justificado | `justificado` | FR-004b |
| fecha de citación | `fecha_citacion` | FR-008, FR-012b |
| fecha de alta | `fecha_alta` | FR-008, FR-012 |
| días perdidos | `dias_perdidos` (calculada) | FR-006, FR-020, FR-020b |
| grupo de enfermedad | `grupo_enfermedad_id` | FR-009, FR-028c |
| detalle de enfermedad | `detalle_enfermedad_id` | FR-010, FR-028c |
| observaciones | `observaciones` | FR-006b, FR-006c |
| auditoría | columnas + `ficha_medica_auditoria` | FR-032, FR-033 |
