package com.ferrovias.sismedico.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.models.Observaciones;
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;

// Mira una ficha ya guardada y dice si algo en ella no cierra con las reglas de hoy.
// Intencional: no valida nada y no rechaza nada. Es lo contrario de ValidadorFichaMedica, que
// corre al guardar y bloquea; esto corre al leer y solo señala, para que una ficha anterior a
// 2016 se pueda abrir y recorrer igual (FR-029, FR-030).
@Component
public class EvaluadorInconsistencia {

	private final CatalogoRepositorio catalogo;
	private final CalculadorDiasPerdidos calculador;

	public EvaluadorInconsistencia(CatalogoRepositorio catalogo, CalculadorDiasPerdidos calculador) {
		this.catalogo = catalogo;
		this.calculador = calculador;
	}

	public Resultado evaluar(FichaMedica ficha) {
		List<String> motivos = new ArrayList<>();

		if (ficha.grupoEnfermedad() == null || ficha.detalleEnfermedad() == null) {
			motivos.add(Motivos.SIN_CLASIFICACION);
		}
		if (tieneCodigoHuerfano(ficha)) {
			motivos.add(Motivos.CODIGO_HUERFANO);
		}
		if (ficha.fechaCitacion() == null && ficha.fechaAlta() == null) {
			motivos.add(Motivos.SIN_FECHA_FIN);
		}

		Long dias = calculador.calcular(ficha);
		if (dias != null && dias < 0) {
			motivos.add(Motivos.DIAS_PERDIDOS_NEGATIVOS);
		}

		Observaciones observaciones = ficha.observaciones();
		if (observaciones != null
				&& observaciones.longitud() > ValidadorFichaMedica.MAXIMO_CARACTERES_OBSERVACIONES) {
			motivos.add(Motivos.OBSERVACIONES_EXCEDIDAS);
		}
		if (tieneCampoSinResponder(ficha)) {
			motivos.add(Motivos.CAMPO_SIN_RESPONDER);
		}

		return new Resultado(!motivos.isEmpty(), List.copyOf(motivos));
	}

	// El código quedó apuntando a un grupo o a un detalle que ya no está en el catálogo.
	// Intencional: no comprueba que el detalle pertenezca al grupo. Esa es una regla de escritura;
	// acá la pregunta es solamente si el código se puede resolver a una descripción.
	private boolean tieneCodigoHuerfano(FichaMedica ficha) {
		if (ficha.grupoEnfermedad() != null && !catalogo.existeGrupo(ficha.grupoEnfermedad())) {
			return true;
		}
		return ficha.detalleEnfermedad() != null
				&& catalogo.buscarDetalle(ficha.detalleEnfermedad()).isEmpty();
	}

	// Intencional: null no es "no". Una ficha importada puede no traer estos campos, y mostrarlos
	// como "no" inventaría un dato que nadie cargó.
	private boolean tieneCampoSinResponder(FichaMedica ficha) {
		return ficha.estadoPaciente() == null
				|| ficha.estabaEnServicio() == null
				|| ficha.atendidoServicioMedico() == null
				|| ficha.envioMedicoDomicilio() == null
				|| ficha.justificado() == null;
	}

	// Por qué una ficha queda señalada; viaja en motivosInconsistencia.
	public record Resultado(boolean incompleta, List<String> motivos) {

		public static final Resultado CONSISTENTE = new Resultado(false, List.of());
	}

	// Códigos estables de la API. No se inventan motivos fuera de esta lista.
	public static final class Motivos {

		public static final String SIN_CLASIFICACION = "SIN_CLASIFICACION";
		public static final String CODIGO_HUERFANO = "CODIGO_HUERFANO";
		public static final String SIN_FECHA_FIN = "SIN_FECHA_FIN";
		public static final String DIAS_PERDIDOS_NEGATIVOS = "DIAS_PERDIDOS_NEGATIVOS";
		public static final String OBSERVACIONES_EXCEDIDAS = "OBSERVACIONES_EXCEDIDAS";
		public static final String CAMPO_SIN_RESPONDER = "CAMPO_SIN_RESPONDER";

		public static final List<String> TODOS = List.of(
				SIN_CLASIFICACION, CODIGO_HUERFANO, SIN_FECHA_FIN,
				DIAS_PERDIDOS_NEGATIVOS, OBSERVACIONES_EXCEDIDAS, CAMPO_SIN_RESPONDER);

		private Motivos() {
		}
	}
}
