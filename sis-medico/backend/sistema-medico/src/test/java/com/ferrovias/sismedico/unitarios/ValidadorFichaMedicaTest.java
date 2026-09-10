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
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;
import com.ferrovias.sismedico.service.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;
import com.ferrovias.sismedico.service.ValidadorFichaMedica;

/**
 * Las reglas bloqueantes, una por una, sin base.
 *
 * <p>Cubre US1-4 a US1-9 y los bordes de fecha. Es la contraparte de
 * {@code ValidacionAcumulativaTest}: aquel verifica que lleguen todas juntas,
 * este verifica que cada una diga lo que tiene que decir.
 */
class ValidadorFichaMedicaTest {

	private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

	/**
	 * Reloj fijo en el 15 de junio de 2026.
	 *
	 * <p>Sin esto, "evento futuro" y la advertencia de antigüedad dependerían del
	 * día en que se corra el test y las dos pruebas serían intermitentes (D7).
	 */
	private static final LocalDate HOY = LocalDate.of(2026, 6, 15);
	private static final Clock RELOJ = Clock.fixed(
			HOY.atStartOfDay(ARGENTINA).toInstant(), ARGENTINA);

	private CatalogoRepositorio catalogo;
	private ValidadorFichaMedica validador;

	@BeforeEach
	void prepararValidador() {
		catalogo = mock(CatalogoRepositorio.class);
		when(catalogo.existeGrupo(anyInt())).thenReturn(true);
		when(catalogo.detallePerteneceAlGrupo(anyInt(), anyInt())).thenReturn(true);
		validador = new ValidadorFichaMedica(RELOJ, catalogo, new CalculadorDiasPerdidos());
	}

	/** Ficha válida de base. Cada test le cambia solo lo que quiere probar. */
	private FichaMedica valida() {
		return new FichaMedica(null, 0L, 4821,
				HOY.minusDays(5), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				HOY.plusDays(3), null,
				100, 1001, new Observaciones("Reposo"), null);
	}

	private List<String> codigos(FichaMedica ficha) {
		return validador.validar(ficha).stream().map(Violacion::codigo).toList();
	}

	@Test
	void la_ficha_de_base_no_tiene_violaciones() {
		// Si esto falla, todo el resto del archivo miente: los tests estarían
		// pasando por una violación distinta de la que dicen probar.
		assertThat(validador.validar(valida())).isEmpty();
	}

	// ------------------------------------------------------- US1-4: FR-007

	@Test
	void una_fecha_de_evento_posterior_a_hoy_se_rechaza() {
		var futura = conFechaEvento(valida(), HOY.plusDays(1));

		assertThat(codigos(futura)).contains(Codigos.EVENTO_FUTURO);
	}

	@Test
	void una_fecha_de_evento_de_hoy_es_valida() {
		// Borde exacto de FR-007: rechaza "posterior a hoy", no "hoy".
		assertThat(codigos(conFechaEvento(valida(), HOY)))
				.doesNotContain(Codigos.EVENTO_FUTURO);
	}

	// ------------------------------------------------- US1-5, US1-6: FR-008

	@Test
	void sin_citacion_y_sin_alta_se_rechaza() {
		var ficha = conFechas(valida(), null, null);

		assertThat(codigos(ficha)).contains(Codigos.FECHAS_FIN_EXCLUYENTES);
		assertThat(validador.validar(ficha))
				.filteredOn(v -> v.codigo().equals(Codigos.FECHAS_FIN_EXCLUYENTES))
				.singleElement()
				.satisfies(v -> assertThat(v.mensaje()).contains("tiene que haber una"));
	}

	@Test
	void con_citacion_y_alta_a_la_vez_se_rechaza() {
		var ficha = conFechas(valida(), HOY.plusDays(3), HOY.minusDays(1));

		assertThat(codigos(ficha)).contains(Codigos.FECHAS_FIN_EXCLUYENTES);
		assertThat(validador.validar(ficha))
				.filteredOn(v -> v.codigo().equals(Codigos.FECHAS_FIN_EXCLUYENTES))
				.singleElement()
				.satisfies(v -> assertThat(v.mensaje()).contains("no las dos"));
	}

