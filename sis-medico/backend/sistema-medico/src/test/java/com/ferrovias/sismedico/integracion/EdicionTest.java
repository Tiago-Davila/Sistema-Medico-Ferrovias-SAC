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

import com.ferrovias.sismedico.models.Operacion;
import com.ferrovias.sismedico.repositories.AuditoriaRepositorio;

// La edición de una ficha ya guardada. Cubre US3-1, US3-6, US3-7 y US3-8.
@AutoConfigureMockMvc
class EdicionTest extends BaseIntegracion {

	private static final int LEGAJO = 953001;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AuditoriaRepositorio auditoria;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void prepararEmpleado() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Quiroga", "Elsa", "Estaciones", "Administrativo");
	}

	// El cuerpo de una ficha; `version` va en null cuando es un alta.
	private String cuerpo(int legajo, Long version, LocalDate evento, LocalDate citacion,
			LocalDate alta, String estado, Boolean inItinere, Integer hora,
			Integer grupo, Integer detalle) {

		return """
				{
				  "version": %s,
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "%s",
				  "inItinere": %s,
				  "estabaEnServicio": false,
				  "horaAccidente": %s,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": false,
				  "justificado": true,
				  "fechaCitacion": %s,
				  "fechaAlta": %s,
				  "grupoEnfermedad": %s,
				  "detalleEnfermedad": %s,
				  "observaciones": "Reposo indicado"
				}
				""".formatted(
						version == null ? "null" : version.toString(),
						legajo, evento, estado,
						inItinere == null ? "null" : inItinere.toString(),
						hora == null ? "null" : hora.toString(),
						citacion == null ? "null" : "\"" + citacion + "\"",
						alta == null ? "null" : "\"" + alta + "\"",
						grupo == null ? "null" : grupo.toString(),
						detalle == null ? "null" : detalle.toString());
	}

	private long altaConCitacion(LocalDate evento, LocalDate citacion) throws Exception {
		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(LEGAJO, null, evento, citacion, null,
						"ENFERMEDAD", null, null, 100, 1001)))
				.andExpect(status().isCreated());

		return jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, LEGAJO, evento.toString());
	}

	private ResultActions editar(long id, String cuerpo) throws Exception {
		return mockMvc.perform(put("/api/fichas/{id}", id)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	@Test
	@WithMockUser(username = "jperez")
	void cargar_el_alta_recalcula_los_dias_perdidos_y_audita_la_modificacion() throws Exception {
		// US3-1: la edición más frecuente del sistema. El empleado se reincorpora, el operario
		// reemplaza la citación por el alta.
		LocalDate evento = LocalDate.now(reloj).minusDays(20);
		long id = altaConCitacion(evento, evento.plusDays(7));

		editar(id, cuerpo(LEGAJO, 0L, evento, null, evento.plusDays(10),
				"ENFERMEDAD", null, null, 100, 1001))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.datos.diasPerdidos").value(10))
				.andExpect(jsonPath("$.datos.fechaCitacion").doesNotExist())
				.andExpect(jsonPath("$.datos.version").value(1))
				.andExpect(jsonPath("$.datos.auditoria.modificadaPor").value("jperez"))
				// M2: la envoltura del PUT es idéntica a la del alta.
				.andExpect(jsonPath("$.advertencias").isArray());

		// FR-033 y SC-004: la modificación deja su propio asiento, sin pisar el del alta.
		assertThat(auditoria.historialDe(id))
				.extracting(AuditoriaRepositorio.Asiento::operacion)
				.containsExactly(Operacion.ALTA, Operacion.MODIFICACION);
	}

	@Test
	@WithMockUser(username = "jperez")
	void cambiar_el_estado_limpia_los_campos_en_lo_guardado() throws Exception {
		// US3-7 y FR-027. Lo que se comprueba es la base, no la respuesta: un campo que dejó de
		// aplicar pero sigue con su valor viejo en la fila es exactamente el dato oculto que
		// FR-026 prohíbe.
		LocalDate evento = LocalDate.now(reloj).minusDays(10);

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(LEGAJO, null, evento, evento.plusDays(5), null,
						"ACCIDENTADO", true, 7, 100, 1001)))
				.andExpect(status().isCreated());

		long id = jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, LEGAJO, evento.toString());

		assertThat(jdbc.queryForObject(
				"SELECT hora_accidente FROM ficha_medica WHERE id = ?", Integer.class, id))
				.isEqualTo(7);

		editar(id, cuerpo(LEGAJO, 0L, evento, evento.plusDays(5), null,
				"ENFERMEDAD", true, 7, 100, 1001))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.datos.inItinere").doesNotExist())
				.andExpect(jsonPath("$.datos.horaAccidente").doesNotExist());

		// El servicio no mira lo que mandó el cliente: mandó in itinere y hora, y se limpiaron.
		var fila = jdbc.queryForMap(
				"SELECT in_itinere, hora_accidente FROM ficha_medica WHERE id = ?", id);
		assertThat(fila.get("in_itinere")).isNull();
		assertThat(fila.get("hora_accidente")).isNull();
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_historica_sin_clasificacion_se_abre_pero_exige_completarse_al_guardar() throws Exception {
		// US3-8. Es la bisagra entre las dos mitades del proyecto: tolerante al leer, estricto al
		// escribir. La misma ficha, los mismos datos, dos respuestas distintas.
		jdbc.update("""
				INSERT INTO ficha_medica (legajo, fecha_evento, fecha_alta, estado_paciente,
				                          creada_por, creada_en)
				VALUES (?, '2014-03-11', '2014-03-20', 'E', 'importacion', SYSUTCDATETIME())
				""", LEGAJO);

		long id = jdbc.queryForObject("SELECT id FROM ficha_medica WHERE legajo = ?",
				Long.class, LEGAJO);

		mockMvc.perform(get("/api/fichas/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.incompleta").value(true));

		editar(id, cuerpo(LEGAJO, 0L, LocalDate.of(2014, 3, 11), null, LocalDate.of(2014, 3, 20),
				"ENFERMEDAD", null, null, null, null))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='GRUPO_REQUERIDO')]").exists())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='DETALLE_REQUERIDO')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_alta_que_genera_solapamiento_con_una_ficha_posterior_se_rechaza() throws Exception {
		// US3-6. El solapamiento no aparece al crear la ficha sino al cerrarla: mientras solo
		// tenía citación no chocaba con nada.
		long primera = altaConCitacion(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5));
		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(LEGAJO, null, LocalDate.of(2026, 3, 10), null,
						LocalDate.of(2026, 3, 20), "ENFERMEDAD", null, null, 100, 1001)))
				.andExpect(status().isCreated());

		editar(primera, cuerpo(LEGAJO, 0L, LocalDate.of(2026, 3, 1), null,
				LocalDate.of(2026, 3, 15), "ENFERMEDAD", null, null, 100, 1001))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO')]").exists())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO')].fichaEnConflicto"
						+ ".fechaEvento").value("2026-03-10"));
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_no_se_solapa_ni_se_duplica_consigo_misma() throws Exception {
		// Si la ficha que se edita no se excluyera de sus propias comprobaciones, ninguna
		// edición sería posible: siempre chocaría contra la versión guardada de sí misma.
		LocalDate evento = LocalDate.now(reloj).minusDays(20);
		long id = altaConCitacion(evento, evento.plusDays(7));

		editar(id, cuerpo(LEGAJO, 0L, evento, evento.plusDays(9), null,
				"ENFERMEDAD", null, null, 100, 1001))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "jperez")
	void editar_una_ficha_inexistente_da_404() throws Exception {
		editar(99_999_999L, cuerpo(LEGAJO, 0L, LocalDate.now(reloj).minusDays(3), null,
				LocalDate.now(reloj), "ENFERMEDAD", null, null, 100, 1001))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "jperez")
	void guardar_sin_version_se_rechaza_en_vez_de_asumir_cero() throws Exception {
		// Sin esto, un cliente que se olvida de mandar la versión pisaría la ficha creyendo que
		// la abrió recién: la versión 0 es una versión real, no "sin especificar".
		LocalDate evento = LocalDate.now(reloj).minusDays(20);
		long id = altaConCitacion(evento, evento.plusDays(7));

		editar(id, cuerpo(LEGAJO, null, evento, null, evento.plusDays(10),
				"ENFERMEDAD", null, null, 100, 1001))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.campo=='version')]").exists());
	}
}
