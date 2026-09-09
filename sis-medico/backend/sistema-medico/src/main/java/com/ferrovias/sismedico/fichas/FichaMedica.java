package com.ferrovias.sismedico.fichas;

import java.time.LocalDate;

/**
 * Una ficha médica: <b>un</b> evento médico de un empleado en una fecha.
 *
 * <p>Es la entidad central. Un empleado acumula una ficha por evento: dos
 * eventos en fechas distintas son dos fichas, nunca renglones dentro de una
 * sola. La combinación de legajo y fecha del evento la identifica entre las
 * fichas no eliminadas.
 *
 * <h2>Por qué no hay ni una anotación de validación acá</h2>
 *
 * Es deliberado y {@code PersistenciaArchUnitTest} lo hace cumplir. FR-004b
 * declara obligatorios el estado, el grupo, el detalle y cuatro campos de sí/no,
 * pero esa obligatoriedad rige <b>al guardar</b>. Las fichas anteriores a 2016
 * no tienen grupo ni detalle, algunas no tienen ninguna de las dos fechas de
 * fin, y varias tienen los campos de sí/no vacíos. Todas tienen que poder
 * leerse, mostrarse y navegarse sin que nada falle (FR-028, FR-030).
 *
 * <p>Un {@code @NotNull} sobre {@code grupoEnfermedad} rompería eso: cada
 * consulta de una ficha vieja sería una excepción. La obligatoriedad vive en
 * {@link ValidadorFichaMedica} y solo se aplica al escribir.
 *
 * <p>Por la misma razón casi todo es nulable, incluidos los booleanos, que van
 * como {@link Boolean} y no como {@code boolean}: FR-004d exige distinguir "no"
 * de "sin responder", porque un valor ausente <b>no</b> se interpreta como no.
 *
 * <h2>Lo que no está</h2>
 *
 * No hay campo {@code diasPerdidos}. Se derivan de las dos fechas y no son un
 * dato (FR-020b): guardarlos acá abriría la posibilidad de que discrepen de las
 * fechas, que es justo lo que FR-020b prohíbe. Los calcula
 * {@link CalculadorDiasPerdidos} y los devuelve la columna calculada del
 * esquema.
 *
 * <p>Tampoco están apellido, nombre, sección ni categoría laboral. La ficha
 * referencia al empleado por su legajo y nada más (FR-003c): esos datos viven en
 * el padrón externo y se resuelven al consultar.
 *
 * @param id {@code null} mientras la ficha no se guardó
 * @param version bloqueo optimista (D2). Nace en 0.
 * @param legajo editable: corregir una ficha cargada con el legajo equivocado se
 *     hace cambiándoselo, no eliminándola (FR-003d)
 * @param inItinere el único booleano con tres estados legítimos: sí, no y sin
 *     valor definido (FR-004e). FR-016 lo exige solo con estado accidentado y
 *     FR-025 lo deja vacío con enfermedad.
 * @param horaAccidente solo la hora, sin minutos, de 0 a 23 (FR-004g)
 * @param grupoEnfermedad código del catálogo. Puede ser un código huérfano en
 *     una ficha histórica: no hay FK que lo impida (FR-028c).
 * @param auditoria quién y cuándo, sobre esta ficha (FR-032)
 */
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

	/**
	 * Datos de auditoría de la ficha (FR-032).
	 *
	 * <p>Guarda solo la última modificación. El rastro completo de todas las
	 * escrituras lo lleva {@code ficha_medica_auditoria}, porque FR-033 exige
	 * registrar <b>toda</b> escritura y estas columnas solas no pueden: la
	 * segunda modificación pisa el rastro de la primera.
	 */
	public record Auditoria(
			String creadaPor,
			java.time.Instant creadaEn,
			String modificadaPor,
			java.time.Instant modificadaEn) {
	}

	/**
	 * Solo {@code id}, {@code legajo} y fechas (M3).
	 *
	 * <p>El {@code toString()} que genera un record imprime todos los
	 * componentes, incluidas las observaciones y los códigos de enfermedad. Un
	 * solo {@code log.debug("ficha {}", ficha)} escrito sin pensar volcaría el
	 * diagnóstico de un empleado identificado a un archivo de texto (FR-035).
	 *
	 * <p>El legajo sí va: sin él ningún mensaje de diagnóstico sirve para
	 * ubicar el problema, y por sí solo no es información clínica.
	 */
	@Override
	public String toString() {
		return "FichaMedica[id=" + id
				+ ", legajo=" + legajo
				+ ", fechaEvento=" + fechaEvento
				+ ", fechaCitacion=" + fechaCitacion
				+ ", fechaAlta=" + fechaAlta
				+ ", version=" + version + "]";
	}

	/** La ficha ya existe en la base. */
	public boolean estaGuardada() {
		return id != null;
	}

	/**
	 * Fin del período de la ficha, con el extremo derecho excluido (FR-014b).
	 *
	 * <p>{@code null} si la ficha no tiene ninguna de las dos fechas de fin. En
	 * ese caso su período se trata como abierto hasta hoy (FR-014c), y ese "hoy"
	 * lo pone quien evalúa el solapamiento, desde el {@code Clock}: no se
	 * resuelve acá para que el dominio no tenga que saber qué día es.
	 */
	public LocalDate finDelPeriodo() {
		return fechaCitacion != null ? fechaCitacion : fechaAlta;
	}
}
