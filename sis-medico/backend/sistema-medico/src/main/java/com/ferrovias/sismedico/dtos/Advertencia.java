package com.ferrovias.sismedico.dtos;

// Algo que conviene que el operario sepa, y que no impide guardar.
// Intencional: a diferencia de Violacion, viaja en una respuesta exitosa y nunca cambia el
// código de estado. El bloqueo por antigüedad está desactivado a propósito en este sistema;
// si alguien convierte esto en una Violacion, está cambiando una regla de negocio.
public record Advertencia(String campo, String codigo, String mensaje) {

	public static final String EVENTO_ANTIGUO = "EVENTO_ANTIGUO";

	public static Advertencia eventoAntiguo(long diasDeAntiguedad) {
		return new Advertencia("fechaEvento", EVENTO_ANTIGUO,
				"La fecha del evento tiene " + diasDeAntiguedad + " días de antigüedad.");
	}
}
