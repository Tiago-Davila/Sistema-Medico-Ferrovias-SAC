package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * US1-12 y FR-019: la advertencia de antigüedad <b>no</b> es un error.
 *
 * <p>Es la distinción más fácil de romper de toda la feature, y romperla es
 * silencioso: si alguien la convierte en violación, el operario deja de poder
 * cargar eventos viejos y nadie lo nota hasta que llega uno.
 *
 * <p>En el sistema de referencia el bloqueo por antigüedad está deliberadamente
 * desactivado. FR-019 exige que acá tampoco bloquee nunca.
 */
@AutoConfigureMockMvc
class AdvertenciaAntiguedadTest extends BaseIntegracion {

	private static final int LEGAJO = 953001;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void prepararEmpleado() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Barrios", "Osvaldo", "Vía y Obras", "Capataz");
	}

	private ResultActions altaConEventoHace(int dias) throws Exception {
		LocalDate evento = LocalDate.now(reloj).minusDays(dias);
		String cuerpo = """
				{
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "ENFERMEDAD",
				  "inItinere": null, "estabaEnServicio": false, "horaAccidente": null,
				  "atendidoServicioMedico": true, "envioMedicoDomicilio": false,
				  "justificado": true,
				  "fechaCitacion": "%s", "fechaAlta": null,
				  "grupoEnfermedad": 100, "detalleEnfermedad": 1001,
				  "observaciones": null
				}
				""".formatted(LEGAJO, evento, evento.plusDays(1));

		return mockMvc.perform(
				post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_evento_de_mas_de_45_dias_se_guarda_y_devuelve_la_advertencia() throws Exception {
		// US1-12: 201, no 422. La ficha queda cargada.
		altaConEventoHace(62)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias.length()").value(1))
				.andExpect(jsonPath("$.advertencias[0].codigo").value("EVENTO_ANTIGUO"))
				.andExpect(jsonPath("$.advertencias[0].campo").value("fechaEvento"))
				.andExpect(jsonPath("$.advertencias[0].mensaje",
						org.hamcrest.Matchers.containsString("62")));

		Integer guardadas = jdbc.queryForObject(
				"SELECT COUNT(*) FROM ficha_medica WHERE legajo = ?", Integer.class, LEGAJO);
		assertThat(guardadas)
				.as("la advertencia no puede impedir el guardado")
				.isEqualTo(1);
	}

	@Test
	@WithMockUser(username = "jperez")
	void la_advertencia_no_aparece_entre_las_violaciones() throws Exception {
		// Si EVENTO_ANTIGUO apareciera en `violaciones`, el frontend lo mostraría
		// como error bloqueante junto a su campo.
		altaConEventoHace(62)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.violaciones").doesNotExist());
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_evento_reciente_se_guarda_sin_advertencias() throws Exception {
		altaConEventoHace(3)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias.length()").value(0));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_borde_de_45_dias() throws Exception {
		// Exactamente 45 todavía no advierte; 46 sí. FR-019 dice "más de 45".
		altaConEventoHace(45)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias.length()").value(0));

		altaConEventoHace(46)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.advertencias.length()").value(1));
	}
}
