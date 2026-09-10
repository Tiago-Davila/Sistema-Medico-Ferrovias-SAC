package com.ferrovias.sismedico.unitarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;

/**
 * El dominio tolera lo que el histórico trae y no cuenta lo que no debe.
 */
class FichaMedicaTest {

	private FichaMedica fichaConDatosClinicos() {
		return new FichaMedica(8842L, 3L, 4821,
				LocalDate.of(2026, 3, 3), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				LocalDate.of(2026, 3, 10), null,
				100, 1001,
				new Observaciones("Bronquitis con antecedentes de asma"),
				null);
	}

	@Test
	void el_toString_no_lleva_observaciones_ni_codigos_de_enfermedad() {
		// M3. El toString() que genera un record imprime todos los componentes:
		// un solo log.debug("ficha {}", ficha) volcaría el diagnóstico de un
		// empleado identificado a un archivo de texto.
		String texto = fichaConDatosClinicos().toString();

		assertThat(texto)
				.contains("id=8842")
				.contains("legajo=4821")
				.contains("fechaEvento=2026-03-03")
				.doesNotContain("Bronquitis")
				.doesNotContain("asma")
				.doesNotContain("observaciones")
				.doesNotContain("1001");
	}

	@Test
	void una_ficha_historica_sin_clasificacion_ni_fechas_de_fin_se_construye_igual() {
		// FR-028 y FR-030: si el dominio rechazara esto, cada consulta de una
		// ficha anterior a 2016 sería una excepción.
		var historica = new FichaMedica(1L, 0L, 4821,
				LocalDate.of(2014, 3, 11), null,
				null, null, null, null, null, null,
				null, null, null, null, null, null);

		assertThat(historica.grupoEnfermedad()).isNull();
		assertThat(historica.estadoPaciente()).isNull();
		assertThat(historica.finDelPeriodo()).isNull();
	}

	@Test
	void los_booleanos_distinguen_no_de_sin_responder() {
		// FR-004d: un valor ausente no se interpreta como "no". Si fueran
		// boolean primitivo, esta distinción no existiría.
		var sinResponder = new FichaMedica(1L, 0L, 4821, LocalDate.of(2016, 1, 1), null,
				null, null, null, null, null, null, null, null, null, null, null, null);
		var respondioQueNo = new FichaMedica(1L, 0L, 4821, LocalDate.of(2016, 1, 1), null,
				null, null, null, null, null, false, null, null, null, null, null, null);

		assertThat(sinResponder.justificado()).isNull();
		assertThat(respondioQueNo.justificado()).isFalse();
	}

	@Test
	void el_fin_del_periodo_es_la_citacion_y_si_no_el_alta() {
		var conCitacion = fichaConDatosClinicos();
		assertThat(conCitacion.finDelPeriodo()).isEqualTo(LocalDate.of(2026, 3, 10));

		var conAlta = new FichaMedica(1L, 0L, 4821, LocalDate.of(2026, 3, 1), null,
				null, null, null, null, null, null, null, LocalDate.of(2026, 3, 10),
				null, null, null, null);
		assertThat(conAlta.finDelPeriodo()).isEqualTo(LocalDate.of(2026, 3, 10));
	}

	@Test
	void el_estado_del_paciente_tiene_exactamente_dos_valores() {
		// FR-005. El sistema de referencia tiene ramas para otros dos que nunca
		// se ejecutaron en más de 80.000 registros: es código muerto.
		assertThat(EstadoPaciente.values()).hasSize(2);
		assertThat(EstadoPaciente.ACCIDENTADO.codigo()).isEqualTo("A");
		assertThat(EstadoPaciente.ENFERMEDAD.codigo()).isEqualTo("E");
	}

	@Test
	void un_estado_desconocido_se_lee_como_vacio_y_no_como_error() {
		// FR-030: no se valida al leer. Una ficha con un estado que hoy no
		// existe tiene que poder abrirse.
		assertThat(EstadoPaciente.desdeCodigo("Z")).isNull();
		assertThat(EstadoPaciente.desdeCodigo(null)).isNull();
		assertThat(EstadoPaciente.desdeCodigo("E")).isEqualTo(EstadoPaciente.ENFERMEDAD);
	}
}
