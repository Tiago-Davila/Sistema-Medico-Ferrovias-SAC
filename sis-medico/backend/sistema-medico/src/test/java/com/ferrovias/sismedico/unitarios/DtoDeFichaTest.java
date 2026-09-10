package com.ferrovias.sismedico.unitarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ferrovias.sismedico.dtos.FichaEntradaDTO;
import com.ferrovias.sismedico.dtos.RespuestaDeEscrituraDTO;

/** M4 y M2: lo que el contrato de entrada y salida no permite. */
class DtoDeFichaTest {

	@Test
	void el_dto_de_entrada_no_tiene_donde_recibir_los_dias_perdidos() {
		// M4. No es que se ignore lo que mande el cliente: no existe el campo.
		// Es la diferencia entre una regla que se hace cumplir y una que se
		// confía. Si alguien agrega el componente, este test lo señala.
		List<String> componentes = Arrays.stream(FichaEntradaDTO.class.getRecordComponents())
				.map(componente -> componente.getName())
				.toList();

		assertThat(componentes).doesNotContain("diasPerdidos");
		assertThat(componentes).doesNotContain("incompleta", "motivosInconsistencia", "auditoria");
	}

	@Test
	void el_dto_de_entrada_no_completa_valores_por_su_cuenta() {
		// Un null que llega como null es lo que hace que CAMPO_REQUERIDO se
		// dispare. Rellenarlo con false taparía la falta y el operario guardaría
		// un "no" que nunca respondió, contra FR-004d.
		var entrada = new FichaEntradaDTO(null, 4821, null, null,
				null, null, null, null, null, null, null, null, null, null, null);

		var ficha = entrada.aDominio(null);

		assertThat(ficha.justificado()).isNull();
		assertThat(ficha.estabaEnServicio()).isNull();
		assertThat(ficha.atendidoServicioMedico()).isNull();
		assertThat(ficha.envioMedicoDomicilio()).isNull();
		assertThat(ficha.observaciones()).isNull();
	}

	@Test
	void sin_version_la_ficha_nace_en_cero() {
		// D2. En el alta no se manda version.
		assertThat(new FichaEntradaDTO(null, 4821, null, null, null, null, null,
				null, null, null, null, null, null, null, null)
				.aDominio(null).version()).isZero();
	}

	@Test
	void las_advertencias_siempre_estan_aunque_esten_vacias() {
		// M2. Un arreglo vacío y un campo ausente obligan a dos ramas distintas
		// en el cliente, y la segunda es la que nadie prueba.
		assertThat(new RespuestaDeEscrituraDTO(null, null).advertencias()).isEmpty();
		assertThat(new RespuestaDeEscrituraDTO(null, List.of()).advertencias()).isEmpty();
	}
}
