package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
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

// Los días perdidos como columna calculada (D4). Cubre US2-5, US2-6 y SC-006.
@AutoConfigureMockMvc
class DiasPerdidosTest extends BaseIntegracion {

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

	private Integer enLaBase(int legajo) {
		return jdbc.queryForObject("SELECT dias_perdidos FROM ficha_medica WHERE legajo = ?",
				Integer.class, legajo);
	}

	@Test
	@WithMockUser(username = "jperez")
	void con_fecha_de_alta_son_la_diferencia_entre_las_dos_fechas() throws Exception {
		// US2-5. 2018-06-01 a 2018-06-10.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990010, "2018-06-01")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diasPerdidos").value(9));
	}

	@Test
	@WithMockUser(username = "jperez")
	void sin_fecha_de_alta_quedan_vacios_y_no_en_cero() throws Exception {
		// US2-6 y FR-021. Cero significaría que el empleado se reincorporó el mismo día; vacío
		// significa que todavía no volvió. Son cosas distintas.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990003, "2019-04-12")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diasPerdidos").doesNotExist());

		assertThat(enLaBase(990003)).isNull();
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_historica_con_alta_anterior_al_evento_da_negativo_y_sale_asi() throws Exception {
		// SC-006 y FR-020c: sin topear. Mostrar cero escondería el dato roto y nadie lo
		// corregiría nunca.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990005, "2020-01-10")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diasPerdidos").value(-10))
				.andExpect(jsonPath("$.motivosInconsistencia[?(@=='DIAS_PERDIDOS_NEGATIVOS')]")
						.exists());

		assertThat(enLaBase(990005)).isEqualTo(-10);
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_historica_de_mas_de_999_dias_sale_con_su_valor_real() throws Exception {
		// SC-006. El tope de FR-011 es de escritura: al leer, 1095 son 1095.
		mockMvc.perform(get("/api/fichas/{id}", idDe(990006, "2017-01-01")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.diasPerdidos").value(1095));

		assertThat(enLaBase(990006)).isEqualTo(1095);
	}

	@Test
	void la_columna_calculada_coincide_con_lo_que_calcula_el_backend() {
		// D4: los dos caminos tienen que dar lo mismo sobre todo el dataset. Si divergen, uno de
		// los dos está mal y no hay forma de saber cuál mira el operario.
		Integer distintos = jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica
				WHERE fecha_alta IS NOT NULL
				  AND dias_perdidos <> DATEDIFF(day, fecha_evento, fecha_alta)
				""", Integer.class);

		assertThat(distintos).isZero();
	}

	@Test
	void la_columna_no_es_persistida_y_por_eso_no_se_puede_desincronizar() {
		// D4. Si alguien la volviera persistida, este test se pone rojo antes de que un UPDATE
		// de fechas deje los días perdidos viejos en la base.
		Integer persistida = jdbc.queryForObject("""
				SELECT CAST(is_persisted AS INT) FROM sys.computed_columns
				WHERE object_id = OBJECT_ID('ficha_medica') AND name = 'dias_perdidos'
				""", Integer.class);

		assertThat(persistida).isZero();
	}
}
