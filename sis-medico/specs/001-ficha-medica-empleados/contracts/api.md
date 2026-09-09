# Contrato de API: Ficha médica de empleados

**Fecha**: 2026-09-09
**Base**: `/api`
**Formato**: JSON. Errores en `application/problem+json` (RFC 9457).

Ningún nombre del sistema viejo aparece en esta superficie (Principio II). Todos
los endpoints exigen usuario autenticado (FR-036e); cualquier operario
autenticado accede a cualquier legajo (FR-036d).

## Envoltura de escritura

**Toda** respuesta exitosa de escritura tiene la misma forma. Un único lugar donde
el frontend mira las advertencias.

```json
{
  "datos": { },
  "advertencias": [
    { "codigo": "EVENTO_ANTIGUO", "campo": "fechaEvento",
      "mensaje": "La fecha del evento tiene 62 días de antigüedad." }
  ]
}
```

`advertencias` siempre está presente; vacío si no hay ninguna. **Una advertencia
nunca cambia el código de estado**: el guardado ya ocurrió (M2). No hay
confirmación en dos pasos.

## Formato de error

```json
{
  "type": "https://ferrovias/errores/ficha-invalida",
  "title": "La ficha tiene datos que hay que corregir",
  "status": 422,
  "violaciones": [
    { "campo": "fechaAlta",       "codigo": "ALTA_ANTERIOR_AL_EVENTO",
      "mensaje": "La fecha de alta no puede ser anterior a la del evento." },
    { "campo": "detalleEnfermedad", "codigo": "DETALLE_FUERA_DE_GRUPO",
      "mensaje": "El detalle no pertenece al grupo elegido." }
  ]
}
```

`violaciones` trae **todas** las que se detectaron, no la primera (M1). Cada una
lleva el campo que la origina para que el frontend la muestre junto a su control.

| Estado | Cuándo |
|---|---|
| 200 / 201 | Éxito, con `advertencias` posiblemente no vacío |
| 204 | Eliminación |
| 401 | Sin autenticar (FR-036e) |
| 404 | Legajo o ficha inexistente |
| 409 | La ficha cambió desde que se abrió (FR-037, D2) |
| 422 | Una o más violaciones de reglas de negocio |
| 503 | Padrón externo no disponible (ver supuesto CHK040) |

### Códigos de violación

| Código | Campo | Requisito |
|---|---|---|
| `EVENTO_FUTURO` | fechaEvento | FR-007 |
| `FECHAS_FIN_EXCLUYENTES` | fechaCitacion, fechaAlta | FR-008 |
| `GRUPO_REQUERIDO` | grupoEnfermedad | FR-009 |
| `DETALLE_REQUERIDO` | detalleEnfermedad | FR-009 |
| `DETALLE_FUERA_DE_GRUPO` | detalleEnfermedad | FR-010 |
| `ALTA_SUPERA_999_DIAS` | fechaAlta | FR-011 |
| `ALTA_ANTERIOR_AL_EVENTO` | fechaAlta | FR-012 |
| `CITACION_ANTERIOR_AL_EVENTO` | fechaCitacion | FR-012b |
| `FICHA_DUPLICADA` | fechaEvento | FR-013 |
| `SOLAPAMIENTO` | fechaEvento | FR-014, FR-015 |
| `SOLAPAMIENTO_FICHA_INCOMPLETA` | fechaEvento | FR-014c, FR-014d |
| `IN_ITINERE_REQUERIDO` | inItinere | FR-016 |
| `CAMPO_REQUERIDO` | *(el que falte)* | FR-004b, FR-004c |
| `OBSERVACIONES_MUY_LARGAS` | observaciones | FR-006b |
| `LEGAJO_INEXISTENTE` | legajo | FR-002 |

Los dos códigos de solapamiento llevan datos extra para cumplir FR-015 y FR-014d:

```json
{ "campo": "fechaEvento", "codigo": "SOLAPAMIENTO_FICHA_INCOMPLETA",
  "mensaje": "Choca con la ficha del 12/04/2019, que está incompleta. Cargale una fecha de citación o de alta para poder continuar.",
  "fichaEnConflicto": { "id": 3312, "fechaEvento": "2019-04-12", "incompleta": true } }
```

### Códigos de advertencia

| Código | Requisito |
|---|---|
| `EVENTO_ANTIGUO` | FR-019 — más de 45 días. **Nunca** bloquea. |

## Endpoints

### `GET /api/empleados/{legajo}`

