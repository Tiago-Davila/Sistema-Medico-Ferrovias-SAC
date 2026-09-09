package com.ferrovias.sismedico.integracion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Carga el juego de datos de prueba en la base del contenedor.
 *
 * <p><b>Carga sin filtrar y sin validar</b> (FR-036b). Las fichas entran con un
 * {@code INSERT} directo, sin pasar por el servicio: es lo que hace un proceso de
 * importación real y es la única forma de meter en la base fichas que violan las
 * reglas de hoy. Si estos datos pasaran por {@code ValidadorFichaMedica}, la
 * mitad sería rechazada y los tests de tolerancia de lectura no tendrían nada
 * que leer.
 *
 * <p>Ver {@code src/test/resources/datos/LEEME.md} para el detalle de qué trae
 * cada archivo y por qué hay fichas fabricadas además de las reales.
 */
public final class CargadorDatosHistoricos {

	private static final String SIN_VALOR = "NULL";

	/** Marca de "el día de hoy" en el archivo de fichas construidas. */
	private static final String HOY = "HOY";

	private static final String USUARIO_IMPORTACION = "importacion";

	private final JdbcTemplate jdbc;
	private final Clock reloj;

	public CargadorDatosHistoricos(JdbcTemplate jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	/** Deja la base con el padrón y las fichas del juego de datos. */
	public void cargarTodo() {
		vaciar();
		cargarPadron();
		cargarFichasHistoricas();
		cargarFichasConstruidas();
	}

	public void vaciar() {
		jdbc.update("DELETE FROM ficha_medica_auditoria");
		jdbc.update("DELETE FROM ficha_medica");
		jdbc.update("DELETE FROM " + BaseIntegracion.BASE_PADRON + ".dbo.empleado");
	}

	private void cargarPadron() {
		List<String[]> filas = leer("/datos/empleados-padron.csv", true);
		jdbc.batchUpdate(
				"INSERT INTO " + BaseIntegracion.BASE_PADRON + ".dbo.empleado "
						+ "(legajo, apellido, nombre, seccion, categoria_laboral) VALUES (?,?,?,?,?)",
				filas, filas.size(),
				(PreparedStatement ps, String[] fila) -> {
					ps.setInt(1, Integer.parseInt(fila[0]));
					ps.setString(2, fila[1]);
					ps.setString(3, fila[2]);
					ps.setString(4, fila[3]);
					ps.setString(5, fila[4]);
				});
	}

	/**
	 * Fichas reales anonimizadas.
	 *
	 * <p>Los cuatro indicadores del volcado no se mapean: no hay definición de
	 * columnas que diga cuál es cuál, e inventarla sería peor que dejar los
	 * campos en NULL. FR-004d contempla exactamente eso para el histórico, así
	 * que estas fichas quedan con los cinco campos de sí/no sin responder, que
	 * es un estado legítimo al leer y rechazable al guardar.
	 */
	private void cargarFichasHistoricas() {
		List<String[]> filas = leer("/datos/fichas-historicas.csv", true);
		jdbc.batchUpdate(SQL_INSERCION, filas, filas.size(),
				(PreparedStatement ps, String[] fila) -> {
					ps.setInt(1, Integer.parseInt(fila[0]));
					ps.setDate(2, Date.valueOf(fecha(fila[1])));
					fechaODato(ps, 3, fila[2]);
					fechaODato(ps, 4, fila[3]);
					ps.setString(5, fila[4]);
					// in_itinere, estaba_en_servicio, hora_accidente,
					// atendido_servicio_medico, envio_medico_domicilio,
					// justificado: sin definición de columnas, sin mapeo.
					ps.setNull(6, Types.BIT);
					ps.setNull(7, Types.BIT);
					ps.setNull(8, Types.TINYINT);
					ps.setNull(9, Types.BIT);
					ps.setNull(10, Types.BIT);
					ps.setNull(11, Types.BIT);
					enteroONulo(ps, 12, fila[9]);
					enteroONulo(ps, 13, fila[10]);
					ps.setString(14, fila[11].isEmpty() ? null : fila[11]);
					ps.setString(15, USUARIO_IMPORTACION);
				});
	}

	/** Fichas fabricadas. Acá sí sabemos qué significa cada columna. */
	private void cargarFichasConstruidas() {
		List<String[]> filas = leer("/datos/fichas-construidas.csv", true);
		jdbc.batchUpdate(SQL_INSERCION, filas, filas.size(),
				(PreparedStatement ps, String[] fila) -> {
					ps.setInt(1, Integer.parseInt(fila[1]));
					ps.setDate(2, Date.valueOf(fecha(fila[2])));
					fechaODato(ps, 3, fila[3]);
					fechaODato(ps, 4, fila[4]);
					ps.setString(5, fila[5]);
					bitONulo(ps, 6, fila[6]);
					bitONulo(ps, 7, fila[7]);
					enteroONulo(ps, 8, fila[8]);
					bitONulo(ps, 9, fila[9]);
					bitONulo(ps, 10, fila[10]);
					bitONulo(ps, 11, fila[11]);
					enteroONulo(ps, 12, fila[12]);
					enteroONulo(ps, 13, fila[13]);
					ps.setString(14, fila[14].isEmpty() ? null : fila[14]);
					ps.setString(15, USUARIO_IMPORTACION);
				});
	}

	private static final String SQL_INSERCION = """
			INSERT INTO ficha_medica (
			    legajo, fecha_evento, fecha_citacion, fecha_alta, estado_paciente,
			    in_itinere, estaba_en_servicio, hora_accidente,
			    atendido_servicio_medico, envio_medico_domicilio, justificado,
			    grupo_enfermedad_id, detalle_enfermedad_id, observaciones,
			    creada_por, creada_en)
			VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, SYSUTCDATETIME())
			""";

	/**
	 * Resuelve el literal HOY contra el {@link Clock} de la aplicación.
	 *
	 * <p>Fijar esa fecha en el archivo dejaría el caso vencido al día siguiente:
	 * es el borde de FR-014c, una ficha sin fecha de fin cuyo período abierto
	 * tiene longitud cero.
	 */
	private LocalDate fecha(String valor) {
		return HOY.equals(valor) ? LocalDate.now(reloj) : LocalDate.parse(valor);
	}

	private void fechaODato(PreparedStatement ps, int posicion, String valor) throws java.sql.SQLException {
		if (SIN_VALOR.equals(valor) || valor.isEmpty()) {
			ps.setNull(posicion, Types.DATE);
		} else {
			ps.setDate(posicion, Date.valueOf(fecha(valor)));
		}
	}

	private void enteroONulo(PreparedStatement ps, int posicion, String valor) throws java.sql.SQLException {
		if (SIN_VALOR.equals(valor) || valor.isEmpty()) {
			ps.setNull(posicion, Types.INTEGER);
		} else {
			ps.setInt(posicion, Integer.parseInt(valor));
		}
	}

	private void bitONulo(PreparedStatement ps, int posicion, String valor) throws java.sql.SQLException {
		if (SIN_VALOR.equals(valor) || valor.isEmpty()) {
			ps.setNull(posicion, Types.BIT);
		} else {
			ps.setBoolean(posicion, "1".equals(valor));
		}
	}

	private List<String[]> leer(String recurso, boolean tieneEncabezado) {
		try (var entrada = CargadorDatosHistoricos.class.getResourceAsStream(recurso)) {
			if (entrada == null) {
				throw new IllegalStateException("Falta el archivo de datos " + recurso);
			}
			var lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
			List<String[]> filas = new ArrayList<>();
			String linea = tieneEncabezado ? lector.readLine() : null;
			while ((linea = lector.readLine()) != null) {
				if (!linea.isBlank()) {
					filas.add(linea.split(";", -1));
				}
			}
			return filas;
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer " + recurso, e);
		}
	}
}
