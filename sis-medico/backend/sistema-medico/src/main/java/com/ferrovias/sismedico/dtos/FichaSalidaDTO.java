package com.ferrovias.sismedico.dtos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.ferrovias.sismedico.models.EstadoPaciente;
import com.ferrovias.sismedico.models.FichaMedica;
import com.ferrovias.sismedico.repositories.CatalogoRepositorio;
import com.ferrovias.sismedico.service.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.service.EvaluadorInconsistencia;

// Una ficha completa tal como está almacenada, sin topear ni normalizar nada.
// Intencional: días perdidos negativos o fuera de rango salen tal cual, un código de enfermedad
// huérfano sale con su id y descripción null. "Arreglar" un dato feo acá lo esconde en vez de
// señalarlo, y el operario pierde la única pista de que la ficha necesita atención.
public record FichaSalidaDTO(
		long id,
		long version,
		int legajo,
		LocalDate fechaEvento,
		EstadoPaciente estadoPaciente,
		Boolean inItinere,
		Boolean estabaEnServicio,
		Integer horaAccidente,
		Boolean atendidoServicioMedico,
		Boolean envioMedicoDomicilio,
		Boolean justificado,
		LocalDate fechaCitacion,
		LocalDate fechaAlta,
		Long diasPerdidos,
		CodigoConDescripcion grupoEnfermedad,
		CodigoConDescripcion detalleEnfermedad,
		String observaciones,
		boolean incompleta,
		List<String> motivosInconsistencia,
		AuditoriaDTO auditoria) {

	// Un código del catálogo con su descripción; descripción null si el código quedó huérfano.
	public record CodigoConDescripcion(int id, String descripcion) {
	}

	public record AuditoriaDTO(String creadaPor, Instant creadaEn,
			String modificadaPor, Instant modificadaEn) {
	}

	public static FichaSalidaDTO de(FichaMedica ficha, CatalogoRepositorio catalogo,
			CalculadorDiasPerdidos calculador, EvaluadorInconsistencia.Resultado evaluacion) {

		return de(ficha, catalogo, calculador, evaluacion.incompleta(), evaluacion.motivos());
	}

	public static FichaSalidaDTO de(FichaMedica ficha, CatalogoRepositorio catalogo,
			CalculadorDiasPerdidos calculador, boolean incompleta, List<String> motivos) {

		return new FichaSalidaDTO(
				ficha.id(),
				ficha.version(),
				ficha.legajo(),
				ficha.fechaEvento(),
				ficha.estadoPaciente(),
				ficha.inItinere(),
				ficha.estabaEnServicio(),
				ficha.horaAccidente(),
				ficha.atendidoServicioMedico(),
				ficha.envioMedicoDomicilio(),
				ficha.justificado(),
				ficha.fechaCitacion(),
				ficha.fechaAlta(),
				calculador.calcular(ficha),
				grupoConDescripcion(ficha, catalogo),
				detalleConDescripcion(ficha, catalogo),
				ficha.observaciones() == null ? null : ficha.observaciones().texto(),
				incompleta,
				motivos,
				auditoriaDe(ficha));
	}

	private static CodigoConDescripcion grupoConDescripcion(FichaMedica ficha,
			CatalogoRepositorio catalogo) {

		if (ficha.grupoEnfermedad() == null) {
			return null;
		}
		return new CodigoConDescripcion(ficha.grupoEnfermedad(),
				catalogo.buscarGrupo(ficha.grupoEnfermedad())
						.map(grupo -> grupo.descripcion())
						.orElse(null));
	}

	private static CodigoConDescripcion detalleConDescripcion(FichaMedica ficha,
			CatalogoRepositorio catalogo) {

		if (ficha.detalleEnfermedad() == null) {
			return null;
		}
		return new CodigoConDescripcion(ficha.detalleEnfermedad(),
				catalogo.buscarDetalle(ficha.detalleEnfermedad())
						.map(detalle -> detalle.descripcion())
						.orElse(null));
	}

	private static AuditoriaDTO auditoriaDe(FichaMedica ficha) {
		var auditoria = ficha.auditoria();
		return auditoria == null ? null : new AuditoriaDTO(
				auditoria.creadaPor(), auditoria.creadaEn(),
				auditoria.modificadaPor(), auditoria.modificadaEn());
	}
}
