package com.ferrovias.sismedico.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.models.DetalleEnfermedad;
import com.ferrovias.sismedico.models.GrupoEnfermedad;
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;

// Los catálogos de grupo y detalle de enfermedad, de solo lectura.
@RestController
@RequestMapping("/api/enfermedades")
public class CatalogoController {

	private final CatalogoRepositorio catalogo;

	public CatalogoController(CatalogoRepositorio catalogo) {
		this.catalogo = catalogo;
	}

	@GetMapping("/grupos")
	public List<GrupoEnfermedad> grupos() {
		return catalogo.grupos();
	}

	// Detalles de un grupo; lista vacía si el grupo no existe, nunca 404.
	@GetMapping("/grupos/{grupoEnfermedadId}/detalles")
	public List<DetalleEnfermedad> detalles(@PathVariable int grupoEnfermedadId) {
		return catalogo.detallesDe(grupoEnfermedadId);
	}
}
