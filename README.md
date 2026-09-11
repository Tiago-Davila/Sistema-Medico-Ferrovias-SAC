# Sistema Medico Ferrovias SAC

Aplicacion web para cargar, consultar, editar y eliminar fichas medicas de empleados de Ferrovias SAC.

El proyecto reemplaza funcionalmente una sola pantalla de un sistema viejo en IdeaFix. No es una migracion linea por linea ni copia el esquema legacy: toma el comportamiento esperado y lo implementa con un modelo propio, nombres claros en espanol y reglas de negocio en el backend.

## Alcance

La pantalla trabaja con:

- identificacion por legajo, con apellido y nombre resueltos desde el padron;
- datos del evento medico: fecha, estado del paciente, in itinere, servicio, hora, atencion, envio medico y justificacion;
- fechas de citacion y alta;
- dias perdidos calculados;
- grupo y detalle de enfermedad;
- observaciones;
- auditoria de altas, modificaciones y bajas.

Queda fuera del alcance todo lo que no sea esa pantalla: ART, reportes, parte diario, consultas medicas, COVID, padron de medicos, mails automaticos y perfiles funcionales distintos del operario.

## Estructura

```text
.
├── CLAUDE.md
├── README.md
└── sis-medico/
    ├── .specify/memory/constitution.md
    ├── backend/sistema-medico/
    │   ├── build.gradle
    │   └── src/main/
    │       ├── java/com/ferrovias/sismedico/
    │       │   ├── controllers/
    │       │   ├── comun/
    │       │   ├── dtos/
    │       │   ├── exceptions/
    │       │   ├── models/
    │       │   ├── repositories/
    │       │   └── service/
    │       └── resources/
    │           ├── application.yml
    │           ├── application-local.yml
    │           └── db/migration/
    ├── frontend/
    │   ├── AGENTS.md
    │   ├── app/
    │   ├── components/
    │   ├── lib/
    │   └── package.json
    └── specs/001-ficha-medica-empleados/
```

## Backend

Stack principal:

- Java 21;
- Spring Boot;
- Spring Security;
- Spring JDBC con `JdbcTemplate`;
- Flyway;
- SQL Server;
- OpenAPI por springdoc;
- JUnit, ArchUnit y Testcontainers para pruebas.

Capas:

- `controllers/`: endpoints REST.
- `service/`: reglas de negocio, validaciones, calculo de dias perdidos, solapamiento y auditoria.
- `repositories/`: acceso a SQL Server con `JdbcTemplate`.
- `models/`: entidades y value objects del dominio.
- `dtos/`: formas de entrada y salida de la API.
- `exceptions/`: errores de dominio.
- `comun/`: seguridad, reloj, logs y manejo global de errores.

Las migraciones estan en:

```text
sis-medico/backend/sistema-medico/src/main/resources/db/migration/
```

Tablas propias de la aplicacion:

- `ficha_medica`
- `ficha_medica_auditoria`
- `grupo_enfermedad`
- `detalle_enfermedad`

El padron no pertenece a esta aplicacion. Se consulta como base externa mediante `app.padron.esquema`, por defecto `padron.dbo`.

## Frontend

Stack principal:

- Next.js;
- React;
- TypeScript;
- Tailwind;
- React Hook Form;
- Zod;
- Vitest.

La unica pantalla del sistema es:

```text
/fichas
```

La raiz `/` redirige a `/fichas`.

El frontend consume `/api/*`. En desarrollo existe un proxy en:

```text
sis-medico/frontend/app/api/[...path]/route.ts
```

Ese proxy reenvia al backend y puede agregar Basic Auth local con:

- `BACKEND_API_BASE`
- `BACKEND_DEV_USER`
- `BACKEND_DEV_PASS`

## Rutas de la API

Todas las rutas estan bajo `/api`.

| Metodo | Ruta | Uso |
|---|---|---|
| `GET` | `/api/empleados/{legajo}` | Buscar empleado en el padron. |
| `GET` | `/api/empleados/{legajo}/fichas` | Listar fichas vivas del empleado. |
| `GET` | `/api/fichas/{id}` | Abrir una ficha completa. |
| `POST` | `/api/fichas` | Crear una ficha. |
| `PUT` | `/api/fichas/{id}` | Modificar una ficha. |
| `DELETE` | `/api/fichas/{id}?version={version}` | Baja logica de una ficha. |
| `GET` | `/api/enfermedades/grupos` | Listar grupos de enfermedad. |
| `GET` | `/api/enfermedades/grupos/{grupoId}/detalles` | Listar detalles de un grupo. |

## Configuracion local

`application.yml` no trae credenciales ni base por defecto. La aplicacion falla al arrancar si no se le indica una conexion SQL Server explicita.

Variables necesarias para el backend:

