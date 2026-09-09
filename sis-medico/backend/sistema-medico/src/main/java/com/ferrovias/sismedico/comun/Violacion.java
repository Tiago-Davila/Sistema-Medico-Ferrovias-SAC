package com.ferrovias.sismedico.comun;

import java.util.List;

/**
 * Un motivo por el que una ficha no se puede guardar.
 *
 * <p>Siempre lleva el campo que la origina, para que la pantalla la muestre
 * junto a su control y el operario sepa qué corregir sin buscarlo (SC-005).
 *
 * <p>El mensaje es para el operario y por lo tanto <b>nunca</b> repite el
 * contenido clínico de la ficha: dice qué regla se violó, no qué dice la
 * observación ni qué enfermedad se cargó (FR-035, M3).
 *
 * @param campo campo de la ficha, con el nombre que usa la API
 * @param codigo código estable de [contracts/api.md]
 * @param mensaje texto para el operario
 * @param fichaEnConflicto solo para los dos códigos de solapamiento, que por
 *     FR-015 y FR-014d tienen que identificar la ficha con la que se choca.
 *     {@code null} en todos los demás casos.
 */
public record Violacion(
		String campo,
		String codigo,
		String mensaje,
		FichaEnConflicto fichaEnConflicto) {

	public Violacion(String campo, String codigo, String mensaje) {
		this(campo, codigo, mensaje, null);
	}

	/**
	 * Identificación mínima de la ficha con la que se choca. Solo fecha de
	 * evento y si está incompleta: lo justo para que el operario la ubique en el
	 * listado. Nada de contenido clínico.
	 */
	public record FichaEnConflicto(long id, java.time.LocalDate fechaEvento, boolean incompleta) {
	}

	/** Códigos de [contracts/api.md]. No se inventan códigos fuera de esta lista. */
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
