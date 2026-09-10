package com.ferrovias.sismedico.models;

// Un empleado tal como lo devuelve el padrón externo; no se administra ni se guarda acá.
// Intencional: categoriaLaboral es la categoría laboral del padrón, distinta de la categoría de
// enfermedad de la ficha médica. Nunca "categoria" a secas.
public record Empleado(
		int legajo,
		String apellido,
		String nombre,
		String seccion,
		String categoriaLaboral) {
}
