package com.ferrovias.sismedico.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** US1-1 y US1-2: confirmar la identidad antes de cargar nada. */
@AutoConfigureMockMvc
class ConsultaDeEmpleadoTest extends BaseIntegracion {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void cargarPadron() {
		new CargadorDatosHistoricos(jdbc, reloj).cargarTodo();
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_legajo_del_padron_devuelve_apellido_y_nombre() throws Exception {
		// US1-1: es lo que el operario mira para confirmar que es la persona
		// correcta antes de cargar el evento.
		mockMvc.perform(get("/api/empleados/900001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.legajo").value(900001))
				.andExpect(jsonPath("$.apellido").isNotEmpty())
				.andExpect(jsonPath("$.nombre").isNotEmpty())
				.andExpect(jsonPath("$.seccion").isNotEmpty())
				.andExpect(jsonPath("$.categoriaLaboral").isNotEmpty());
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_legajo_inexistente_da_404() throws Exception {
		// US1-2 y FR-002: se informa y no se habilita la carga.
		mockMvc.perform(get("/api/empleados/1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("https://ferrovias/errores/inexistente"));
	}

	@Test
	@WithMockUser(username = "jperez")
	void los_ceros_a_la_izquierda_no_cambian_el_empleado() throws Exception {
		// FR-004f: 000900001 y 900001 resuelven al mismo empleado, porque el
		// legajo se trata como entero.
		mockMvc.perform(get("/api/empleados/000900001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.legajo").value(900001));
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_legajo_dado_de_baja_en_el_padron_da_404_aunque_tenga_fichas() throws Exception {
		// La referencia colgada que D3 acepta: el padrón puede dar de baja un
		// legajo con fichas y el sistema no puede impedirlo. La consulta del
		// empleado devuelve 404, y al leer la ficha FR-028 manda mostrarla igual.
		mockMvc.perform(get("/api/empleados/990012"))
				.andExpect(status().isNotFound());
	}
}
