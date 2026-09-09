package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.ferrovias.sismedico.auditoria.AuditoriaRepositorio;
import com.ferrovias.sismedico.auditoria.Operacion;

/**
 * El alta de punta a punta: US1-3, US1-13, US1-14, US1-15 y US1-16.
 */
@AutoConfigureMockMvc
class AltaFichaTest extends BaseIntegracion {

	private static final int LEGAJO = 952001;

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
				LEGAJO, "Ledesma", "Norma", "Estaciones", "Administrativo");
	}

	private ResultActions alta(LocalDate evento, LocalDate citacion, LocalDate altaMedica)
			throws Exception {

		String cuerpo = """
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
				  "observaciones": "Reposo indicado"
				}
				""".formatted(LEGAJO, evento,
						citacion == null ? "null" : "\"" + citacion + "\"",
						altaMedica == null ? "null" : "\"" + altaMedica + "\"");

		return mockMvc.perform(
				post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private void insertarDirecto(int legajo, String evento, String citacion, String altaMedica) {
		// Entra sin pasar por el servicio, igual que la importación histórica:
		// es la única forma de dejar en la base una ficha que hoy no se podría
		// guardar (FR-036b).
		jdbc.update("""
				INSERT INTO ficha_medica (legajo, fecha_evento, fecha_citacion, fecha_alta,
				                          creada_por, creada_en)
				VALUES (?, ?, ?, ?, 'importacion', SYSUTCDATETIME())
				""", legajo, evento, citacion, altaMedica);
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_alta_valida_persiste_con_su_auditoria() throws Exception {
		// US1-3.
		LocalDate evento = LocalDate.now(reloj).minusDays(3);

		alta(evento, evento.plusDays(7), null)
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.datos.legajo").value(LEGAJO))
				.andExpect(jsonPath("$.datos.version").value(0))
				.andExpect(jsonPath("$.datos.auditoria.creadaPor").value("jperez"))
				.andExpect(jsonPath("$.datos.auditoria.modificadaPor").doesNotExist())
				// M2: la envoltura siempre trae advertencias, aunque esté vacía.
				.andExpect(jsonPath("$.advertencias").isArray())
				.andExpect(jsonPath("$.advertencias.length()").value(0));

		long id = jdbc.queryForObject("SELECT id FROM ficha_medica WHERE legajo = ?",
				Long.class, LEGAJO);

		// SC-004: la escritura queda registrada, en la misma transacción.
		assertThat(auditoria.historialDe(id))
				.singleElement()
				.satisfies(asiento -> {
					assertThat(asiento.operacion()).isEqualTo(Operacion.ALTA);
					assertThat(asiento.usuario()).isEqualTo("jperez");
				});
	}

	@Test
	@WithMockUser(username = "jperez")
	void los_dias_perdidos_los_calcula_el_backend() throws Exception {
		// FR-020b y M4: el cliente no tiene dónde mandarlos.
		LocalDate evento = LocalDate.now(reloj).minusDays(20);

		alta(evento, null, evento.plusDays(10))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.datos.diasPerdidos").value(10));
	}

	@Test
	@WithMockUser(username = "jperez")
	void sin_fecha_de_alta_los_dias_perdidos_quedan_vacios() throws Exception {
		// FR-021: vacío, no cero.
		LocalDate evento = LocalDate.now(reloj).minusDays(3);

		alta(evento, evento.plusDays(7), null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.datos.diasPerdidos").doesNotExist());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_segunda_ficha_con_la_misma_fecha_de_evento_se_rechaza() throws Exception {
		// US1-13, FR-013.
		LocalDate evento = LocalDate.now(reloj).minusDays(3);
		alta(evento, evento.plusDays(7), null).andExpect(status().isCreated());

		alta(evento, evento.plusDays(5), null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='FICHA_DUPLICADA')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_cuyo_periodo_se_solapa_se_rechaza_indicando_con_cual() throws Exception {
		// US1-14 y FR-015: el mensaje identifica la ficha en conflicto por su
		// fecha de evento, que es con lo que el operario la ubica.
		insertarDirecto(LEGAJO, "2026-03-01", null, "2026-03-10");

		alta(LocalDate.of(2026, 3, 5), null, LocalDate.of(2026, 3, 15))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO')]").exists())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO')].fichaEnConflicto"
						+ ".fechaEvento").value("2026-03-01"));
	}

	@Test
	@WithMockUser(username = "jperez")
	void dos_fichas_que_comparten_un_extremo_conviven() throws Exception {
		// US1-15 y FR-014b. El empleado recibe el alta el 10 y se accidenta ese
		// mismo día: son dos fichas legítimas y rechazar la segunda sería un
		// falso positivo sobre un caso real.
		insertarDirecto(LEGAJO, "2026-03-01", null, "2026-03-10");

		alta(LocalDate.of(2026, 3, 10), null, LocalDate.of(2026, 3, 18))
				.andExpect(status().isCreated());
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_historica_incompleta_bloquea_con_un_mensaje_accionable() throws Exception {
		// US1-16, FR-014c, FR-014d y SC-009. Es la consecuencia dura que el
		// cliente aceptó: el legajo queda bloqueado hasta que alguien complete
		// la ficha vieja. Sin la explicación, el rechazo es un muro.
		insertarDirecto(LEGAJO, "2019-04-12", null, null);
		LocalDate evento = LocalDate.now(reloj).minusDays(3);

		alta(evento, evento.plusDays(7), null)
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='SOLAPAMIENTO_FICHA_INCOMPLETA')]")
						.exists())
				.andExpect(jsonPath("$.violaciones[0].fichaEnConflicto.incompleta").value(true))
				.andExpect(jsonPath("$.violaciones[0].mensaje",
						org.hamcrest.Matchers.containsString("fecha de citación o de alta")));
	}

	@Test
	@WithMockUser(username = "jperez")
	void una_ficha_eliminada_no_bloquea_la_carga_de_una_nueva() throws Exception {
		// FR-039: la eliminada queda fuera de unicidad y de solapamiento, y su
		// combinación de legajo y fecha se puede volver a usar.
		LocalDate evento = LocalDate.now(reloj).minusDays(3);
		alta(evento, evento.plusDays(7), null).andExpect(status().isCreated());
		jdbc.update("UPDATE ficha_medica SET eliminada_por='jperez', eliminada_en=SYSUTCDATETIME() "
				+ "WHERE legajo = ?", LEGAJO);

		alta(evento, evento.plusDays(7), null).andExpect(status().isCreated());
	}

	@Test
	@WithMockUser(username = "jperez")
	void un_legajo_que_no_existe_en_el_padron_se_rechaza() throws Exception {
		// FR-002. Va como violación de negocio y no como 404, porque llega junto
		// con las demás violaciones de la ficha en una sola respuesta.
		String cuerpo = """
				{
				  "legajo": 1,
				  "fechaEvento": "%s",
				  "estadoPaciente": "ENFERMEDAD",
				  "inItinere": null, "estabaEnServicio": false, "horaAccidente": null,
				  "atendidoServicioMedico": true, "envioMedicoDomicilio": false,
				  "justificado": true,
				  "fechaCitacion": "%s", "fechaAlta": null,
				  "grupoEnfermedad": 100, "detalleEnfermedad": 1001,
				  "observaciones": null
				}
				""".formatted(LocalDate.now(reloj).minusDays(3), LocalDate.now(reloj).plusDays(4));

		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.violaciones[?(@.codigo=='LEGAJO_INEXISTENTE')]").exists());
	}

	@Test
	@WithMockUser(username = "jperez")
	void la_ficha_guardada_no_se_puede_recuperar_por_otro_legajo() throws Exception {
		// FR-003c: la ficha referencia al empleado por su legajo y no guarda
		// copia de sus datos. Lo que se comprueba acá es lo segundo.
		LocalDate evento = LocalDate.now(reloj).minusDays(3);
		alta(evento, evento.plusDays(7), null).andExpect(status().isCreated());

		Integer columnasDelPadron = jdbc.queryForObject("""
				SELECT COUNT(*) FROM sys.columns
				WHERE object_id = OBJECT_ID('ficha_medica')
				  AND name IN ('apellido', 'nombre', 'seccion', 'categoria_laboral')
				""", Integer.class);

		assertThat(columnasDelPadron).isZero();
	}
}
