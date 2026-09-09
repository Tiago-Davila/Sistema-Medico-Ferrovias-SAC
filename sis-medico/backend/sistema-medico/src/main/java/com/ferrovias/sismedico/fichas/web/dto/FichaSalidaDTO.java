package com.ferrovias.sismedico.fichas.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.ferrovias.sismedico.enfermedades.CatalogoRepositorio;
import com.ferrovias.sismedico.fichas.CalculadorDiasPerdidos;
import com.ferrovias.sismedico.fichas.EstadoPaciente;
import com.ferrovias.sismedico.fichas.FichaMedica;

/**
 * Una ficha completa, tal como está almacenada (FR-028).
 *
 * <p><b>No topea ni normaliza nada.</b> Días perdidos negativos o mayores a 999
 * salen tal cual (FR-020c), los booleanos ausentes salen como {@code null} y no
 * como {@code false} (FR-004d), las observaciones de más de 500 caracteres salen
 * completas (FR-006c), y un código de enfermedad huérfano sale con su id y la
 * descripción en {@code null} (FR-028b).
 *
 * <p>Cualquier "arreglo" que se le haga acá a un dato feo lo esconde en lugar de
 * corregirlo, y el operario pierde la única señal de que la ficha necesita
 * atención.
 *
 * @param diasPerdidos derivado, nunca recibido: no existe en el DTO de entrada
 * @param motivosInconsistencia por qué la ficha se marca. Lo calcula la lectura,
 *     sin aplicar ninguna validación (FR-029, FR-030).
 */
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

	/**
	 * Un código del catálogo con su descripción, si la tiene.
	 *
	 * <p>{@code descripcion} en {@code null} cuando el código quedó huérfano
	 * (FR-028b). El id se muestra igual: el operario tiene que ver qué está
	 * cargado, aunque no signifique nada hoy.
	 */
	public record CodigoConDescripcion(int id, String descripcion) {
	}

	/** FR-032. */
	public record AuditoriaDTO(String creadaPor, Instant creadaEn,
			String modificadaPor, Instant modificadaEn) {
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
				// Se recalcula siempre desde las fechas (FR-020b). El valor que
				// el sistema de referencia traía no se usa: se descarta.
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
