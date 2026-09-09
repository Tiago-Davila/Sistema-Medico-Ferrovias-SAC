package com.ferrovias.sismedico.fichas;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

/**
 * Días perdidos: la diferencia entre la fecha de alta y la del evento.
 *
 * <p><b>No topea nada</b> (FR-020, FR-020c). Devuelve el valor real aunque sea
 * negativo o mayor a 999. El límite de 999 es una restricción de escritura, ya
 * cubierta por FR-011, y aplicarlo también al leer estaría mal: una ficha
 * histórica con el alta anterior al evento tiene que mostrar la diferencia
 * negativa y quedar señalada como inconsistente, no aparecer en cero ni vacía.
 * Mostrar cero escondería el error del dato en lugar de exponerlo.
 *
 * <p>Sin fecha de alta el valor es {@code null}, no cero (FR-021). Son cosas
 * distintas: cero días perdidos es un empleado que se reincorporó el mismo día;
 * vacío es un empleado que todavía no se reincorporó.
 *
 * <p>Esta clase reproduce en Java lo que el esquema calcula en SQL
 * ({@code DATEDIFF(day, fecha_evento, fecha_alta)}, D4). Las dos existen a
 * propósito: la columna calculada es lo que hace imposible que el valor
 * almacenado se desincronice de las fechas, y esto es lo que permite anticipar
 * el valor sin ir a la base, sobre todo al validar FR-011 antes de guardar.
 */
@Component
public class CalculadorDiasPerdidos {

	/** Tope de escritura de FR-011. Exactamente 999 es válido; 1000 no. */
	public static final int MAXIMO_DIAS_AL_GUARDAR = 999;

	/**
	 * @return la diferencia real en días, o {@code null} si no hay fecha de alta
	 */
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
