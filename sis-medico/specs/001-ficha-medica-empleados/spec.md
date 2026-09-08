# Feature Specification: Ficha médica de empleados

**Feature Branch**: `feature/001-sistema-medico`

**Created**: 2026-09-08

**Status**: Draft

**Input**: Aplicación de una sola pantalla para que un operario administrativo del
área de personal registre, consulte, edite y elimine los eventos médicos
(accidentes y enfermedades) del plantel de Ferrovías SAC. Reemplaza
funcionalmente la pantalla equivalente de un sistema de terminal de 1996. El
operario no es personal médico: recibe la información del servicio médico en
papel o de palabra y la carga muchas veces al día, casi siempre con teclado. Se
especifican 5 escenarios de uso, 13 reglas de negocio (R1–R13), el
comportamiento tolerante de lectura sobre registros históricos importados, los
casos borde y los criterios de aceptación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar una ficha médica nueva (Priority: P1)

El operario recibe del servicio médico el aviso de que un empleado se accidentó o
se enfermó. Ingresa el número de legajo, confirma contra el apellido y nombre que
el sistema muestra que es la persona correcta, completa los datos del evento
(fecha, estado del paciente, clasificación de la enfermedad, fecha de citación o
de alta, observaciones) y guarda. Si algo está mal, el sistema le dice
exactamente qué corregir antes de guardar.

**Why this priority**: Es la razón de existir de la pantalla y la operación más
frecuente del día. Sin esto no hay producto. Las demás historias operan sobre
fichas que esta historia crea.

**Independent Test**: Se prueba de punta a punta cargando una ficha para un
legajo existente y verificando que queda registrada con su auditoría, y que cada
regla bloqueante impide guardar con un mensaje que identifica el campo a
corregir.

**Acceptance Scenarios**:

1. **Given** un legajo que existe en el padrón, **When** el operario lo ingresa,
   **Then** el sistema muestra el apellido y nombre del empleado para que
   confirme la identidad antes de cargar nada.
2. **Given** un legajo que no existe en el padrón, **When** el operario lo
   ingresa, **Then** el sistema lo informa y no habilita la carga de la ficha.
3. **Given** una ficha completa y válida con estado enfermedad, grupo y detalle
   coherentes y solo fecha de citación, **When** el operario guarda, **Then** la
   ficha queda registrada junto con el usuario y el momento de creación.
4. **Given** una fecha del evento posterior a hoy, **When** el operario intenta
   guardar, **Then** el sistema rechaza el guardado e indica que la fecha del
   evento no puede ser futura.
5. **Given** una ficha sin fecha de citación y sin fecha de alta, **When** el
   operario intenta guardar, **Then** el sistema rechaza el guardado e indica que
   debe cargarse exactamente una de las dos.
6. **Given** una ficha con fecha de citación y fecha de alta cargadas a la vez,
   **When** el operario intenta guardar, **Then** el sistema rechaza el guardado
   e indica que las dos fechas son excluyentes.
7. **Given** una ficha sin grupo o sin detalle de enfermedad, **When** el operario
   intenta guardar, **Then** el sistema rechaza el guardado e indica cuál de los
   dos falta.
8. **Given** un detalle de enfermedad que no pertenece al grupo elegido, **When**
   el operario intenta guardar, **Then** el sistema rechaza el guardado e indica
   que el detalle no corresponde al grupo.
9. **Given** estado accidentado sin el campo in itinere definido, **When** el
   operario intenta guardar, **Then** el sistema rechaza el guardado e indica que
   in itinere es obligatorio para accidentes.
10. **Given** estado accidentado con in itinere marcado, **When** el operario lo
    marca, **Then** el campo "estaba en servicio" queda en sí automáticamente y
    deja de ser editable.
11. **Given** estado enfermedad, **When** el operario lo selecciona, **Then** in
    itinere y hora del accidente quedan vacíos y no aplicables, y envío de médico
    a domicilio queda habilitado.
12. **Given** una ficha cuya fecha del evento tiene más de 45 días de antigüedad,
    **When** el operario guarda, **Then** el sistema advierte que se está
    cargando un evento viejo pero **permite** guardar.
13. **Given** un legajo que ya tiene una ficha con la fecha del evento X, **When**
    el operario intenta guardar una segunda ficha con la misma fecha para ese
    legajo, **Then** el sistema rechaza el guardado.
