package com.ferrovias.sismedico.enfermedades;

/**
 * Grupo de enfermedad: clasificación de primer nivel del catálogo.
 *
 * @param id código con el que el operario lo tipea
 * @param descripcion texto del catálogo
 */
public record GrupoEnfermedad(int id, String descripcion) {
}