	// ------------------------------------------------------- US1-7: FR-009

	@Test
	void sin_grupo_y_sin_detalle_se_indica_cada_uno_por_separado() {
		// US1-7 pide que el mensaje indique cuál de los dos falta. Un solo
		// código para ambos no lo cumpliría.
		var ficha = conClasificacion(valida(), null, null);

		assertThat(codigos(ficha))
				.contains(Codigos.GRUPO_REQUERIDO, Codigos.DETALLE_REQUERIDO);
	}

	@Test
	void con_grupo_pero_sin_detalle_solo_falta_el_detalle() {
		assertThat(codigos(conClasificacion(valida(), 100, null)))
				.contains(Codigos.DETALLE_REQUERIDO)
				.doesNotContain(Codigos.GRUPO_REQUERIDO);
	}

	// ------------------------------------------------------- US1-8: FR-010

	@Test
	void un_detalle_que_no_pertenece_al_grupo_se_rechaza() {
		when(catalogo.detallePerteneceAlGrupo(2001, 100)).thenReturn(false);

		assertThat(codigos(conClasificacion(valida(), 100, 2001)))
				.contains(Codigos.DETALLE_FUERA_DE_GRUPO);
	}

	@Test
	void sin_grupo_no_se_reclama_que_el_detalle_no_pertenezca() {
		// Con el grupo vacío, "el detalle no pertenece al grupo" no le dice nada
		// al operario: primero tiene que elegir un grupo.
		assertThat(codigos(conClasificacion(valida(), null, 2001)))
				.contains(Codigos.GRUPO_REQUERIDO)
				.doesNotContain(Codigos.DETALLE_FUERA_DE_GRUPO);
	}

	@Test
	void un_grupo_huerfano_no_se_puede_guardar() {
		// FR-031: la ficha histórica se lee igual, pero al guardarla hay que
		// completarle una clasificación válida.
		when(catalogo.existeGrupo(88888)).thenReturn(false);
		when(catalogo.detallePerteneceAlGrupo(888888, 88888)).thenReturn(false);

		assertThat(codigos(conClasificacion(valida(), 88888, 888888)))
				.contains(Codigos.DETALLE_FUERA_DE_GRUPO);
	}

	// ------------------------------------------- FR-011, FR-012 y FR-012b

	@Test
	void una_fecha_de_alta_anterior_al_evento_se_rechaza() {
		var ficha = conFechas(valida(), null, HOY.minusDays(10));

		assertThat(codigos(conFechaEvento(ficha, HOY.minusDays(5))))
				.contains(Codigos.ALTA_ANTERIOR_AL_EVENTO);
	}

	@Test
	void una_fecha_de_alta_igual_al_evento_es_valida() {
		// Borde: da cero días perdidos y se guarda.
		var ficha = conFechas(conFechaEvento(valida(), HOY.minusDays(5)), null, HOY.minusDays(5));

		assertThat(codigos(ficha)).isEmpty();
	}

	@Test
	void exactamente_999_dias_es_valido_y_1000_no() {
		// FR-011, el borde que más fácil se implementa con el signo cambiado.
		var evento = LocalDate.of(2024, 1, 1);

		var con999 = conFechas(conFechaEvento(valida(), evento), null, evento.plusDays(999));
		var con1000 = conFechas(conFechaEvento(valida(), evento), null, evento.plusDays(1000));

		assertThat(codigos(con999)).doesNotContain(Codigos.ALTA_SUPERA_999_DIAS);
		assertThat(codigos(con1000)).contains(Codigos.ALTA_SUPERA_999_DIAS);
	}

