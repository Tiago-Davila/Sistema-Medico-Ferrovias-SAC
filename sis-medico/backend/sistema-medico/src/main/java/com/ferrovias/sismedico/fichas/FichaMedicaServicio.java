package com.ferrovias.sismedico.fichas;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ferrovias.sismedico.auditoria.AuditoriaServicio;
import com.ferrovias.sismedico.comun.Advertencia;
import com.ferrovias.sismedico.comun.Violacion;
import com.ferrovias.sismedico.comun.Violacion.Codigos;
import com.ferrovias.sismedico.empleados.PadronRepositorio;

/**
 * Donde vive el negocio de la ficha médica.
 *
 * <p><b>Toda la validación pasa por acá, nunca por el controlador.</b> El
 * controlador traduce HTTP y nada más. Si una regla estuviera en el controlador,
 * bastaría con agregar un segundo punto de entrada para saltearla.
 *
 * <h2>Por qué el alta valida en tres etapas</h2>
 *
 * Ninguna es opcional y las tres acumulan en la misma lista, porque el operario
 * tiene que recibir todos los problemas de una vez (M1, SC-001):
 *
 * <ol>
 *   <li>Las reglas que no necesitan la base, en {@link ValidadorFichaMedica}.
 *   <li>La existencia del legajo en el padrón externo (FR-002).
 *   <li>Unicidad (FR-013) y solapamiento (FR-014), que necesitan mirar las otras
 *       fichas del legajo.
 * </ol>
 *
 * <p>Recién si las tres quedan vacías se persiste. Y el índice único filtrado
 * del esquema sigue siendo la última red: si dos operarios cargan la misma ficha
 * en el mismo instante, los dos pasan la validación y la base rechaza al
 * segundo.
 *
 * <h2>Guardado en un solo paso</h2>
 *
 * Las advertencias viajan con la respuesta exitosa, sin cambiar el código de
 * estado y sin confirmación en dos pasos (M2). Un segundo viaje o un diálogo
 * pelearían contra la carga por teclado y contra SC-001.
 */
@Service
public class FichaMedicaServicio {

	private final ValidadorFichaMedica validador;
	private final DetectorSolapamiento detector;
	private final FichaMedicaRepositorio repositorio;
	private final PadronRepositorio padron;
	private final AuditoriaServicio auditoria;

	public FichaMedicaServicio(ValidadorFichaMedica validador, DetectorSolapamiento detector,
			FichaMedicaRepositorio repositorio, PadronRepositorio padron,
			AuditoriaServicio auditoria) {

		this.validador = validador;
		this.detector = detector;
		this.repositorio = repositorio;
		this.padron = padron;
		this.auditoria = auditoria;
	}

	/**
	 * Da de alta una ficha.
	 *
	 * <p>Persistencia y asiento de auditoría van en la misma transacción, no por
	 * prolijidad: si el asiento quedara afuera podría existir una escritura sin
	 * registro, y SC-004 exige el 100 %.
	 *
	 * @return la ficha guardada y las advertencias de FR-019, que nunca bloquean
	 * @throws FichaInvalidaException con todas las violaciones juntas
	 */
	@Transactional
	public Resultado darDeAlta(FichaMedica ficha) {
		List<Violacion> violaciones = validarTodo(ficha, null);
		if (!violaciones.isEmpty()) {
			throw new FichaInvalidaException(violaciones);
		}

		FichaMedica normalizada = aplicarDependenciasEntreCampos(ficha);

		long id = repositorio.insertar(normalizada, auditoria.usuarioActual(), auditoria.ahora());
		auditoria.registrarAlta(id);

		return new Resultado(
				repositorio.buscar(id).orElseThrow(),
				validador.advertencias(normalizada));
	}

