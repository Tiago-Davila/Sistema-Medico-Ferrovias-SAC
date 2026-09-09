package com.ferrovias.sismedico.empleados;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Consulta de solo lectura contra el padrón de empleados.
 *
 * <h2>El padrón no se copia</h2>
 *
 * FR-003b: vive en una base separada, mantenida por otro sistema, y esta
 * aplicación <b>lo consulta donde está</b>. No lo importa, no lo sincroniza y no
 * lo cachea. Cada búsqueda va a la fuente.
 *
 * <p>No es purismo: una copia se desactualiza, y un legajo dado de baja o un
 * apellido corregido en el padrón seguirían apareciendo viejos acá sin que nadie
 * se entere. El alta de personal ocurre íntegramente fuera de esta aplicación, y
 * un legajo nuevo aparece solo porque apareció en el padrón.
 *
 * <p>Por lo mismo la ficha guarda el legajo y nada más (FR-003c): apellido,
 * nombre, sección y categoría laboral se resuelven al consultar. Si se copiaran
 * a la ficha quedarían congelados en el momento de la carga.
 *
 * <h2>Solo lectura, y solo estas dos consultas</h2>
 *
 * No hay alta, modificación ni baja de empleados, ni las va a haber: FR-003 lo
 * prohíbe explícitamente.
 *
 * <h2>Consulta entre bases</h2>
 *
 * Se usa nombre de tres partes sobre el mismo {@code DataSource}, asumiendo que
 * el padrón está en la misma instancia de SQL Server. Es el supuesto que T002
 * tenía que verificar contra la base real y quedó sin verificar: si estuviera en
 * otra instancia hace falta un {@code DataSource} aparte. Eso cambia esta clase
 * y la configuración, no el diseño.
 *
 * <p>Por eso el nombre de la base es una propiedad y no está escrito en el SQL.
 */
@Repository
public class PadronRepositorio {

	private static final RowMapper<Empleado> A_EMPLEADO = (fila, numero) -> new Empleado(
			fila.getInt("legajo"),
			fila.getString("apellido"),
			fila.getString("nombre"),
			fila.getString("seccion"),
			fila.getString("categoria_laboral"));

	private final JdbcTemplate jdbc;
	private final String esquemaDelPadron;

	public PadronRepositorio(JdbcTemplate jdbc,
			@Value("${app.padron.esquema}") String esquemaDelPadron) {
		this.jdbc = jdbc;
		this.esquemaDelPadron = esquemaDelPadron;
	}

	/**
	 * Busca un empleado por legajo (FR-001).
	 *
	 * @param legajo entero, sin ceros a la izquierda significativos: {@code 000482}
	 *     y {@code 482} resuelven al mismo empleado (FR-004f). Que sea entero es
	 *     el supuesto que T003 tenía que verificar contra el padrón real; la
	 *     autoridad es esa base, no FR-004f.
	 * @return vacío si el legajo no existe en el padrón (FR-002)
	 * @throws PadronNoDisponibleException si la base externa no responde
	 */
	public Optional<Empleado> buscar(int legajo) {
		try {
			return jdbc.query("""
					SELECT legajo, apellido, nombre, seccion, categoria_laboral
					FROM %s.empleado
					WHERE legajo = ?
					""".formatted(esquemaDelPadron), A_EMPLEADO, legajo)
					.stream().findFirst();

		} catch (DataAccessResourceFailureException | QueryTimeoutException e) {
			// El padrón es una dependencia externa y puede estar caído. Se
			// distingue de "el legajo no existe" a propósito: uno es 404 y el
			// otro 503, y confundirlos le diría al operario que el empleado no
			// existe cuando lo que pasa es que no se lo puede consultar.
			throw new PadronNoDisponibleException(e);
		}
	}

	/** {@code true} si el legajo existe. Es lo que necesita FR-002 al guardar. */
	public boolean existe(int legajo) {
		return buscar(legajo).isPresent();
	}
}
