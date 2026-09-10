package com.ferrovias.sismedico.exceptions;

import java.util.List;

import com.ferrovias.sismedico.dtos.Violacion;

// La ficha viola una o más reglas de negocio y no se puede guardar; lleva todas las violaciones.
// Intencional: el mensaje de la excepción solo enumera códigos, nunca contenido clínico, porque
// termina en el log.
public class FichaInvalidaException extends RuntimeException {

	private final transient List<Violacion> violaciones;

	public FichaInvalidaException(List<Violacion> violaciones) {
		super("La ficha tiene " + violaciones.size() + " violaciones: "
				+ violaciones.stream().map(Violacion::codigo).toList());
		this.violaciones = List.copyOf(violaciones);
	}

	public List<Violacion> violaciones() {
		return violaciones;
	}
}
