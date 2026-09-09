package com.ferrovias.sismedico.comun;

/**
 * Algo que conviene que el operario sepa, y que <b>no</b> impide guardar.
 *
 * <p>La distinción con {@link Violacion} no es de matiz. Una advertencia viaja
 * en el cuerpo de una respuesta exitosa, con el guardado ya hecho: nunca cambia
 * el código de estado y nunca abre una confirmación en dos pasos (M2). Un
 * segundo viaje o un diálogo pelearían contra la carga por teclado y contra
 * SC-001.
 *
 * <p>Hoy hay una sola: {@code EVENTO_ANTIGUO}, de FR-019. En el sistema de
 * referencia el bloqueo por antigüedad está deliberadamente desactivado, y
 * FR-019 exige que acá tampoco bloquee nunca. Si alguna vez alguien la convierte
 * en {@link Violacion}, está cambiando una regla de negocio.
 *
 * @param campo campo que la origina, con el nombre que usa la API
 * @param codigo código estable de [contracts/api.md]
 * @param mensaje texto para el operario, sin contenido clínico (FR-035)
 */
public record Advertencia(String campo, String codigo, String mensaje) {

	/** FR-019: la fecha del evento tiene más de 45 días. Nunca bloquea. */
	public static final String EVENTO_ANTIGUO = "EVENTO_ANTIGUO";

	public static Advertencia eventoAntiguo(long diasDeAntiguedad) {
		return new Advertencia("fechaEvento", EVENTO_ANTIGUO,
				"La fecha del evento tiene " + diasDeAntiguedad + " días de antigüedad.");
	}
}
