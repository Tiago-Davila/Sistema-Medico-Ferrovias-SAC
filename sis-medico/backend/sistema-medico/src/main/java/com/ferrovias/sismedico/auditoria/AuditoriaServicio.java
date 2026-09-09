package com.ferrovias.sismedico.auditoria;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Registra toda escritura sobre una ficha (FR-033, SC-004).
 *
 * <p>El usuario sale del contexto de seguridad y <b>no se recibe por
 * parámetro</b>. Es deliberado: un parámetro es algo que alguien puede pasar
 * mal, y en un rastro de auditoría eso significa atribuirle a un operario una
 * escritura que hizo otro. El único que sabe quién está autenticado es Spring
 * Security.
 *
 * <p>El momento sale del {@link Clock} (D7), no de {@code SYSUTCDATETIME()}: un
 * solo reloj para todo el sistema, y tests deterministas.
 *
 * <p>Los métodos no abren transacción propia. Se ejecutan dentro de la del
 * servicio que hace la escritura, para que auditoría y dato se confirmen o se
 * deshagan juntos. Si el asiento fuera en otra transacción podría quedar un
 * registro de algo que nunca ocurrió, o una escritura sin registro.
 */
@Service
public class AuditoriaServicio {

	/**
	 * Con qué se registra una escritura que no viene de una petición
	 * autenticada. Hoy no hay ninguna: toda la API exige usuario (FR-036e). Está
	 * para que un asiento nunca quede sin usuario, porque la columna es NOT NULL
	 * y perder el asiento sería peor que registrar que no se supo quién fue.
	 */
	static final String USUARIO_DESCONOCIDO = "desconocido";

	private final AuditoriaRepositorio repositorio;
	private final Clock reloj;

	public AuditoriaServicio(AuditoriaRepositorio repositorio, Clock reloj) {
		this.repositorio = repositorio;
		this.reloj = reloj;
	}

	public void registrarAlta(long fichaId) {
		repositorio.registrar(fichaId, Operacion.ALTA, usuarioActual(), Instant.now(reloj));
	}

	public void registrarModificacion(long fichaId) {
		repositorio.registrar(fichaId, Operacion.MODIFICACION, usuarioActual(), Instant.now(reloj));
	}

	public void registrarBaja(long fichaId) {
		repositorio.registrar(fichaId, Operacion.BAJA, usuarioActual(), Instant.now(reloj));
	}

	/** Quién está autenticado. Es también lo que va a las columnas de FR-032. */
	public String usuarioActual() {
		var autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || autenticacion.getName() == null
				|| autenticacion.getName().isBlank()) {
			return USUARIO_DESCONOCIDO;
		}
		return autenticacion.getName();
	}

	public Instant ahora() {
		return Instant.now(reloj);
	}
}
