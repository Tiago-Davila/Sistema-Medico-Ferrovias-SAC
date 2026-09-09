package com.ferrovias.sismedico.fichas;

/**
 * Estado del paciente. Catálogo cerrado de exactamente dos valores (FR-005).
 *
 * <p>Verificado contra ochenta mil registros del sistema de referencia. Ese
 * sistema tiene ramas de código para otros dos estados que nunca se ejecutaron:
 * es código muerto y no se reimplementa. <b>No agregar valores acá</b> sin una
 * decisión del cliente.
 */
public enum EstadoPaciente {

	/** Accidente. Habilita in itinere y hora del accidente (FR-023). */
	ACCIDENTADO("A"),

	/** Enfermedad. Habilita envío de médico a domicilio (FR-025). */
	ENFERMEDAD("E");

	private final String codigo;

	EstadoPaciente(String codigo) {
		this.codigo = codigo;
	}

	/** Lo que va en la columna {@code estado_paciente}, que es CHAR(1). */
	public String codigo() {
		return codigo;
	}

	/**
	 * Traduce el código almacenado.
	 *
	 * <p>Devuelve {@code null} ante cualquier cosa que no reconozca, incluido
	 * {@code null}. <b>No lanza excepción a propósito</b>: FR-030 prohíbe validar
	 * al leer, y una ficha histórica con un estado que hoy no existe tiene que
	 * poder abrirse igual (FR-028). Rechazarla acá convertiría cada consulta en
	 * un error.
	 */
	public static EstadoPaciente desdeCodigo(String codigo) {
		if (codigo == null) {
			return null;
		}
		for (EstadoPaciente estado : values()) {
			if (estado.codigo.equals(codigo.trim())) {
				return estado;
			}
		}
		return null;
	}
}
