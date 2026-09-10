package com.ferrovias.sismedico.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ferrovias.sismedico.dtos.Advertencia;
import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.dtos.Violacion.Codigos;
import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;

// Todas las reglas que deciden si una ficha se puede guardar.
// Intencional: cada regla agrega a la lista de violaciones y sigue, nunca corta con un return;
// el operario tiene que ver todos los errores de una carga junta, no uno por viaje.
@Component
public class ValidadorFichaMedica {

	public static final int MAXIMO_CARACTERES_OBSERVACIONES = 500;
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

	// Corre todas las reglas de guardado y devuelve todas las violaciones encontradas.
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

	// Avisa si el evento tiene más de 45 días de antigüedad.
	// Intencional: esto nunca bloquea el guardado, a propósito. Si alguien la suma a las
	// violaciones de validar(), está cambiando una regla de negocio.
	public List<Advertencia> advertencias(FichaMedica ficha) {
		if (ficha.fechaEvento() == null) {
			return List.of();
		}
		long antiguedad = ChronoUnit.DAYS.between(ficha.fechaEvento(), LocalDate.now(reloj));
		return antiguedad > DIAS_PARA_ADVERTIR_ANTIGUEDAD
				? List.of(Advertencia.eventoAntiguo(antiguedad))
				: List.of();
	}

	// Los campos que no pueden faltar al guardar una ficha.
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

	// La fecha del evento no puede ser posterior a hoy.
	private void fechaDelEvento(FichaMedica ficha, List<Violacion> violaciones) {
		if (ficha.fechaEvento() != null && ficha.fechaEvento().isAfter(LocalDate.now(reloj))) {
			violaciones.add(new Violacion("fechaEvento", Codigos.EVENTO_FUTURO,
					"La fecha del evento no puede ser posterior a hoy."));
		}
	}

	// Exige exactamente una de las dos fechas de fin (citación o alta) y valida su relación con el evento.
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

		if (hayCitacion && ficha.fechaCitacion().isBefore(ficha.fechaEvento())) {
			violaciones.add(new Violacion("fechaCitacion", Codigos.CITACION_ANTERIOR_AL_EVENTO,
					"La fecha de citación no puede ser anterior a la del evento."));
		}

		if (hayAlta) {
			if (ficha.fechaAlta().isBefore(ficha.fechaEvento())) {
				violaciones.add(new Violacion("fechaAlta", Codigos.ALTA_ANTERIOR_AL_EVENTO,
						"La fecha de alta no puede ser anterior a la del evento."));
			} else {
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

	// Exige grupo y detalle de enfermedad, y que el detalle pertenezca al grupo elegido.
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
			// Intencional: un grupo huérfano se puede leer, pero no volver a guardar sin corregirlo.
			violaciones.add(new Violacion("grupoEnfermedad", Codigos.GRUPO_REQUERIDO,
					"El grupo de enfermedad no existe en el catálogo."));
		}
	}

	// Con estado accidentado, in itinere es obligatorio.
	private void dependenciasDelEstado(FichaMedica ficha, List<Violacion> violaciones) {
		if (ficha.estadoPaciente() == EstadoPaciente.ACCIDENTADO && ficha.inItinere() == null) {
			violaciones.add(new Violacion("inItinere", Codigos.IN_ITINERE_REQUERIDO,
					"En un accidente hay que indicar si fue in itinere."));
		}
	}

	// Las observaciones no pueden superar los 500 caracteres al guardar.
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
