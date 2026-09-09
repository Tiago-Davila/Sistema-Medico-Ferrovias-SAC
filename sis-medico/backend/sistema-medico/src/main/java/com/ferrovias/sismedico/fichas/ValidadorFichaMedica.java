package com.ferrovias.sismedico.fichas;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ferrovias.sismedico.comun.Advertencia;
import com.ferrovias.sismedico.comun.Violacion;
import com.ferrovias.sismedico.comun.Violacion.Codigos;
import com.ferrovias.sismedico.enfermedades.CatalogoRepositorio;

/**
 * Todas las reglas que deciden si una ficha se puede guardar.
 *
 * <h2>Acumula. No cortocircuita. Nunca.</h2>
 *
 * Cada regla agrega a la lista y la ejecución sigue. <b>No hay un solo
 * {@code return} en el medio de {@link #validar}</b>, y no puede haberlo.
 *
 * <p>El motivo es SC-001. El operario carga dieciséis campos de una vez y las
 * reglas se pisan: quien se equivoca en las fechas suele equivocarse también en
 * la clasificación. Devolverle un error por viaje lo obliga a tantos viajes como
 * errores tenga, y la carga en sesenta segundos deja de ser posible.
 *
 * <p>{@code ValidacionAcumulativaTest} carga una ficha con siete violaciones
 * simultáneas y exige las siete. Es el test que existe para que este comentario
 * no dependa de que alguien lo lea.
 *
 * <h2>Solo al escribir</h2>
 *
 * Nada de esto corre al leer (FR-030). Las fichas históricas violan varias de
 * estas reglas y tienen que poder abrirse y navegarse igual (FR-028, FR-031). Es
 * la razón por la que la obligatoriedad vive acá y no en el modelo ni en el
 * esquema.
 *
 * <h2>Lo que no está acá</h2>
 *
 * Unicidad (FR-013) y solapamiento (FR-014, FR-015) necesitan mirar las otras
 * fichas del legajo: los resuelven {@link DetectorSolapamiento} y el índice
 * único del esquema, y el servicio junta las tres listas de violaciones en una
 * sola respuesta. La existencia del legajo en el padrón (FR-002) también la
 * verifica el servicio, contra la base externa.
 */
@Component
public class ValidadorFichaMedica {

	/** FR-006b. */
	public static final int MAXIMO_CARACTERES_OBSERVACIONES = 500;

	/** FR-019. Advierte, nunca bloquea. */
	public static final int DIAS_PARA_ADVERTIR_ANTIGUEDAD = 45;

	private final Clock reloj;
	private final CatalogoRepositorio catalogo;
	private final CalculadorDiasPerdidos calculador;

	public ValidadorFichaMedica(Clock reloj, CatalogoRepositorio catalogo,
			CalculadorDiasPerdidos calculador) {
		this.reloj = reloj;
		this.catalogo = catalogo;
		this.calculador = calculador;
	}

	/**
	 * @return todas las violaciones encontradas. Vacía si la ficha se puede
	 *     guardar en lo que a estas reglas respecta.
	 */
	public List<Violacion> validar(FichaMedica ficha) {
		List<Violacion> violaciones = new ArrayList<>();

		obligatoriedad(ficha, violaciones);
		fechaDelEvento(ficha, violaciones);
		fechasDeFin(ficha, violaciones);
		clasificacion(ficha, violaciones);
		dependenciasDelEstado(ficha, violaciones);
		observaciones(ficha, violaciones);

		return List.copyOf(violaciones);
	}

	/**
	 * FR-019: más de 45 días de antigüedad. <b>Advierte y deja guardar.</b>
	 *
	 * <p>Está separado de {@link #validar} a propósito, para que la diferencia
	 * sea estructural y no una convención. En el sistema de referencia el
	 * bloqueo por antigüedad está deliberadamente desactivado, y FR-019 exige que
	 * acá tampoco bloquee nunca. Si alguien la mueve a la lista de violaciones,
	 * está cambiando una regla de negocio.
	 */
	public List<Advertencia> advertencias(FichaMedica ficha) {
		if (ficha.fechaEvento() == null) {
			return List.of();
		}
		long antiguedad = ChronoUnit.DAYS.between(ficha.fechaEvento(), LocalDate.now(reloj));
		return antiguedad > DIAS_PARA_ADVERTIR_ANTIGUEDAD
				? List.of(Advertencia.eventoAntiguo(antiguedad))
				: List.of();
	}

