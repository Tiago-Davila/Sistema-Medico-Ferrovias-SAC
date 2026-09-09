package com.ferrovias.sismedico.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-036e: ninguna operación de lectura ni de escritura pasa sin usuario
 * autenticado, incluidas las que no pasan por la pantalla.
 *
 * <p>Se prueba contra rutas que todavía no existen a propósito. Spring Security
 * rechaza antes de enrutar, así que la respuesta correcta es 401 y no 404: si
 * alguna vez devolviera 404, significaría que la petición llegó a atravesar la
 * cadena de seguridad.
 */
@AutoConfigureMockMvc
class AutenticacionRequeridaTest extends BaseIntegracion {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void sin_autenticar_la_consulta_de_un_empleado_da_401() throws Exception {
		mockMvc.perform(get("/api/empleados/4821"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sin_autenticar_el_alta_de_una_ficha_da_401() throws Exception {
		mockMvc.perform(post("/api/fichas"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sin_autenticar_la_baja_de_una_ficha_da_401() throws Exception {
		mockMvc.perform(delete("/api/fichas/1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void la_documentacion_de_openapi_tampoco_esta_abierta() throws Exception {
		// Describe la forma de los datos clínicos. No es una excepción.
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isUnauthorized());
	}
}