14. **Given** un legajo con una ficha cuyo período cubre del 1 al 10 de marzo,
    **When** el operario intenta guardar otra ficha del mismo legajo cuyo período
    se superpone con ese rango, **Then** el sistema rechaza el guardado e indica
    con qué ficha choca, identificándola por su fecha de evento.
15. **Given** un legajo con una ficha con evento el 1 de marzo y alta el 10 de
    marzo, **When** el operario guarda otra ficha del mismo legajo con evento el
    10 de marzo, **Then** el sistema la acepta: compartir un extremo no es
    solapamiento.
16. **Given** un legajo con una ficha histórica sin fecha de citación ni de alta
    con evento en 2019, **When** el operario intenta guardar una ficha nueva para
    ese legajo con evento posterior, **Then** el sistema rechaza el guardado,
    indica que la ficha de 2019 está incompleta y le señala que debe completarle
    una fecha de fin para poder continuar.

---

### User Story 2 - Consultar y navegar las fichas de un empleado (Priority: P2)

El operario ingresa un legajo y ve las fichas médicas ya cargadas de ese
empleado, pudiendo moverse entre ellas para revisar qué se registró y cuándo. Las
fichas importadas del sistema anterior se ven completas aunque estén incompletas
según las reglas de hoy.

**Why this priority**: Es la condición previa para editar y eliminar, y por sí
sola ya sustituye la función de consulta de la pantalla vieja. Además es donde se
verifica el comportamiento tolerante de lectura, que es un requisito duro del
proyecto.

**Independent Test**: Se prueba cargando el padrón y un conjunto de fichas —
incluyendo históricas sin grupo ni detalle— y verificando que todas se listan, se
abren y se recorren sin error.

**Acceptance Scenarios**:

1. **Given** un legajo con varias fichas cargadas, **When** el operario lo
   consulta, **Then** el sistema lista las fichas de ese empleado y le permite
   abrir cualquiera de ellas.
2. **Given** una ficha histórica importada sin grupo ni detalle de enfermedad,
   **When** el operario la abre, **Then** el sistema la muestra entera con los
   campos que sí tiene, la señala visualmente como incompleta y no produce
   ningún error.
3. **Given** una ficha histórica incompleta abierta en pantalla, **When** el
   operario navega a otra ficha sin guardar, **Then** el sistema no le exige
   completar nada.
4. **Given** un legajo sin ninguna ficha cargada, **When** el operario lo
   consulta, **Then** el sistema lo indica explícitamente en lugar de mostrar una
   pantalla vacía ambigua.
5. **Given** una ficha con fecha de alta cargada, **When** el operario la abre,
   **Then** los días perdidos se muestran calculados y no editables.
6. **Given** una ficha sin fecha de alta, **When** el operario la abre, **Then**
   los días perdidos se muestran vacíos.

---

### User Story 3 - Editar una ficha para registrar el alta (Priority: P3)

El empleado se reincorpora. El operario abre la ficha correspondiente, reemplaza
la fecha de citación por la fecha de alta y guarda. Los días perdidos se
recalculan solos. Es la edición más frecuente, pero el operario también corrige
cualquier otro campo del mismo modo.

**Why this priority**: Cierra el ciclo de vida de la ficha y es lo que hace que
los días perdidos existan. Depende de poder consultar (US2), por eso va después.

**Independent Test**: Se prueba tomando una ficha guardada con solo fecha de
citación, reemplazándola por una fecha de alta válida, y verificando que se
recalculan los días perdidos, se re-aplican todas las validaciones y se registra
quién modificó y cuándo.

**Acceptance Scenarios**:

1. **Given** una ficha guardada con solo fecha de citación, **When** el operario
   carga la fecha de alta y limpia la de citación, **Then** la ficha se guarda,
   los días perdidos se recalculan y quedan registrados el usuario y el momento
   de modificación.
2. **Given** una ficha en edición, **When** el operario intenta dejar cargadas la
   fecha de citación y la de alta al mismo tiempo, **Then** el sistema rechaza el
   guardado por la misma regla que en el alta inicial.
3. **Given** una fecha de alta igual a la fecha del evento, **When** el operario
   guarda, **Then** la ficha se guarda con cero días perdidos.
4. **Given** una fecha de alta anterior a la fecha del evento, **When** el
   operario intenta guardar, **Then** el sistema rechaza el guardado.
