package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que el juego de datos contiene de verdad los casos que FR-036b exige.
 *
 * <p>Sin esto, alguien puede vaciar {@code fichas-construidas.csv} y toda la
 * suite de tolerancia de lectura de US2 seguiría en verde sin probar nada: los
 * tests pasarían porque no habría ficha inconsistente que abrir. Este test es lo
 * que convierte al juego de datos en una garantía en lugar de un archivo que
 * nadie mira.
 */
class JuegoDeDatosTest extends BaseIntegracion {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void cargar() {
		new CargadorDatosHistoricos(jdbc, reloj).cargarTodo();
	}

	private int contar(String condicion) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM ficha_medica WHERE " + condicion, Integer.class);
	}

	@Test
	void entra_sin_filtrar_y_sin_validar() {
		// El volcado real completo, no una selección de las que cumplen las
		// reglas de hoy.
		assertThat(contar("1=1")).isEqualTo(670 + 16);
	}

	@Test
	void hay_fichas_sin_grupo_ni_detalle() {
		// FR-036b lo pide por nombre. Sin estas, FR-028 y FR-031 no se prueban.
		assertThat(contar("grupo_enfermedad_id IS NULL AND detalle_enfermedad_id IS NULL"))
				.isPositive();
		assertThat(contar("grupo_enfermedad_id IS NOT NULL AND detalle_enfermedad_id IS NULL"))
				.isPositive();
	}

	@Test
	void hay_fichas_sin_ninguna_de_las_dos_fechas_de_fin() {
		// FR-014c: período abierto hasta hoy. Es lo que bloquea un legajo entero.
		assertThat(contar("fecha_citacion IS NULL AND fecha_alta IS NULL")).isPositive();
	}

	@Test
	void hay_una_ficha_de_periodo_abierto_con_evento_hoy() {
		// Borde de FR-014c: período abierto de longitud cero, no bloquea.
		Integer fichas = jdbc.queryForObject(
				"SELECT COUNT(*) FROM ficha_medica "
						+ "WHERE fecha_citacion IS NULL AND fecha_alta IS NULL AND fecha_evento = ?",
				Integer.class, java.sql.Date.valueOf(java.time.LocalDate.now(reloj)));

		assertThat(fichas).isPositive();
	}

	@Test
	void hay_fichas_con_las_dos_fechas_de_fin() {
		// Viola FR-008 al escribir. Al leer se muestra igual (FR-028).
		assertThat(contar("fecha_citacion IS NOT NULL AND fecha_alta IS NOT NULL")).isPositive();
	}

	@Test
	void hay_dias_perdidos_negativos_y_mayores_a_999() {
		// FR-020c y SC-006: el valor real, sin topear, en los dos extremos.
		assertThat(contar("dias_perdidos < 0")).isPositive();
		assertThat(contar("dias_perdidos > 999")).isPositive();
	}

	@Test
	void hay_una_observacion_de_mas_de_500_caracteres() {
		// FR-006c: se muestra completa. El tope de 500 es solo de escritura.
		assertThat(contar("LEN(observaciones) > 500")).isPositive();
	}

	@Test
	void hay_un_codigo_de_enfermedad_huerfano() {
		// FR-028b y FR-028c: código sin descripción, sin FK que lo impida.
		assertThat(contar("grupo_enfermedad_id IS NOT NULL "
				+ "AND grupo_enfermedad_id NOT IN (SELECT id FROM grupo_enfermedad)"))
				.isPositive();
	}

	@Test
	void hay_fichas_con_campos_de_si_o_no_sin_responder() {
		// FR-004d: ausente se muestra vacío, no como "no".
		assertThat(contar("justificado IS NULL")).isPositive();
	}

	@Test
	void hay_dos_fichas_que_comparten_un_extremo() {
		// FR-014b: compartir un extremo no es solapamiento. Si el dataset no las
		// tuviera, US1-15 no sería verificable sobre datos cargados.
		Integer pares = jdbc.queryForObject("""
				SELECT COUNT(*) FROM ficha_medica a
				JOIN ficha_medica b ON b.legajo = a.legajo AND b.fecha_evento = a.fecha_alta
				""", Integer.class);

		assertThat(pares).isPositive();
	}

	@Test
	void hay_un_legajo_con_fichas_que_no_esta_en_el_padron() {
		// Consecuencia que D3 acepta de frente: el padrón puede dar de baja un
		// legajo con fichas y el sistema no puede impedirlo. Al leer, FR-028
		// manda mostrar la ficha igual.
		Integer colgados = jdbc.queryForObject("""
				SELECT COUNT(DISTINCT f.legajo)
				FROM ficha_medica f
				WHERE NOT EXISTS (
				    SELECT 1 FROM padron.dbo.empleado e WHERE e.legajo = f.legajo)
				""", Integer.class);

		assertThat(colgados).isEqualTo(1);
	}

	@Test
	void ningun_legajo_ni_observacion_del_volcado_crudo_sobrevivio() {
		// FR-036c. Los legajos reales del volcado son de cinco dígitos; los del
		// juego de datos arrancan en 900001. Y ninguna observación conserva
		// texto clínico real.
		assertThat(contar("legajo < 900000")).isZero();

		for (String diagnosticoReal : new String[] {
				"ARTRITIS", "AMIGDALITIS", "CEFALEA", "ABORTO", "PSIQ", "GRIPAL", "HERNIA" }) {
			assertThat(contar("observaciones LIKE '%" + diagnosticoReal + "%'"))
					.as("el volcado anonimizado no puede conservar '%s'", diagnosticoReal)
					.isZero();
		}
	}
}
