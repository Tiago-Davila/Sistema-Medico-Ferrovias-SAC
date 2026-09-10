package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.service.DetectorSolapamiento;

/** FR-014, FR-014b, FR-014c, FR-014d y FR-015, contra la consulta real. */
class DetectorSolapamientoTest extends BaseIntegracion {

	private static final int LEGAJO = 960001;
	private static final int OTRO_LEGAJO = 960002;

	@Autowired
	private DetectorSolapamiento detector;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void vaciar() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
	}

	private long insertar(int legajo, String evento, String citacion, String alta) {
		jdbc.update("""
				INSERT INTO ficha_medica (legajo, fecha_evento, fecha_citacion, fecha_alta,
				                          creada_por, creada_en)
				VALUES (?, ?, ?, ?, 'test', SYSUTCDATETIME())
				""", legajo, evento, citacion, alta);
		return jdbc.queryForObject(
				"SELECT id FROM ficha_medica WHERE legajo = ? AND fecha_evento = ?",
				Long.class, legajo, evento);
	}

	@Test
	void dos_periodos_que_se_pisan_chocan() {
		// Del 1 al 10 de marzo, contra una nueva del 5 al 15.
		insertar(LEGAJO, "2026-03-01", null, "2026-03-10");

		var violacion = detector.detectar(LEGAJO, null,
				LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15));

		assertThat(violacion).isPresent();
		assertThat(violacion.get().codigo()).isEqualTo(Violacion.Codigos.SOLAPAMIENTO);
		// FR-015: identificada por su fecha de evento, que es como el operario
		// la ubica en el listado.
		assertThat(violacion.get().mensaje()).contains("01/03/2026");
		assertThat(violacion.get().fichaEnConflicto().fechaEvento())
				.isEqualTo(LocalDate.of(2026, 3, 1));
	}

	@Test
	void compartir_un_extremo_no_es_solapamiento() {
		// FR-014b y US1-15. El empleado recibe el alta el 10 y se accidenta ese
		// mismo día: son dos fichas legítimas. Rechazar la segunda sería un
		// falso positivo sobre un caso real.
		insertar(LEGAJO, "2026-03-01", null, "2026-03-10");

		var violacion = detector.detectar(LEGAJO, null,
				LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 18));

		assertThat(violacion).isEmpty();
	}

	@Test
	void una_ficha_historica_sin_fecha_de_fin_bloquea_con_mensaje_accionable() {
		// FR-014c y FR-014d, US1-16. Es la consecuencia dura que el cliente
		// aceptó: el legajo queda bloqueado hasta que alguien complete la ficha.
		insertar(LEGAJO, "2019-04-12", null, null);

		var violacion = detector.detectar(LEGAJO, null,
				LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));

		assertThat(violacion).isPresent();
		assertThat(violacion.get().codigo())
				.isEqualTo(Violacion.Codigos.SOLAPAMIENTO_FICHA_INCOMPLETA);
		assertThat(violacion.get().fichaEnConflicto().incompleta()).isTrue();
		// SC-009: sin esta frase el rechazo es un muro sin explicación.
		assertThat(violacion.get().mensaje())
				.contains("12/04/2019")
				.contains("incompleta")
				.contains("fecha de citación o de alta");
	}

	@Test
	void una_ficha_de_periodo_abierto_con_evento_hoy_no_bloquea() {
		// Borde de FR-014c: su período abierto tiene longitud cero. R6 sigue
		// impidiendo otra ficha con esa misma fecha, pero eso es unicidad, no
		// solapamiento.
		LocalDate hoy = LocalDate.now(reloj);
		insertar(LEGAJO, hoy.toString(), null, null);

		var violacion = detector.detectar(LEGAJO, null, hoy.plusDays(1), hoy.plusDays(5));

		assertThat(violacion).isEmpty();
	}

	@Test
	void una_ficha_no_choca_consigo_misma_al_editarla() {
		long id = insertar(LEGAJO, "2026-03-01", null, "2026-03-10");

		var violacion = detector.detectar(LEGAJO, id,
				LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12));

		assertThat(violacion).isEmpty();
	}

	@Test
	void las_fichas_de_otro_empleado_no_intervienen() {
		insertar(OTRO_LEGAJO, "2026-03-01", null, "2026-03-10");

		var violacion = detector.detectar(LEGAJO, null,
				LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15));

		assertThat(violacion).isEmpty();
	}

	@Test
	void una_ficha_eliminada_no_bloquea() {
		// FR-039: fuera de las validaciones de unicidad y solapamiento.
		long id = insertar(LEGAJO, "2026-03-01", null, "2026-03-10");
		jdbc.update("UPDATE ficha_medica SET eliminada_por='x', eliminada_en=SYSUTCDATETIME() "
				+ "WHERE id = ?", id);

		var violacion = detector.detectar(LEGAJO, null,
				LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15));

		assertThat(violacion).isEmpty();
	}

	@Test
	void el_solapamiento_se_evalua_contra_el_legajo_de_destino() {
		// FR-003e: al reasignar una ficha, lo que importa son las fichas del
		// empleado al que va, no las del que venía.
		insertar(OTRO_LEGAJO, "2026-03-01", null, "2026-03-10");
		long id = insertar(LEGAJO, "2026-03-05", null, "2026-03-15");

		assertThat(detector.detectar(LEGAJO, id,
				LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15))).isEmpty();

		assertThat(detector.detectar(OTRO_LEGAJO, id,
				LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15))).isPresent();
	}

	@Test
	void una_citacion_el_mismo_dia_del_evento_da_periodo_de_longitud_cero() {
		// Caso borde del spec: el período tiene longitud cero y por FR-014b no
		// se solapa con ninguna otra.
		insertar(LEGAJO, "2026-03-01", "2026-03-01", null);

		var violacion = detector.detectar(LEGAJO, null,
				LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));

		assertThat(violacion).isEmpty();
	}
}
