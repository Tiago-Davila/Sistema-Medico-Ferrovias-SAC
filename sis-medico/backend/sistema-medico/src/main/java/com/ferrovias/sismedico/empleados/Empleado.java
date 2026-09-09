package com.ferrovias.sismedico.empleados;

/**
 * Un empleado del plantel, tal como lo devuelve el padrón.
 *
 * <p>Esta aplicación <b>no lo administra ni lo guarda</b> (FR-003, FR-003b). Es
 * el resultado de una consulta, no una entidad propia: no hay tabla
 * {@code empleado} en el esquema y no puede haberla.
 *
 * <p>Ojo con {@code categoriaLaboral}: en el padrón "categoría" es la categoría
 * laboral del empleado, y en la pantalla de la ficha "categoría" es el grupo de
 * enfermedad. Son dos cosas distintas y ninguna se llama "categoria" a secas.
 */
public record Empleado(
		int legajo,
		String apellido,
		String nombre,
		String seccion,
		String categoriaLaboral) {
}
