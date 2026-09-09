package com.ferrovias.sismedico.unitarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.ferrovias.sismedico.comun.ComprobacionNivelDeLogs;

/**
 * Verifica que la comprobación de M3 realmente voltea el arranque.
 *
 * <p>Un guardarraíl que no se prueba es una intención. Este test baja el nivel a
 * DEBUG a propósito y exige que la aplicación se niegue a levantar.
 */
class ComprobacionNivelDeLogsTest {

	private final LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();

	@AfterEach
	void restaurarNiveles() {
		for (String nombre : ComprobacionNivelDeLogs.LOGGERS_VIGILADOS) {
			contexto.getLogger(nombre).setLevel(null);
		}
	}

	@Test
	void con_los_niveles_normales_la_aplicacion_arranca() {
		assertThatCode(() -> new ComprobacionNivelDeLogs().onApplicationEvent(null))
				.doesNotThrowAnyException();
	}

	@Test
	void con_JdbcTemplate_en_DEBUG_el_arranque_falla() {
		contexto.getLogger("org.springframework.jdbc.core.JdbcTemplate").setLevel(Level.DEBUG);

		assertThatThrownBy(() -> new ComprobacionNivelDeLogs().onApplicationEvent(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JdbcTemplate")
				.hasMessageContaining("FR-035");
	}

	@Test
	void con_TRACE_tambien_falla() {
		// TRACE es el nivel peligroso de verdad: ahí salen los parámetros.
		contexto.getLogger("org.springframework.jdbc.core.JdbcTemplate").setLevel(Level.TRACE);

		assertThatThrownBy(() -> new ComprobacionNivelDeLogs().onApplicationEvent(null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cada_logger_vigilado_voltea_el_arranque_por_su_cuenta() {
		// Que la lista no se vuelva decorativa: si alguien agrega un nombre y no
		// funciona, o saca uno, este test lo señala.
		assertThat(ComprobacionNivelDeLogs.LOGGERS_VIGILADOS).hasSizeGreaterThanOrEqualTo(4);

		Arrays.stream(ComprobacionNivelDeLogs.LOGGERS_VIGILADOS).forEach(nombre -> {
			restaurarNiveles();
			contexto.getLogger(nombre).setLevel(Level.DEBUG);

			assertThatThrownBy(() -> new ComprobacionNivelDeLogs().onApplicationEvent(null))
					.as("el logger %s tiene que voltear el arranque en DEBUG", nombre)
					.isInstanceOf(IllegalStateException.class);
		});
	}

	@Test
	void el_mensaje_explica_por_que_y_no_solo_que() {
		contexto.getLogger("com.microsoft.sqlserver.jdbc").setLevel(Level.DEBUG);

		assertThatThrownBy(() -> new ComprobacionNivelDeLogs().onApplicationEvent(null))
				.hasMessageContaining("observaciones médicas")
				.hasMessageContaining("Subilo a INFO");
	}
}
