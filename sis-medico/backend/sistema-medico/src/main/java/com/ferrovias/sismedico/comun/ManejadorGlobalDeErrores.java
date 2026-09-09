package com.ferrovias.sismedico.comun;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ferrovias.sismedico.empleados.PadronNoDisponibleException;
import com.ferrovias.sismedico.fichas.ConflictoDeVersionException;
import com.ferrovias.sismedico.fichas.FichaInvalidaException;

/**
 * Traduce las excepciones de negocio al formato de error del contrato:
 * {@code application/problem+json} (RFC 9457).
 *
 * <p>Está acá y no en los controladores para que la forma del error sea una
 * sola. El frontend mira un único lugar, igual que con las advertencias.
 *
 * <p><b>Ninguno de estos métodos escribe el contenido de la ficha en el log.</b>
 * Un manejador de errores es el lugar donde más natural parece volcar "el objeto
 * que falló", y es justo donde eso filtraría observaciones y diagnósticos a un
 * archivo de texto (FR-035, M3). Se registran códigos y cantidades, nada más.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

	private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

	private static final String BASE_TIPOS = "https://ferrovias/errores/";

	/**
	 * 422 con <b>todas</b> las violaciones, cada una con su campo (M1).
	 *
	 * <p>No es 400: el cuerpo estaba bien formado y se entendió. Lo que falla
	 * son reglas de negocio.
	 */
	@ExceptionHandler(FichaInvalidaException.class)
	ProblemDetail fichaInvalida(FichaInvalidaException e) {
		// Solo los códigos. Los mensajes también son seguros, pero el log no
		// necesita el detalle y cuanto menos viaje, mejor.
		LOG.info("Ficha rechazada por {} violaciones: {}",
				e.violaciones().size(), e.violaciones().stream().map(Violacion::codigo).toList());

		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		problema.setType(URI.create(BASE_TIPOS + "ficha-invalida"));
		problema.setTitle("La ficha tiene datos que hay que corregir");
		problema.setProperty("violaciones", e.violaciones());
		return problema;
	}

	/** 409: la ficha cambió desde que se abrió (FR-037, D2). */
	@ExceptionHandler(ConflictoDeVersionException.class)
	ProblemDetail conflictoDeVersion(ConflictoDeVersionException e) {
		LOG.info("Guardado rechazado por conflicto de versión sobre la ficha {}", e.id());

		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problema.setType(URI.create(BASE_TIPOS + "conflicto-de-version"));
		problema.setTitle("La ficha fue modificada por otro usuario");
		problema.setDetail("Otro operario guardó cambios sobre esta ficha desde que la abriste. "
				+ "Volvé a abrirla para ver el estado actual: tus cambios no se aplicaron y los "
				+ "de él no se pisaron.");
		return problema;
	}

	/** 404: legajo o ficha inexistente. Una ficha eliminada cuenta como inexistente (FR-039c). */
	@ExceptionHandler(RecursoInexistenteException.class)
	ProblemDetail inexistente(RecursoInexistenteException e) {
		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problema.setType(URI.create(BASE_TIPOS + "inexistente"));
		problema.setTitle("No existe lo que se pidió");
		problema.setDetail(e.getMessage());
		return problema;
	}

	/** 503: el padrón externo no responde (supuesto del hueco CHK040). */
	@ExceptionHandler(PadronNoDisponibleException.class)
	ProblemDetail padronCaido(PadronNoDisponibleException e) {
		// Sí se registra la causa: es una falla de infraestructura y no lleva
		// datos de ninguna ficha.
		LOG.error("El padrón de empleados no responde", e);

		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
		problema.setType(URI.create(BASE_TIPOS + "padron-no-disponible"));
		problema.setTitle("El padrón de empleados no está disponible");
		problema.setDetail("No se puede confirmar la identidad del empleado, así que no se "
				+ "pueden cargar ni editar fichas. Las fichas ya cargadas se siguen pudiendo "
				+ "consultar.");
		return problema;
	}
}
