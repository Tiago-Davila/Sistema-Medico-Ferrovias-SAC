package com.ferrovias.sismedico.repositories;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.ferrovias.sismedico.exceptions.PadronNoDisponibleException;
import com.ferrovias.sismedico.models.Empleado;

// Consulta de solo lectura contra el padrón de empleados, que vive en otra base y no se copia.
// Intencional: la ficha guarda solo el legajo; apellido, nombre, sección y categoría laboral se
// resuelven en cada consulta, para no quedar congelados desde el momento de la carga.
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

	// Busca un empleado por legajo; lanza PadronNoDisponibleException si la base externa no responde.
	public Optional<Empleado> buscar(int legajo) {
		try {
			return jdbc.query("""
					SELECT legajo, apellido, nombre, seccion, categoria_laboral
					FROM %s.empleado
					WHERE legajo = ?
					""".formatted(esquemaDelPadron), A_EMPLEADO, legajo)
					.stream().findFirst();

		} catch (DataAccessResourceFailureException | QueryTimeoutException e) {
			// Intencional: se distingue "padrón caído" (503) de "legajo inexistente" (404).
			throw new PadronNoDisponibleException(e);
		}
	}

	public boolean existe(int legajo) {
		return buscar(legajo).isPresent();
	}
}
