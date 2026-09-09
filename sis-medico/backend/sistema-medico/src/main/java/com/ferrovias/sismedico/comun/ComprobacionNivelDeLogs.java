package com.ferrovias.sismedico.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Falla el arranque si el log de SQL puede filtrar datos clínicos (M3).
 *
 * <p>En {@code DEBUG}, {@code JdbcTemplate} imprime cada sentencia; en
 * {@code TRACE}, además los parámetros. Los parámetros de esta aplicación son
 * observaciones médicas, códigos de enfermedad y legajos: un archivo de texto
 * con el diagnóstico de cada empleado, generado sin que nadie lo decida.
 *
 * <p>Es la fuga más fácil de pasar por alto porque nadie la escribe: alcanza con
 * que alguien baje el nivel para depurar una consulta y se olvide de subirlo, o
 * con que un application.yml de producción herede un nivel de desarrollo.
 * FR-035 lo prohíbe y el Principio VI lo prohíbe, pero ninguna de las dos cosas
 * se hace cumplir sola.
 *
 * <p>La respuesta es voltear el arranque, no advertir. Una aplicación que no
 * levanta se arregla en minutos; una que levanta filtrando diagnósticos no se
 * nota hasta la auditoría, y para entonces los archivos ya están escritos y
 * rotados a donde sea que vayan los logs.
 *
 * <p>Fuera del perfil {@code test}, donde bajar el nivel es legítimo: es lo que
 * hace {@code FugaDeLogsTest} para poder capturar todo y comprobar que no hay
 * centinelas.
 */
@Component
@Profile("!test")
public class ComprobacionNivelDeLogs implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger LOG = LoggerFactory.getLogger(ComprobacionNivelDeLogs.class);

	/**
	 * Loggers que en nivel fino imprimen datos de las filas.
	 *
	 * <p>No es solo JdbcTemplate: el driver de SQL Server y el pool también
	 * llegan a los valores.
	 */
	public static final String[] LOGGERS_VIGILADOS = {
			JdbcTemplate.class.getName(),
			"org.springframework.jdbc.core",
			"org.springframework.jdbc.core.StatementCreatorUtils",
			"com.microsoft.sqlserver.jdbc",
	};

	@Override
	public void onApplicationEvent(ApplicationReadyEvent evento) {
		for (String nombre : LOGGERS_VIGILADOS) {
			Logger logger = LoggerFactory.getLogger(nombre);
			if (logger.isDebugEnabled()) {
				throw new IllegalStateException("""
						El logger '%s' está en DEBUG o TRACE. En ese nivel se imprimen las \
						sentencias SQL y sus parámetros, que en esta aplicación son \
						observaciones médicas y códigos de enfermedad de empleados \
						identificados. Está prohibido por FR-035 y por el Principio VI de \
						la constitución. Subilo a INFO antes de levantar la aplicación."""
						.formatted(nombre));
			}
		}
		LOG.info("Nivel de logs verificado: ningún logger de acceso a datos imprime parámetros.");
	}
}
