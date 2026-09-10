package com.ferrovias.sismedico.unitarios;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.comun.ManejadorGlobalDeErrores;
import com.ferrovias.sismedico.exceptions.RecursoInexistenteException;
import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.exceptions.PadronNoDisponibleException;
import com.ferrovias.sismedico.exceptions.ConflictoDeVersionException;
import com.ferrovias.sismedico.exceptions.FichaInvalidaException;

/**
 * Verifica la traducción de excepciones al formato del contrato.
 *
 * <p>Usa un controlador de mentira: lo que se prueba es el manejador, no una
 * ruta real. Así el test corre en la suite rápida, sin base ni contexto de
 * Spring completo.
 */
class ManejadorGlobalDeErroresTest {

	private MockMvc mockMvc;

	@BeforeEach
	void prepararMockMvc() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ControladorQueFalla())
				.setControllerAdvice(new ManejadorGlobalDeErrores())
				.build();
	}

	@Test
	void ficha_invalida_da_422_con_todas_las_violaciones() throws Exception {
		mockMvc.perform(get("/prueba/ficha-invalida"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.type").value("https://ferrovias/errores/ficha-invalida"))
				// M1: las dos, no la primera.
				.andExpect(jsonPath("$.violaciones.length()").value(2))
				.andExpect(jsonPath("$.violaciones[0].campo").value("fechaAlta"))
				.andExpect(jsonPath("$.violaciones[0].codigo").value("ALTA_ANTERIOR_AL_EVENTO"))
				.andExpect(jsonPath("$.violaciones[1].campo").value("detalleEnfermedad"))
				.andExpect(jsonPath("$.violaciones[1].codigo").value("DETALLE_FUERA_DE_GRUPO"));
	}

	@Test
	void el_solapamiento_identifica_la_ficha_en_conflicto() throws Exception {
		// FR-015 y FR-014d: sin este dato el rechazo no es accionable.
		mockMvc.perform(get("/prueba/solapamiento"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[0].codigo").value("SOLAPAMIENTO_FICHA_INCOMPLETA"))
				.andExpect(jsonPath("$.violaciones[0].fichaEnConflicto.id").value(3312))
				.andExpect(jsonPath("$.violaciones[0].fichaEnConflicto.fechaEvento").value("2019-04-12"))
				.andExpect(jsonPath("$.violaciones[0].fichaEnConflicto.incompleta").value(true));
	}

	@Test
	void conflicto_de_version_da_409() throws Exception {
		mockMvc.perform(get("/prueba/conflicto"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("https://ferrovias/errores/conflicto-de-version"));
	}

	@Test
	void recurso_inexistente_da_404() throws Exception {
		mockMvc.perform(get("/prueba/inexistente"))
				.andExpect(status().isNotFound());
	}

	@Test
	void padron_caido_da_503() throws Exception {
		mockMvc.perform(get("/prueba/padron-caido"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("https://ferrovias/errores/padron-no-disponible"));
	}

	@RestController
	static class ControladorQueFalla {

		@GetMapping("/prueba/ficha-invalida")
		void fichaInvalida() {
			throw new FichaInvalidaException(List.of(
					new Violacion("fechaAlta", Violacion.Codigos.ALTA_ANTERIOR_AL_EVENTO,
							"La fecha de alta no puede ser anterior a la del evento."),
					new Violacion("detalleEnfermedad", Violacion.Codigos.DETALLE_FUERA_DE_GRUPO,
							"El detalle no pertenece al grupo elegido.")));
		}

		@GetMapping("/prueba/solapamiento")
		void solapamiento() {
			throw new FichaInvalidaException(List.of(
					new Violacion("fechaEvento", Violacion.Codigos.SOLAPAMIENTO_FICHA_INCOMPLETA,
							"Choca con la ficha del 12/04/2019, que está incompleta. Cargale una "
									+ "fecha de citación o de alta para poder continuar.",
							new Violacion.FichaEnConflicto(3312,
									java.time.LocalDate.of(2019, 4, 12), true))));
		}

		@GetMapping("/prueba/conflicto")
		void conflicto() {
			throw new ConflictoDeVersionException(8842);
		}

		@GetMapping("/prueba/inexistente")
		void inexistente() {
			throw new RecursoInexistenteException("No existe la ficha 8842.");
		}

		@GetMapping("/prueba/padron-caido")
		void padronCaido() {
			throw new PadronNoDisponibleException(new java.sql.SQLException("sin conexión"));
		}
	}
}