5. **Given** una fecha de alta que deja más de 999 días respecto de la fecha del
   evento, **When** el operario intenta guardar, **Then** el sistema rechaza el
   guardado.
6. **Given** una ficha existente y una ficha posterior del mismo empleado,
   **When** el operario agrega una fecha de alta que hace que ambos períodos se
   solapen, **Then** el sistema rechaza el guardado e indica con qué ficha choca.
7. **Given** una ficha guardada con estado accidentado y hora del accidente
   cargada, **When** el operario cambia el estado a enfermedad y guarda, **Then**
   in itinere y hora del accidente quedan vacíos en el registro guardado, no
   conservados de forma oculta.
8. **Given** una ficha histórica importada sin grupo ni detalle, **When** el
   operario la edita y guarda, **Then** el sistema le exige completar grupo y
   detalle antes de aceptar el guardado.
9. **Given** una ficha abierta por dos operarios a la vez, **When** el segundo
   guarda después de que el primero ya guardó, **Then** el sistema rechaza el
   segundo guardado, informa que la ficha fue modificada por otro usuario y no
   pisa los cambios del primero.

---

### User Story 4 - Eliminar una ficha cargada por error (Priority: P4)

El operario detecta que cargó una ficha equivocada —legajo errado, duplicado— y
la elimina.

**Why this priority**: Es la operación menos frecuente y la única que no es parte
del flujo normal de trabajo. El sistema es usable sin ella durante un tiempo.

**Independent Test**: Se prueba eliminando una ficha recién creada y verificando
que deja de aparecer en la consulta del empleado y que la eliminación queda
registrada.

**Acceptance Scenarios**:

1. **Given** una ficha cargada por error, **When** el operario la elimina,
   **Then** el sistema pide confirmación antes de proceder.
2. **Given** una eliminación confirmada, **When** se completa, **Then** la ficha
   deja de aparecer en la consulta de fichas del empleado y queda registrado qué
   usuario la eliminó y cuándo.
3. **Given** una ficha eliminada que se solapaba con otra, **When** el operario
   carga una ficha nueva en ese período, **Then** la ficha eliminada ya no
   provoca rechazo por solapamiento.
4. **Given** una ficha eliminada, **When** el operario consulta las fichas de ese
   empleado, **Then** la ficha eliminada no aparece por ningún medio en la
   pantalla.
5. **Given** una ficha eliminada por error, **When** se requiere recuperarla,
   **Then** los datos siguen conservados y la recuperación es posible sin volver
   a cargarlos a mano, aunque no exista una pantalla de restauración.

---

### Edge Cases

- **Fecha de alta igual a la fecha del evento**: es válido y da cero días
  perdidos.
- **Fecha de alta anterior a la fecha del evento**: se rechaza.
- **Legajo inexistente en el padrón**: se informa y no se habilita la carga.
- **Dos operarios editando la misma ficha**: el segundo guardado se rechaza
  informando que la ficha cambió; no se pisan cambios en silencio.
- **Cambio de estado en una ficha ya guardada**: los campos que dejan de aplicar
  se limpian en el registro guardado, no quedan escondidos con el valor anterior.
- **Agregar fecha de alta que genera solapamiento con una ficha posterior ya
  existente**: se rechaza indicando la ficha en conflicto.
- **Ficha histórica sin grupo ni detalle**: se muestra y se navega sin problema;
  se exige completarla solo si el operario intenta guardarla.
- **Ficha histórica que viola otras reglas actuales** (por ejemplo sin ninguna de
  las dos fechas, o con las dos): se muestra igual, bajo el mismo criterio.
- **Dos fichas que comparten un extremo** (alta de una igual al evento de la
  otra): no se solapan, se aceptan las dos.
- **Ficha histórica sin fecha de fin**: su período se considera abierto hasta hoy
  y bloquea toda carga posterior de ese legajo hasta que se le complete una fecha
  de fin. El mensaje de rechazo debe explicar esa salida.
- **Ficha histórica sin fecha de fin y con evento en el día de hoy**: su período
  abierto es de longitud cero; no bloquea cargas posteriores, pero R6 sigue
  impidiendo otra ficha con esa misma fecha de evento.
- **Ficha eliminada**: deja de existir para el operario, no participa en las
  validaciones de unicidad ni de solapamiento, y sus datos se conservan.