	@Test
	void una_fecha_de_citacion_anterior_al_evento_se_rechaza() {
		var ficha = conFechas(conFechaEvento(valida(), HOY.minusDays(5)), HOY.minusDays(10), null);

		assertThat(codigos(ficha)).contains(Codigos.CITACION_ANTERIOR_AL_EVENTO);
	}

	@Test
	void una_citacion_muy_lejana_en_el_futuro_es_valida() {
		// FR-012c: sin tope superior. El tope de 999 rige solo para el alta.
		var ficha = conFechas(valida(), HOY.plusYears(3), null);

		assertThat(codigos(ficha)).isEmpty();
	}

	// ------------------------------------------------------- US1-9: FR-016

	@Test
	void un_accidente_sin_in_itinere_definido_se_rechaza() {
		var accidente = conEstado(valida(), EstadoPaciente.ACCIDENTADO, null);

		assertThat(codigos(accidente)).contains(Codigos.IN_ITINERE_REQUERIDO);
	}

	@Test
	void un_accidente_con_in_itinere_en_no_es_valido() {
		// "No" es una respuesta. Lo que FR-016 exige es que tenga valor
		// definido, no que sea sí.
		assertThat(codigos(conEstado(valida(), EstadoPaciente.ACCIDENTADO, false)))
				.doesNotContain(Codigos.IN_ITINERE_REQUERIDO);
	}

	@Test
	void una_enfermedad_sin_in_itinere_es_valida() {
		// FR-025: con enfermedad, in itinere no aplica y queda vacío.
		assertThat(codigos(conEstado(valida(), EstadoPaciente.ENFERMEDAD, null)))
				.doesNotContain(Codigos.IN_ITINERE_REQUERIDO);
	}

	// ------------------------------------------------ FR-004b y FR-004c

	@Test
	void los_cuatro_campos_de_si_o_no_son_obligatorios_al_guardar() {
		var ficha = valida();
		var sinResponder = new FichaMedica(ficha.id(), ficha.version(), ficha.legajo(),
				ficha.fechaEvento(), ficha.estadoPaciente(), ficha.inItinere(),
				null, ficha.horaAccidente(), null, null, null,
				ficha.fechaCitacion(), ficha.fechaAlta(), ficha.grupoEnfermedad(),
				ficha.detalleEnfermedad(), ficha.observaciones(), ficha.auditoria());

		assertThat(validador.validar(sinResponder))
				.filteredOn(v -> v.codigo().equals(Codigos.CAMPO_REQUERIDO))
				.extracting(Violacion::campo)
				.containsExactlyInAnyOrder("estabaEnServicio", "atendidoServicioMedico",
						"envioMedicoDomicilio", "justificado");
	}

	@Test
	void responder_que_no_no_es_lo_mismo_que_no_responder() {
		// FR-004d. Si el validador tratara false como ausente, una ficha
		// perfectamente respondida sería rechazada.
		assertThat(codigos(valida())).doesNotContain(Codigos.CAMPO_REQUERIDO);
	}

	// ------------------------------------------------------------ FR-006b

	@Test
	void mas_de_500_caracteres_de_observaciones_se_rechaza() {
		var ficha = conObservaciones(valida(), "x".repeat(501));

		assertThat(codigos(ficha)).contains(Codigos.OBSERVACIONES_MUY_LARGAS);
	}

	@Test
	void exactamente_500_caracteres_es_valido() {
		assertThat(codigos(conObservaciones(valida(), "x".repeat(500))))
				.doesNotContain(Codigos.OBSERVACIONES_MUY_LARGAS);
	}

	@Test
	void el_mensaje_de_observaciones_dice_cuantas_sobran_y_no_que_dicen() {
		// FR-035: el mensaje va a la pantalla y al log.
		var ficha = conObservaciones(valida(), "SECRETO" + "x".repeat(500));

		assertThat(validador.validar(ficha))
				.filteredOn(v -> v.codigo().equals(Codigos.OBSERVACIONES_MUY_LARGAS))
				.singleElement()
				.satisfies(v -> {
					assertThat(v.mensaje()).contains("507").contains("7");
					assertThat(v.mensaje()).doesNotContain("SECRETO");
				});
	}

