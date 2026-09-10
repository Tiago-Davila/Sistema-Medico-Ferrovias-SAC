package com.ferrovias.sismedico.repositories;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;

// Acceso a la tabla ficha_medica con JdbcTemplate.
// Intencional: al leer no se valida nada, el mapeo devuelve la fila tal cual (booleanos null
// distintos de false, estados desconocidos como null, observaciones sin truncar). Rechazar algo
// acá dejaría a una ficha histórica sin poder abrirse.
@Repository
public class FichaMedicaRepositorio {

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

	// Inserta una ficha nueva y devuelve su id; la versión nace en 0 por el DEFAULT de la columna.
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

	// Actualiza la ficha solo si nadie la tocó desde que el operario la abrió (D2).
	// Devuelve false si el UPDATE no afectó ninguna fila, que es como se manifiesta el conflicto:
	// otro operario ya guardó, o la ficha fue eliminada mientras tanto.
	public boolean actualizar(FichaMedica ficha, long nuevaVersion, String usuario,
			java.time.Instant momento) {

		int filas = jdbc.update(conexion -> {
			PreparedStatement ps = conexion.prepareStatement("""
					UPDATE ficha_medica SET
					    legajo = ?, fecha_evento = ?, estado_paciente = ?,
					    in_itinere = ?, estaba_en_servicio = ?, hora_accidente = ?,
					    atendido_servicio_medico = ?, envio_medico_domicilio = ?, justificado = ?,
					    fecha_citacion = ?, fecha_alta = ?,
					    grupo_enfermedad_id = ?, detalle_enfermedad_id = ?, observaciones = ?,
					    modificada_por = ?, modificada_en = ?, version = ?
					WHERE id = ? AND version = ? AND eliminada_en IS NULL
					""");

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
			ps.setLong(17, nuevaVersion);
			ps.setLong(18, ficha.id());
			ps.setLong(19, ficha.version());
			return ps;
		});

		return filas == 1;
	}

	// Borrado lógico: marca quién y cuándo, sin borrar la fila (FR-039b).
	// Intencional: el predicado `eliminada_en IS NULL` ya deja la ficha fuera de la unicidad, del
	// solapamiento y de los listados, así que no hace falta tocar ningún otro lado.
	public boolean eliminar(long id, long version, String usuario, java.time.Instant momento) {
		int filas = jdbc.update("""
				UPDATE ficha_medica
				SET eliminada_por = ?, eliminada_en = ?
				WHERE id = ? AND version = ? AND eliminada_en IS NULL
				""", usuario, java.sql.Timestamp.from(momento), id, version);

		return filas == 1;
	}

	// Busca una ficha viva por id; las eliminadas no son visibles por ningún medio.
	public Optional<FichaMedica> buscar(long id) {
		return jdbc.query("SELECT " + COLUMNAS + " FROM ficha_medica "
				+ "WHERE id = ? AND eliminada_en IS NULL", A_FICHA, id)
				.stream().findFirst();
	}

	// Fichas vivas de un empleado, de la más reciente a la más vieja.
	// Intencional: sin paginado y sin filtros. Un empleado tiene decenas de fichas en toda su
	// carrera, no miles, y paginar obligaría al operario a buscar en qué página quedó la que busca.
	public List<FichaMedica> listarPorLegajo(int legajo) {
		return jdbc.query("SELECT " + COLUMNAS + " FROM ficha_medica "
				+ "WHERE legajo = ? AND eliminada_en IS NULL "
				+ "ORDER BY fecha_evento DESC, id DESC", A_FICHA, legajo);
	}

	// El legajo ya tiene una ficha viva con esa fecha de evento.
	public boolean existeOtraConMismaFecha(int legajo, LocalDate fechaEvento, Long idActual) {
		Integer cuantas = jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica
				WHERE legajo = ? AND fecha_evento = ? AND eliminada_en IS NULL AND id <> ?
				""", Integer.class, legajo, Date.valueOf(fechaEvento),
				idActual != null ? idActual : -1L);

		return cuantas != null && cuantas > 0;
	}

	// Intencional: getBoolean devuelve false ante un NULL, así que hay que preguntar wasNull
	// para no confundir "no" con "sin responder".
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