- **Diferencia de exactamente 999 días entre evento y alta**: es válido; se
  rechaza a partir de 1000.
- **Empleado sin fichas cargadas**: se indica explícitamente.
- **Ficha cuya antigüedad supera los 45 días al editarla** (no solo al crearla):
  la advertencia también aplica.

## Requirements *(mandatory)*

### Functional Requirements

**Identificación del empleado**

- **FR-001**: El sistema MUST permitir buscar un empleado por número de legajo y
  mostrar su apellido y nombre para que el operario confirme la identidad antes
  de cargar datos.
- **FR-002**: El sistema MUST rechazar la creación de una ficha para un legajo
  que no existe en el padrón, informándolo explícitamente.
- **FR-003**: El sistema MUST tratar el padrón de empleados como datos de solo
  consulta. No MUST ofrecer ninguna función para crear, modificar ni eliminar
  empleados.

**Campos de la ficha médica**

- **FR-004**: El sistema MUST registrar, para cada ficha médica: legajo del
  empleado, fecha del evento, estado del paciente, in itinere, estaba en
  servicio, hora del accidente, atendido por servicio médico, envío de médico a
  domicilio, justificado, fecha de citación, fecha de alta, días perdidos, grupo
  de enfermedad, detalle de enfermedad y observaciones.
- **FR-005**: El sistema MUST ofrecer únicamente dos valores para el estado del
  paciente: accidentado y enfermedad.
- **FR-006**: El sistema MUST impedir la edición manual de los días perdidos en
  cualquier circunstancia.

**Validaciones bloqueantes** *(impiden guardar; cada mensaje MUST identificar qué
campo corregir y por qué)*

- **FR-007** *(R1)*: El sistema MUST rechazar el guardado si la fecha del evento
  es posterior al día actual.
- **FR-008** *(R2)*: El sistema MUST rechazar el guardado salvo que esté cargada
  exactamente una de las dos fechas, citación o alta. Ni ambas ni ninguna.
- **FR-009** *(R3)*: El sistema MUST rechazar el guardado si falta el grupo o el
  detalle de enfermedad.
- **FR-010** *(R4)*: El sistema MUST rechazar el guardado si el detalle de
  enfermedad no pertenece al grupo de enfermedad elegido.
- **FR-011** *(R5)*: El sistema MUST rechazar el guardado si entre la fecha del
  evento y la fecha de alta hay más de 999 días. Una diferencia de exactamente
  999 días es válida.
- **FR-012**: El sistema MUST rechazar el guardado si la fecha de alta es
  anterior a la fecha del evento. Una fecha de alta igual a la del evento es
  válida.
- **FR-013** *(R6)*: El sistema MUST rechazar el guardado si el empleado ya tiene
  otra ficha con la misma fecha de evento.
- **FR-014** *(R7)*: El sistema MUST rechazar el guardado si el período de la
  ficha se solapa con el de otra ficha del mismo empleado. El período va desde la
  fecha del evento hasta la fecha de citación o, si no hay citación, hasta la
  fecha de alta.
- **FR-014b** *(R7)*: El sistema MUST tratar la fecha de fin del período como no
  incluida a efectos de solapamiento: dos fichas que comparten un extremo NO se
  solapan. Formalmente, dos fichas se solapan si y solo si el inicio de cada una
  es estrictamente anterior al fin de la otra. Una ficha con alta el 10 de marzo
  y otra con evento el 10 de marzo pueden coexistir.
- **FR-014c** *(R7)*: Para las fichas históricas importadas que no tienen ninguna
  de las dos fechas de fin, el sistema MUST tratar su período como abierto desde
  la fecha del evento hasta el día actual, evaluado en el momento de validar.
- **FR-014d**: Cuando una ficha histórica de período abierto impide guardar una
  ficha nueva por FR-014c, el mensaje MUST indicar que la ficha en conflicto está
  incompleta y MUST señalar que la vía para destrabarla es completarle la fecha
  de citación o de alta. Es la única salida disponible para el operario, porque
  una ficha de período abierto bloquea toda carga posterior de ese legajo.
- **FR-015** *(R7)*: El mensaje de solapamiento MUST identificar la ficha en
  conflicto por su fecha de evento, de modo que el operario pueda ubicarla.
- **FR-016** *(R8)*: El sistema MUST rechazar el guardado si el estado es
  accidentado y el campo in itinere no tiene valor definido.
