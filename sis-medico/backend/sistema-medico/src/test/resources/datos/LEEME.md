# Juego de datos de prueba

FR-036b: esta feature tiene que incluir un juego de datos con casos históricos
inconsistentes reales, porque sin ellos FR-028 a FR-031, FR-014c y SC-002 no son
verificables. La tolerancia de lectura es la mitad del riesgo de la feature y sin
estos casos no se ejercita nada de ella.

## Los tres archivos

| Archivo | Qué es |
|---|---|
| `fichas-historicas.csv` | 670 fichas reales del sistema de referencia, anonimizadas |
| `fichas-construidas.csv` | 16 fichas fabricadas, para cubrir lo que el volcado no trae |
| `empleados-padron.csv` | padrón ficticio, se carga en la base del padrón del contenedor |

El volcado crudo **no está versionado** y no debe estarlo: trae legajos reales,
diagnósticos reales y números de siniestro de la ART. Está en `assets/`, que
`.gitignore` excluye. Lo que entra al repositorio es el derivado que produce
`anonimizar-historico.py`, y ese script documenta exactamente qué se conservó y
qué no.

## Por qué hay fichas fabricadas

El volcado que tenemos es un extracto reciente y **está limpio**. Verificado
sobre las 670 filas:

| Caso que FR-036b exige | Cuántas hay en el volcado |
|---|---|
| Sin grupo ni detalle de enfermedad | 0 |
| Sin ninguna de las dos fechas de fin | 0 |
| Con las dos fechas de fin | 0 |
| Alta anterior al evento | 0 |
| Más de 999 días entre evento y alta | 0 |
| Observación de más de 500 caracteres | 0 (la más larga tiene 70) |
| Código de enfermedad huérfano | 0 |
| (legajo, fecha de evento) repetido | 0 |

Cubre agosto y septiembre de 2026, no el archivo histórico de ochenta mil fichas
con registros anteriores a 2016. Usado solo, los tests de tolerancia de lectura
de US2 pasarían sin probar nada.

`fichas-construidas.csv` llena esos huecos. Está en un archivo aparte, y no
mezclado con las reales, para que nunca haya duda sobre qué dato vino del
sistema de referencia y qué dato fabricamos nosotros. La columna `caso` nombra
qué ejercita cada fila.

Cuando aparezca un volcado del archivo histórico de verdad, estas filas se
reemplazan por las reales equivalentes.

## Rangos de legajo

| Rango | Origen |
|---|---|
| 900001–900421 | fichas reales anonimizadas |
| 990001–990012 | fichas construidas |

El legajo **990012 tiene fichas y no está en el padrón** a propósito. Es la
referencia colgada que D3 acepta de frente: si el padrón da de baja un legajo con
fichas cargadas, el sistema no puede impedirlo, y al leer FR-028 manda mostrar la
ficha igual.

## `HOY` como fecha

`fichas-construidas.csv` usa el literal `HOY` en la fecha de evento de un caso.
Lo resuelve el cargador contra el `Clock` de la aplicación, no contra el reloj
del sistema. Es el caso borde de FR-014c: una ficha sin fecha de fin con evento
en el día de hoy tiene período abierto de longitud cero, así que **no** bloquea
cargas posteriores, aunque R6 siga impidiendo otra ficha con esa misma fecha.
Fijar esa fecha en el archivo la dejaría vencida al día siguiente.

## Las cuatro columnas sin identificar

El volcado trae cuatro columnas booleanas, y la ficha tiene cinco campos de sí/no
más la hora del accidente. Sin definición de columnas no se sabe cuál es cuál: la
única señal fuerte es que la segunda solo tiene valor en las fichas de accidente,
lo que la haría *in itinere*.

Se conservan con nombres neutros (`indicador1`..`indicador4`) en vez de
adivinarles el significado, y el cargador las deja sin mapear: los campos quedan
en `NULL`, que es exactamente lo que FR-004d contempla para el histórico. Cuando
llegue la definición, cambia el cargador y no hay que volver a pedir el volcado.
