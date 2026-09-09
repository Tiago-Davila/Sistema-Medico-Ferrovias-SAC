package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vuelca el contrato de OpenAPI a un archivo, para que el frontend derive de ahí
 * sus esquemas Zod.
 *
 * <p>El backend es la autoridad de todas las reglas (FR-018). Si el frontend
 * escribiera sus esquemas a mano, tendríamos dos copias de las mismas reglas y
 * la divergencia sería cuestión de tiempo: el operario vería un error distinto
 * del que aplica el servidor, que es peor que no validar en el cliente.
 *
 * <p>Está como test y no como tarea de Gradle a propósito. Generarlo exige
 * levantar la aplicación con su base, así que ya estamos en la suite de
 * integración; y al ser un test, si el contrato deja de generarse la suite se
 * pone roja en lugar de dejar un archivo viejo circulando.
 */
@AutoConfigureMockMvc
class ContratoOpenApiTest extends BaseIntegracion {

	/**
	 * Fuera de {@code build/} a propósito: lo consume el frontend, que es otro
	 * proyecto, y tiene que sobrevivir a un {@code clean}.
	 */
	private static final Path DESTINO = Path.of("../../frontend/lib/contrato-openapi.json");

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(username = "generador")
	void vuelca_el_contrato_para_el_frontend() throws Exception {
		String contrato = mockMvc.perform(get("/v3/api-docs"))
				.andReturn().getResponse().getContentAsString();

		// Si esto falla, el frontend estaría derivando esquemas de un contrato
		// vacío y no se notaría hasta que un formulario deje de validar.
		assertThat(contrato)
				.contains("/api/fichas")
				.contains("/api/empleados/{legajo}")
				.contains("/api/enfermedades/grupos")
				.contains("FichaEntradaDTO");

		// M4: el contrato tampoco puede ofrecer dónde mandar los días perdidos.
		assertThat(contrato.split("\"FichaEntradaDTO\"")[1].split("\\}\\s*,\\s*\"")[0])
				.as("el DTO de entrada no puede exponer diasPerdidos (M4)")
				.doesNotContain("diasPerdidos");

		Files.createDirectories(DESTINO.getParent());
		Files.writeString(DESTINO, contrato, StandardCharsets.UTF_8);
	}
}
