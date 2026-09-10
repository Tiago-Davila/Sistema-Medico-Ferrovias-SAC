package com.ferrovias.sismedico.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ferrovias.sismedico.dtos.Advertencia;
import com.ferrovias.sismedico.dtos.Violacion;
import com.ferrovias.sismedico.dtos.Violacion.Codigos;
import com.ferrovias.sismedico.exceptions.ConflictoDeVersionException;
import com.ferrovias.sismedico.exceptions.FichaInvalidaException;
import com.ferrovias.sismedico.exceptions.RecursoInexistenteException;
import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.repositories.FichaMedicaRepositorio;
import com.ferrovias.sismedico.repositories.PadronRepositorio;

// Donde vive el negocio de la ficha médica; el controlador solo traduce HTTP.
@Service
public class FichaMedicaServicio {

	private final ValidadorFichaMedica validador;
	private final DetectorSolapamiento detector;
	private final FichaMedicaRepositorio repositorio;
	private final PadronRepositorio padron;
	private final AuditoriaServicio auditoria;

	public FichaMedicaServicio(ValidadorFichaMedica validador, DetectorSolapamiento detector,
			FichaMedicaRepositorio repositorio, PadronRepositorio padron,
			AuditoriaServicio auditoria) {

		this.validador = validador;
		this.detector = detector;
		this.repositorio = repositorio;
		this.padron = padron;
		this.auditoria = auditoria;
	}

	// Valida la ficha, la guarda y registra el alta en la auditoría, todo en la misma transacción.
	@Transactional
	public Resultado darDeAlta(FichaMedica ficha) {
		List<Violacion> violaciones = validarTodo(ficha, null);
		if (!violaciones.isEmpty()) {
			throw new FichaInvalidaException(violaciones);
		}

		FichaMedica normalizada = aplicarDependenciasEntreCampos(ficha);

		long id = repositorio.insertar(normalizada, auditoria.usuarioActual(), auditoria.ahora());
		auditoria.registrarAlta(id);

		return new Resultado(
				repositorio.buscar(id).orElseThrow(),
				validador.advertencias(normalizada));
	}

	// Vuelve a validar la ficha entera, la guarda y registra la modificación (FR-017, FR-033).
	// Intencional: se valida todo de nuevo, no solo lo que cambió. Una ficha histórica que se
	// abre y se guarda tiene que cumplir las reglas de hoy, aunque no cumpliera las de su época.
	@Transactional
	public Resultado modificar(long id, Long version, FichaMedica entrada) {
		if (version == null) {
			throw new FichaInvalidaException(List.of(new Violacion("version",
					Codigos.CAMPO_REQUERIDO,
					"Falta la versión de la ficha. Volvé a abrirla antes de guardar.")));
		}

		FichaMedica guardada = buscar(id);

		// El guardarraíl de verdad es el UPDATE de más abajo, que compara la versión dentro de la
		// misma sentencia. Esto es para no devolver un 422 con violaciones calculadas sobre una
		// ficha que ya cambió: el operario corregiría campos mirando datos viejos.
		if (guardada.version() != version) {
			throw new ConflictoDeVersionException(id);
		}

		// El legajo es editable (FR-003d). Todo lo que sigue mira el legajo de la entrada, que es
		// el de destino, así que unicidad y solapamiento se evalúan contra el empleado al que la
		// ficha se está moviendo y no contra el que la tenía (FR-003e).
		List<Violacion> violaciones = validarTodo(entrada, id);
		if (!violaciones.isEmpty()) {
			throw new FichaInvalidaException(violaciones);
		}

		FichaMedica normalizada = aplicarDependenciasEntreCampos(entrada);

		if (!repositorio.actualizar(normalizada, version + 1,
				auditoria.usuarioActual(), auditoria.ahora())) {
			throw new ConflictoDeVersionException(id);
		}
		auditoria.registrarModificacion(id);

		return new Resultado(buscar(id), validador.advertencias(normalizada));
	}

