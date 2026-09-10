package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Corregir el legajo de una ficha cargada contra el empleado equivocado. Cubre US3-10 y US3-11.
// Intencional: la unicidad y el solapamiento se evalúan contra el empleado de DESTINO. Evaluarlos
// contra el de origen dejaría entrar una ficha que choca con las que el destino ya tiene.
@AutoConfigureMockMvc
class ReasignacionLegajoTest extends BaseIntegracion {

	private static final int ORIGEN = 955001;
	private static final int DESTINO = 955002;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void prepararEmpleados() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				ORIGEN, "Paz", "Hugo", "Vía y Obras", "Oficial");
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				DESTINO, "Paz", "Hugo Alberto", "Vía y Obras", "Oficial");
	}

	private String cuerpo(int legajo, Long version, LocalDate evento, LocalDate citacion,
			LocalDate alta) {

		return """
				{
				  "version": %s,
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "ENFERMEDAD",
				  "inItinere": null,
				  "estabaEnServicio": false,
				  "horaAccidente": null,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": false,
				  "justificado": true,
				  "fechaCitacion": %s,
				  "fechaAlta": %s,
				  "grupoEnfermedad": 100,
				  "detalleEnfermedad": 1001,
				  "observaciones": null
				}
				""".formatted(
						version == null ? "null" : version.toString(), legajo, evento,
						citacion == null ? "null" : "\"" + citacion + "\"",
						alta == null ? "null" : "\"" + alta + "\"");
	}

	private long alta(int legajo, LocalDate evento, LocalDate citacion, LocalDate altaMedica)
			throws Exception {

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(legajo, null, evento, citacion, altaMedica)))
				.andExpect(status().isCreated());

		return jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, legajo, evento.toString());
	}

	private ResultActions editar(long id, String cuerpo) throws Exception {
		return mockMvc.perform(put("/api/fichas/{id}", id)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	@Test
	@WithMockUser(username = "jperez")
	void corregir_el_legajo_mueve_la_ficha_al_otro_empleado() throws Exception {
		// US3-10. Dos empleados con el mismo apellido: el operario cargó contra el equivocado.
		LocalDate evento = LocalDate.of(2026, 5, 4);
		long id = alta(ORIGEN, evento, evento.plusDays(6), null);

		editar(id, cuerpo(DESTINO, 0L, evento, evento.plusDays(6), null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.datos.legajo").value(DESTINO));

		mockMvc.perform(get("/api/empleados/{legajo}/fichas", ORIGEN))
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/empleados/{legajo}/fichas", DESTINO))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_legajo_de_destino_se_valida_contra_su_propio_padron() throws Exception {
		// FR-002 al editar: mover una ficha a un legajo inexistente sería crear una referencia
		// colgada a propósito, que es justo lo que D3 acepta solo cuando no puede evitarlo.
		LocalDate evento = LocalDate.of(2026, 5, 4);
		long id = alta(ORIGEN, evento, evento.plusDays(6), null);

		editar(id, cuerpo(999_999, 0L, evento, evento.plusDays(6), null))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='LEGAJO_INEXISTENTE')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void la_ficha_choca_contra_una_del_destino_con_la_misma_fecha_de_evento() throws Exception {
		// US3-11. El origen no tenía conflicto; el destino sí. Evaluar la unicidad contra el
		// legajo viejo dejaría pasar el duplicado.
		LocalDate evento = LocalDate.of(2026, 5, 4);
		long id = alta(ORIGEN, evento, evento.plusDays(6), null);
		alta(DESTINO, evento, evento.plusDays(3), null);

		editar(id, cuerpo(DESTINO, 0L, evento, evento.plusDays(6), null))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='FICHA_DUPLICADA')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void la_ficha_choca_por_solapamiento_contra_el_periodo_del_destino() throws Exception {
		// US3-11 en su otra forma: no comparten fecha de evento, pero sí se pisan los períodos.
		long id = alta(ORIGEN, LocalDate.of(2026, 5, 4), null, LocalDate.of(2026, 5, 20));
		alta(DESTINO, LocalDate.of(2026, 5, 10), null, LocalDate.of(2026, 5, 25));

		editar(id, cuerpo(DESTINO, 0L, LocalDate.of(2026, 5, 4), null, LocalDate.of(2026, 5, 20)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_rechazo_deja_la_ficha_donde_estaba() throws Exception {
		// La transacción tiene que deshacerse entera: una ficha a medio mover es un dato perdido
		// para los dos empleados a la vez.
		LocalDate evento = LocalDate.of(2026, 5, 4);
		long id = alta(ORIGEN, evento, evento.plusDays(6), null);
		alta(DESTINO, evento, evento.plusDays(3), null);

		editar(id, cuerpo(DESTINO, 0L, evento, evento.plusDays(6), null))
				.andExpect(status().isUnprocessableEntity());

		assertThat(jdbc.queryForObject("SELECT legajo FROM ficha_medica WHERE id = ?",
				Integer.class, id))
				.isEqualTo(ORIGEN);
	}
}