Confirmación de identidad (FR-001). Resuelve contra el padrón externo; no guarda
nada.

```json
{ "legajo": 4821, "apellido": "Sosa", "nombre": "Ramón",
  "seccion": "Vía y Obras", "categoriaLaboral": "Oficial" }
```

`404` si el legajo no existe (FR-002). `503` si el padrón no responde.

### `GET /api/empleados/{legajo}/fichas`

Listado del empleado. Orden por fecha de evento descendente, todas, **sin
paginado ni filtros** (FR-031b, FR-031c). Excluye eliminadas (FR-039c).

```json
[ { "id": 8842, "fechaEvento": "2026-03-03", "estadoPaciente": "ENFERMEDAD",
    "fechaCitacion": "2026-03-10", "fechaAlta": null, "diasPerdidos": null,
    "incompleta": false } ]
```

`incompleta` marca la ficha para FR-029. Se calcula al leer y **no aplica ninguna
validación** (FR-030): es una señal, no un rechazo.

### `GET /api/fichas/{id}`

Ficha completa. Devuelve el registro **tal como está almacenado**, sin topear ni
normalizar nada (FR-028).

```json
{ "id": 8842, "version": 3, "legajo": 4821,
  "fechaEvento": "2026-03-03", "estadoPaciente": "ENFERMEDAD",
  "inItinere": null, "estabaEnServicio": false, "horaAccidente": null,
  "atendidoServicioMedico": true, "envioMedicoDomicilio": false, "justificado": true,
  "fechaCitacion": "2026-03-10", "fechaAlta": null, "diasPerdidos": null,
  "grupoEnfermedad": { "id": 3, "descripcion": "Respiratorio" },
  "detalleEnfermedad": { "id": 31, "descripcion": "Faringitis" },
  "observaciones": "…",
  "incompleta": false,
  "motivosInconsistencia": [],
  "auditoria": { "creadaPor": "jperez", "creadaEn": "2026-03-03T14:02:00Z",
                 "modificadaPor": null, "modificadaEn": null } }
```

Casos de lectura tolerante que este contrato tiene que soportar:

- `diasPerdidos` **negativo** o mayor a 999 en fichas históricas (FR-020c).
- `grupoEnfermedad` con `descripcion: null` y el `id` presente, cuando el código
  quedó huérfano del catálogo (FR-028b).
- Booleanos en `null` en fichas históricas, distintos de `false` (FR-004d).
- `observaciones` de más de 500 caracteres (FR-006c).

`motivosInconsistencia` enumera por qué la ficha se marca: `SIN_CLASIFICACION`,
`CODIGO_HUERFANO`, `SIN_FECHA_FIN`, `DIAS_PERDIDOS_NEGATIVOS`,
`OBSERVACIONES_EXCEDIDAS`, `CAMPO_SIN_RESPONDER`.

### `POST /api/fichas`

Alta. `201` con la envoltura de escritura.

El cuerpo **no tiene** `diasPerdidos` ni `version`: el primero lo calcula el
backend y el segundo nace en 0 (M4). Mandar `diasPerdidos` no es un error, es
imposible: no existe el campo donde recibirlo.

### `PUT /api/fichas/{id}`

Modificación, incluida la reasignación de legajo (FR-003d). `200` con la
envoltura.

`version` es **obligatorio** en el cuerpo. Si no coincide con la almacenada,
`409` (D2). Si cambia el `legajo`, unicidad y solapamiento se evalúan contra el
empleado **de destino** (FR-003e).

### `DELETE /api/fichas/{id}`

Borrado lógico (FR-039b). `204`. Requiere `version` como parámetro de consulta,
para que una eliminación tampoco pise un cambio ajeno.

La confirmación de FR-038 es del frontend, **no un modal**: fila de confirmación
en línea, operable por teclado.

### `GET /api/enfermedades/grupos` · `GET /api/enfermedades/grupos/{id}/detalles`

Catálogos para la selección por código tipeado. Se cargan una vez al abrir la
pantalla; son pocos registros y estables.

## Lo que este contrato no tiene

Ni endpoint, ni campo, ni hueco reservado para: bloque de aseguradora, reportes,
listados, exportaciones, parte diario, novedad centralizada, consultas médicas,
seguimiento, justificación de horas, COVID, padrón de médicos, licencias,
notificaciones, restauración de fichas eliminadas, ni perfiles distintos del
operario. Si mañana entran, se agregan entonces.