	// ------------------------------------------------- FR-019: advertencia

	@Test
	void un_evento_de_mas_de_45_dias_advierte_pero_no_bloquea() {
		// US1-12. En el sistema de referencia el bloqueo por antigüedad está
		// desactivado a propósito y FR-019 exige que acá tampoco bloquee nunca.
		var vieja = conFechaEvento(valida(), HOY.minusDays(62));

		assertThat(validador.validar(vieja)).isEmpty();
		assertThat(validador.advertencias(vieja))
				.singleElement()
				.satisfies(a -> {
					assertThat(a.codigo()).isEqualTo("EVENTO_ANTIGUO");
					assertThat(a.mensaje()).contains("62");
				});
	}

	@Test
	void exactamente_45_dias_todavia_no_advierte() {
		assertThat(validador.advertencias(conFechaEvento(valida(), HOY.minusDays(45)))).isEmpty();
		assertThat(validador.advertencias(conFechaEvento(valida(), HOY.minusDays(46))))
				.hasSize(1);
	}

	// ------------------------------------------------------------- helpers

	private static FichaMedica conFechaEvento(FichaMedica f, LocalDate fechaEvento) {
		return new FichaMedica(f.id(), f.version(), f.legajo(), fechaEvento, f.estadoPaciente(),
				f.inItinere(), f.estabaEnServicio(), f.horaAccidente(), f.atendidoServicioMedico(),
				f.envioMedicoDomicilio(), f.justificado(), f.fechaCitacion(), f.fechaAlta(),
				f.grupoEnfermedad(), f.detalleEnfermedad(), f.observaciones(), f.auditoria());
	}

	private static FichaMedica conFechas(FichaMedica f, LocalDate citacion, LocalDate alta) {
		return new FichaMedica(f.id(), f.version(), f.legajo(), f.fechaEvento(), f.estadoPaciente(),
				f.inItinere(), f.estabaEnServicio(), f.horaAccidente(), f.atendidoServicioMedico(),
				f.envioMedicoDomicilio(), f.justificado(), citacion, alta,
				f.grupoEnfermedad(), f.detalleEnfermedad(), f.observaciones(), f.auditoria());
	}

	private static FichaMedica conClasificacion(FichaMedica f, Integer grupo, Integer detalle) {
		return new FichaMedica(f.id(), f.version(), f.legajo(), f.fechaEvento(), f.estadoPaciente(),
				f.inItinere(), f.estabaEnServicio(), f.horaAccidente(), f.atendidoServicioMedico(),
				f.envioMedicoDomicilio(), f.justificado(), f.fechaCitacion(), f.fechaAlta(),
				grupo, detalle, f.observaciones(), f.auditoria());
	}

	private static FichaMedica conEstado(FichaMedica f, EstadoPaciente estado, Boolean inItinere) {
		return new FichaMedica(f.id(), f.version(), f.legajo(), f.fechaEvento(), estado,
				inItinere, f.estabaEnServicio(), f.horaAccidente(), f.atendidoServicioMedico(),
				f.envioMedicoDomicilio(), f.justificado(), f.fechaCitacion(), f.fechaAlta(),
				f.grupoEnfermedad(), f.detalleEnfermedad(), f.observaciones(), f.auditoria());
	}

	private static FichaMedica conObservaciones(FichaMedica f, String texto) {
		return new FichaMedica(f.id(), f.version(), f.legajo(), f.fechaEvento(), f.estadoPaciente(),
				f.inItinere(), f.estabaEnServicio(), f.horaAccidente(), f.atendidoServicioMedico(),
				f.envioMedicoDomicilio(), f.justificado(), f.fechaCitacion(), f.fechaAlta(),
				f.grupoEnfermedad(), f.detalleEnfermedad(), Observaciones.de(texto), f.auditoria());
	}
}
