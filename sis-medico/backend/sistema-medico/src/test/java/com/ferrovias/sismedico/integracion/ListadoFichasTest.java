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

// El listado de fichas de un empleado. Cubre US2-1 y US2-4.
@AutoConfigureMockMvc
class ListadoFichasTest extends BaseIntegracion {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void cargar() {
		new CargadorDatosHistoricos(jdbc, reloj).cargarTodo();
	}

	@Test
	@WithMockUser(username = "jperez")
	void las_fichas_salen_de_la_mas_reciente_a_la_mas_vieja() throws Exception {
		// US2-1. El operario busca casi siempre la última: va primera.
		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990011))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].fechaEvento").value("2021-12-30"))
				.andExpect(jsonPath("$[1].fechaEvento").value("2017-08-14"))
				.andExpect(jsonPath("$[2].fechaEvento").value("2013-02-01"));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_listado_no_pagina_ni_filtra() throws Exception {
		// FR-031b y FR-031c: todas de una. Un empleado tiene decenas de fichas en su carrera, no
		// miles, y paginar obligaría al operario a acordarse en qué página quedó.
		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990011)
				.param("pagina", "2").param("tamaño", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_empleado_sin_fichas_devuelve_una_lista_vacia() throws Exception {
		// US2-4. La lista vacía es la respuesta correcta, no un 404: el empleado existe, lo que
		// no tiene es ficha. La pantalla lo dice con todas las letras.
		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 900999))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_listado_marca_las_incompletas() throws Exception {
		// FR-029: la señal viaja en el listado, no solo al abrir la ficha. Si no, el operario
		// tendría que abrirlas una por una para descubrir cuál necesita atención.
		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990001))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.incompleta==true)]").exists());

		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990010))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.incompleta==true)]").doesNotExist());
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_listado_no_lleva_observaciones_ni_codigos_de_enfermedad() throws Exception {
		// Principio VI: para elegir qué fila abrir no hace falta tener en pantalla los
		// diagnósticos de toda la carrera del empleado.
		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990011))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].observaciones").doesNotExist())
				.andExpect(jsonPath("$[0].grupoEnfermedad").doesNotExist())
				.andExpect(jsonPath("$[0].detalleEnfermedad").doesNotExist());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_eliminada_no_aparece_en_el_listado() throws Exception {
		// FR-039c, verificado desde acá además de desde US4: el listado es la pantalla donde el
		// operario la vería reaparecer.
		jdbc.update("UPDATE ficha_medica SET eliminada_por = 'jperez', "
				+ "eliminada_en = SYSUTCDATETIME() WHERE legajo = ? AND fecha_evento = ?",
				990011, "2021-12-30");

		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990011))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].fechaEvento").value("2017-08-14"));
	}
}