	// ---------------------------------------------------------------- reglas

	/**
	 * FR-004b y FR-004c: qué campos no pueden faltar al guardar.
	 *
	 * <p>Los cuatro de sí/no van con {@code CAMPO_REQUERIDO} y no con un código
	 * por campo: es el mismo problema repetido, y cada violación ya trae el campo
	 * que la origina para que la pantalla la ubique.
	 *
	 * <p>Notar que {@code null} y {@code false} no son lo mismo (FR-004d): una
	 * ficha nueva no puede quedar con ninguno sin responder, pero responder que
	 * no es perfectamente válido.
	 */
	private void obligatoriedad(FichaMedica ficha, List<Violacion> violaciones) {
		if (ficha.fechaEvento() == null) {
			violaciones.add(requerido("fechaEvento", "la fecha del evento"));
		}
		if (ficha.estadoPaciente() == null) {
			violaciones.add(requerido("estadoPaciente", "el estado del paciente"));
		}
		if (ficha.estabaEnServicio() == null) {
			violaciones.add(requerido("estabaEnServicio", "si estaba en servicio"));
		}
		if (ficha.atendidoServicioMedico() == null) {
			violaciones.add(requerido("atendidoServicioMedico", "si lo atendió el servicio médico"));
		}
		if (ficha.envioMedicoDomicilio() == null) {
			violaciones.add(requerido("envioMedicoDomicilio", "si se envió médico a domicilio"));
		}
		if (ficha.justificado() == null) {
			violaciones.add(requerido("justificado", "si está justificado"));
		}
	}

	/** FR-007: la fecha del evento no puede ser posterior a hoy. */
	private void fechaDelEvento(FichaMedica ficha, List<Violacion> violaciones) {
		if (ficha.fechaEvento() != null && ficha.fechaEvento().isAfter(LocalDate.now(reloj))) {
			violaciones.add(new Violacion("fechaEvento", Codigos.EVENTO_FUTURO,
					"La fecha del evento no puede ser posterior a hoy."));
		}
	}

	/**
	 * FR-008, FR-011, FR-012 y FR-012b: las dos fechas de fin.
	 *
	 * <p>FR-008 exige exactamente una de las dos. Ni ambas ni ninguna: el
	 * período de la ficha tiene que quedar bien formado para que el solapamiento
	 * de FR-014 sea computable.
	 */
	private void fechasDeFin(FichaMedica ficha, List<Violacion> violaciones) {
		boolean hayCitacion = ficha.fechaCitacion() != null;
		boolean hayAlta = ficha.fechaAlta() != null;

		if (hayCitacion == hayAlta) {
			violaciones.add(new Violacion("fechaCitacion", Codigos.FECHAS_FIN_EXCLUYENTES,
					hayCitacion
							? "Cargá la fecha de citación o la de alta, no las dos."
							: "Falta la fecha de citación o la de alta: tiene que haber una."));
		}

		if (ficha.fechaEvento() == null) {
			return;
		}

		// FR-012b: la citación no puede ser anterior al evento. Sin tope
		// superior (FR-012c): el tope de 999 rige solo para el alta.
		if (hayCitacion && ficha.fechaCitacion().isBefore(ficha.fechaEvento())) {
			violaciones.add(new Violacion("fechaCitacion", Codigos.CITACION_ANTERIOR_AL_EVENTO,
					"La fecha de citación no puede ser anterior a la del evento."));
		}

		if (hayAlta) {
			// FR-012: igual al evento es válido y da cero días perdidos.
			if (ficha.fechaAlta().isBefore(ficha.fechaEvento())) {
				violaciones.add(new Violacion("fechaAlta", Codigos.ALTA_ANTERIOR_AL_EVENTO,
						"La fecha de alta no puede ser anterior a la del evento."));
			} else {
				// FR-011: exactamente 999 es válido, 1000 no. Se evalúa solo si
				// el alta es posterior, para no acumular dos mensajes sobre el
				// mismo error de tipeo.
				Long dias = calculador.calcular(ficha);
				if (dias != null && dias > CalculadorDiasPerdidos.MAXIMO_DIAS_AL_GUARDAR) {
					violaciones.add(new Violacion("fechaAlta", Codigos.ALTA_SUPERA_999_DIAS,
							"Entre la fecha del evento y la de alta no puede haber más de "
									+ CalculadorDiasPerdidos.MAXIMO_DIAS_AL_GUARDAR
									+ " días. Revisá las dos fechas."));
				}
			}
		}
	}