- **FR-017**: El sistema MUST aplicar todas las validaciones bloqueantes tanto al
  crear como al editar una ficha.
- **FR-018**: El sistema MUST rechazar en el servidor toda escritura que viole
  cualquiera de las validaciones anteriores, con independencia de lo que haya
  validado la interfaz. Ninguna regla MUST existir únicamente en el cliente.

**Advertencia no bloqueante**

- **FR-019** *(R9)*: El sistema MUST advertir al operario cuando la fecha del
  evento tiene más de 45 días de antigüedad, al crear y al modificar, y MUST
  permitir guardar de todos modos. Esta comprobación MUST NOT convertirse nunca
  en bloqueante: en el sistema de referencia el bloqueo está deliberadamente
  desactivado.

**Cálculo de días perdidos**

- **FR-020** *(R10)*: El sistema MUST calcular los días perdidos como la
  diferencia entre la fecha de alta y la fecha del evento, con un tope de 999.
- **FR-021** *(R10)*: El sistema MUST dejar los días perdidos vacíos cuando no
  hay fecha de alta.
- **FR-022** *(R10)*: El sistema MUST recalcular los días perdidos
  automáticamente cada vez que se carga o se cambia la fecha de alta, sin
  intervención del operario.

**Dependencias entre campos**

- **FR-023** *(R11)*: Con estado accidentado, el sistema MUST habilitar in
  itinere y hora del accidente, y MUST dejar envío de médico a domicilio en no,
  no aplicable.
- **FR-024** *(R11)*: Con in itinere marcado, el sistema MUST fijar "estaba en
  servicio" en sí de forma automática y MUST impedir su edición.
- **FR-025** *(R12)*: Con estado enfermedad, el sistema MUST dejar in itinere y
  hora del accidente vacíos y no aplicables, y MUST habilitar envío de médico a
  domicilio.
- **FR-026** *(R13)*: Al cambiar el estado del paciente, el sistema MUST limpiar
  los campos que dejan de aplicar, de modo que el valor anterior no quede
  guardado de forma oculta.
- **FR-027**: El sistema MUST aplicar FR-026 también sobre fichas ya guardadas
  cuyo estado se cambia al editarlas.

**Comportamiento de lectura**

- **FR-028**: El sistema MUST mostrar cualquier ficha existente completa, tal
  como está almacenada, aunque viole reglas que hoy son obligatorias.
- **FR-029**: El sistema MUST señalar visualmente las fichas incompletas o
  inconsistentes, sin impedir consultarlas ni navegar entre ellas.
- **FR-030**: El sistema MUST NOT aplicar ninguna validación al leer ni al
  navegar. Las validaciones MUST aplicarse únicamente al guardar.
- **FR-031**: El sistema MUST exigir que una ficha histórica incompleta se
  complete solo cuando el operario intenta guardarla.

**Auditoría y datos de salud**

- **FR-032**: El sistema MUST registrar, en cada ficha, qué usuario la creó y en
  qué momento, y qué usuario la modificó por última vez y en qué momento.
- **FR-033**: El sistema MUST registrar toda escritura —creación, modificación y
  eliminación— con el usuario responsable y el momento.
- **FR-034**: El sistema MUST identificar al operario mediante las credenciales
  corporativas de la empresa. No MUST administrar usuarios ni contraseñas
  propias.
- **FR-035**: El sistema MUST NOT incluir observaciones, detalles de enfermedad
  ni ninguna descripción clínica en registros de diagnóstico técnico ni en
  mensajes de error dirigidos a operaciones.
- **FR-036**: El sistema MUST operar con un único perfil funcional de operario.
  No MUST distinguir perfiles de médico ni de psicólogo.

**Concurrencia y eliminación**

- **FR-037**: El sistema MUST rechazar el guardado de una ficha que fue
  modificada por otro usuario desde que el operario la abrió, informándolo de
  forma explícita y sin descartar los cambios ajenos.
- **FR-038**: El sistema MUST pedir confirmación antes de eliminar una ficha.
- **FR-039**: Una ficha eliminada MUST NOT aparecer en las consultas del empleado
  ni MUST participar en las validaciones de unicidad y solapamiento.
- **FR-039b**: El sistema MUST conservar la ficha eliminada, marcada como tal, en
  lugar de descartarla. La eliminación MUST ser reversible sin necesidad de
  volver a cargar los datos a mano.
