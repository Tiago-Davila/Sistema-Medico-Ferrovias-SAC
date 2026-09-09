package com.ferrovias.sismedico.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Los catálogos que alimentan la selección por código tipeado. */
@AutoConfigureMockMvc
class ConsultaDeCatalogoTest extends BaseIntegracion {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithMockUser(username = "jperez")
	void los_grupos_se_listan_completos() throws Exception {
		mockMvc.perform(get("/api/enfermedades/grupos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(27))
				.andExpect(jsonPath("$[0].id").value(100))
				.andExpect(jsonPath("$[0].descripcion").isNotEmpty());
	}

	@Test
	@WithMockUser(username = "jperez")
	void los_detalles_cuelgan_de_su_grupo() throws Exception {
		mockMvc.perform(get("/api/enfermedades/grupos/100/detalles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(7))
				.andExpect(jsonPath("$[0].grupoEnfermedadId").value(100));
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_grupo_inexistente_devuelve_lista_vacia_y_no_404() throws Exception {
		// Para la pantalla es lo mismo: no hay detalles que ofrecer. El rechazo
		// del código lo hace el guardado con FR-010, que es donde el operario
		// puede corregirlo.
		mockMvc.perform(get("/api/enfermedades/grupos/88888/detalles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}
}
