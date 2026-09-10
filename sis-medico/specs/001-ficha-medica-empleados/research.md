# Research: Ficha médica de empleados

**Fecha**: 2026-09-09
**Spec**: [spec.md](spec.md)

El stack está fijado por la constitución y no se vuelve a elegir. Este documento
resuelve las decisiones abiertas y deja constancia de las alternativas
descartadas.

## D1 — Clave primaria de la ficha médica

**Decisión**: clave sustituta `id BIGINT IDENTITY`, con índice único **filtrado**
sobre `(legajo, fecha_evento) WHERE eliminada_en IS NULL`.

**Rationale**: la clave natural quedó descartada por dos hechos del spec, no por
preferencia de estilo.

1. **FR-003d hace editable el legajo.** Una ficha cargada con el legajo
   equivocado se corrige editándola. Con clave natural, corregir el legajo
   *muta la clave primaria*: la URL `/api/fichas/4821/2026-03-03` deja de
   existir y pasa a ser otra, las referencias de auditoría apuntan a un registro
   que ya no está, y el operario que tenía la ficha abierta pierde el hilo. Con
   clave sustituta, `/api/fichas/8842` sobrevive al cambio de legajo.
2. **FR-039 restringe la unicidad a las fichas no eliminadas.** Una clave
   primaria no puede expresar "único salvo las borradas". Un índice único
   filtrado sí, y SQL Server lo soporta de forma nativa.

Además el control de concurrencia de D2 necesita una identidad estable e
inmutable contra la cual comparar la versión; la clave natural no la da.

**Alternativas descartadas**:

- *Clave natural compuesta `(legajo, fecha_evento)`, como el sistema viejo*:
  incompatible con FR-003d y con FR-039, según lo anterior. Que el sistema de
  referencia la use no es argumento: la constitución dice que del sistema viejo
  se toma el comportamiento, no la forma.
- *Clave sustituta con `UNIQUE` común sobre el par*: rompe apenas se elimina una
  ficha y se vuelve a cargar la misma combinación, caso que el borde de
  Edge Cases admite explícitamente.

## D2 — Control de concurrencia

**Decisión**: bloqueo optimista con columna `version BIGINT NOT NULL`,
incrementada por la capa de servicio. Cada `UPDATE` lleva
`WHERE id = ? AND version = ?` y verifica que haya afectado exactamente una fila;
si afectó cero, se responde **409 Conflict**.

**Rationale**: FR-037 exige rechazar el segundo guardado sin descartar los
cambios ajenos, y SC-008 exige cero pérdidas silenciosas. El bloqueo pesimista
está descartado por la naturaleza del trabajo: el operario abre una ficha y puede
tardar minutos, y un lock sostenido bloquearía a los demás sin que nadie sepa por
qué. La columna es esquema, por eso se decide acá.

**Alternativas descartadas**:

- *`rowversion` de SQL Server*: lo mantiene el motor y evita olvidos, pero es
  `binary(8)`, obliga a serializar y comparar bytes en la API, y ata el esquema a
  SQL Server. La ganancia no compensa con `JdbcTemplate`, donde el `UPDATE` se
  escribe a mano igual.
- *Bloqueo pesimista (`UPDLOCK`)*: descartado por lo anterior.
- *Sin control*: viola FR-037 y SC-008.

## D3 — Integridad referencial contra el padrón de empleados

**Decisión**: **no hay clave foránea**. La existencia del legajo se verifica en la
capa de servicio contra el padrón, y su ausencia es un rechazo bloqueante
(FR-002).

**Rationale**: esto no es una elección, es una consecuencia. FR-003b establece que
el padrón vive en **otra base de datos**, mantenida por otro sistema, que esta
aplicación consulta y nunca copia. SQL Server no admite claves foráneas entre
bases: una FK solo puede referenciar una tabla de la misma base. La integridad,
entonces, solo puede vivir en el servicio.

Consecuencia que hay que aceptar de frente: si el padrón da de baja un legajo que
tiene fichas cargadas, la base queda con una referencia colgada. El sistema no lo
impide y no puede impedirlo. Al leer, FR-028 manda mostrar la ficha igual, con el
legajo sin apellido ni nombre resueltos.

**Alternativas descartadas**:

- *FK contra una copia local del padrón*: exigiría copiar el padrón, que FR-003b
  prohíbe expresamente.
- *Advertir en vez de rechazar*: contradice FR-002, que es bloqueante.

## D4 — Días perdidos: almacenados o calculados

**Decisión**: **columna calculada no persistida** en SQL Server:

```sql
dias_perdidos AS DATEDIFF(day, fecha_evento, fecha_alta)
```

