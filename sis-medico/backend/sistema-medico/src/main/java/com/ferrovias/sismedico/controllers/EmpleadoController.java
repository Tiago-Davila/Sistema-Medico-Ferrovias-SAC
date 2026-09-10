package com.ferrovias.sismedico.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.dtos.FichaResumenDTO;
import com.ferrovias.sismedico.exceptions.RecursoInexistenteException;
import com.ferrovias.sismedico.models.Empleado;
import com.ferrovias.sismedico.repositories.PadronRepositorio;
import com.ferrovias.sismedico.service.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.service.EvaluadorInconsistencia;
import com.ferrovias.sismedico.service.FichaMedicaServicio;

// Consulta de solo lectura al padrón de empleados. No hay alta, modificación ni baja acá.
@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

	private final PadronRepositorio padron;
	private final FichaMedicaServicio fichas;
	private final CalculadorDiasPerdidos calculador;
	private final EvaluadorInconsistencia evaluador;

	public EmpleadoController(PadronRepositorio padron, FichaMedicaServicio fichas,
			CalculadorDiasPerdidos calculador, EvaluadorInconsistencia evaluador) {

		this.padron = padron;
		this.fichas = fichas;
		this.calculador = calculador;
		this.evaluador = evaluador;
	}

	// Busca un empleado por legajo; 404 si no existe en el padrón.
	@GetMapping("/{legajo}")
	public Empleado buscar(@PathVariable int legajo) {
		return padron.buscar(legajo).orElseThrow(() -> new RecursoInexistenteException(
				"El legajo " + legajo + " no existe en el padrón."));
	}

	// Fichas del empleado, de la más reciente a la más vieja; lista vacía si no tiene ninguna.
	// Intencional: no se consulta el padrón acá. Con el padrón caído no se pueden cargar ni
	// editar fichas, pero el operario tiene que poder seguir consultando lo ya cargado.
	@GetMapping("/{legajo}/fichas")
	public List<FichaResumenDTO> fichasDe(@PathVariable int legajo) {
		return fichas.listarDe(legajo).stream()
				.map(ficha -> FichaResumenDTO.de(ficha, calculador, evaluador.evaluar(ficha)))
				.toList();
	}
}