- **FR-039c**: Una ficha eliminada MUST NOT ser visible para el operario en
  ninguna pantalla de consulta ni de navegación.
- **FR-039d**: La aplicación MUST NOT ofrecer una pantalla de restauración de
  fichas eliminadas: el perfil único de operario no la incluye y agregarla
  excede el alcance de esta feature. La recuperación es una intervención técnica
  sobre los datos, habilitada por FR-039b.

**Velocidad de carga**

- **FR-040**: El sistema MUST permitir completar y guardar una ficha usando
  únicamente el teclado, sin recurrir al mouse en ningún paso del flujo.
- **FR-041**: El sistema MUST permitir encadenar la carga de una ficha nueva
  inmediatamente después de guardar la anterior, sin pasos intermedios de
  navegación.

### Key Entities

- **Empleado**: la persona del plantel. Número de legajo (lo identifica),
  apellido, nombre, sección y categoría laboral. Proviene de un padrón externo de
  solo consulta; esta aplicación no lo administra.
- **Ficha médica**: el evento médico de un empleado en una fecha. Entidad
  central. Se identifica por la combinación de empleado y fecha del evento, que
  es única. Referencia a un estado del paciente, a un grupo de enfermedad y a un
  detalle de enfermedad, y lleva sus propios datos de auditoría.
- **Grupo de enfermedad**: clasificación de primer nivel del catálogo. Ejemplos
  reales: respiratorio, tubo digestivo y boca, genitourinario, oftalmológico,
  tejido cutáneo, quemaduras, cabeza, quirúrgicos, columna, músculo esquelético,
  esguince, fracturas, traumatismos, luxaciones.
- **Detalle de enfermedad**: clasificación de segundo nivel, dependiente de un
  grupo. Un detalle solo es válido dentro de su grupo. Ejemplos: *tronco* dentro
  de traumatismos; *síndrome gripal*, *faringitis*, *bronquitis*, *neumonía*,
  *otitis*, *sinusitis*, *intoxicación respiratoria por tóxicos* dentro de
  respiratorio.
- **Estado del paciente**: catálogo cerrado de exactamente dos valores,
  accidentado y enfermedad. Verificado contra ochenta mil registros del sistema
  actual.
- **Registro de auditoría**: quién y cuándo, asociado a cada escritura sobre una
  ficha médica.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un operario entrenado registra una ficha médica nueva completa en
  60 segundos o menos, usando solo el teclado, en un tiempo igual o menor al que
  le lleva la misma carga en la pantalla de terminal actual, medido sobre una
  muestra de al menos 10 cargas reales.
- **SC-002**: El 100 % de las fichas históricas importadas —incluidas las que no
  tienen grupo ni detalle de enfermedad— se abren y se recorren sin producir
  ningún error.
- **SC-003**: Cero fichas guardadas violan alguna de las validaciones bloqueantes
  R1 a R8, verificado sobre el total de fichas creadas o modificadas desde la
  puesta en marcha.
- **SC-004**: El 100 % de las escrituras —creaciones, modificaciones y
  eliminaciones— quedan registradas con el usuario responsable y el momento.
- **SC-005**: Ante un guardado rechazado, el operario identifica y corrige el
  campo señalado sin ayuda externa en al menos el 90 % de los casos, medido en
  pruebas con usuarios reales.
- **SC-006**: Los días perdidos mostrados coinciden con la diferencia entre fecha
  de alta y fecha del evento en el 100 % de las fichas que tienen fecha de alta.
- **SC-007**: Ningún registro de diagnóstico técnico del sistema contiene
  observaciones, detalles de enfermedad ni descripciones clínicas, verificado por
  inspección de los registros generados durante las pruebas de aceptación.
- **SC-008**: Cero casos de pérdida silenciosa de cambios por edición
  concurrente, verificado con pruebas de dos operarios editando la misma ficha.
- **SC-009**: El 100 % de los rechazos por solapamiento contra una ficha
  histórica incompleta explican cuál es la ficha en conflicto y qué hay que
  completar para destrabarla.
- **SC-010**: El 100 % de las fichas eliminadas conservan sus datos y son
  recuperables, verificado sobre las eliminaciones realizadas durante las pruebas
  de aceptación.

## Assumptions

