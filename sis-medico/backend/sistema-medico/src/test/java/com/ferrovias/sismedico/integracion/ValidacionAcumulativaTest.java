package com.ferrovias.sismedico.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * M1: el rechazo trae <b>todas</b> las violaciones, no la primera.
 *
 * <p>Es lo que impide que alguien introduzca un {@code return} temprano en
 * {@code ValidadorFichaMedica}. Sin este test, cortocircuitar el validador es un
 * cambio de una línea que no rompe ningún otro test: cada regla suelta se sigue
 * detectando, solo que de a una, y nadie se entera hasta que el operario lo
 * sufre.
 *
 * <p>Lo que está en juego es SC-001. El operario carga dieciséis campos de una
 * vez y las reglas se pisan entre sí: quien se equivoca en las fechas suele
 * equivocarse también en la clasificación. Devolverle un error por viaje lo
 * obliga a tantos viajes como errores tenga, y la carga en sesenta segundos deja
 * de ser posible.
 *
 * <p><b>Queda en rojo hasta que existan T030 y T038.</b> Es lo esperado.
 *
 * <p>Va contra la respuesta de la API y no contra el validador suelto, porque es
 * la respuesta lo que T025 exige verificar: una acumulación correcta que después
 * el controlador o el manejador de errores recortan no sirve de nada. Por eso
 * también vive en {@code integracion/} y no en {@code unitarios/}: necesita la
 * pila entera. La cobertura por regla, sin base, la aporta T041.
 */
@AutoConfigureMockMvc
class ValidacionAcumulativaTest extends BaseIntegracion {

	private static final int LEGAJO = 950002;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void prepararEmpleadoSinFichas() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		// El legajo tiene que existir en el padrón: si no, LEGAJO_INEXISTENTE
		// sería una octava violación y el test dejaría de decir lo que dice.
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Quiroga", "Elsa", "Tráfico", "Administrativo");
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_con_siete_violaciones_devuelve_las_siete() throws Exception {
		String cuerpo = """
				{
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "ACCIDENTADO",
				  "inItinere": null,
				  "estabaEnServicio": false,
				  "horaAccidente": null,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": false,
				  "justificado": null,
				  "fechaCitacion": null,
				  "fechaAlta": null,
				  "grupoEnfermedad": null,
				  "detalleEnfermedad": null,
				  "observaciones": "%s"
				}
				"""
				// 1. FR-007: fecha del evento posterior a hoy.
				.formatted(LEGAJO, LocalDate.now(reloj).plusDays(10),
						// 6. FR-006b: más de 500 caracteres.
						"x".repeat(501));

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones.length()").value(7))
				.andExpect(jsonPath("$.violaciones[*].codigo", Matchers.containsInAnyOrder(
						// 1. Fecha del evento en el futuro.
						"EVENTO_FUTURO",
						// 2. Ni citación ni alta.
						"FECHAS_FIN_EXCLUYENTES",
						// 3. Sin grupo de enfermedad.
						"GRUPO_REQUERIDO",
						// 4. Sin detalle de enfermedad.
						"DETALLE_REQUERIDO",
						// 5. Accidentado sin in itinere definido.
						"IN_ITINERE_REQUERIDO",
						// 6. Observaciones de más de 500 caracteres.
						"OBSERVACIONES_MUY_LARGAS",
						// 7. Justificado sin responder.
						"CAMPO_REQUERIDO")));
	}

	@Test
	@WithMockUser(username = "jperez")
	void cada_violacion_dice_a_que_campo_corresponde() throws Exception {
		// SC-005: el operario corrige sin ayuda externa, y para eso la pantalla
		// necesita saber junto a qué control mostrar cada mensaje. Una violación
		// sin campo es un cartel rojo sin destino.
		String cuerpo = """
				{
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "ACCIDENTADO",
				  "inItinere": null,
				  "estabaEnServicio": false,
				  "horaAccidente": null,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": false,
				  "justificado": null,
				  "fechaCitacion": null,
				  "fechaAlta": null,
				  "grupoEnfermedad": null,
				  "detalleEnfermedad": null,
				  "observaciones": null
				}
				""".formatted(LEGAJO, LocalDate.now(reloj).plusDays(10));

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[*].campo",
						Matchers.everyItem(Matchers.not(Matchers.blankOrNullString()))))
				.andExpect(jsonPath("$.violaciones[*].mensaje",
						Matchers.everyItem(Matchers.not(Matchers.blankOrNullString()))));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_rechazo_no_devuelve_el_contenido_clinico_de_la_ficha() throws Exception {
		// FR-035: los mensajes de rechazo van a la pantalla y al log. Dicen qué
		// regla se violó, no qué escribió el médico.
		String centinela = "CENTINELA-OBSERVACION-3d91f0";
		String cuerpo = """
				{
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "ACCIDENTADO",
				  "inItinere": null,
				  "estabaEnServicio": false,
				  "horaAccidente": null,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": false,
				  "justificado": null,
				  "fechaCitacion": null,
				  "fechaAlta": null,
				  "grupoEnfermedad": null,
				  "detalleEnfermedad": null,
				  "observaciones": "%s"
				}
				""".formatted(LEGAJO, LocalDate.now(reloj).plusDays(10),
						centinela + "x".repeat(500));

		String respuesta = mockMvc.perform(
				post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnprocessableEntity())
				.andReturn().getResponse().getContentAsString();

		org.assertj.core.api.Assertions.assertThat(respuesta)
				.as("el 422 no puede devolver la observación que el operario escribió")
				.doesNotContain(centinela);
	}
}
