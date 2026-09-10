package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// SC-003: ninguna ficha escrita por la aplicación viola una regla bloqueante R1 a R8.
//
// Intencional: los otros tests preguntan "¿esta regla rechaza lo que tiene que rechazar?", uno
// por uno. Este pregunta lo contrario y de una sola vez: después de intentar escribir de todo,
// ¿quedó guardada alguna ficha que no debería estar? Es la diferencia entre probar cada puerta y
// revisar si alguien entró.
//
// El predicado que separa las dos poblaciones es el usuario: 'importacion' escribió sin pasar por
// el servicio, como hace un proceso de importación real, y esas fichas violan las reglas de hoy a
// propósito (FR-036b). Todo lo demás lo escribió la aplicación y no puede violar ninguna.
@AutoConfigureMockMvc
class ReglasBloqueantesTest extends BaseIntegracion {

	private static final int LEGAJO = 957001;
	private static final String IMPORTADAS = "importacion";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void prepararEscenario() {
		new CargadorDatosHistoricos(jdbc, reloj).cargarTodo();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Ferreyra", "Mirta", "Estaciones", "Administrativo");
	}

	private String cuerpo(Long version, LocalDate evento, LocalDate citacion, LocalDate alta,
			String estado, Boolean inItinere, Integer grupo, Integer detalle) {

		return """
				{
				  "version": %s,
				  "legajo": %d,
				  "fechaEvento": "%s",
				  "estadoPaciente": "%s",
				  "inItinere": %s,
				  "estabaEnServicio": false,
				  "horaAccidente": null,
				  "atendidoServicioMedico": true,
				  "envioMedicoDomicilio": false,
				  "justificado": true,
				  "fechaCitacion": %s,
				  "fechaAlta": %s,
				  "grupoEnfermedad": %s,
				  "detalleEnfermedad": %s,
				  "observaciones": null
				}
				""".formatted(
						version == null ? "null" : version.toString(), LEGAJO, evento, estado,
						inItinere == null ? "null" : inItinere.toString(),
						citacion == null ? "null" : "\"" + citacion + "\"",
						alta == null ? "null" : "\"" + alta + "\"",
						grupo == null ? "null" : grupo.toString(),
						detalle == null ? "null" : detalle.toString());
	}