**Rationale**: FR-020b ya obliga a que sea un valor derivado que nunca discrepe de
las fechas. Una columna calculada satisface eso *y* resuelve el reparo del
enunciado: sigue siendo consultable por SQL directo, porque para quien consulta es
una columna más. No se puede desincronizar, porque no se almacena.

El comportamiento cae solo, sin código:

- `fecha_alta` nula → `DATEDIFF` devuelve `NULL` → días perdidos vacíos (FR-021).
- alta anterior al evento → valor negativo, que es lo que FR-020c manda mostrar.
- diferencia mayor a 999 → valor real, sin topear, que es lo que FR-020 manda
  mostrar tras la clarificación.

El tope de 999 **no va en la columna**: es regla de escritura, la aplica FR-011 en
el servicio.

**Alternativas descartadas**:

- *Columna almacenada mantenida por la aplicación*: el riesgo que menciona el
  enunciado es real —cualquier `UPDATE` externo a las fechas la desincroniza— y
  además contradice FR-020b.
- *Cálculo solo en Java, sin columna*: cumple FR-020b pero deja el dato fuera del
  alcance de SQL, que era justamente el costo a evitar.
- *Columna calculada `PERSISTED`*: permitiría indexarla, pero nadie consulta ni
  ordena por días perdidos en esta pantalla. Sería optimización sin problema
  medido, contra el Principio V.

## D5 — Auditoría: columnas o tabla de historial

**Decisión**: **las dos cosas**. Columnas en `ficha_medica` para el último cambio
(`creada_por`, `creada_en`, `modificada_por`, `modificada_en`), más una tabla
`ficha_medica_auditoria` de solo inserción con un renglón por escritura.

**Rationale**: no es redundancia, son dos requisitos distintos.

- **FR-032** pide exactamente columnas: quién creó y quién modificó por última
  vez, que además se muestran en pantalla. Resolverlo con un `JOIN` a la tabla de
  historial en cada lectura sería más complejo, no menos.
- **FR-033** pide registrar **toda** escritura —creación, modificación y
  eliminación—. Las columnas guardan solo la última: la segunda modificación pisa
  el rastro de la primera. Con columnas solamente, FR-033 no se cumple.

Contra el Principio V de simplicidad proporcional: lo que se agrega es **una tabla
y un `INSERT` en la misma transacción**. No hay abstracción, ni interfaz, ni
capa de eventos, ni disparadores. El Principio V prohíbe complejidad especulativa,
no funcionalidad requerida, y el Principio VI trata la auditoría de escrituras
sobre datos de salud como restricción dura.

**Qué guarda el historial**: `ficha_id`, `operacion` (ALTA/MODIFICACION/BAJA),
`usuario`, `momento`. **No guarda el contenido de la ficha.** El spec pide "el
usuario responsable, el momento y el registro afectado", nada más. Guardar
copias completas duplicaría diagnósticos y observaciones en una segunda tabla sin
que ningún requisito lo pida.

**Limitación aceptada y explícita**: no se pueden reconstruir los valores
anteriores de una ficha. Se sabe *quién tocó qué y cuándo*, no *qué decía antes*.
Si el cliente necesitara reconstrucción histórica, es un cambio de alcance.

**Alternativas descartadas**:

- *Solo columnas*: incumple FR-033.
- *Solo tabla de historial*: cumple FR-033 pero encarece cada lectura de pantalla
  para obtener lo que FR-032 pide mostrar.
- *Historial con copia completa de la ficha*: duplica datos clínicos sin
  requisito que lo justifique.

## D6 — Longitud del campo de observaciones

**Decisión**: columna `NVARCHAR(MAX)`, con el límite de **500 caracteres aplicado
en la capa de servicio** al guardar.

**Rationale**: es la única combinación que satisface los dos requisitos a la vez.
FR-006b fija 500 al escribir; FR-006c prohíbe truncar las observaciones
históricas, que pueden ser más largas y cuya longitud máxima real **no
conocemos**. Cualquier límite fijo en la columna corre el riesgo de truncar datos
clínicos en la importación, de forma silenciosa e irreversible.

`NVARCHAR(MAX)` almacena fuera de fila solo cuando el valor supera 8000 bytes, así
que para observaciones de 500 caracteres el costo es nulo. Con este volumen y una
sola pantalla, no hay contrapartida medible.

**Alternativas descartadas**:

- *`NVARCHAR(500)`*: trunca el histórico y viola FR-006c.
- *`NVARCHAR(4000)`*: probablemente alcance, pero apostar sin haber medido el dato
  viejo es exactamente el riesgo que FR-006c busca evitar.
- *Medir primero el máximo legacy y fijar el bound*: es lo correcto si el dato
  estuviera disponible hoy. Queda anotado en el plan como verificación durante la
  importación; si el máximo resultara chico, achicar la columna después es una
  migración trivial, mientras que recuperar texto truncado es imposible.

