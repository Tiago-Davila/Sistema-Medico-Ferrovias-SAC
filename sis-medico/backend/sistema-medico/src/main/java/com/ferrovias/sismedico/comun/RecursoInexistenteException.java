package com.ferrovias.sismedico.comun;

/**
 * No existe lo que se pidió: un legajo que no está en el padrón, o una ficha
 * inexistente o eliminada.
 *
 * <p>Una ficha eliminada se trata como inexistente a propósito: FR-039c dice que
 * no debe ser visible para el operario por ningún medio, y devolver 404 en lugar
 * de 410 evita confirmar que alguna vez existió.
 *
 * <p>El mensaje identifica el recurso por su clave, nunca por su contenido.
 */
public class RecursoInexistenteException extends RuntimeException {

	public RecursoInexistenteException(String mensaje) {
		super(mensaje);
	}
}
