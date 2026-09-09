package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * FR-023, FR-024 y FR-025: lo que el estado del paciente deriva.
 *
 * <p>Cubre US1-10 y US1-11.
 *
 * <p>Se verifica <b>contra lo que quedó guardado</b>, no contra lo que devuelve
 * la respuesta. Es la diferencia que importa: la pantalla deshabilita y
 * autocompleta estos campos, así que un backend que no los derivara parecería
 * funcionar en la interfaz y guardaría mal. FR-018 exige que el servidor los
 * aplique con independencia de lo que haya validado el cliente, y la única forma
 * de comprobarlo es mandar valores contradictorios a propósito y mirar la fila.
 */
@AutoConfigureMockMvc
class DependenciasDeCamposTest extends BaseIntegracion {

	private static final int LEGAJO = 951001;

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
				LEGAJO, "Vera", "Hugo", "Tráfico", "Oficial");
	}

	private long guardar(String estado, Boolean inItinere, Boolean estabaEnServicio,
			Integer horaAccidente, Boolean envioMedicoDomicilio, LocalDate evento) throws Exception {

		String cuerpo = """
				{
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "%s",
				  "inItinere": %s,
				  "estabaEnServicio": %s,
				  "horaAccidente": %s,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": %s,
				  "justificado": true,
				  "fechaCitacion": "%s",
				  "fechaAlta": null,
				  "grupoEnfermedad": 100,
				  "detalleEnfermedad": 1001,
				  "observaciones": null
				}
				""".formatted(LEGAJO, evento, estado, inItinere, estabaEnServicio,
						horaAccidente, envioMedicoDomicilio, evento.plusDays(2));

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated());

		return jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, LEGAJO, java.sql.Date.valueOf(evento));
	}

	private Boolean booleanoGuardado(long id, String columna) {
		return jdbc.queryForObject(
				"SELECT " + columna + " FROM ficha_medica WHERE id = ?", Boolean.class, id);
	}

	@Test
	@WithMockUser(username = "jperez")
	void con_in_itinere_marcado_estaba_en_servicio_queda_en_si() throws Exception {
		// FR-024, US1-10. Se manda estabaEnServicio en false a propósito: el
		// servidor tiene que sobrescribirlo sin mirar lo enviado.
		long id = guardar("ACCIDENTADO", true, false, 7, false, LocalDate.now(reloj).minusDays(2));

		assertThat(booleanoGuardado(id, "estaba_en_servicio")).isTrue();
	}

	@Test
	@WithMockUser(username = "jperez")
	void sin_in_itinere_estaba_en_servicio_es_lo_que_respondio_el_operario() throws Exception {
		// FR-024 fuerza el valor solo cuando in itinere está marcado. Con in
		// itinere en "no", el campo sigue siendo del operario.
		long id = guardar("ACCIDENTADO", false, false, 7, false, LocalDate.now(reloj).minusDays(2));

		assertThat(booleanoGuardado(id, "estaba_en_servicio")).isFalse();
	}

	@Test
	@WithMockUser(username = "jperez")
	void con_estado_enfermedad_in_itinere_y_hora_quedan_vacios_en_lo_guardado() throws Exception {
		// FR-025, US1-11. Se mandan valores a propósito: no aplican, así que se
		// limpian. Guardarlos escondidos sería tener un dato que la pantalla no
		// muestra y que nadie puede corregir.
		long id = guardar("ENFERMEDAD", true, true, 9, false, LocalDate.now(reloj).minusDays(3));

		assertThat(booleanoGuardado(id, "in_itinere")).isNull();
		assertThat(jdbc.queryForObject("SELECT hora_accidente FROM ficha_medica WHERE id = ?",
				Integer.class, id)).isNull();
	}

	@Test
	@WithMockUser(username = "jperez")
	void con_estado_enfermedad_el_envio_de_medico_a_domicilio_queda_habilitado() throws Exception {
		// FR-025: con enfermedad este campo sí aplica, así que se respeta lo que
		// el operario respondió.
		long id = guardar("ENFERMEDAD", null, true, null, true, LocalDate.now(reloj).minusDays(4));

		assertThat(booleanoGuardado(id, "envio_medico_domicilio")).isTrue();
	}

	@Test
	@WithMockUser(username = "jperez")
	void con_estado_accidentado_el_envio_de_medico_a_domicilio_queda_en_no() throws Exception {
		// FR-023 y FR-026b. Queda en NO, no vacío: FR-004c no le admite tercer
		// estado. Es la parte de "limpiar" que más fácil se implementa mal.
		long id = guardar("ACCIDENTADO", false, false, 7, true, LocalDate.now(reloj).minusDays(5));

		assertThat(booleanoGuardado(id, "envio_medico_domicilio")).isFalse();
		assertThat(booleanoGuardado(id, "envio_medico_domicilio")).isNotNull();
	}

	@Test
	@WithMockUser(username = "jperez")
	void con_estado_accidentado_in_itinere_y_hora_se_conservan() throws Exception {
		// FR-023: con accidente estos dos campos aplican y se guardan.
		long id = guardar("ACCIDENTADO", false, true, 14, false, LocalDate.now(reloj).minusDays(6));

		assertThat(booleanoGuardado(id, "in_itinere")).isFalse();
		assertThat(jdbc.queryForObject("SELECT hora_accidente FROM ficha_medica WHERE id = ?",
				Integer.class, id)).isEqualTo(14);
	}
}
