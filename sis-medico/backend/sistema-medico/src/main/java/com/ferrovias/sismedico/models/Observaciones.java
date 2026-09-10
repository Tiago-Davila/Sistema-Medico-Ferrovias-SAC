package com.ferrovias.sismedico.models;

import java.util.Objects;

// El texto libre de una ficha médica.
// Intencional: es un tipo propio en vez de un String para que su toString() no imprima el
// contenido clínico por accidente en un log o en una traza de excepción.
public final class Observaciones {

	private final String texto;

	public Observaciones(String texto) {
		this.texto = texto;
	}

	// null si la ficha no tiene observaciones.
	public static Observaciones de(String texto) {
		return texto == null ? null : new Observaciones(texto);
	}

	public String texto() {
		return texto;
	}

	public int longitud() {
		return texto == null ? 0 : texto.length();
	}

	// Solo la longitud, nunca el contenido.
	@Override
	public String toString() {
		return "Observaciones[longitud=" + longitud() + "]";
	}

	@Override
	public boolean equals(Object otro) {
		return otro instanceof Observaciones o && Objects.equals(texto, o.texto);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(texto);
	}
}
