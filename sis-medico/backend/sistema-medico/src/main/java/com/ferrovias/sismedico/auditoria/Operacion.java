package com.ferrovias.sismedico.auditoria;

/**
 * Qué se le hizo a una ficha. Las tres únicas escrituras posibles (FR-033).
 *
 * <p>El valor va a la columna {@code operacion}, que es {@code VARCHAR(12)}.
 */
public enum Operacion {

	ALTA,
	MODIFICACION,
	BAJA
}