	private void intentarAlta(String cuerpo) throws Exception {
		mockMvc.perform(post("/api/fichas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	// Cuántas fichas escritas por la aplicación violan cada una de las ocho reglas.
	// Intencional: la consulta pregunta por el resultado en la base, no por la respuesta HTTP. Un
	// rechazo que igual dejó la fila escrita sería invisible desde el lado del cliente.
	private int violacionesEnLoEscritoPorLaAplicacion() {
		Integer cuantas = jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica f
				WHERE f.eliminada_en IS NULL
				  AND (f.creada_por <> ? OR f.modificada_por IS NOT NULL)
				  AND (
				        -- R1 / FR-007: evento posterior a hoy
				        f.fecha_evento > CAST(? AS DATE)
				        -- R2 / FR-008: exactamente una de las dos fechas de fin
				     OR (f.fecha_citacion IS NULL AND f.fecha_alta IS NULL)
				     OR (f.fecha_citacion IS NOT NULL AND f.fecha_alta IS NOT NULL)
				        -- R3 / FR-009: grupo y detalle obligatorios
				     OR f.grupo_enfermedad_id IS NULL
				     OR f.detalle_enfermedad_id IS NULL
				        -- R4 / FR-010: el detalle tiene que pertenecer al grupo
				     OR NOT EXISTS (SELECT 1 FROM detalle_enfermedad d
				                    WHERE d.id = f.detalle_enfermedad_id
				                      AND d.grupo_enfermedad_id = f.grupo_enfermedad_id)
				        -- R5 / FR-011: más de 999 días entre evento y alta
				     OR (f.fecha_alta IS NOT NULL
				         AND DATEDIFF(day, f.fecha_evento, f.fecha_alta) > 999)
				        -- FR-012: alta anterior al evento
				     OR (f.fecha_alta IS NOT NULL AND f.fecha_alta < f.fecha_evento)
				        -- R6 / FR-013: (legajo, fecha de evento) repetido entre fichas vivas
				     OR EXISTS (SELECT 1 FROM ficha_medica o
				                WHERE o.id <> f.id AND o.eliminada_en IS NULL
				                  AND o.legajo = f.legajo AND o.fecha_evento = f.fecha_evento)
				        -- R7 / FR-014: períodos solapados, extremo derecho excluido
				     OR EXISTS (SELECT 1 FROM ficha_medica o
				                WHERE o.id <> f.id AND o.eliminada_en IS NULL
				                  AND o.legajo = f.legajo
				                  AND f.fecha_evento < COALESCE(o.fecha_citacion, o.fecha_alta,
				                                                CAST(? AS DATE))
				                  AND o.fecha_evento < COALESCE(f.fecha_citacion, f.fecha_alta,
				                                                CAST(? AS DATE)))
				        -- R8 / FR-016: en un accidente, in itinere es obligatorio
				     OR (f.estado_paciente = 'A' AND f.in_itinere IS NULL)
				  )
				""", Integer.class, IMPORTADAS,
				LocalDate.now(reloj).toString(), LocalDate.now(reloj).toString(),
				LocalDate.now(reloj).toString());

		return cuantas == null ? 0 : cuantas;
	}

	@Test
	@WithMockUser(username = "jperez")
	void ninguna_ficha_escrita_por_la_aplicacion_viola_una_regla_bloqueante() throws Exception {
		LocalDate hoy = LocalDate.now(reloj);
		LocalDate evento = hoy.minusDays(30);

		// Una tanda de intentos que viola cada regla, una por una. Ninguno tiene que dejar rastro.
		List<String> invalidos = List.of(
				// R1: evento futuro
				cuerpo(null, hoy.plusDays(1), hoy.plusDays(5), null, "ENFERMEDAD", null, 100, 1001),
				// R2: ninguna de las dos fechas de fin
				cuerpo(null, evento, null, null, "ENFERMEDAD", null, 100, 1001),
				// R2: las dos fechas de fin
				cuerpo(null, evento, evento.plusDays(3), evento.plusDays(9), "ENFERMEDAD", null, 100, 1001),
				// R3: sin grupo ni detalle
				cuerpo(null, evento, evento.plusDays(3), null, "ENFERMEDAD", null, null, null),
				// R4: detalle que no pertenece al grupo
				cuerpo(null, evento, evento.plusDays(3), null, "ENFERMEDAD", null, 100, 2001),
				// R5: más de 999 días
				cuerpo(null, evento, null, evento.plusDays(1500), "ENFERMEDAD", null, 100, 1001),
				// FR-012: alta anterior al evento
				cuerpo(null, evento, null, evento.minusDays(1), "ENFERMEDAD", null, 100, 1001),
				// R8: accidente sin in itinere
				cuerpo(null, evento, evento.plusDays(3), null, "ACCIDENTADO", null, 100, 1001));

		for (String invalido : invalidos) {
			intentarAlta(invalido);
		}

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ficha_medica WHERE legajo = ?",
				Integer.class, LEGAJO))
				.as("ningún intento inválido puede haber dejado fila")
				.isZero();

		// Ahora sí, una válida, para que la comprobación de más abajo no sea sobre el conjunto
		// vacío: un invariante que se cumple porque no hay nada que revisar no prueba nada.
		intentarAlta(cuerpo(null, evento, evento.plusDays(3), null, "ENFERMEDAD", null, 100, 1001));

		long id = jdbc.queryForObject("SELECT id FROM ficha_medica WHERE legajo = ?",
				Long.class, LEGAJO);

		// Y una modificación válida, para que la población incluya fichas modificadas y no solo
		// creadas: SC-003 habla de las dos.
		mockMvc.perform(put("/api/fichas/{id}", id).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(0L, evento, null, evento.plusDays(12), "ENFERMEDAD", null, 100, 1001)));

		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica
				WHERE creada_por <> ? OR modificada_por IS NOT NULL
				""", Integer.class, IMPORTADAS))
				.as("tiene que haber fichas escritas por la aplicación para revisar")
				.isPositive();

		assertThat(violacionesEnLoEscritoPorLaAplicacion())
				.as("SC-003: cero fichas escritas por la aplicación violan R1 a R8")
				.isZero();
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_invariante_encuentra_las_violaciones_del_historico_cuando_se_lo_apunta_ahi() {
		// Sin esto, la comprobación de arriba podría estar dando cero porque la consulta no
		// detecta nada. Acá se la corre sobre el histórico, que sí viola las reglas a propósito,
		// y se exige que las encuentre.
		Integer enElHistorico = jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica
				WHERE creada_por = ?
				  AND (grupo_enfermedad_id IS NULL
				       OR detalle_enfermedad_id IS NULL
				       OR (fecha_citacion IS NULL AND fecha_alta IS NULL)
				       OR (fecha_citacion IS NOT NULL AND fecha_alta IS NOT NULL)
				       OR (fecha_alta IS NOT NULL AND fecha_alta < fecha_evento))
				""", Integer.class, IMPORTADAS);

		assertThat(enElHistorico)
				.as("el juego de datos tiene que traer fichas que violan las reglas de hoy")
				.isPositive();
	}
}
