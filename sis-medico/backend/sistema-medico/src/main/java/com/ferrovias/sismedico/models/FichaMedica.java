package com.ferrovias.sismedico.models;

import java.time.LocalDate;

// Un evento médico de un empleado en una fecha; el legajo más la fecha del evento la identifican.
// Intencional: casi todo el resto es nulable, porque las fichas anteriores a 2016 pueden no tener
// grupo, detalle ni fechas de fin. La obligatoriedad al guardar vive en ValidadorFichaMedica, no acá.
public record FichaMedica(
		Long id,
		long version,
		int legajo,
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
		Observaciones observaciones,
		Auditoria auditoria) {

	// Quién y cuándo hizo la última modificación de la ficha.
	public record Auditoria(
			String creadaPor,
			java.time.Instant creadaEn,
			String modificadaPor,
			java.time.Instant modificadaEn) {
	}

	// Intencional: no imprime observaciones ni códigos de enfermedad, para no volcar datos
	// clínicos a un log con un simple log.debug("{}", ficha).
	@Override
	public String toString() {
		return "FichaMedica[id=" + id
				+ ", legajo=" + legajo
				+ ", fechaEvento=" + fechaEvento
				+ ", fechaCitacion=" + fechaCitacion
				+ ", fechaAlta=" + fechaAlta
				+ ", version=" + version + "]";
	}

	public boolean estaGuardada() {
		return id != null;
	}

	// Fin del período de la ficha; si no tiene ninguna fecha de fin, se trata como abierto hasta hoy.
	public LocalDate finDelPeriodo() {
		return fechaCitacion != null ? fechaCitacion : fechaAlta;
	}
}
