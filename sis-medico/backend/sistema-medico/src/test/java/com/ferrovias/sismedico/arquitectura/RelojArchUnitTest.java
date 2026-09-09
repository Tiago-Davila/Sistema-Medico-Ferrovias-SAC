package com.ferrovias.sismedico.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * D7: nadie le pregunta la hora al sistema, salvo {@code RelojConfig}.
 *
 * <p>Este test pasa desde el primer día y falla apenas alguien escriba una regla
 * de fecha con {@code LocalDate.now()}. Ese es el punto: no documenta la
 * decisión, la hace cumplir.
 *
 * <p>Lo que impide es concreto. El servidor puede correr en UTC. Una ficha
 * cargada a las 21:00 hora argentina se evaluaría contra el día siguiente, y
 * FR-007 la rechazaría por "fecha futura" siendo legítima. Lo mismo pasa con el
 * período abierto de FR-014c y con la advertencia de antigüedad de FR-019, que
 * se correrían un día. Y sin un reloj inyectable, ningún test de fechas es
 * determinista: el que hoy pasa, el 31 de diciembre falla.
 *
 * <p>Alcanza al código de producción. Los tests sí pueden usar el reloj del
 * sistema cuando la fecha no es lo que están probando.
 */
class RelojArchUnitTest {

	private static final String PAQUETE = "com.ferrovias.sismedico";

	/** El único lugar autorizado a resolver la hora y la zona. */
	private static final String CONFIGURACION_DEL_RELOJ = PAQUETE + ".comun.RelojConfig";

	private final JavaClasses produccion = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(PAQUETE);

	@Test
	void nadie_usa_el_reloj_del_sistema_fuera_de_RelojConfig() {
		ArchRule regla = noClasses()
				.that().doNotHaveFullyQualifiedName(CONFIGURACION_DEL_RELOJ)
				.should().callMethod(LocalDate.class, "now")
				.orShould().callMethod(LocalDateTime.class, "now")
				.orShould().callMethod(LocalTime.class, "now")
				.orShould().callMethod(Instant.class, "now")
				.orShould().callMethod(ZonedDateTime.class, "now")
				.orShould().callMethod(OffsetDateTime.class, "now")
				.orShould().callMethod(Year.class, "now")
				.orShould().callMethod(YearMonth.class, "now")
				.orShould().callMethod(System.class, "currentTimeMillis")
				.orShould().callConstructor(java.util.Date.class)
				.because("""
						toda regla que depende de "hoy" se evalúa contra el bean Clock \
						(D7, FR-018b). Con el reloj del sistema, una carga a las 21:00 \
						hora argentina cae en el día siguiente si el servidor está en UTC \
						y FR-007 rechaza una ficha legítima. Inyectá java.time.Clock y usá \
						LocalDate.now(reloj)""");

		regla.check(produccion);
	}

	@Test
	void nadie_resuelve_la_zona_horaria_por_su_cuenta() {
		// ZoneId.systemDefault() es la otra puerta al mismo problema: devuelve
		// la zona del servidor, no la de app.zona-horaria.
		ArchRule regla = noClasses()
				.that().doNotHaveFullyQualifiedName(CONFIGURACION_DEL_RELOJ)
				.should().callMethod(java.time.ZoneId.class, "systemDefault")
				.orShould().callMethod(java.util.TimeZone.class, "getDefault")
				.because("la zona horaria sale de app.zona-horaria, no del servidor (D7)");

		regla.check(produccion);
	}

	@Test
	void el_bean_Clock_existe_y_es_inyectable() {
		// Si alguien borra RelojConfig, las dos reglas de arriba pasarían por
		// vacuidad: no habría nadie usando el reloj del sistema porque no
		// habría reloj. Este test cierra esa salida.
		ArchRule regla = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
				.that().haveFullyQualifiedName(CONFIGURACION_DEL_RELOJ)
				.should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>(
						"declarar un bean de tipo java.time.Clock") {
					@Override
					public void check(com.tngtech.archunit.core.domain.JavaClass clase,
							com.tngtech.archunit.lang.ConditionEvents eventos) {
						boolean declara = clase.getMethods().stream()
								.anyMatch(m -> m.getRawReturnType().isEquivalentTo(Clock.class));
						if (!declara) {
							eventos.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(clase,
									clase.getName() + " dejó de declarar el bean Clock"));
						}
					}
				});

		regla.check(produccion);
	}
}
