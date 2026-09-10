package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

// Dos operarios sobre la misma ficha. Cubre US3-9 y SC-008.
// Intencional: lo que se prueba no es que el segundo guardado falle, sino que el primero
// sobreviva. Un 409 que igual pisó el cambio ajeno sería peor que no tener bloqueo.
@AutoConfigureMockMvc
class ConcurrenciaTest extends BaseIntegracion {

	private static final int LEGAJO = 954001;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private LocalDate evento;
	private long id;

	@BeforeEach
	void prepararFicha() throws Exception {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Arce", "Beatriz", "Estaciones", "Administrativo");

		evento = LocalDate.now(reloj).minusDays(20);
		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(null, evento.plusDays(7), null)))
				.andExpect(status().isCreated());

		id = jdbc.queryForObject("SELECT id FROM ficha_medica WHERE legajo = ?", Long.class, LEGAJO);
	}

	private String cuerpo(Long version, LocalDate citacion, LocalDate alta) {
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
						version == null ? "null" : version.toString(), LEGAJO, evento,
						citacion == null ? "null" : "\"" + citacion + "\"",
						alta == null ? "null" : "\"" + alta + "\"");
	}

	private ResultActions editar(Long version, LocalDate citacion, LocalDate alta) throws Exception {
		return mockMvc.perform(put("/api/fichas/{id}", id)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo(version, citacion, alta)));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_segundo_guardado_da_409_y_no_pisa_al_primero() throws Exception {
		// US3-9. Los dos operarios abrieron la ficha en la versión 0.
		editar(0L, null, evento.plusDays(10)).andExpect(status().isOk());

		editar(0L, null, evento.plusDays(30))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("La ficha fue modificada por otro usuario"));

		// SC-008: el cambio del primero sigue ahí, entero.
		assertThat(jdbc.queryForObject(
				"SELECT fecha_alta FROM ficha_medica WHERE id = ?", java.sql.Date.class, id)
				.toLocalDate())
				.isEqualTo(evento.plusDays(10));
	}

	@Test
	@WithMockUser(username = "jperez")
	void tras_el_conflicto_el_operario_puede_reintentar_con_la_version_nueva() throws Exception {
		// El 409 tiene que ser recuperable sin intervención: el operario reabre la ficha, ve el
		// estado real y vuelve a guardar. Si no, el rechazo sería un callejón sin salida.
		editar(0L, null, evento.plusDays(10)).andExpect(status().isOk());
		editar(0L, null, evento.plusDays(30)).andExpect(status().isConflict());

		editar(1L, null, evento.plusDays(30))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.datos.version").value(2));
	}

	@Test
	@WithMockUser(username = "jperez")
	void cada_guardado_incrementa_la_version_de_a_uno() throws Exception {
		// D2: la incrementa el servicio. Si dos guardados dejaran la misma versión, el bloqueo
		// optimista no distinguiría una ficha modificada de una intacta.
		editar(0L, evento.plusDays(8), null).andExpect(jsonPath("$.datos.version").value(1));
		editar(1L, evento.plusDays(9), null).andExpect(jsonPath("$.datos.version").value(2));
		editar(2L, null, evento.plusDays(10)).andExpect(jsonPath("$.datos.version").value(3));
	}

	@Test
	@WithMockUser(username = "jperez")
	void eliminar_con_una_version_vieja_tambien_da_409() throws Exception {
		// Eliminar una ficha que otro acaba de editar es tan destructivo como pisarle el cambio,
		// y encima en silencio: el otro operario no vuelve a encontrarla nunca.
		editar(0L, null, evento.plusDays(10)).andExpect(status().isOk());

		mockMvc.perform(delete("/api/fichas/{id}", id).param("version", "0"))
				.andExpect(status().isConflict());

		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM ficha_medica WHERE id = ? AND eliminada_en IS NULL",
				Integer.class, id))
				.isEqualTo(1);
	}
}
