package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SC-007: ningún registro de diagnóstico técnico contiene observaciones,
 * detalles de enfermedad ni descripciones clínicas.
 *
 * <p>Es el guardarraíl de M3 que verifica el resultado, no los mecanismos. Los
 * otros tres —{@code toString()} redactado, {@code toString()} acotado del
 * registro de ficha, y {@code ComprobacionNivelDeLogs}— pueden estar todos bien
 * y aun así filtrar por un cuarto camino que nadie previó: una traza de
 * excepción, un mensaje de error de Spring, el pool de conexiones. Este test no
 * confía en ninguno: ejecuta las tres escrituras, captura <b>todo</b> lo que se
 * emitió, y busca los centinelas.
 *
 * <p>El appender no tiene filtro de nivel, pero <b>los loggers quedan en su
 * nivel configurado</b>. Es a propósito: lo que se verifica es que con la
 * configuración real no se filtra nada. Forzar todo a TRACE probaría lo
 * contrario de lo que interesa, porque el driver de SQL Server sí imprime
 * parámetros en TRACE y por eso mismo existe ComprobacionNivelDeLogs.
 *
 * <p><b>Este test queda en rojo hasta que existan US1, US3 y US4.</b> Es lo
 * esperado. Escrito después del código que vigila no habría impedido nada:
 * habría documentado que la fuga no estaba, en lugar de impedir que se
 * introduzca.
 *
 * <p>Vive en {@code integracion/} y no en {@code arquitectura/}, como sugiere la
 * lista de tareas, porque hace alta, modificación y baja de verdad y eso
 * necesita base. En la suite rápida obligaría a {@code test} a levantar una
 * imagen de 2 GB, que es justo lo que T017 evita.
 */
@AutoConfigureMockMvc
class FugaDeLogsTest extends BaseIntegracion {

	/** Centinelas únicos. Si aparecen en un log, salieron de esta ficha. */
	private static final String OBSERVACION_AL_CREAR = "CENTINELA-ALTA-7f3a9c2b41d8";
	private static final String OBSERVACION_AL_MODIFICAR = "CENTINELA-EDICION-5e1c8a04b6f2";

	private static final int LEGAJO = 950001;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	private final List<ILoggingEvent> capturados = new CopyOnWriteArrayList<>();
	private AppenderBase<ILoggingEvent> appender;

	@BeforeEach
	void prepararEscenarioYCaptura() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
		jdbc.update("INSERT INTO " + BASE_PADRON + ".dbo.empleado "
				+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				LEGAJO, "Sosa", "Ramón", "Vía y Obras", "Oficial");

		appender = new AppenderBase<>() {
			@Override
			protected void append(ILoggingEvent evento) {
				evento.getFormattedMessage();
				capturados.add(evento);
			}
		};
		appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
		appender.start();
		((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT").addAppender(appender);
	}

	@AfterEach
	void soltarCaptura() {
		((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ROOT").detachAppender(appender);
		appender.stop();
		capturados.clear();
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_ciclo_completo_de_una_ficha_no_deja_datos_clinicos_en_el_log() throws Exception {
		LocalDate evento = LocalDate.now(reloj).minusDays(3);

		String respuestaAlta = mockMvc.perform(post("/api/fichas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
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
						  "fechaCitacion": "%s",
						  "fechaAlta": null,
						  "grupoEnfermedad": 100,
						  "detalleEnfermedad": 1001,
						  "observaciones": "%s"
						}
						""".formatted(LEGAJO, evento, evento.plusDays(5), OBSERVACION_AL_CREAR)))
				.andReturn().getResponse().getContentAsString();

		long id = jdbc.queryForObject("SELECT id FROM ficha_medica WHERE legajo = ?", Long.class, LEGAJO);
		long version = jdbc.queryForObject("SELECT version FROM ficha_medica WHERE id = ?", Long.class, id);

		mockMvc.perform(put("/api/fichas/" + id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "version": %d,
						  "legajo": %d,
						  "fechaEvento": "%s",
						  "estadoPaciente": "ENFERMEDAD",
						  "inItinere": null,
						  "estabaEnServicio": false,
						  "horaAccidente": null,
						  "atendidoServicioMedico": true,
						  "envioMedicoDomicilio": false,
						  "justificado": true,
						  "fechaCitacion": null,
						  "fechaAlta": "%s",
						  "grupoEnfermedad": 100,
						  "detalleEnfermedad": 1002,
						  "observaciones": "%s"
						}
						""".formatted(version, LEGAJO, evento, evento.plusDays(2),
						OBSERVACION_AL_MODIFICAR)));

		long versionTrasEditar = jdbc.queryForObject(
				"SELECT version FROM ficha_medica WHERE id = ?", Long.class, id);

		mockMvc.perform(delete("/api/fichas/" + id).param("version", String.valueOf(versionTrasEditar)));

		// Si las tres operaciones no ocurrieron, el test no probó nada. Se exige
		// que hayan pasado antes de declarar limpio el log.
		assertThat(respuestaAlta).as("el alta tiene que haber devuelto un cuerpo").isNotEmpty();
		Integer asientos = jdbc.queryForObject(
				"SELECT COUNT(*) FROM ficha_medica_auditoria WHERE ficha_id = ?", Integer.class, id);
		assertThat(asientos).as("tiene que haber asiento de ALTA, MODIFICACION y BAJA").isEqualTo(3);

		assertThat(textoDelLog())
				.as("SC-007: ninguna observación clínica puede aparecer en el log")
				.doesNotContain(OBSERVACION_AL_CREAR)
				.doesNotContain(OBSERVACION_AL_MODIFICAR);
	}

	/** Todo lo emitido: mensaje formateado, argumentos y trazas de excepción. */
	private String textoDelLog() {
		var texto = new StringBuilder();
		for (ILoggingEvent evento : capturados) {
			texto.append(evento.getLoggerName()).append(' ')
					.append(evento.getFormattedMessage()).append('\n');
			if (evento.getArgumentArray() != null) {
				for (Object argumento : evento.getArgumentArray()) {
					texto.append(argumento).append('\n');
				}
			}
			var throwable = evento.getThrowableProxy();
			while (throwable != null) {
				texto.append(throwable.getClassName()).append(' ')
						.append(throwable.getMessage()).append('\n');
				for (var linea : throwable.getStackTraceElementProxyArray()) {
					texto.append(linea.getSTEAsString()).append('\n');
				}
				throwable = throwable.getCause();
			}
		}
		return texto.toString();
	}
}
