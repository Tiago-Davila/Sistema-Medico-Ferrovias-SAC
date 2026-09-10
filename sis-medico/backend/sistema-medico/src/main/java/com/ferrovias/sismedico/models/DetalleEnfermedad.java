package com.ferrovias.sismedico.models;

// Detalle de enfermedad: clasificación de segundo nivel, válida solo dentro de su grupo.
public record DetalleEnfermedad(int id, int grupoEnfermedadId, String descripcion) {
}