	/**
	 * Las tres etapas de validación, acumulando en una sola lista.
	 *
	 * @param idActual ficha que se está editando, o {@code null} en un alta
	 */
	private List<Violacion> validarTodo(FichaMedica ficha, Long idActual) {
		List<Violacion> violaciones = new ArrayList<>(validador.validar(ficha));

		// FR-002. Se consulta el padrón aunque haya otras violaciones: cortar
		// acá le devolvería al operario un problema por vez.
		if (!padron.existe(ficha.legajo())) {
			violaciones.add(new Violacion("legajo", Codigos.LEGAJO_INEXISTENTE,
					"El legajo " + ficha.legajo() + " no existe en el padrón."));
		}

		if (ficha.fechaEvento() != null) {
			// FR-013. El índice único del esquema lo garantiza igual; esto es
			// para que el operario reciba un mensaje con su campo en lugar de un
			// error de base.
			if (repositorio.existeOtraConMismaFecha(ficha.legajo(), ficha.fechaEvento(), idActual)) {
				violaciones.add(new Violacion("fechaEvento", Codigos.FICHA_DUPLICADA,
						"Este empleado ya tiene una ficha con esa fecha de evento."));
			}

			// FR-014. El legajo es el de la ficha que se está guardando, que al
			// reasignar es el de destino (FR-003e).
			detector.detectar(ficha.legajo(), idActual, ficha.fechaEvento(), ficha.finDelPeriodo())
					.ifPresent(violaciones::add);
		}

		return List.copyOf(violaciones);
	}

	/**
	 * FR-023 a FR-026b: lo que el estado del paciente deriva.
	 *
	 * <p>Se aplica en el servidor <b>sin mirar lo que mandó el cliente</b>
	 * (FR-018). La pantalla deshabilita y autocompleta estos campos para que el
	 * operario los vea, pero eso es comodidad: ninguna regla puede existir solo
	 * en el cliente.
	 *
	 * <p>"Limpiar" significa distinto según el campo (FR-026b), y es la parte que
	 * más fácil se implementa mal:
	 *
	 * <ul>
	 *   <li><b>envío de médico a domicilio</b> queda en <i>no</i>, no vacío,
	 *       porque FR-004c no le admite tercer estado;
	 *   <li><b>in itinere</b> queda sin valor definido, que FR-004e sí le admite;
	 *   <li><b>hora del accidente</b> queda vacía.
	 * </ul>
	 */
	private FichaMedica aplicarDependenciasEntreCampos(FichaMedica ficha) {
		Boolean inItinere = ficha.inItinere();
		Integer horaAccidente = ficha.horaAccidente();
		Boolean envioMedicoDomicilio = ficha.envioMedicoDomicilio();
		Boolean estabaEnServicio = ficha.estabaEnServicio();

		if (ficha.estadoPaciente() == EstadoPaciente.ENFERMEDAD) {
			// FR-025: in itinere y hora del accidente no aplican y quedan
			// vacíos. Envío de médico a domicilio queda habilitado, así que se
			// respeta lo que el operario respondió.
			inItinere = null;
			horaAccidente = null;

		} else if (ficha.estadoPaciente() == EstadoPaciente.ACCIDENTADO) {
			// FR-023: con accidente, envío de médico a domicilio no aplica y va
			// en "no". En "no", no vacío: FR-004c no le admite tercer estado.
			envioMedicoDomicilio = false;
		}

		// FR-024: in itinere marcado implica que estaba en servicio. Se preserva
		// tal como está en el sistema de referencia, aunque sea contraintuitivo:
		// está documentado en los supuestos del spec para que no se "corrija"
		// por error.
		if (Boolean.TRUE.equals(inItinere)) {
			estabaEnServicio = true;
		}

		return new FichaMedica(
				ficha.id(), ficha.version(), ficha.legajo(), ficha.fechaEvento(),
				ficha.estadoPaciente(), inItinere, estabaEnServicio, horaAccidente,
				ficha.atendidoServicioMedico(), envioMedicoDomicilio, ficha.justificado(),
				ficha.fechaCitacion(), ficha.fechaAlta(),
				ficha.grupoEnfermedad(), ficha.detalleEnfermedad(),
				ficha.observaciones(), ficha.auditoria());
	}

	/**
	 * Lo que devuelve una escritura exitosa.
	 *
	 * <p>Misma forma para el alta y para la modificación, así el frontend mira un
	 * solo lugar. {@code advertencias} siempre está, vacía si no hay ninguna.
	 */
	public record Resultado(FichaMedica ficha, List<Advertencia> advertencias) {
	}
}