Supuestos adoptados donde la descripción no fue explícita. Los marcados como *a
confirmar* son preguntas abiertas registradas, no decisiones cerradas.

- **Reemplazo de citación por alta**: como R2 exige exactamente una de las dos
  fechas, registrar el alta de un empleado implica necesariamente limpiar la
  fecha de citación en la misma edición. Es el flujo normal de la historia US3,
  no un caso borde.
- **"Estaba en servicio" aplica a ambos estados**: a diferencia de in itinere y
  hora del accidente (solo accidentes) y de envío de médico a domicilio (solo
  enfermedades), la descripción no restringe este campo a un estado. Se asume
  editable en accidentado y en enfermedad, salvo cuando in itinere lo fuerza a sí
  (FR-024).
- **In itinere implica "estaba en servicio" = sí**: se preserva tal como está en
  el sistema de referencia, aunque sea contraintuitivo. Se documenta acá para que
  no se "corrija" por error durante la implementación.
- **Hora del accidente es opcional**: R8 declara obligatorio solo in itinere para
  el estado accidentado. La hora queda opcional.
- **Grupo y detalle son obligatorios también para accidentes**: R3 no distingue
  por estado, y el catálogo incluye grupos de lesión (fracturas, esguince,
  traumatismos, luxaciones).
- **Concurrencia**: el segundo guardado se rechaza y el operario reintenta sobre
  la versión actualizada. No se intenta fusionar cambios automáticamente.
- **Consecuencia de FR-014c, decidida a conciencia**: tratar las fichas
  históricas sin fecha de fin como períodos abiertos hasta hoy implica que un
  legajo con una de esas fichas queda bloqueado para cargas nuevas hasta que
  alguien la complete. Es el criterio elegido por el cliente por sobre ignorarlas:
  prioriza no crear solapamientos reales, al costo de forzar la corrección del
  dato histórico. FR-014d existe para que ese bloqueo sea comprensible y
  accionable en lugar de aparecer como un rechazo sin explicación. Conviene medir
  cuántos legajos quedan alcanzados antes de la puesta en marcha.
- **Recuperación de fichas eliminadas**: FR-039b garantiza que los datos se
  conservan, pero la recuperación no es una función de la aplicación. Si el
  cliente quiere que el operario pueda deshacer una eliminación por sí mismo, es
  una feature aparte.
- **Fecha de citación anterior a la fecha del evento** *(a confirmar)*: se asume
  que debe rechazarse, por simetría con la fecha de alta (FR-012) y porque un
  período invertido rompe la detección de solapamiento de R7. La descripción
  enumera el caso para el alta pero no para la citación.
- **Límite superior de la fecha de citación** *(a confirmar)*: se asume que no
  tiene el tope de 999 días que R5 impone al alta, porque R5 nombra solo la fecha
  de alta.
- **Tope de 999 en días perdidos**: dado que R5 ya rechaza diferencias mayores a
  999 días, el tope de R10 solo puede manifestarse sobre fichas históricas
  importadas. Se conserva igual, como red de seguridad para esos datos.
- **Longitud de observaciones** *(a confirmar)*: no se especificó un máximo. Se
  asume un límite generoso de texto libre, a fijar contra el dato importado del
  sistema anterior.
- **Alcance de la advertencia de 45 días**: se cuenta desde la fecha del evento
  hasta el día actual, evaluada en el momento de guardar.
- **Padrón de empleados**: se asume disponible y poblado como precondición. Su
  origen y forma de actualización no son parte de esta feature.
- **Catálogos de enfermedad y de estados**: se asume que se importan una sola vez
  desde el sistema anterior y que esta aplicación no ofrece pantallas para
  administrarlos.

## Out of Scope

Nada de esto se implementa, aunque aparezca en la pantalla del sistema viejo o en
su código:

- El bloque de la aseguradora de riesgos del trabajo: aseguradora, número de
  siniestro y observaciones ART.
- El campo de inclusión en el parte del día siguiente.
- Cualquier reporte, listado o exportación.
- La novedad centralizada.
- Consultas médicas y seguimiento médico.
- Justificación de horas.
- Seguimiento COVID.
- Padrón de médicos.
- Exclusión de licencias.
- Notificaciones por correo.
- Los perfiles funcionales de médico y psicólogo, reemplazados por un único
  perfil de operario.
- La administración del padrón de empleados y de los catálogos de enfermedad.
