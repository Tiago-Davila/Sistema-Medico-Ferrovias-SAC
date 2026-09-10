package com.ferrovias.sismedico.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.ferrovias.sismedico.models.FichaMedica;

// Calcula los días perdidos entre el evento y el alta; reproduce en Java lo que el esquema calcula en SQL.
// Intencional: no topea el resultado, ni siquiera si da negativo o pasa los 999. El límite de
// escritura ya lo aplica ValidadorFichaMedica; al leer, una ficha histórica inconsistente tiene
// que mostrar el valor real para quedar señalada, no aparecer en cero.
@Component
public class CalculadorDiasPerdidos {

	public static final int MAXIMO_DIAS_AL_GUARDAR = 999;

	// Diferencia real en días, o null si no hay fecha de alta.
	public Long calcular(LocalDate fechaEvento, LocalDate fechaAlta) {
		if (fechaEvento == null || fechaAlta == null) {
			return null;
		}
		return ChronoUnit.DAYS.between(fechaEvento, fechaAlta);
	}

	public Long calcular(FichaMedica ficha) {
		return calcular(ficha.fechaEvento(), ficha.fechaAlta());
	}
}
