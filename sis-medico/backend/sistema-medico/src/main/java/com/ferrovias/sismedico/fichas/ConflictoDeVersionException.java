package com.ferrovias.sismedico.fichas;

/**
 * La ficha cambió desde que el operario la abrió (FR-037, D2).
 *
 * <p>Se lanza cuando el {@code UPDATE ... WHERE id = ? AND version = ?} afecta
 * cero filas. El segundo guardado se rechaza y los cambios del primero quedan
 * intactos: no se fusiona nada automáticamente y no se pisa nada en silencio
 * (SC-008).
 */
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
