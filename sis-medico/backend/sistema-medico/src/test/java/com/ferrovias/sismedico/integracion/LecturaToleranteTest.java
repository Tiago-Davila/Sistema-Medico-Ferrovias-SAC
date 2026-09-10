package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// La mitad del riesgo de la feature: leer fichas que violan las reglas de hoy sin romperse.
// Cubre US2-2, US2-3 y SC-002.
@AutoConfigureMockMvc
class LecturaToleranteTest extends BaseIntegracion {

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

	private long idDe(int legajo, String fechaEvento) {
		return jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, legajo, fechaEvento);
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_dataset_historico_completo_se_recorre_sin_un_solo_error() throws Exception {
		// SC-002. Es el test que justifica que el juego de datos entre sin filtrar: si alguna
		// ficha del volcado rompiera al abrirse, el operario se quedaría sin poder verla.
		List<Long> ids = jdbc.queryForList("SELECT id FROM ficha_medica", Long.class);
		assertThat(ids).hasSize(670 + 16);

		for (Long id : ids) {
			mockMvc.perform(get("/api/fichas/{id}", id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id));
		}
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_sin_grupo_ni_detalle_se_abre_entera_y_queda_senalada() throws Exception {
		// US2-2 y FR-028: anterior a la clasificación de 2016. Se muestra con lo que sí tiene.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990001, "2014-03-11")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grupoEnfermedad").doesNotExist())
				.andExpect(jsonPath("$.detalleEnfermedad").doesNotExist())
				.andExpect(jsonPath("$.fechaAlta").value("2014-03-20"))
				.andExpect(jsonPath("$.incompleta").value(true))
				.andExpect(jsonPath("$.motivosInconsistencia[?(@=='SIN_CLASIFICACION')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_codigo_huerfano_sale_con_su_id_y_sin_descripcion() throws Exception {
		// FR-028b. Poner el id en null "para que no moleste" borraría el único rastro de qué
		// se había clasificado, y el operario no tendría con qué corregirlo.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990002, "2016-02-15")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grupoEnfermedad.id").value(88888))
				.andExpect(jsonPath("$.grupoEnfermedad.descripcion").doesNotExist())
				.andExpect(jsonPath("$.detalleEnfermedad.id").value(888888))
				.andExpect(jsonPath("$.motivosInconsistencia[?(@=='CODIGO_HUERFANO')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_sin_ninguna_fecha_de_fin_se_abre_igual() throws Exception {
		// FR-014c la usa para bloquear cargas nuevas, pero eso es una regla de escritura: leerla
		// no puede fallar.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990003, "2019-04-12")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaCitacion").doesNotExist())
				.andExpect(jsonPath("$.fechaAlta").doesNotExist())
				.andExpect(jsonPath("$.motivosInconsistencia[?(@=='SIN_FECHA_FIN')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void los_booleanos_sin_responder_salen_vacios_y_no_en_no() throws Exception {
		// FR-004d: null y false son cosas distintas. Mostrar "no" donde nadie respondió
		// inventaría un dato que el sistema viejo nunca tuvo.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990009, "2016-11-03")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inItinere").doesNotExist())
				.andExpect(jsonPath("$.estabaEnServicio").doesNotExist())
				.andExpect(jsonPath("$.justificado").doesNotExist())
				.andExpect(jsonPath("$.motivosInconsistencia[?(@=='CAMPO_SIN_RESPONDER')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_observacion_de_mas_de_500_caracteres_sale_completa() throws Exception {
		// FR-006c: el tope es de escritura. Truncar acá perdería texto clínico de forma
		// irreversible en la única pantalla donde se lo puede leer.
		String observaciones = jdbc.queryForObject(
				"SELECT observaciones FROM ficha_medica WHERE legajo = 990007", String.class);
		assertThat(observaciones.length()).isGreaterThan(500);

		mockMvc.perform(get("/api/fichas/{id}", idDe(990007, "2015-05-05")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.observaciones").value(observaciones))
				.andExpect(jsonPath("$.motivosInconsistencia[?(@=='OBSERVACIONES_EXCEDIDAS')]")
						.exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_con_las_dos_fechas_de_fin_se_lee_igual() throws Exception {
		// Viola FR-008 al escribir y aun así se abre: la validación corre solo al guardar (FR-030).
		mockMvc.perform(get("/api/fichas/{id}", idDe(990008, "2015-09-01")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaCitacion").value("2015-09-10"))
				.andExpect(jsonPath("$.fechaAlta").value("2015-09-20"));
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_de_un_legajo_que_ya_no_esta_en_el_padron_se_lee_igual() throws Exception {
		// La referencia colgada que D3 acepta de frente: el padrón dio de baja al empleado y la
		// ficha sigue existiendo. FR-028 manda mostrarla.
		mockMvc.perform(get("/api/empleados/{legajo}", 990012))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/fichas/{id}", idDe(990012, "2016-04-04")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.legajo").value(990012));

		mockMvc.perform(get("/api/empleados/{legajo}/fichas", 990012))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	@WithMockUser(username = "jperez")
	void navegar_entre_fichas_incompletas_no_exige_completar_nada() throws Exception {
		// US2-3. Abrir una tras otra son puros GET: ninguno puede devolver 422.
		var fichas = jdbc.queryForList(
				"SELECT id FROM ficha_medica WHERE legajo BETWEEN 990001 AND 990012 "
						+ "ORDER BY fecha_evento DESC",
				Long.class);

		for (Long id : fichas) {
			mockMvc.perform(get("/api/fichas/{id}", id)).andExpect(status().isOk());
		}
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_inexistente_da_404() throws Exception {
		mockMvc.perform(get("/api/fichas/{id}", 99_999_999L))
				.andExpect(status().isNotFound());
	}
}
