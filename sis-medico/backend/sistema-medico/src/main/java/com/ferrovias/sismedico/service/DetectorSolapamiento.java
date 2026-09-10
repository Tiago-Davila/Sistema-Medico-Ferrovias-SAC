package com.ferrovias.sismedico.service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.dtos.Violacion.Codigos;

// Detecta si dos fichas del mismo empleado tienen períodos solapados.
// Intencional: el extremo derecho del período no cuenta, así que dos fichas que comparten fecha
// de alta/evento no se solapan. Una ficha histórica sin ninguna fecha de fin se trata como
// abierta hasta hoy, lo que bloquea cargas nuevas para ese legajo hasta que alguien la complete
// (decisión del cliente, no un bug).
@Component
public class DetectorSolapamiento {

	private static final String SQL = """
			SELECT id, fecha_evento,
			       CASE WHEN fecha_citacion IS NULL AND fecha_alta IS NULL
			            THEN 1 ELSE 0 END AS incompleta
			FROM ficha_medica
			WHERE legajo = ?
			  AND eliminada_en IS NULL
			  AND id <> ?
			  AND ? < COALESCE(fecha_citacion, fecha_alta, ?)
			  AND fecha_evento < ?
			ORDER BY fecha_evento
			""";

	// Ninguna ficha guardada tiene este id, así que al dar de alta no excluye nada.
	private static final long NINGUNA = -1L;

	private final JdbcTemplate jdbc;
	private final Clock reloj;

	public DetectorSolapamiento(JdbcTemplate jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	// Busca la primera ficha del legajo con la que la ficha que se está guardando se solapa.
	public Optional<Violacion> detectar(int legajo, Long idActual,
			LocalDate fechaEvento, LocalDate finDelPeriodo) {

		if (fechaEvento == null) {
			return Optional.empty();
		}

		LocalDate hoy = LocalDate.now(reloj);
		LocalDate fin = finDelPeriodo != null ? finDelPeriodo : hoy;

		List<Solapada> choques = jdbc.query(SQL,
				(fila, numero) -> new Solapada(
						fila.getLong("id"),
						fila.getDate("fecha_evento").toLocalDate(),
						fila.getInt("incompleta") == 1),
				legajo,
				idActual != null ? idActual : NINGUNA,
				Date.valueOf(fechaEvento),
				Date.valueOf(hoy),
				Date.valueOf(fin));

		return choques.stream().findFirst().map(DetectorSolapamiento::aViolacion);
	}

	// Arma el mensaje de conflicto, con instrucciones de cómo destrabarlo si la ficha choca con una incompleta.
	private static Violacion aViolacion(Solapada choque) {
		var enConflicto = new Violacion.FichaEnConflicto(
				choque.id(), choque.fechaEvento(), choque.incompleta());

		String fecha = choque.fechaEvento().format(
				java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

		if (choque.incompleta()) {
			return new Violacion("fechaEvento", Codigos.SOLAPAMIENTO_FICHA_INCOMPLETA,
					"Choca con la ficha del " + fecha + ", que está incompleta. Cargale una "
							+ "fecha de citación o de alta para poder continuar.",
					enConflicto);
		}
		return new Violacion("fechaEvento", Codigos.SOLAPAMIENTO,
				"El período de esta ficha se superpone con el de la ficha del " + fecha + ".",
				enConflicto);
	}

	private record Solapada(long id, LocalDate fechaEvento, boolean incompleta) {
	}
}
