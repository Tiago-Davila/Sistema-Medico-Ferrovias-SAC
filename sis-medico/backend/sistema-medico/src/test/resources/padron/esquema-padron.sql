-- Doble de prueba del padrón de empleados.
--
-- ESTO NO ES ESQUEMA NUESTRO. El padrón vive en una base separada, mantenida
-- por otro sistema, y esta aplicación solo lo consulta (FR-003b). Por eso el
-- script está en src/test/resources y no en db/migration: si estuviera entre
-- las migraciones estaríamos declarando que el padrón nos pertenece, y no nos
-- pertenece.
--
-- La tabla `empleado` de este archivo existe únicamente dentro de la base
-- `padron` del contenedor de tests, para poder ejercitar la consulta entre
-- bases por nombre de tres partes. En el esquema de la aplicación no hay ni
-- puede haber una tabla `empleado`.
--
-- Las columnas son las que data-model.md espera de la vista de solo lectura del
-- padrón. Ojo con `categoria_laboral`: en el padrón "categoría" es la categoría
-- laboral del empleado, no el grupo de enfermedad. Son dos cosas distintas y
-- nunca se llaman "categoria" a secas.

CREATE TABLE empleado (
    legajo            INT           NOT NULL,
    apellido          NVARCHAR(60)  NOT NULL,
    nombre            NVARCHAR(60)  NOT NULL,
    seccion           NVARCHAR(60)      NULL,
    categoria_laboral NVARCHAR(60)      NULL,
    CONSTRAINT PK_empleado PRIMARY KEY (legajo)
);
