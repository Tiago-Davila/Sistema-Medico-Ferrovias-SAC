package com.ferrovias.sismedico.fichas;

import java.util.List;

import com.ferrovias.sismedico.comun.Violacion;

/**
 * La ficha viola una o más reglas de negocio y no se puede guardar.
 *
 * <p>Lleva <b>todas</b> las violaciones detectadas, no la primera (M1). El
 * operario carga dieciséis campos de una vez: devolverle un error por viaje lo
 * obliga a tantos viajes como errores tenga, que es lo contrario de SC-001.
 *
 * <p>El mensaje solo enumera <b>códigos</b>. No incluye ni el contenido de la
 * ficha ni los mensajes para el operario, porque una excepción termina en el log
 * y el log no puede llevar datos clínicos (FR-035, M3).
 */
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
