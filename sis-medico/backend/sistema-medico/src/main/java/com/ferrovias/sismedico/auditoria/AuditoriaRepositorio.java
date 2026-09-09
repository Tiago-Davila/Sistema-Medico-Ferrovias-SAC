package com.ferrovias.sismedico.auditoria;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * El historial de escrituras sobre las fichas (D5, FR-033).
 *
 * <p><b>Solo inserción.</b> No hay update ni delete, y no puede haberlos: un
 * rastro de auditoría que se puede editar no es un rastro de auditoría.
 *
 * <p>Existe porque las columnas de auditoría de {@code ficha_medica} no alcanzan.
 * Esas cumplen FR-032 —quién creó, quién modificó por última vez— pero no
 * FR-033, que exige registrar <b>toda</b> escritura: la segunda modificación
 * pisa el rastro de la primera. Es la única complejidad que este diseño agrega
 * por encima de lo mínimo, y es una tabla con un INSERT, sin abstracción ni
 * disparadores.
 *
 * <p><b>No guarda el contenido de la ficha</b>: quién, cuándo, sobre qué
 * registro y qué operación. Un historial con diagnósticos adentro sería una
 * segunda copia de datos clínicos que habría que proteger igual que la primera,
 * y con más superficie porque nunca se borra.
 */
@Repository
public class AuditoriaRepositorio {

	private final JdbcTemplate jdbc;

	public AuditoriaRepositorio(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Deja un asiento.
	 *
	 * <p>Lo llama {@link AuditoriaServicio} dentro de la misma transacción que
	 * la escritura que registra.
	 */
	public void registrar(long fichaId, Operacion operacion, String usuario, Instant momento) {
		jdbc.update("""
				INSERT INTO ficha_medica_auditoria (ficha_id, operacion, usuario, momento)
				VALUES (?, ?, ?, ?)
				""", fichaId, operacion.name(), usuario, Timestamp.from(momento));
	}

	/** Historial de una ficha, del más viejo al más nuevo. */
	public List<Asiento> historialDe(long fichaId) {
		return jdbc.query("""
				SELECT ficha_id, operacion, usuario, momento
				FROM ficha_medica_auditoria
				WHERE ficha_id = ?
				ORDER BY momento, id
				""", (fila, numero) -> new Asiento(
						fila.getLong("ficha_id"),
						Operacion.valueOf(fila.getString("operacion")),
						fila.getString("usuario"),
						fila.getTimestamp("momento").toInstant()),
				fichaId);
	}

	/** Un renglón del historial. */
	public record Asiento(long fichaId, Operacion operacion, String usuario, Instant momento) {
	}
}
