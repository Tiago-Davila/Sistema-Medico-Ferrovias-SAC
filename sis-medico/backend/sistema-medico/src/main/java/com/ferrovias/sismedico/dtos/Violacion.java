package com.ferrovias.sismedico.dtos;

import java.util.List;

// Un motivo por el que una ficha no se puede guardar, con el campo que la origina.
// Intencional: el mensaje nunca repite contenido clínico (qué dice la observación o qué
// enfermedad se cargó), solo qué regla se violó.
public record Violacion(
		String campo,
		String codigo,
		String mensaje,
		FichaEnConflicto fichaEnConflicto) {

	public Violacion(String campo, String codigo, String mensaje) {
		this(campo, codigo, mensaje, null);
	}

	// Identificación mínima de la ficha con la que se choca: solo fecha de evento y si está incompleta.
	public record FichaEnConflicto(long id, java.time.LocalDate fechaEvento, boolean incompleta) {
	}

	// Códigos estables de la API. No se inventan códigos fuera de esta lista.
	public static final class Codigos {

		public static final String EVENTO_FUTURO = "EVENTO_FUTURO";
		public static final String FECHAS_FIN_EXCLUYENTES = "FECHAS_FIN_EXCLUYENTES";
		public static final String GRUPO_REQUERIDO = "GRUPO_REQUERIDO";
		public static final String DETALLE_REQUERIDO = "DETALLE_REQUERIDO";
		public static final String DETALLE_FUERA_DE_GRUPO = "DETALLE_FUERA_DE_GRUPO";
		public static final String ALTA_SUPERA_999_DIAS = "ALTA_SUPERA_999_DIAS";
		public static final String ALTA_ANTERIOR_AL_EVENTO = "ALTA_ANTERIOR_AL_EVENTO";
		public static final String CITACION_ANTERIOR_AL_EVENTO = "CITACION_ANTERIOR_AL_EVENTO";
		public static final String FICHA_DUPLICADA = "FICHA_DUPLICADA";
		public static final String SOLAPAMIENTO = "SOLAPAMIENTO";
		public static final String SOLAPAMIENTO_FICHA_INCOMPLETA = "SOLAPAMIENTO_FICHA_INCOMPLETA";
		public static final String IN_ITINERE_REQUERIDO = "IN_ITINERE_REQUERIDO";
		public static final String CAMPO_REQUERIDO = "CAMPO_REQUERIDO";
		public static final String OBSERVACIONES_MUY_LARGAS = "OBSERVACIONES_MUY_LARGAS";
		public static final String LEGAJO_INEXISTENTE = "LEGAJO_INEXISTENTE";

		public static final List<String> TODOS = List.of(
				EVENTO_FUTURO, FECHAS_FIN_EXCLUYENTES, GRUPO_REQUERIDO, DETALLE_REQUERIDO,
				DETALLE_FUERA_DE_GRUPO, ALTA_SUPERA_999_DIAS, ALTA_ANTERIOR_AL_EVENTO,
				CITACION_ANTERIOR_AL_EVENTO, FICHA_DUPLICADA, SOLAPAMIENTO,
				SOLAPAMIENTO_FICHA_INCOMPLETA, IN_ITINERE_REQUERIDO, CAMPO_REQUERIDO,
				OBSERVACIONES_MUY_LARGAS, LEGAJO_INEXISTENTE);

		private Codigos() {
		}
	}
}
