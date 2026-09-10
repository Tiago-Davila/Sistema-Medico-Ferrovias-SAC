package com.ferrovias.sismedico.exceptions;

// El padrón externo no responde; sin padrón no se puede confirmar la identidad del empleado.
public class PadronNoDisponibleException extends RuntimeException {

	public PadronNoDisponibleException(Throwable causa) {
		super("El padrón de empleados no está disponible.", causa);
	}
}
