package com.ferrovias.sismedico.enfermedades.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.enfermedades.CatalogoRepositorio;
import com.ferrovias.sismedico.enfermedades.DetalleEnfermedad;
import com.ferrovias.sismedico.enfermedades.GrupoEnfermedad;

/**
 * Los catálogos de enfermedad, para la selección por código tipeado.
 *
 * <p>Se cargan una vez al abrir la pantalla: son pocos registros y estables. El
 * operario no navega estas listas, tipea el código que ya se sabe; la lista está
 * para quien no lo recuerda.
 *
 * <p>Solo lectura. La administración del catálogo está fuera de alcance: se
 * importa una vez desde el sistema anterior y evoluciona por migración.
 */
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

	/**
	 * Detalles de un grupo.
	 *
	 * <p>Un grupo inexistente devuelve lista vacía, no 404. Para la pantalla es
	 * lo mismo —no hay detalles que ofrecer— y el rechazo del código lo hace el
	 * guardado, con FR-010, que es donde el operario puede corregirlo.
	 */
	@GetMapping("/grupos/{grupoEnfermedadId}/detalles")
	public List<DetalleEnfermedad> detalles(@PathVariable int grupoEnfermedadId) {
		return catalogo.detallesDe(grupoEnfermedadId);
	}
}