	// Borrado lógico de una ficha, con su asiento de auditoría (FR-038, FR-039b).
	// Intencional: pide la versión igual que la modificación. Eliminar una ficha que otro operario
	// acaba de editar es tan destructivo como pisarle el cambio, y en silencio.
	@Transactional
	public void darDeBaja(long id, Long version) {
		if (version == null) {
			throw new FichaInvalidaException(List.of(new Violacion("version",
					Codigos.CAMPO_REQUERIDO,
					"Falta la versión de la ficha. Volvé a abrirla antes de eliminarla.")));
		}

		FichaMedica guardada = buscar(id);
		if (guardada.version() != version) {
			throw new ConflictoDeVersionException(id);
		}

		if (!repositorio.eliminar(id, version, auditoria.usuarioActual(), auditoria.ahora())) {
			throw new ConflictoDeVersionException(id);
		}
		auditoria.registrarBaja(id);
	}

	// Una ficha viva por id; una eliminada cuenta como inexistente.
	// Intencional: leer no valida nada (FR-030). La ficha sale tal como está guardada, aunque
	// viole reglas que hoy son obligatorias.
	@Transactional(readOnly = true)
	public FichaMedica buscar(long id) {
		return repositorio.buscar(id).orElseThrow(
				() -> new RecursoInexistenteException("No existe la ficha " + id + "."));
	}

	// Fichas vivas de un empleado, de la más reciente a la más vieja.
	// Intencional: no consulta el padrón. Con el padrón caído no se pueden cargar ni editar
	// fichas, pero las ya cargadas se tienen que poder seguir consultando.
	@Transactional(readOnly = true)
	public List<FichaMedica> listarDe(int legajo) {
		return repositorio.listarPorLegajo(legajo);
	}

	// Corre las tres etapas de validación (reglas propias, padrón, unicidad/solapamiento) en una sola lista.
	private List<Violacion> validarTodo(FichaMedica ficha, Long idActual) {
		List<Violacion> violaciones = new ArrayList<>(validador.validar(ficha));

		if (!padron.existe(ficha.legajo())) {
			violaciones.add(new Violacion("legajo", Codigos.LEGAJO_INEXISTENTE,
					"El legajo " + ficha.legajo() + " no existe en el padrón."));
		}

		if (ficha.fechaEvento() != null) {
			if (repositorio.existeOtraConMismaFecha(ficha.legajo(), ficha.fechaEvento(), idActual)) {
				violaciones.add(new Violacion("fechaEvento", Codigos.FICHA_DUPLICADA,
						"Este empleado ya tiene una ficha con esa fecha de evento."));
			}

			detector.detectar(ficha.legajo(), idActual, ficha.fechaEvento(), ficha.finDelPeriodo())
					.ifPresent(violaciones::add);
		}

		return List.copyOf(violaciones);
	}

	// Limpia los campos que dependen del estado del paciente, sin confiar en lo que mandó el cliente.
	private FichaMedica aplicarDependenciasEntreCampos(FichaMedica ficha) {
		Boolean inItinere = ficha.inItinere();
		Integer horaAccidente = ficha.horaAccidente();
		Boolean envioMedicoDomicilio = ficha.envioMedicoDomicilio();
		Boolean estabaEnServicio = ficha.estabaEnServicio();

		if (ficha.estadoPaciente() == EstadoPaciente.ENFERMEDAD) {
			inItinere = null;
			horaAccidente = null;

		} else if (ficha.estadoPaciente() == EstadoPaciente.ACCIDENTADO) {
			envioMedicoDomicilio = false;
		}

		// Intencional: in itinere marcado implica que estaba en servicio, aunque parezca
		// contraintuitivo. Así lo pide el sistema de referencia; no "corregir".
		if (Boolean.TRUE.equals(inItinere)) {
			estabaEnServicio = true;
		}

		return new FichaMedica(
				ficha.id(), ficha.version(), ficha.legajo(), ficha.fechaEvento(),
				ficha.estadoPaciente(), inItinere, estabaEnServicio, horaAccidente,
				ficha.atendidoServicioMedico(), envioMedicoDomicilio, ficha.justificado(),
				ficha.fechaCitacion(), ficha.fechaAlta(),
				ficha.grupoEnfermedad(), ficha.detalleEnfermedad(),
				ficha.observaciones(), ficha.auditoria());
	}

	// Lo que devuelve una escritura exitosa: la ficha guardada y sus advertencias, si hay.
	public record Resultado(FichaMedica ficha, List<Advertencia> advertencias) {
	}
}
