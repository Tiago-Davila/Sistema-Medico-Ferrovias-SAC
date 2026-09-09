package com.ferrovias.sismedico.empleados;

/**
 * El padrón externo no responde.
 *
 * <p>Es una dependencia dura de FR-001: sin padrón no se puede confirmar la
 * identidad del empleado, y por lo tanto no se puede crear ni editar una ficha.
 *
 * <p><b>Supuesto sin confirmar (hueco CHK040)</b>: el plan asume que en ese caso
 * el alta y la edición devuelven 503, y que la consulta de fichas ya cargadas
 * sigue disponible mostrando el legajo sin apellido ni nombre. Falta que el
 * cliente confirme si ese comportamiento degradado es aceptable o si la
 * aplicación debe quedar fuera de servicio.
 */
public class PadronNoDisponibleException extends RuntimeException {

	public PadronNoDisponibleException(Throwable causa) {
		super("El padrón de empleados no está disponible.", causa);
	}
}
