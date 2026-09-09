package com.ferrovias.sismedico.comun;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único lugar del sistema donde se decide qué hora es (D7, FR-018b).
 *
 * <p>Toda regla que depende de "hoy" —FR-007 evento futuro, FR-014c período
 * abierto de las fichas históricas, FR-019 advertencia de antigüedad— se evalúa
 * contra este {@link Clock}, nunca contra {@code LocalDate.now()} ni contra
 * {@code GETDATE()}.
 *
 * <p>La razón no es purismo: el servidor puede correr en UTC, y con el reloj del
 * sistema una carga hecha a las 21:00 hora argentina caería en el día siguiente
 * y FR-007 rechazaría una ficha legítima por "fecha futura". Además, fijar el
 * reloj es lo que vuelve determinista todo test de fechas.
 *
 * <p>{@code RelojArchUnitTest} prohíbe el reloj del sistema en el resto del
 * código de producción, con esta clase como única excepción.
 */
@Configuration
public class RelojConfig {

	/**
	 * @param zonaHoraria valor de {@code app.zona-horaria}. Sin valor por
	 *     defecto a propósito: si falta, la aplicación no arranca en lugar de
	 *     adoptar en silencio la zona de la máquina.
	 */
	@Bean
	Clock reloj(@Value("${app.zona-horaria}") String zonaHoraria) {
		return Clock.system(ZoneId.of(zonaHoraria));
	}
}