### Medición sobre el juego de datos disponible — 2026-09-10 (T074)

Medido sobre `fichas-historicas.csv`, las 670 fichas reales anonimizadas:

| Métrica | Valor |
|---|---|
| Largo máximo | **70 caracteres** |
| Promedio | 15,9 caracteres |
| Fichas sin observaciones | 2 de 670 |
| Fichas de más de 60 caracteres | 4 de 670 |

**La decisión D6 no se cierra con esto, y la columna se queda en `NVARCHAR(MAX)`.**
El volcado que tenemos cubre agosto y septiembre de 2026: es un extracto reciente,
no el archivo de ochenta mil fichas con registros anteriores a 2016. Justamente las
fichas viejas —cargadas cuando no había tope— son las que pueden ser largas, y de
esas no medimos ninguna.

Lo que la medición sí dice es que **el uso actual del campo está muy por debajo de
500**: el máximo real de hoy es un séptimo del límite. Si el archivo histórico
confirmara ese orden de magnitud, achicar la columna sería una migración trivial.
Achicarla ahora, con el 100 % de la evidencia proveniente de dos meses recientes,
sería apostar sobre el tramo del que no tenemos dato.

**Qué falta para cerrar D6**: correr esta misma medición sobre el volcado del
archivo completo, cuando el cliente lo provea.

## D7 — Zona horaria de "hoy"

**Decisión**: propiedad de configuración explícita
`app.zona-horaria=America/Argentina/Buenos_Aires`, que alimenta un bean
`java.time.Clock`. Todo el código que necesita la fecha actual recibe ese `Clock`
inyectado.

**Rationale**: FR-018b exige el reloj del servidor en zona horaria de Argentina y
prohíbe el reloj del cliente. Heredar la zona del sistema operativo haría que el
resultado de FR-007 —la fecha del evento no puede ser futura— dependa de cómo esté
configurado el servidor, que es justamente lo que el enunciado quiere evitar. Una
ficha cargada a las 21:00 hora argentina es "mañana" en UTC: sin zona explícita,
FR-007 rechazaría cargas legítimas al final del día.

**Cómo se garantiza, sin depender de la disciplina**: un test de ArchUnit prohíbe
`LocalDate.now()`, `LocalDateTime.now()` e `Instant.now()` sin argumento en todo
el código de producción, salvo en la clase de configuración del reloj. El test
falla la compilación del módulo si alguien los usa.

**Alternativas descartadas**:

- *Zona heredada del sistema operativo*: prohibido por el enunciado, y frágil ante
  un cambio de servidor.
- *Guardar todo en UTC y convertir al mostrar*: las fechas del dominio son fechas
  civiles sin hora (`LocalDate`), no instantes. Convertirlas introduce el problema
  que se quiere evitar. Los timestamps de auditoría sí son instantes y se guardan
  como `datetime2` en UTC.

## Mecanismos transversales

### M1 — Validación acumulativa

El servicio no lanza en la primera violación. `ValidadorFichaMedica` recorre todas
las reglas y devuelve `List<Violacion>` con `(campo, codigo, mensaje)`. Solo si la
lista no queda vacía se lanza `FichaInvalidaException`, que la transporta entera.
El manejador global la traduce a **422** con todas las violaciones en el cuerpo.

Un test unitario carga una ficha con siete errores simultáneos y verifica que la
respuesta traiga las siete, no la primera. Es la garantía de que nadie introduzca
un `return` temprano.

### M2 — Advertencias contra errores

**Decisión**: guardado en un solo paso. La respuesta **200/201** trae
`advertencias: []` en el cuerpo. **No hay confirmación en dos pasos.**

**Rationale**: la confirmación en dos pasos exige un diálogo o un segundo viaje, y
el enunciado prohíbe los modales que cortan la secuencia de carga; además pelearía
contra SC-001. La advertencia de FR-019 es informativa: el guardado ya ocurrió.
Todos los endpoints de escritura devuelven la misma envoltura, así que el frontend
tiene un único lugar donde mirar.

### M3 — Logs sin datos clínicos

Cuatro mecanismos, ninguno basado en la disciplina de quien escribe:

1. **`toString()` redactado**: el tipo `Observaciones` y los tipos de grupo y
   detalle devuelven una forma sin contenido, del estilo
   `Observaciones[longitud=123]`. Mata el camino más común, que es la
   interpolación accidental dentro de un mensaje de log.
2. **`toString()` del registro de ficha**: emite solo `id`, `legajo` y fechas.
3. **Nivel de log del JDBC clavado**: `JdbcTemplate` en `INFO` y una comprobación
   al arranque que **falla el arranque** si el logger está en `DEBUG` o `TRACE` en
   un perfil que no sea de test. El log de parámetros de SQL es la vía de fuga más
   fácil de pasar por alto.
