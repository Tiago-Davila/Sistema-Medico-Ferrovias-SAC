package com.ferrovias.sismedico.fichas;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ferrovias.sismedico.comun.Violacion;
import com.ferrovias.sismedico.comun.Violacion.Codigos;

/**
 * FR-014: dos fichas del mismo empleado no pueden tener períodos solapados.
 *
 * <h2>El período, y por qué el extremo derecho no cuenta</h2>
 *
 * El período de una ficha va desde la fecha del evento hasta la de citación o,
 * si no hay citación, hasta la de alta. El extremo derecho está <b>excluido</b>
 * (FR-014b): dos fichas que comparten un extremo no se solapan. Un empleado que
 * recibe el alta el 10 de marzo y se accidenta ese mismo 10 de marzo tiene dos
 * fichas legítimas, y rechazar la segunda sería un falso positivo sobre un caso
 * real.
 *
 * <p>Formalmente: se solapan si y solo si el inicio de cada una es
 * estrictamente anterior al fin de la otra.
 *
 * <h2>Las fichas históricas sin fecha de fin</h2>
 *
 * Su período se trata como abierto desde el evento hasta <b>hoy</b> (FR-014c).
 * La consecuencia, decidida a conciencia por el cliente, es dura: un legajo con
 * una de esas fichas queda bloqueado para cargas nuevas hasta que alguien se la
 * complete. Se eligió por sobre ignorarlas porque prioriza no crear
 * solapamientos reales, al costo de forzar la corrección del dato histórico.
 *
 * <p>Por eso FR-014d exige que el mensaje sea accionable: tiene que decir que la
 * ficha en conflicto está incompleta y que la salida es cargarle una fecha de
 * citación o de alta. Es la única salida que el operario tiene, y sin esa frase
 * el rechazo aparece como un muro sin explicación.
 *
 * <p>El "hoy" sale del {@link Clock}, no de {@code GETDATE()}. Con dos relojes
 * —el de la aplicación y el del motor— la misma ficha podría pasar o no según
 * quién evalúe, y ningún test de fechas sería determinista (D7).
 */
@Component
public class DetectorSolapamiento {

	/**
	 * Consulta de data-model.md.
	 *
	 * <p>{@code COALESCE(fecha_citacion, fecha_alta, :hoy)} es el fin del
	 * período de la ficha ya guardada, con el caso abierto de FR-014c resuelto
	 * por el tercer argumento.
	 *
	 * <p>{@code id <> :idActual} es lo que evita que una ficha choque consigo
	 * misma al editarla.
	 */
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

	/** Ninguna ficha guardada tiene este id, así que al dar de alta no excluye nada. */
	private static final long NINGUNA = -1L;

	private final JdbcTemplate jdbc;
	private final Clock reloj;

	public DetectorSolapamiento(JdbcTemplate jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	/**
	 * Busca la primera ficha con la que la que se está guardando se solapa.
	 *
	 * @param legajo el legajo <b>de destino</b>. Al reasignar una ficha a otro
	 *     empleado (FR-003d), unicidad y solapamiento se evalúan contra las
	 *     fichas del empleado al que va, no contra las del que venía (FR-003e).
	 * @param idActual id de la ficha que se está editando, o {@code null} en un
	 *     alta
	 * @return vacío si no hay choque
	 */
	public Optional<Violacion> detectar(int legajo, Long idActual,
			LocalDate fechaEvento, LocalDate finDelPeriodo) {

		if (fechaEvento == null) {
			// Sin fecha de evento no hay período que comparar. La falta ya la
			// reporta ValidadorFichaMedica; acá no se duplica el mensaje.
			return Optional.empty();
		}

		LocalDate hoy = LocalDate.now(reloj);
		// Una ficha nueva sin ninguna fecha de fin también tiene período abierto
		// hasta hoy, igual que las históricas. FR-008 la va a rechazar igual,
		// pero el detector no puede asumir que ya pasó por el validador.
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

	private static Violacion aViolacion(Solapada choque) {
		var enConflicto = new Violacion.FichaEnConflicto(
				choque.id(), choque.fechaEvento(), choque.incompleta());

		// FR-015: el mensaje identifica la ficha en conflicto por su fecha de
		// evento, que es con lo que el operario la ubica en el listado.
		String fecha = choque.fechaEvento().format(
				java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

		if (choque.incompleta()) {
			// FR-014d: el bloqueo tiene que ser accionable. Sin la segunda
			// oración el operario no tiene idea de cómo destrabarlo.
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
