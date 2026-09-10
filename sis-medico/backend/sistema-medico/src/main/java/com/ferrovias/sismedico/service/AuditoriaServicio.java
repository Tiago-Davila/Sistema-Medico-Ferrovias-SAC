package com.ferrovias.sismedico.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.ferrovias.sismedico.models.Operacion;
import com.ferrovias.sismedico.repositories.AuditoriaRepositorio;

// Registra toda escritura sobre una ficha.
// Intencional: el usuario nunca se recibe por parámetro, sale del contexto de seguridad, para que
// nadie pueda atribuirle a un operario una escritura que hizo otro. Los métodos no abren
// transacción propia: corren dentro de la del servicio que escribe, para que auditoría y dato se
// confirmen o se deshagan juntos.
@Service
public class AuditoriaServicio {

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

	// Nombre del usuario autenticado, o un valor fijo si no hay ninguno.
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
