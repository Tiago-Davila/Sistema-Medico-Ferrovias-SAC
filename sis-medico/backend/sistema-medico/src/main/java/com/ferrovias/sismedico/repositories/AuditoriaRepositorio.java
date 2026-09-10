package com.ferrovias.sismedico.repositories;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ferrovias.sismedico.models.Operacion;

// El historial de escrituras sobre las fichas: solo inserción, nunca update ni delete.
// Intencional: no guarda el contenido de la ficha, solo quién, cuándo, sobre qué registro y qué
// operación. Un historial con diagnósticos sería otra copia de datos clínicos que proteger.
@Repository
public class AuditoriaRepositorio {

	private final JdbcTemplate jdbc;

	public AuditoriaRepositorio(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// Deja un asiento; se llama dentro de la misma transacción que la escritura que registra.
	public void registrar(long fichaId, Operacion operacion, String usuario, Instant momento) {
		jdbc.update("""
				INSERT INTO ficha_medica_auditoria (ficha_id, operacion, usuario, momento)
				VALUES (?, ?, ?, ?)
				""", fichaId, operacion.name(), usuario, Timestamp.from(momento));
	}

	// Historial de una ficha, del más viejo al más nuevo.
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

	public record Asiento(long fichaId, Operacion operacion, String usuario, Instant momento) {
	}
}
