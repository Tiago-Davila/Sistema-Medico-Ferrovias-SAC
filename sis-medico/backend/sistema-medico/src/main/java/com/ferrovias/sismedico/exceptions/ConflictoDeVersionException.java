package com.ferrovias.sismedico.exceptions;

// La ficha cambió desde que el operario la abrió: se lanza cuando el UPDATE optimista afecta cero filas.
public class ConflictoDeVersionException extends RuntimeException {

	private final long id;

	public ConflictoDeVersionException(long id) {
		super("La ficha " + id + " fue modificada por otro usuario.");
		this.id = id;
	}

	public long id() {
		return id;
	}
}
