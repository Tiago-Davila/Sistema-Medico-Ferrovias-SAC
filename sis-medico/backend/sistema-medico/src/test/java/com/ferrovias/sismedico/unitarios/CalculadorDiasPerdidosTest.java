package com.ferrovias.sismedico.unitarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.ferrovias.sismedico.fichas.CalculadorDiasPerdidos;

/** FR-020, FR-020c, FR-021 y SC-006. */
class CalculadorDiasPerdidosTest {

	private final CalculadorDiasPerdidos calculador = new CalculadorDiasPerdidos();

	@ParameterizedTest(name = "de {0} a {1} son {2} días")
	@CsvSource({
			"2026-03-01, 2026-03-11,   10",
			// Borde: alta el mismo día del evento. Es válido y da cero.
			"2026-03-01, 2026-03-01,    0",
			// Borde exacto de FR-011: 999 es válido, 1000 no.
			"2024-01-01, 2026-09-26,  999",
			"2024-01-01, 2026-09-27, 1000",
	})
	void calcula_la_diferencia_entre_las_dos_fechas(String evento, String alta, long esperado) {
		assertThat(calculador.calcular(LocalDate.parse(evento), LocalDate.parse(alta)))
				.isEqualTo(esperado);
	}

	@Test
	void sin_fecha_de_alta_el_valor_es_vacio_y_no_cero() {
		// FR-021. Cero días perdidos es un empleado que se reincorporó el mismo
		// día; vacío es uno que todavía no se reincorporó. No son lo mismo.
		assertThat(calculador.calcular(LocalDate.of(2026, 3, 1), null)).isNull();
	}

	@Test
	void una_ficha_historica_con_alta_anterior_al_evento_da_negativo() {
		// FR-020c: la diferencia real, aunque sea negativa. No cero, no vacío.
		// Mostrar cero escondería el error del dato en lugar de exponerlo.
		assertThat(calculador.calcular(LocalDate.of(2020, 1, 10), LocalDate.of(2019, 12, 31)))
				.isEqualTo(-10L);
	}

	@Test
	void una_ficha_historica_de_mas_de_999_dias_no_se_topea_al_leer() {
		// FR-020: el límite de 999 es de escritura (FR-011) y no se aplica al
		// leer. La restricción actúa al cargar, no al mostrar.
		assertThat(calculador.calcular(LocalDate.of(2017, 1, 1), LocalDate.of(2020, 1, 1)))
				.isEqualTo(1095L);
	}

	@Test
	void sin_fecha_de_evento_tampoco_hay_valor() {
		assertThat(calculador.calcular(null, LocalDate.of(2026, 3, 1))).isNull();
	}
}
