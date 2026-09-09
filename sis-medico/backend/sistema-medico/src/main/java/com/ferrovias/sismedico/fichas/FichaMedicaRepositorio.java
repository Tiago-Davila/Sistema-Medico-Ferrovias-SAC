package com.ferrovias.sismedico.fichas;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Acceso a {@code ficha_medica} con {@link JdbcTemplate}.
 *
 * <p>Clase concreta, sin interfaz: no hay una segunda implementación ni motivo
 * para preverla (Principio V).
 *
 * <h2>Al leer no se valida nada</h2>
 *
 * FR-030. El mapeo devuelve lo que hay en la fila, tal cual: booleanos en
 * {@code null} distintos de {@code false} (FR-004d), estados que hoy no existen
 * como {@code null} en vez de excepción, códigos de enfermedad huérfanos
 * (FR-028c), y observaciones de más de 500 caracteres sin truncar (FR-006c).
 * Cualquier cosa que este mapeo rechace es una ficha histórica que el operario
 * no va a poder abrir.
 *
 * <h2>Los días perdidos no se insertan</h2>
 *
 * {@code dias_perdidos} es columna calculada (D4): no aparece en el INSERT y no
 * puede aparecer. Es lo que hace estructuralmente imposible que el valor
 * almacenado discrepe de las fechas, como exige FR-020b.
 */
@Repository
public class FichaMedicaRepositorio {

	/** Columnas de lectura. Incluye dias_perdidos, que la calcula el motor. */
	private static final String COLUMNAS = """
			id, version, legajo, fecha_evento, estado_paciente,
			in_itinere, estaba_en_servicio, hora_accidente,
			atendido_servicio_medico, envio_medico_domicilio, justificado,
			fecha_citacion, fecha_alta, dias_perdidos,
			grupo_enfermedad_id, detalle_enfermedad_id, observaciones,
			creada_por, creada_en, modificada_por, modificada_en
			""";

	private static final RowMapper<FichaMedica> A_FICHA = (fila, numero) -> new FichaMedica(
			fila.getLong("id"),
			fila.getLong("version"),
			fila.getInt("legajo"),
			fecha(fila, "fecha_evento"),
			EstadoPaciente.desdeCodigo(fila.getString("estado_paciente")),
			booleano(fila, "in_itinere"),
			booleano(fila, "estaba_en_servicio"),
			entero(fila, "hora_accidente"),
			booleano(fila, "atendido_servicio_medico"),
			booleano(fila, "envio_medico_domicilio"),
			booleano(fila, "justificado"),
			fecha(fila, "fecha_citacion"),
			fecha(fila, "fecha_alta"),
			entero(fila, "grupo_enfermedad_id"),
			entero(fila, "detalle_enfermedad_id"),
			Observaciones.de(fila.getString("observaciones")),
			new FichaMedica.Auditoria(
					fila.getString("creada_por"),
					instante(fila, "creada_en"),
					fila.getString("modificada_por"),
					instante(fila, "modificada_en")));

	private final JdbcTemplate jdbc;