```powershell
$env:SISMEDICO_DB_URL = "jdbc:sqlserver://localhost:14333;databaseName=sismedico;encrypt=true;trustServerCertificate=true"
$env:SISMEDICO_DB_USUARIO = "sa"
$env:SISMEDICO_DB_CLAVE = "TuClaveLocal123!"
$env:SPRING_PROFILES_ACTIVE = "local"
```

El perfil `local` habilita el usuario de desarrollo:

```text
usuario: operario
clave: local-dev-only
```

Para el frontend, si el backend corre en el puerto default de Spring Boot (`8080`), configurar:

```powershell
$env:BACKEND_API_BASE = "http://localhost:8080/api"
$env:BACKEND_DEV_USER = "operario"
$env:BACKEND_DEV_PASS = "local-dev-only"
```

## Levantar en desarrollo

Backend:

```powershell
cd sis-medico/backend/sistema-medico
.\gradlew.bat bootRun
```

Frontend:

```powershell
cd sis-medico/frontend
npm run dev
```

Pruebas rapidas del backend, sin Docker:

```powershell
cd sis-medico/backend/sistema-medico
.\gradlew.bat test
```

Pruebas de integracion con SQL Server por Testcontainers:

```powershell
cd sis-medico/backend/sistema-medico
.\gradlew.bat integrationTest
```

Frontend:

```powershell
cd sis-medico/frontend
npm test
npm run typecheck
npm run lint
```

## Ver la base con SSMS

SSMS no es un servidor: es un cliente grafico. Para ver inserts, deletes, tablas o importar datos, siempre necesita conectarse a una instancia real de SQL Server.

Opciones locales:

1. Instalar SQL Server Developer, Express o LocalDB.
2. Levantar SQL Server en Docker.
3. Conectarse al contenedor que levantan los tests de integracion mientras esta vivo.

Una forma simple con Docker:

```powershell
docker run --name sismedico-sql `
  -e "ACCEPT_EULA=Y" `
  -e "MSSQL_SA_PASSWORD=TuClaveLocal123!" `
  -p 14333:1433 `
  -d mcr.microsoft.com/mssql/server:2022-latest
```

Despues, en SSMS:

- Server type: `Database Engine`
- Server name: `localhost,14333`
- Authentication: `SQL Server Authentication`
- Login: `sa`
- Password: `TuClaveLocal123!`
- Trust server certificate: activado, si SSMS lo pide.

Crear las dos bases:

```sql
CREATE DATABASE sismedico;
CREATE DATABASE padron;
```

Crear un padron minimo para desarrollo:

```sql
USE padron;

CREATE TABLE dbo.empleado (
    legajo            INT           NOT NULL,
    apellido          NVARCHAR(60)  NOT NULL,
    nombre            NVARCHAR(60)  NOT NULL,
    seccion           NVARCHAR(60)      NULL,
    categoria_laboral NVARCHAR(60)      NULL,
    CONSTRAINT PK_empleado PRIMARY KEY (legajo)
);

INSERT INTO dbo.empleado (legajo, apellido, nombre, seccion, categoria_laboral)
VALUES (1001, N'Perez', N'Ana', N'Trafico', N'Operaria');
```

Al arrancar el backend contra `sismedico`, Flyway crea las tablas propias de la aplicacion. Desde SSMS se pueden consultar, por ejemplo:

```sql
USE sismedico;

SELECT * FROM dbo.ficha_medica;
SELECT * FROM dbo.ficha_medica_auditoria;
SELECT * FROM dbo.grupo_enfermedad;
SELECT * FROM dbo.detalle_enfermedad;
```

## Agentes

Los agentes que trabajen en este repositorio deben leer, en este orden:

1. `sis-medico/.specify/memory/constitution.md`: autoridad del proyecto.
2. `CLAUDE.md`: guia operativa del repositorio.
3. `sis-medico/frontend/AGENTS.md`: reglas especificas generadas por Next para el frontend.

Reglas importantes para agentes:

- no agregar funcionalidades fuera del alcance cerrado;
- no copiar nombres ni estructura legacy;
- mantener las reglas de negocio en el backend;
- no filtrar datos clinicos en logs;
- no cerrar preguntas abiertas del cliente por cuenta propia;
- no tocar cambios no relacionados del working tree.

## Documentacion funcional

La especificacion viva de la feature esta en:

```text
sis-medico/specs/001-ficha-medica-empleados/
```

Archivos utiles:

- `spec.md`: requerimientos funcionales.
- `plan.md`: diseno de implementacion.
- `data-model.md`: modelo de datos.
- `contracts/api.md`: contrato esperado de API.
- `quickstart.md`: recorrido funcional y pruebas de punta a punta.
- `tasks.md`: tareas y pendientes confirmados.
