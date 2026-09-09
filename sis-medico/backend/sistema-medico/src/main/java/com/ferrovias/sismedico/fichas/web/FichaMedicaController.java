package com.ferrovias.sismedico.fichas.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.enfermedades.CatalogoRepositorio;
import com.ferrovias.sismedico.fichas.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.fichas.FichaMedicaServicio;
import com.ferrovias.sismedico.fichas.web.dto.FichaEntradaDTO;
import com.ferrovias.sismedico.fichas.web.dto.FichaSalidaDTO;
import com.ferrovias.sismedico.fichas.web.dto.RespuestaDeEscrituraDTO;

/**
 * La API de fichas médicas.
 *
 * <p><b>Traduce HTTP y nada más.</b> No hay una sola regla de negocio en esta
 * clase, ni una validación, ni un valor por defecto. Todo eso vive en
 * {@link FichaMedicaServicio}: si estuviera acá, agregar un segundo punto de
 * entrada alcanzaría para saltearlo.
 *
 * <p>Los errores tampoco se manejan acá. Las excepciones suben al
 * {@code ManejadorGlobalDeErrores}, que las traduce a {@code problem+json} en un
 * solo lugar, para que el frontend mire una sola forma de error.
 */
@RestController
@RequestMapping("/api/fichas")
public class FichaMedicaController {

	private final FichaMedicaServicio servicio;
	private final CatalogoRepositorio catalogo;
	private final CalculadorDiasPerdidos calculador;

	public FichaMedicaController(FichaMedicaServicio servicio, CatalogoRepositorio catalogo,
			CalculadorDiasPerdidos calculador) {

		this.servicio = servicio;
		this.catalogo = catalogo;
		this.calculador = calculador;
	}

	/**
	 * Alta de una ficha (US1).
	 *
	 * <p><b>Guardado en un solo paso.</b> Devuelve 201 con la envoltura
	 * {@code { datos, advertencias }}, siempre presente aunque vacía (M2). Una
	 * advertencia nunca cambia el código de estado y no hay confirmación en dos
	 * pasos: exigiría un modal o un segundo viaje, y las dos cosas pelean contra
	 * la carga por teclado y contra SC-001.
	 *
	 * <p>Si la ficha viola reglas, el servicio lanza y el manejador global
	 * devuelve 422 con <b>todas</b> las violaciones (M1).
	 */
	@PostMapping
	public ResponseEntity<RespuestaDeEscrituraDTO> darDeAlta(@RequestBody FichaEntradaDTO entrada) {
		var resultado = servicio.darDeAlta(entrada.aDominio(null));

		// Al dar de alta la ficha es nueva, así que no puede ser inconsistente:
		// acaba de pasar todas las validaciones. El cálculo de inconsistencia
		// para las históricas llega con US2.
		FichaSalidaDTO datos = FichaSalidaDTO.de(
				resultado.ficha(), catalogo, calculador, false, List.of());

		return ResponseEntity
				.created(URI.create("/api/fichas/" + datos.id()))
				.body(new RespuestaDeEscrituraDTO(datos, resultado.advertencias()));
	}
}
