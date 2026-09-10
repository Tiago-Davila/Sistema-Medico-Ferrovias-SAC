package com.ferrovias.sismedico.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * El modelo de dominio y de persistencia no lleva anotaciones de validación.
 *
 * <p>Es la tensión central de esta feature: hay que <b>escribir con reglas
 * estrictas sobre datos históricos que no las cumplen</b>. Un {@code @NotNull}
 * sobre el grupo de enfermedad parece obvio —FR-004b lo declara obligatorio— y
 * rompe FR-028 y FR-030: las fichas anteriores a 2016 no tienen grupo, y tienen
 * que poder leerse, mostrarse y navegarse sin que nada falle.
 *
 * <p>La obligatoriedad vive en {@code ValidadorFichaMedica} y se aplica
 * únicamente al guardar. La regla no es "no anotar por prolijidad": es que
 * validar al construir el objeto convierte cada lectura de una ficha histórica
 * en una excepción.
 *
 * <p>Lo mismo vale del lado del esquema, y por eso V1 no tiene ningún
 * {@code NOT NULL} ni {@code CHECK} que reproduzca una regla de negocio, ni FK
 * contra el catálogo de enfermedades.
 *
 * <p>Los DTO de <b>entrada</b> de la API son otra cosa: ahí sí se recibe algo
 * que se va a escribir. Aun así la validación de negocio no vive en
 * anotaciones, porque tiene que acumular todas las violaciones y devolverlas
 * juntas (M1), y Bean Validation por campo no da eso con la forma que el
 * contrato exige.
 */
class PersistenciaArchUnitTest {

	private static final String PAQUETE = "com.ferrovias.sismedico";

	/**
	 * Las anotaciones de Bean Validation que tendría sentido poner y que no van.
	 * Se listan por nombre y no por clase para que la regla siga valiendo aunque
	 * jakarta.validation no esté en el classpath.
	 */
	private static final DescribedPredicate<JavaClass> ANOTACION_DE_VALIDACION =
			new DescribedPredicate<>("una anotación de Bean Validation") {
				@Override
				public boolean test(JavaClass anotacion) {
					String nombre = anotacion.getName();
					return nombre.startsWith("jakarta.validation.")
							|| nombre.startsWith("javax.validation.")
							|| nombre.startsWith("org.hibernate.validator.");
				}
			};

	private final JavaClasses dominio = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(PAQUETE + ".models", PAQUETE + ".controllers", PAQUETE + ".service",
					PAQUETE + ".dtos", PAQUETE + ".repositories");

	@Test
	void el_dominio_no_lleva_anotaciones_de_validacion() {
		// Va con classes().should(...) y no con noClasses(): bajo noClasses()
		// ArchUnit invierte el resultado de la condición, y una condición
		// escrita a mano que reporta violaciones al encontrar anotaciones
		// terminaría exigiendo que todas las clases las lleven.
		ArchRule regla = ArchRuleDefinition.classes()
				.should(new ArchCondition<JavaClass>("no llevar anotaciones de Bean Validation") {
					@Override
					public void check(JavaClass clase, ConditionEvents eventos) {
						reportarAnotaciones(clase, clase, eventos, "la clase");
						for (JavaField campo : clase.getAllFields()) {
							reportarAnotaciones(campo, clase, eventos, "el campo " + campo.getName());
						}
						for (JavaMember miembro : clase.getAllMethods()) {
							reportarAnotaciones(miembro, clase, eventos,
									"el método " + miembro.getName());
						}
						for (JavaMember miembro : clase.getAllConstructors()) {
							reportarAnotaciones(miembro, clase, eventos, "el constructor");
						}
					}
				})
				.because("""
						la obligatoriedad de FR-004b vive en ValidadorFichaMedica y se \
						aplica solo al guardar. Anotada sobre el modelo, se aplicaría \
						también al leer, y las fichas históricas anteriores a 2016 no \
						tienen grupo ni detalle: cada lectura sería una excepción, contra \
						FR-028 y FR-030""")
				.allowEmptyShould(true);

		regla.check(dominio);
	}

	@Test
	void el_dominio_no_depende_de_jakarta_validation() {
		// Cierra la puerta de atrás: un validador propio invocado desde el
		// modelo tendría el mismo efecto que la anotación.
		ArchRule regla = ArchRuleDefinition.noClasses()
				.should().dependOnClassesThat().resideInAnyPackage(
						"jakarta.validation..", "javax.validation..")
				.because("validar al construir el objeto rompe la lectura tolerante (FR-030)");

		regla.check(dominio);
	}

	@Test
	void el_dominio_no_depende_de_jpa_ni_de_hibernate() {
		// La constitución fija JdbcTemplate. JPA además arrastra su propio ciclo
		// de vida, del que cuelgan validaciones automáticas al persistir.
		ArchRule regla = ArchRuleDefinition.noClasses()
				.should().dependOnClassesThat().resideInAnyPackage(
						"jakarta.persistence..", "org.hibernate..")
				.because("el acceso a datos es JdbcTemplate, sin capa de mapeo");

		regla.check(dominio);
	}

	private static void reportarAnotaciones(Object anotado, JavaClass clase, ConditionEvents eventos,
			String donde) {

		var anotaciones = switch (anotado) {
			case JavaClass c -> c.getAnnotations();
			case JavaMember m -> m.getAnnotations();
			default -> java.util.Set.<com.tngtech.archunit.core.domain.JavaAnnotation<?>>of();
		};

		anotaciones.stream()
				.filter(a -> ANOTACION_DE_VALIDACION.test(a.getRawType()))
				.forEach(a -> eventos.add(SimpleConditionEvent.violated(clase,
						clase.getName() + ": " + donde + " lleva " + a.getRawType().getSimpleName()
								+ ". La obligatoriedad va en ValidadorFichaMedica, no en el modelo.")));
	}
}
