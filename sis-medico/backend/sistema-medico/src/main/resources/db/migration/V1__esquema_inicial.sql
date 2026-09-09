-- V1: esquema inicial de la ficha médica.
--
-- REGLA DE NULABILIDAD (data-model.md): el esquema es deliberadamente permisivo.
-- Toda columna que pueda faltar en una ficha histórica se declara NULL, aunque
-- la regla de negocio de hoy la exija al guardar. La obligatoriedad de FR-004b
-- la hace cumplir ValidadorFichaMedica, y solo al escribir.
--
-- Prohibido en este archivo, sin excepción:
--   * NOT NULL en columnas que el histórico puede no traer.
--   * CHECK que reproduzca una regla de negocio.
--   * Clave foránea contra el catálogo de enfermedades (FR-028c admite códigos
--     huérfanos).
--
-- Si una restricción de acá empieza a rechazar fichas históricas al importarlas,
-- la restricción está de más: la validación va en el servicio.

-- Los dos índices de esta migración son filtrados (WHERE eliminada_en IS NULL) y
-- SQL Server exige QUOTED_IDENTIFIER ON para crearlos. El driver JDBC lo deja en
-- ON, pero sqlcmd lo deja en OFF y el CREATE INDEX falla con el error 1934, que
-- no menciona la causa real. Se fija acá para que el script se pueda aplicar con
-- cualquier herramienta.
SET QUOTED_IDENTIFIER ON;

CREATE TABLE ficha_medica (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    -- D2: bloqueo optimista. La incrementa el servicio, no el motor.
    version                 BIGINT         NOT NULL CONSTRAINT DF_ficha_version DEFAULT 0,

    -- FR-004f: entero, sin ceros a la izquierda. Sin FK: el padrón vive en otra
    -- base y SQL Server no admite FK entre bases (D3).
    legajo                  INT            NOT NULL,
    -- NOT NULL porque forma la identidad de negocio y ninguna ficha histórica
    -- carece de ella.
    fecha_evento            DATE           NOT NULL,

    -- 'A' accidentado, 'E' enfermedad (FR-005). Sin CHECK: la restricción vive
    -- en el servicio, para que el histórico se lea igual.
    estado_paciente         CHAR(1)            NULL,
    -- FR-004e: el único booleano con tres estados legítimos.
    in_itinere              BIT                NULL,
    estaba_en_servicio      BIT                NULL,
    -- FR-004g: solo la hora, sin minutos. 0-23.
    hora_accidente          TINYINT            NULL,
    -- Los cuatro de FR-004c son obligatorios al guardar y NULL en la base por
    -- FR-004d: la nulabilidad es para el histórico, no para las fichas nuevas.
    atendido_servicio_medico BIT               NULL,
    envio_medico_domicilio  BIT                NULL,
    justificado             BIT                NULL,

    fecha_citacion          DATE               NULL,
    fecha_alta              DATE               NULL,
    -- D4: columna calculada no persistida. Consultable por SQL e imposible de
    -- desincronizar de las fechas. Devuelve negativos y valores mayores a 999
    -- tal cual, como exigen FR-020 y FR-020c: el tope es de escritura (FR-011).
    dias_perdidos           AS DATEDIFF(day, fecha_evento, fecha_alta),

    -- NULL y SIN FK contra el catálogo. Las fichas previas a 2016 no los tienen
    -- y FR-028c admite códigos huérfanos.
    grupo_enfermedad_id     INT                NULL,
    detalle_enfermedad_id   INT                NULL,
    -- D6: el tope de 500 de FR-006b lo aplica el servicio. NVARCHAR(MAX) porque
    -- todavía no se midió el largo máximo real del histórico (T074) y truncar
    -- datos clínicos es irreversible.
    observaciones           NVARCHAR(MAX)      NULL,

    -- FR-032: quién y cuándo creó, quién y cuándo modificó por última vez.
    creada_por              NVARCHAR(100)  NOT NULL,
    creada_en               DATETIME2(0)   NOT NULL,
    modificada_por          NVARCHAR(100)      NULL,
    modificada_en           DATETIME2(0)       NULL,
    -- FR-039b: borrado lógico. `eliminada_en IS NULL` es el predicado de "viva"
    -- en todo el sistema.
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

-- D5: historial de escrituras. Las columnas de auditoría de ficha_medica
-- cumplen FR-032 pero no FR-033: la segunda modificación pisa el rastro de la
-- primera. Esta tabla es de solo inserción, un renglón por escritura, en la
-- misma transacción que la escritura.
--
-- NO guarda el contenido de la ficha: quién, cuándo, sobre qué registro y qué
-- operación. Un historial con diagnósticos adentro sería otra copia de datos
-- clínicos que habría que proteger.
CREATE TABLE ficha_medica_auditoria (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    ficha_id    BIGINT        NOT NULL,
    operacion   VARCHAR(12)   NOT NULL,   -- ALTA | MODIFICACION | BAJA
    usuario     NVARCHAR(100) NOT NULL,
    momento     DATETIME2(0)  NOT NULL,   -- UTC

    CONSTRAINT PK_ficha_auditoria PRIMARY KEY (id),
    -- Acá sí hay FK: ambas tablas viven en la base propia.
    CONSTRAINT FK_ficha_auditoria_ficha FOREIGN KEY (ficha_id)
        REFERENCES ficha_medica (id)
);

CREATE INDEX IX_ficha_auditoria_ficha ON ficha_medica_auditoria (ficha_id, momento);
