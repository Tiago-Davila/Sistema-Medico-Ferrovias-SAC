package com.ferrovias.sismedico.unitarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.dtos.Violacion.Codigos;
import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;
import com.ferrovias.sismedico.service.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.service.ValidadorFichaMedica;

// Los bordes de fecha al editar una ficha ya guardada. Cubre US3-2 a US3-5.
// Intencional: son las mismas reglas del alta, aplicadas sobre una ficha con id y versión. Ese es
// el punto de FR-017: editar no es un camino con reglas más flojas.
class BordesDeFechaTest {

	private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

	// Reloj fijo: si dependiera del día de la corrida, "evento futuro" sería intermitente (D7).
	private static final LocalDate HOY = LocalDate.of(2026, 6, 15);
	private static final Clock RELOJ = Clock.fixed(HOY.atStartOfDay(ARGENTINA).toInstant(), ARGENTINA);

	private static final LocalDate EVENTO = LocalDate.of(2024, 1, 10);

	private ValidadorFichaMedica validador;

	@BeforeEach
	void prepararValidador() {
		CatalogoRepositorio catalogo = mock(CatalogoRepositorio.class);
		when(catalogo.existeGrupo(anyInt())).thenReturn(true);
		when(catalogo.detallePerteneceAlGrupo(anyInt(), anyInt())).thenReturn(true);
		validador = new ValidadorFichaMedica(RELOJ, catalogo, new CalculadorDiasPerdidos());
	}

	// Una ficha ya guardada, con citación y sin alta: el punto de partida de la edición más común.
	private FichaMedica guardada(LocalDate citacion, LocalDate alta) {
		return new FichaMedica(8842L, 3L, 4821,
				EVENTO, EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				citacion, alta,
				100, 1001, new Observaciones("Reposo"), null);
	}

	private List<String> codigos(FichaMedica ficha) {
		return validador.validar(ficha).stream().map(Violacion::codigo).toList();
	}

	@Test
	void reemplazar_la_citacion_por_el_alta_es_valido() {
		// US3-1 en su parte de reglas: es la edición que el operario hace todos los días.
		assertThat(validador.validar(guardada(null, EVENTO.plusDays(12)))).isEmpty();
	}

	@Test
	void dejar_las_dos_fechas_de_fin_se_rechaza_tambien_al_editar() {
		// US3-2 y FR-017. Es la trampa de la edición: el operario carga el alta y se olvida de
		// borrar la citación, y quedan las dos.
		assertThat(codigos(guardada(EVENTO.plusDays(5), EVENTO.plusDays(12))))
				.contains(Codigos.FECHAS_FIN_EXCLUYENTES);
	}

	@Test
	void quedarse_sin_ninguna_fecha_de_fin_tambien_se_rechaza() {
		// La otra mitad de FR-008: borrar la citación sin cargar el alta deja la ficha abierta,
		// y una ficha abierta bloquea todo el legajo por FR-014c.
		assertThat(codigos(guardada(null, null))).contains(Codigos.FECHAS_FIN_EXCLUYENTES);
	}

	@Test
	void un_alta_el_mismo_dia_del_evento_da_cero_dias_perdidos() {
		// US3-3. Es válido: el empleado se reincorporó el mismo día.
		FichaMedica ficha = guardada(null, EVENTO);

		assertThat(validador.validar(ficha)).isEmpty();
		assertThat(new CalculadorDiasPerdidos().calcular(ficha)).isZero();
	}

	@Test
	void un_alta_anterior_al_evento_se_rechaza() {
		// US3-4. El empleado no puede volver antes de haberse ido.
		assertThat(codigos(guardada(null, EVENTO.minusDays(1))))
				.contains(Codigos.ALTA_ANTERIOR_AL_EVENTO);
	}

	@Test
	void exactamente_999_dias_entre_evento_y_alta_es_valido() {
		// Borde exacto de FR-011: rechaza a partir de 1000, no en 999.
		FichaMedica ficha = guardada(null, EVENTO.plusDays(999));

		assertThat(codigos(ficha)).doesNotContain(Codigos.ALTA_SUPERA_999_DIAS);
		assertThat(new CalculadorDiasPerdidos().calcular(ficha)).isEqualTo(999L);
	}

	@Test
	void mas_de_999_dias_entre_evento_y_alta_se_rechaza() {
		// US3-5.
		assertThat(codigos(guardada(null, EVENTO.plusDays(1000))))
				.contains(Codigos.ALTA_SUPERA_999_DIAS);
	}

	@Test
	void un_alta_anterior_al_evento_no_dispara_ademas_el_tope_de_999() {
		// Los días perdidos dan -1: negativo, no mayor a 999. Dos violaciones sobre la misma
		// fecha mandarían al operario a corregir algo que no está mal.
		assertThat(codigos(guardada(null, EVENTO.minusDays(1))))
				.doesNotContain(Codigos.ALTA_SUPERA_999_DIAS);
	}

	@Test
	void una_citacion_anterior_al_evento_se_rechaza() {
		// FR-012b. La citación es siempre posterior al evento: se cita para después.
		assertThat(codigos(guardada(EVENTO.minusDays(1), null)))
				.contains(Codigos.CITACION_ANTERIOR_AL_EVENTO);
	}

	@Test
	void la_advertencia_de_antiguedad_tambien_aplica_al_editar() {
		// FR-019, que el spec extiende explícitamente a la edición. El evento es de 2024 y el
		// reloj está en 2026: pasa largo los 45 días.
		assertThat(validador.advertencias(guardada(null, EVENTO.plusDays(10))))
				.singleElement()
				.satisfies(a -> assertThat(a.codigo()).isEqualTo("EVENTO_ANTIGUO"));
	}
}