	public FichaMedicaRepositorio(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Inserta una ficha nueva y devuelve su id.
	 *
	 * <p>La versión nace en 0 (D2). No se pasa: la pone el DEFAULT de la
	 * columna, para que no haya dos lugares que decidan el valor inicial.
	 *
	 * @param momento momento de creación, del {@code Clock} (D7)
	 */
	public long insertar(FichaMedica ficha, String usuario, java.time.Instant momento) {
		var claves = new GeneratedKeyHolder();

		jdbc.update(conexion -> {
			PreparedStatement ps = conexion.prepareStatement("""
					INSERT INTO ficha_medica (
					    legajo, fecha_evento, estado_paciente,
					    in_itinere, estaba_en_servicio, hora_accidente,
					    atendido_servicio_medico, envio_medico_domicilio, justificado,
					    fecha_citacion, fecha_alta,
					    grupo_enfermedad_id, detalle_enfermedad_id, observaciones,
					    creada_por, creada_en)
					VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
					""", Statement.RETURN_GENERATED_KEYS);

			ps.setInt(1, ficha.legajo());
			ponerFecha(ps, 2, ficha.fechaEvento());
			ponerTexto(ps, 3, ficha.estadoPaciente() == null ? null : ficha.estadoPaciente().codigo());
			ponerBooleano(ps, 4, ficha.inItinere());
			ponerBooleano(ps, 5, ficha.estabaEnServicio());
			ponerEntero(ps, 6, ficha.horaAccidente(), Types.TINYINT);
			ponerBooleano(ps, 7, ficha.atendidoServicioMedico());
			ponerBooleano(ps, 8, ficha.envioMedicoDomicilio());
			ponerBooleano(ps, 9, ficha.justificado());
			ponerFecha(ps, 10, ficha.fechaCitacion());
			ponerFecha(ps, 11, ficha.fechaAlta());
			ponerEntero(ps, 12, ficha.grupoEnfermedad(), Types.INTEGER);
			ponerEntero(ps, 13, ficha.detalleEnfermedad(), Types.INTEGER);
			ponerTexto(ps, 14, ficha.observaciones() == null ? null : ficha.observaciones().texto());
			ps.setString(15, usuario);
			ps.setTimestamp(16, java.sql.Timestamp.from(momento));
			return ps;
		}, claves);

		Number id = claves.getKey();
		if (id == null) {
			throw new IllegalStateException("El INSERT no devolvió el id de la ficha.");
		}
		return id.longValue();
	}

	/**
	 * Busca una ficha viva por id.
	 *
	 * <p>Las eliminadas quedan afuera: FR-039c dice que no deben ser visibles
	 * para el operario por ningún medio, y devolverlas acá las haría visibles
	 * por la API.
	 */
	public Optional<FichaMedica> buscar(long id) {
		return jdbc.query("SELECT " + COLUMNAS + " FROM ficha_medica "
				+ "WHERE id = ? AND eliminada_en IS NULL", A_FICHA, id)
				.stream().findFirst();
	}

	/** FR-013: ¿el legajo ya tiene una ficha viva con esa fecha de evento? */
	public boolean existeOtraConMismaFecha(int legajo, LocalDate fechaEvento, Long idActual) {
		Integer cuantas = jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica
				WHERE legajo = ? AND fecha_evento = ? AND eliminada_en IS NULL AND id <> ?
				""", Integer.class, legajo, Date.valueOf(fechaEvento),
				idActual != null ? idActual : -1L);

		return cuantas != null && cuantas > 0;
	}

	// ------------------------------------------------------------- mapeo

	/**
	 * {@code null} y {@code false} son cosas distintas (FR-004d).
	 *
	 * <p>{@code ResultSet.getBoolean} devuelve {@code false} ante un NULL, que
	 * es exactamente el error que FR-004d prohíbe: mostraría "no" donde el
	 * histórico no tiene respuesta. Hay que preguntar por {@code wasNull}.
	 */
	private static Boolean booleano(ResultSet fila, String columna) throws SQLException {
		boolean valor = fila.getBoolean(columna);
		return fila.wasNull() ? null : valor;
	}

	private static Integer entero(ResultSet fila, String columna) throws SQLException {
		int valor = fila.getInt(columna);
		return fila.wasNull() ? null : valor;
	}

	private static LocalDate fecha(ResultSet fila, String columna) throws SQLException {
		Date valor = fila.getDate(columna);
		return valor == null ? null : valor.toLocalDate();
	}

	private static java.time.Instant instante(ResultSet fila, String columna) throws SQLException {
		java.sql.Timestamp valor = fila.getTimestamp(columna);
		return valor == null ? null : valor.toInstant();
	}

	private static void ponerFecha(PreparedStatement ps, int posicion, LocalDate valor)
			throws SQLException {
		if (valor == null) {
			ps.setNull(posicion, Types.DATE);
		} else {
			ps.setDate(posicion, Date.valueOf(valor));
		}
	}

	private static void ponerBooleano(PreparedStatement ps, int posicion, Boolean valor)
			throws SQLException {
		if (valor == null) {
			ps.setNull(posicion, Types.BIT);
		} else {
			ps.setBoolean(posicion, valor);
		}
	}

	private static void ponerEntero(PreparedStatement ps, int posicion, Integer valor, int tipo)
			throws SQLException {
		if (valor == null) {
			ps.setNull(posicion, tipo);
		} else {
			ps.setInt(posicion, valor);
		}
	}

	private static void ponerTexto(PreparedStatement ps, int posicion, String valor)
			throws SQLException {
		if (valor == null) {
			ps.setNull(posicion, Types.NVARCHAR);
		} else {
			ps.setString(posicion, valor);
		}
	}
}
