package com.ferrovias.sismedico.comun;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.exceptions.ConflictoDeVersionException;
import com.ferrovias.sismedico.exceptions.FichaInvalidaException;
import com.ferrovias.sismedico.exceptions.PadronNoDisponibleException;
import com.ferrovias.sismedico.exceptions.RecursoInexistenteException;

// Traduce las excepciones de negocio al formato de error del contrato: application/problem+json.
// Intencional: ningún método escribe el contenido de la ficha en el log, solo códigos y cantidades.
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

	private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

	private static final String BASE_TIPOS = "https://ferrovias/errores/";

	// 422 con todas las violaciones de la ficha, cada una con su campo.
	@ExceptionHandler(FichaInvalidaException.class)
	ProblemDetail fichaInvalida(FichaInvalidaException e) {
		LOG.info("Ficha rechazada por {} violaciones: {}",
				e.violaciones().size(), e.violaciones().stream().map(Violacion::codigo).toList());

		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		problema.setType(URI.create(BASE_TIPOS + "ficha-invalida"));
		problema.setTitle("La ficha tiene datos que hay que corregir");
		problema.setProperty("violaciones", e.violaciones());
		return problema;
	}

	// 409: la ficha cambió desde que se abrió.
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

	// 404: legajo o ficha inexistente; una ficha eliminada cuenta como inexistente.
	@ExceptionHandler(RecursoInexistenteException.class)
	ProblemDetail inexistente(RecursoInexistenteException e) {
		ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problema.setType(URI.create(BASE_TIPOS + "inexistente"));
		problema.setTitle("No existe lo que se pidió");
		problema.setDetail(e.getMessage());
		return problema;
	}

	// 503: el padrón externo no responde.
	@ExceptionHandler(PadronNoDisponibleException.class)
	ProblemDetail padronCaido(PadronNoDisponibleException e) {
		// Sí se registra la causa: es una falla de infraestructura, no datos de una ficha.
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
