package com.ferrovias.sismedico.unitarios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ferrovias.sismedico.fichas.Observaciones;

/** M3: el contenido clínico no sale por {@code toString()}. */
class ObservacionesTest {

	@Test
	void el_toString_no_dice_que_dice_la_observacion() {
		var observaciones = new Observaciones("Bronquitis con antecedentes de asma");

		assertThat(observaciones.toString())
				.isEqualTo("Observaciones[longitud=35]")
				.doesNotContain("Bronquitis")
				.doesNotContain("asma");
	}

	@Test
	void una_observacion_historica_de_mas_de_500_caracteres_entra_sin_problema() {
		// FR-006c: se muestra completa. El tope de 500 es de escritura y lo
		// aplica el validador, no este tipo.
		var largas = new Observaciones("x".repeat(1200));

		assertThat(largas.texto()).hasSize(1200);
		assertThat(largas.longitud()).isEqualTo(1200);
	}

	@Test
	void sin_observaciones_no_hay_objeto() {
		assertThat(Observaciones.de(null)).isNull();
	}

	@Test
	void dos_observaciones_con_el_mismo_texto_son_iguales() {
		assertThat(new Observaciones("igual")).isEqualTo(new Observaciones("igual"));
		assertThat(new Observaciones("igual")).hasSameHashCodeAs(new Observaciones("igual"));
	}
}
