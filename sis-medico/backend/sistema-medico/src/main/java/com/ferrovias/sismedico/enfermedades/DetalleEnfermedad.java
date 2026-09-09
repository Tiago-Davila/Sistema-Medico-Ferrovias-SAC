package com.ferrovias.sismedico.enfermedades;

/**
 * Detalle de enfermedad: clasificación de segundo nivel, dependiente de un
 * grupo. Un detalle solo es válido dentro de su grupo (FR-010).
 *
 * @param id código con el que el operario lo tipea
 * @param grupoEnfermedadId grupo al que pertenece
 * @param descripcion texto del catálogo
 */
public record DetalleEnfermedad(int id, int grupoEnfermedadId, String descripcion) {
}
