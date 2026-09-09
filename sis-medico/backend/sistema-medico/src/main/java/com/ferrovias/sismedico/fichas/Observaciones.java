package com.ferrovias.sismedico.fichas;

import java.util.Objects;

/**
 * El texto libre de una ficha médica.
 *
 * <p>Existe como tipo propio por una sola razón: que su {@code toString()} no
 * devuelva el contenido. Es el campo más sensible de la ficha —lo escribe el
 * servicio médico y describe el cuadro del empleado— y en un {@code String}
 * pelado termina en cualquier log, en cualquier traza de excepción y en
 * cualquier mensaje de error que alguien arme con concatenación, sin que haga
 * falta que nadie lo decida (FR-035, M3).
 *
 * <p>Envolverlo no impide leerlo: {@link #texto()} sigue disponible para quien
 * lo necesita de verdad. Lo que impide es que se filtre <b>por accidente</b>,
 * que es como se filtra siempre.
 *
 * <p><b>No valida nada.</b> El tope de 500 caracteres de FR-006b lo aplica
 * {@link ValidadorFichaMedica} al guardar. Acá tiene que poder entrar una
 * observación histórica de mil caracteres, porque FR-006c manda mostrarla
 * completa y sin truncar.
 */
public final class Observaciones {

	private final String texto;

	public Observaciones(String texto) {
		this.texto = texto;
	}

	/** {@code null} si la ficha no tiene observaciones. */
	public static Observaciones de(String texto) {
		return texto == null ? null : new Observaciones(texto);
	}

	public String texto() {
		return texto;
	}

	public int longitud() {
		return texto == null ? 0 : texto.length();
	}

	/**
	 * Solo la longitud. Nunca el contenido.
	 *
	 * <p>La longitud sí sirve: es lo que hace falta para diagnosticar un rechazo
	 * por FR-006b o para entender una ficha histórica marcada como inconsistente,
	 * y no dice nada del paciente.
	 */
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