4. **Test de fuga**: un test de integración ejecuta el alta, la modificación y la
   baja de una ficha cuyas observaciones y descripciones son centinelas únicos,
   captura *todo* el log con un appender en memoria a nivel `TRACE`, y falla si
   algún centinela aparece. Es la garantía real, y es la que verifica SC-007.

### M4 — Cálculos siempre del backend

Los DTO de entrada **no tienen** campo de días perdidos: el valor que mande el
cliente no se puede aceptar porque no existe lugar donde recibirlo. `estaba en
servicio` sí viaja, pero el servicio lo sobrescribe cuando `in itinere` está
marcado (FR-024), sin consultar lo enviado. El frontend puede anticipar ambos para
mostrarlos mientras el operario escribe; lo que se guarda es lo que calculó el
servidor.

## Incógnitas resueltas y supuestos

| Tema | Estado |
|---|---|
| Instancia del padrón externo | **Supuesto**: misma instancia de SQL Server, accesible por nombre de tres partes. Si estuviera en otra instancia hace falta un `DataSource` aparte; cambia la configuración, no el diseño. Verificar antes de codificar. |
| Tipo del legajo en el padrón | **Supuesto**: entero (FR-004f). La autoridad es la base externa. Si guardara texto con relleno, FR-004f queda mal y la columna pasa a texto. Verificar antes de codificar. |
| ¿El padrón se importa o se consulta? | **Resuelto el 2026-09-09: se consulta en la base externa, no se importa.** Confirma FR-003b y FR-003c. No hay tabla `empleado`; D3 queda sin clave foránea. Alternativa descartada: importarlo una vez, que habría creado tabla propia y hecho posible la FK. |
| Padrón no disponible | **Abierto** (CHK040). El plan asume degradación: no se crea ni edita, la consulta sigue andando sin apellido ni nombre. |
| Máximo real de observaciones en el dato viejo | **Medido parcialmente el 2026-09-10** (T074): 70 caracteres sobre las 670 fichas reales disponibles, que son de 2026. Sigue **abierto** para el archivo anterior a 2016, que es el tramo que puede ser largo. No bloquea: D6 eligió `NVARCHAR(MAX)`. |
| Cuántos legajos bloquea FR-014c | **Medido parcialmente el 2026-09-10** (T075): 0 de 421 legajos en el extracto disponible. Sigue **abierto** para el archivo completo. Ver la sección de más abajo. |
| Herramienta de migración de esquema | **Decisión**: Flyway. La base es propia y admite DDL libre; versionar el esquema junto al código es lo que permite que los catálogos viajen como datos semilla. No estaba en la lista de stack de la constitución, que enumera el stack de aplicación, no el utillaje. |

## Cuántos legajos quedan bloqueados por FR-014c — medición del 2026-09-10 (T075)

Una ficha sin ninguna de las dos fechas de fin tiene período abierto hasta hoy, y
eso bloquea **toda** carga posterior de ese legajo hasta que alguien la complete
(FR-014c). Es la consecuencia más dura que el cliente aceptó, y hay que saber a
cuántos empleados alcanza antes de la puesta en marcha: si son muchos, el arranque
del sistema queda dominado por esa corrección y no por la carga normal.

| Origen | Fichas sin fecha de fin | Legajos alcanzados |
|---|---|---|
| `fichas-historicas.csv` (670 reales, 421 legajos) | **0** | **0** |
| `fichas-construidas.csv` (16 fabricadas, 12 legajos) | 2 | 2 (990003, 990004) |

**Las dos del archivo de fichas construidas no cuentan para esta medición**: las
fabricamos nosotros para poder probar FR-014c y el borde de período abierto de
longitud cero. Contarlas sería medirnos a nosotros mismos.

**Resultado: sobre el dato real disponible, ningún legajo queda bloqueado.** Y ese
resultado no alcanza para tranquilizarse. El extracto cubre agosto y septiembre de
2026, dos meses en los que el sistema de referencia ya exigía cerrar las fichas.
Las fichas de período abierto que preocupan son las viejas, y de esas no hay
ninguna en el volcado.

**Qué falta para cerrar la medición**: correrla sobre el volcado del archivo
completo. La consulta es directa sobre la tabla ya cargada:

```sql
SELECT COUNT(*) AS fichas, COUNT(DISTINCT legajo) AS legajos_bloqueados
FROM ficha_medica
WHERE fecha_citacion IS NULL AND fecha_alta IS NULL AND eliminada_en IS NULL;
```

Si el número resultara alto, la conversación con el cliente no es técnica sino de
puesta en marcha: hay que decidir si esos legajos se corrigen antes de arrancar o
si FR-014c se relaja para las fichas anteriores a una fecha de corte. **Esa
decisión no se toma acá**: se registra como pregunta abierta y la responde el
cliente.
