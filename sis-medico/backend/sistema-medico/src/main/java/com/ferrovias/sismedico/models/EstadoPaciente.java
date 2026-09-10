package com.ferrovias.sismedico.models;

// Estado del paciente: solo dos valores. No agregar más sin que el cliente lo pida.
public enum EstadoPaciente {

	ACCIDENTADO("A"),
	ENFERMEDAD("E");

	private final String codigo;

	EstadoPaciente(String codigo) {
		this.codigo = codigo;
	}

	// Lo que va en la columna estado_paciente.
	public String codigo() {
		return codigo;
	}

	// Traduce el código guardado en la base; null si no lo reconoce, sin lanzar excepción.
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
