package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.repositories.FichaMedicaRepositorio;
import com.ferrovias.sismedico.models.Observaciones;

/** Inserción y lectura, con la tolerancia que FR-028 y FR-030 exigen. */
class FichaMedicaRepositorioTest extends BaseIntegracion {

	private static final int LEGAJO = 970001;

	@Autowired
	private FichaMedicaRepositorio repositorio;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void vaciar() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
	}

	private FichaMedica fichaCompleta() {
		return new FichaMedica(null, 0L, LEGAJO,
				LocalDate.of(2026, 3, 3), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				LocalDate.of(2026, 3, 10), null,
				100, 1001,
				new Observaciones("Reposo indicado"),
				null);
	}

	@Test
	void inserta_y_recupera_la_ficha_igual() {
		long id = repositorio.insertar(fichaCompleta(), "jperez", Instant.now(reloj));

		assertThat(repositorio.buscar(id)).isPresent().get().satisfies(ficha -> {
			assertThat(ficha.legajo()).isEqualTo(LEGAJO);
			assertThat(ficha.fechaEvento()).isEqualTo(LocalDate.of(2026, 3, 3));
			assertThat(ficha.estadoPaciente()).isEqualTo(EstadoPaciente.ENFERMEDAD);
			assertThat(ficha.fechaCitacion()).isEqualTo(LocalDate.of(2026, 3, 10));
			assertThat(ficha.observaciones().texto()).isEqualTo("Reposo indicado");
			assertThat(ficha.auditoria().creadaPor()).isEqualTo("jperez");
			assertThat(ficha.auditoria().modificadaPor()).isNull();
		});
	}

	@Test
	void la_version_nace_en_cero() {
		// D2. La pone el DEFAULT de la columna, no el repositorio: un solo lugar
		// decide el valor inicial.
		long id = repositorio.insertar(fichaCompleta(), "jperez", Instant.now(reloj));

		assertThat(repositorio.buscar(id)).get()
				.satisfies(ficha -> assertThat(ficha.version()).isZero());
	}

	@Test
	void un_booleano_sin_responder_se_lee_vacio_y_no_como_no() {
		// FR-004d, y el error de mapeo más fácil de cometer: getBoolean()
		// devuelve false ante un NULL. Sin wasNull(), toda ficha histórica
		// mostraría "no" donde no hay respuesta.
		long id = repositorio.insertar(new FichaMedica(null, 0L, LEGAJO,
				LocalDate.of(2016, 5, 5), null,
				null, null, null, null, null, null,
				null, null, null, null, null, null), "importacion", Instant.now(reloj));

		assertThat(repositorio.buscar(id)).get().satisfies(ficha -> {
			assertThat(ficha.justificado()).isNull();
			assertThat(ficha.estabaEnServicio()).isNull();
			assertThat(ficha.atendidoServicioMedico()).isNull();
			assertThat(ficha.inItinere()).isNull();
		});
	}

	@Test
	void una_ficha_con_codigo_huerfano_se_lee_sin_error() {
		// FR-028b y FR-028c: no hay FK contra el catálogo y leerla no falla.
		long id = repositorio.insertar(new FichaMedica(null, 0L, LEGAJO,
				LocalDate.of(2016, 2, 15), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				null, LocalDate.of(2016, 2, 20),
				88888, 888888, null, null), "importacion", Instant.now(reloj));

		assertThat(repositorio.buscar(id)).get().satisfies(ficha -> {
			assertThat(ficha.grupoEnfermedad()).isEqualTo(88888);
			assertThat(ficha.detalleEnfermedad()).isEqualTo(888888);
		});
	}

	@Test
	void una_observacion_de_mas_de_500_caracteres_se_lee_completa() {
		// FR-006c: no se trunca ni al leer ni al almacenar.
		long id = repositorio.insertar(new FichaMedica(null, 0L, LEGAJO,
				LocalDate.of(2015, 5, 5), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				null, LocalDate.of(2015, 5, 15), 100, 1001,
				new Observaciones("y".repeat(1200)), null), "importacion", Instant.now(reloj));

		assertThat(repositorio.buscar(id)).get()
				.satisfies(ficha -> assertThat(ficha.observaciones().longitud()).isEqualTo(1200));
	}

	@Test
	void el_indice_unico_rechaza_dos_fichas_vivas_con_la_misma_fecha() {
		// FR-013, US1-13. La última red: aunque el servicio no chequeara, la
		// base no deja pasar el duplicado.
		repositorio.insertar(fichaCompleta(), "jperez", Instant.now(reloj));

		assertThatThrownBy(() -> repositorio.insertar(fichaCompleta(), "otro", Instant.now(reloj)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void despues_de_eliminar_se_puede_reusar_la_misma_fecha() {
		// FR-039: la unicidad rige solo entre fichas vivas.
		long id = repositorio.insertar(fichaCompleta(), "jperez", Instant.now(reloj));
		jdbc.update("UPDATE ficha_medica SET eliminada_por='jperez', eliminada_en=SYSUTCDATETIME() "
				+ "WHERE id = ?", id);

		long nueva = repositorio.insertar(fichaCompleta(), "otro", Instant.now(reloj));

		assertThat(nueva).isNotEqualTo(id);
		assertThat(repositorio.buscar(nueva)).isPresent();
	}

	@Test
	void una_ficha_eliminada_no_se_encuentra() {
		// FR-039c: invisible para el operario por cualquier medio, incluida la
		// API.
		long id = repositorio.insertar(fichaCompleta(), "jperez", Instant.now(reloj));
		jdbc.update("UPDATE ficha_medica SET eliminada_por='jperez', eliminada_en=SYSUTCDATETIME() "
				+ "WHERE id = ?", id);

		assertThat(repositorio.buscar(id)).isEmpty();
	}

	@Test
	void la_unicidad_se_consulta_sin_contarse_a_si_misma() {
		long id = repositorio.insertar(fichaCompleta(), "jperez", Instant.now(reloj));

		assertThat(repositorio.existeOtraConMismaFecha(LEGAJO, LocalDate.of(2026, 3, 3), id))
				.isFalse();
		assertThat(repositorio.existeOtraConMismaFecha(LEGAJO, LocalDate.of(2026, 3, 3), null))
				.isTrue();
	}

	@Test
	void los_dias_perdidos_los_calcula_el_motor_y_no_se_insertan() {
		// D4 y FR-020b: no hay forma de guardar un valor que discrepe de las
		// fechas, porque no hay dónde guardarlo.
		long id = repositorio.insertar(new FichaMedica(null, 0L, LEGAJO,
				LocalDate.of(2026, 3, 1), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				null, LocalDate.of(2026, 3, 11), 100, 1001, null, null),
				"jperez", Instant.now(reloj));

		Integer dias = jdbc.queryForObject(
				"SELECT dias_perdidos FROM ficha_medica WHERE id = ?", Integer.class, id);

		assertThat(dias).isEqualTo(10);
	}
}
