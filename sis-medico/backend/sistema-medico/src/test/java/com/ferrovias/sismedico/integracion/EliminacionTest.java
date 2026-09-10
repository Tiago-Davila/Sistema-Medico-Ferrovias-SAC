package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.ferrovias.sismedico.models.Operacion;
import com.ferrovias.sismedico.repositories.AuditoriaRepositorio;

// El borrado lógico. Cubre US4-2 a US4-5 y SC-010.
@AutoConfigureMockMvc
class EliminacionTest extends BaseIntegracion {

	private static final int LEGAJO = 956001;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuditoriaRepositorio auditoria;

	@Autowired
	private Clock reloj;

	private LocalDate evento;

	@BeforeEach
	void prepararEmpleado() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Bravo", "Silvia", "Estaciones", "Administrativo");
		evento = LocalDate.now(reloj).minusDays(20);
	}

	private String cuerpo(LocalDate evento, LocalDate citacion, LocalDate alta) {
		return """
				{
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
				  "observaciones": "Cargada por error"
				}
				""".formatted(LEGAJO, evento,
						citacion == null ? "null" : "\"" + citacion + "\"",
						alta == null ? "null" : "\"" + alta + "\"");
	}

	private long alta(LocalDate evento, LocalDate citacion, LocalDate altaMedica) throws Exception {
		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(evento, citacion, altaMedica)))
				.andExpect(status().isCreated());

		return jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, LEGAJO, evento.toString());
	}

	@Test
	@WithMockUser(username = "jperez")
	void la_baja_devuelve_204_y_deja_su_asiento_de_auditoria() throws Exception {
		// US4-2, FR-033 y SC-004: la baja también es una escritura y deja su asiento.
		long id = alta(evento, evento.plusDays(7), null);

		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNoContent());

		assertThat(auditoria.historialDe(id))
				.extracting(AuditoriaRepositorio.Asiento::operacion)
				.containsExactly(Operacion.ALTA, Operacion.BAJA);

		var fila = jdbc.queryForMap(
				"SELECT eliminada_por, eliminada_en FROM ficha_medica WHERE id = ?", id);
		assertThat(fila.get("eliminada_por")).isEqualTo("jperez");
		assertThat(fila.get("eliminada_en")).isNotNull();
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_eliminada_no_aparece_por_ningun_medio() throws Exception {
		// US4-4 y FR-039c. Las dos vías de consulta que tiene el operario: el listado y el
		// acceso directo por id.
		long id = alta(evento, evento.plusDays(7), null);
		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/empleados/{legajo}/fichas", LEGAJO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		// 404 y no 410: no hace falta confirmarle a nadie que la ficha existió alguna vez.
		mockMvc.perform(get("/api/fichas/{id}", id))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_eliminada_libera_la_combinacion_de_legajo_y_fecha() throws Exception {
		// FR-039: el índice único filtrado deja de contarla, así que la carga que se hizo mal se
		// puede rehacer bien con la misma fecha de evento.
		long id = alta(evento, evento.plusDays(7), null);
		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(evento, evento.plusDays(7), null)))
				.andExpect(status().isCreated());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_eliminada_deja_de_bloquear_por_solapamiento() throws Exception {
		// US4-3. Es la razón práctica por la que se elimina: la ficha espuria le tapaba el
		// período al empleado y no dejaba cargar la buena.
		long id = alta(LocalDate.of(2026, 3, 1), null, LocalDate.of(2026, 3, 20));

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(LocalDate.of(2026, 3, 10), null, LocalDate.of(2026, 3, 25))))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO')]").exists());

		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(LocalDate.of(2026, 3, 10), null, LocalDate.of(2026, 3, 25))))
				.andExpect(status().isCreated());
	}

	@Test
	@WithMockUser(username = "jperez")
	void los_datos_de_una_ficha_eliminada_se_conservan_enteros() throws Exception {
		// US4-5, SC-010 y FR-039b. No hay pantalla de restauración (FR-039d) y no la va a haber:
		// lo que se garantiza es que el dato sigue en la base para una intervención técnica.
		long id = alta(evento, evento.plusDays(7), null);
		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNoContent());

		var fila = jdbc.queryForMap("""
				SELECT legajo, fecha_evento, fecha_citacion, grupo_enfermedad_id,
				       detalle_enfermedad_id, observaciones, creada_por
				FROM ficha_medica WHERE id = ?
				""", id);

		assertThat(fila.get("legajo")).isEqualTo(LEGAJO);
		assertThat(fila.get("grupo_enfermedad_id")).isEqualTo(100);
		assertThat(fila.get("detalle_enfermedad_id")).isEqualTo(1001);
		assertThat(fila.get("observaciones")).isEqualTo("Cargada por error");
		assertThat(fila.get("creada_por")).isEqualTo("jperez");
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_ya_eliminada_no_se_puede_volver_a_eliminar() throws Exception {
		// La segunda baja no puede dejar un asiento de auditoría de algo que no pasó.
		long id = alta(evento, evento.plusDays(7), null);
		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isNotFound());

		assertThat(auditoria.historialDe(id)).hasSize(2);
	}

	@Test
	@WithMockUser(username = "jperez")
	void eliminar_una_ficha_inexistente_da_404() throws Exception {
		mockMvc.perform(delete("/api/fichas/{id}", 99_999_999L).param("version", "0"))
				.andExpect(status().isNotFound());
	}
}
