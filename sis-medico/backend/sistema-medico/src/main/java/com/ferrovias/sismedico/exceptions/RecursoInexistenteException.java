package com.ferrovias.sismedico.exceptions;

// No existe lo que se pidió: un legajo fuera del padrón, o una ficha inexistente o eliminada.
// Intencional: una ficha eliminada se trata como inexistente (404, no 410) para no confirmar que
// alguna vez existió.
public class RecursoInexistenteException extends RuntimeException {

	public RecursoInexistenteException(String mensaje) {
		super(mensaje);
	}
}
