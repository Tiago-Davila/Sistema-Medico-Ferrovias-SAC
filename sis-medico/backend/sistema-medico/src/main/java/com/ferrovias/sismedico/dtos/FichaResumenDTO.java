package com.ferrovias.sismedico.dtos;

import java.time.LocalDate;

import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.service.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.service.EvaluadorInconsistencia;

// Una ficha en el listado del empleado: lo justo para elegir cuál abrir.
// Intencional: no lleva observaciones ni códigos de enfermedad. El listado se ve entero de un
// vistazo y no hace falta que los diagnósticos de toda la carrera de un empleado estén en pantalla
// para elegir una fila.
public record FichaResumenDTO(
		long id,
		LocalDate fechaEvento,
		EstadoPaciente estadoPaciente,
		LocalDate fechaCitacion,
		LocalDate fechaAlta,
		Long diasPerdidos,
		boolean incompleta) {

	public static FichaResumenDTO de(FichaMedica ficha, CalculadorDiasPerdidos calculador,
			EvaluadorInconsistencia.Resultado evaluacion) {

		return new FichaResumenDTO(
				ficha.id(),
				ficha.fechaEvento(),
				ficha.estadoPaciente(),
				ficha.fechaCitacion(),
				ficha.fechaAlta(),
				calculador.calcular(ficha),
				evaluacion.incompleta());
	}
}
