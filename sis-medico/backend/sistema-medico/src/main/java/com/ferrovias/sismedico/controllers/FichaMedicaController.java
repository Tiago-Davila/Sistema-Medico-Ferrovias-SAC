package com.ferrovias.sismedico.controllers;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.dtos.FichaEntradaDTO;
import com.ferrovias.sismedico.dtos.FichaSalidaDTO;
import com.ferrovias.sismedico.dtos.RespuestaDeEscrituraDTO;
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;
import com.ferrovias.sismedico.service.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.service.EvaluadorInconsistencia;
import com.ferrovias.sismedico.service.FichaMedicaServicio;

// La API de fichas médicas. Solo traduce HTTP; toda regla de negocio vive en FichaMedicaServicio.
@RestController
@RequestMapping("/api/fichas")
public class FichaMedicaController {

	private final FichaMedicaServicio servicio;
	private final CatalogoRepositorio catalogo;
	private final CalculadorDiasPerdidos calculador;
	private final EvaluadorInconsistencia evaluador;

	public FichaMedicaController(FichaMedicaServicio servicio, CatalogoRepositorio catalogo,
			CalculadorDiasPerdidos calculador, EvaluadorInconsistencia evaluador) {

		this.servicio = servicio;
		this.catalogo = catalogo;
		this.calculador = calculador;
		this.evaluador = evaluador;
	}

	// La ficha completa, tal como está almacenada; 404 si no existe o fue eliminada.
	@GetMapping("/{id}")
	public FichaSalidaDTO buscar(@PathVariable long id) {
		var ficha = servicio.buscar(id);
		return FichaSalidaDTO.de(ficha, catalogo, calculador, evaluador.evaluar(ficha));
	}

	// Da de alta una ficha y devuelve 201 con la ficha guardada y sus advertencias.
	@PostMapping
	public ResponseEntity<RespuestaDeEscrituraDTO> darDeAlta(@RequestBody FichaEntradaDTO entrada) {
		var resultado = servicio.darDeAlta(entrada.aDominio(null));

		// Una ficha recién creada nunca es inconsistente: acaba de pasar todas las validaciones.
		FichaSalidaDTO datos = FichaSalidaDTO.de(resultado.ficha(), catalogo, calculador,
				EvaluadorInconsistencia.Resultado.CONSISTENTE);

		return ResponseEntity
				.created(URI.create("/api/fichas/" + datos.id()))
				.body(new RespuestaDeEscrituraDTO(datos, resultado.advertencias()));
	}

	// Modifica una ficha, incluida la reasignación de legajo; 409 si otro operario ya guardó.
	@PutMapping("/{id}")
	public RespuestaDeEscrituraDTO modificar(@PathVariable long id,
			@RequestBody FichaEntradaDTO entrada) {

		var resultado = servicio.modificar(id, entrada.version(), entrada.aDominio(id));

		// Igual que en el alta: acaba de pasar todas las validaciones, así que ya no queda nada
		// inconsistente que señalar, ni siquiera si la ficha venía del histórico.
		FichaSalidaDTO datos = FichaSalidaDTO.de(resultado.ficha(), catalogo, calculador,
				EvaluadorInconsistencia.Resultado.CONSISTENTE);

		// Misma envoltura que el alta, para que el frontend mire un solo lugar (M2).
		return new RespuestaDeEscrituraDTO(datos, resultado.advertencias());
	}

	// Elimina una ficha; la versión va en la consulta para que una baja tampoco pise un cambio ajeno.
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void eliminar(@PathVariable long id, @RequestParam Long version) {
		servicio.darDeBaja(id, version);
	}
}
