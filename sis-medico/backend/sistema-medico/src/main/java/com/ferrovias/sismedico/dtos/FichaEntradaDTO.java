package com.ferrovias.sismedico.dtos;

import java.time.LocalDate;

import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;

// Lo que llega en el cuerpo de un alta o una modificación de ficha.
// Intencional: no tiene campo diasPerdidos, porque se calcula y no se recibe. No lleva
// anotaciones de validación: la validación de negocio acumula todas las violaciones juntas
// y eso vive en ValidadorFichaMedica, no en anotaciones por campo.
public record FichaEntradaDTO(
		Long version,
		Integer legajo,
		LocalDate fechaEvento,
		EstadoPaciente estadoPaciente,
		Boolean inItinere,
		Boolean estabaEnServicio,
		Integer horaAccidente,
		Boolean atendidoServicioMedico,
		Boolean envioMedicoDomicilio,
		Boolean justificado,
		LocalDate fechaCitacion,
		LocalDate fechaAlta,
		Integer grupoEnfermedad,
		Integer detalleEnfermedad,
		String observaciones) {

	// Pasa al dominio sin decidir nada: un null que llega null sigue null, para que el
	// validador lo rechace si corresponde en vez de tapar la falta con un valor por defecto.
	public FichaMedica aDominio(Long id) {
		return new FichaMedica(
				id,
				version == null ? 0L : version,
				legajo == null ? 0 : legajo,
				fechaEvento,
				estadoPaciente,
				inItinere,
				estabaEnServicio,
				horaAccidente,
				atendidoServicioMedico,
				envioMedicoDomicilio,
				justificado,
				fechaCitacion,
				fechaAlta,
				grupoEnfermedad,
				detalleEnfermedad,
				Observaciones.de(observaciones),
				null);
	}
}