	/**
	 * FR-009 y FR-010: grupo y detalle.
	 *
	 * <p>Grupo y detalle faltantes dan dos violaciones distintas y no una: US1-7
	 * pide que el mensaje indique cuál de los dos falta.
	 *
	 * <p>La pertenencia al grupo solo se comprueba si están los dos. Con el grupo
	 * vacío, "el detalle no pertenece al grupo" no le dice nada al operario:
	 * primero tiene que elegir un grupo.
	 */
	private void clasificacion(FichaMedica ficha, List<Violacion> violaciones) {
		if (ficha.grupoEnfermedad() == null) {
			violaciones.add(new Violacion("grupoEnfermedad", Codigos.GRUPO_REQUERIDO,
					"Elegí el grupo de enfermedad."));
		}
		if (ficha.detalleEnfermedad() == null) {
			violaciones.add(new Violacion("detalleEnfermedad", Codigos.DETALLE_REQUERIDO,
					"Elegí el detalle de enfermedad."));
		}

		if (ficha.grupoEnfermedad() != null && ficha.detalleEnfermedad() != null
				&& !catalogo.detallePerteneceAlGrupo(
						ficha.detalleEnfermedad(), ficha.grupoEnfermedad())) {

			violaciones.add(new Violacion("detalleEnfermedad", Codigos.DETALLE_FUERA_DE_GRUPO,
					"El detalle no pertenece al grupo elegido."));
		} else if (ficha.grupoEnfermedad() != null && !catalogo.existeGrupo(ficha.grupoEnfermedad())) {
			// Un grupo huérfano se lee sin problema (FR-028b) pero no se guarda.
			// Es lo que obliga a completar una ficha histórica al editarla.
			violaciones.add(new Violacion("grupoEnfermedad", Codigos.GRUPO_REQUERIDO,
					"El grupo de enfermedad no existe en el catálogo."));
		}
	}

	/**
	 * FR-016: con estado accidentado, in itinere es obligatorio.
	 *
	 * <p>Es la única regla que distingue por estado. Y es también el único campo
	 * con tres estados legítimos: acá se exige que tenga uno definido, sí o no
	 * (FR-004e).
	 */
	private void dependenciasDelEstado(FichaMedica ficha, List<Violacion> violaciones) {
		if (ficha.estadoPaciente() == EstadoPaciente.ACCIDENTADO && ficha.inItinere() == null) {
			violaciones.add(new Violacion("inItinere", Codigos.IN_ITINERE_REQUERIDO,
					"En un accidente hay que indicar si fue in itinere."));
		}
	}

	/**
	 * FR-006b: hasta 500 caracteres al guardar.
	 *
	 * <p>El mensaje dice cuántos caracteres hay y cuántos sobran, no qué dicen
	 * (FR-035). Una observación histórica más larga se muestra completa
	 * (FR-006c); lo que no se puede es volver a guardarla así.
	 */
	private void observaciones(FichaMedica ficha, List<Violacion> violaciones) {
		Observaciones observaciones = ficha.observaciones();
		if (observaciones != null && observaciones.longitud() > MAXIMO_CARACTERES_OBSERVACIONES) {
			violaciones.add(new Violacion("observaciones", Codigos.OBSERVACIONES_MUY_LARGAS,
					"Las observaciones tienen " + observaciones.longitud() + " caracteres y el "
							+ "máximo son " + MAXIMO_CARACTERES_OBSERVACIONES + ". Sacale "
							+ (observaciones.longitud() - MAXIMO_CARACTERES_OBSERVACIONES) + "."));
		}
	}

	private static Violacion requerido(String campo, String comoSeLlamaEnPantalla) {
		return new Violacion(campo, Codigos.CAMPO_REQUERIDO, "Falta completar " + comoSeLlamaEnPantalla + ".");
	}
}
