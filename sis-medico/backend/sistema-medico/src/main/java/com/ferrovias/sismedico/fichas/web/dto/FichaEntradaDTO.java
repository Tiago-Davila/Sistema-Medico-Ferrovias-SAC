package com.ferrovias.sismedico.fichas.web.dto;

import java.time.LocalDate;

import com.ferrovias.sismedico.fichas.EstadoPaciente;
import com.ferrovias.sismedico.fichas.FichaMedica;
import com.ferrovias.sismedico.fichas.Observaciones;

/**
 * Lo que llega en el cuerpo de un alta o una modificación.
 *
 * <h2>No hay campo diasPerdidos</h2>
 *
 * No es que se ignore lo que mande el cliente: <b>no existe dónde recibirlo</b>
 * (M4). Es la diferencia entre una regla que se hace cumplir y una que se
 * confía. Los días perdidos se derivan de las dos fechas (FR-020b) y los
 * devuelve la columna calculada del esquema.
 *
 * <p>{@code estabaEnServicio} sí viaja, pero el servicio lo sobrescribe cuando
 * {@code inItinere} está marcado (FR-024) sin mirar lo enviado. El frontend
 * puede anticiparlo para mostrarlo mientras el operario escribe; anticipar no es
 * decidir.
 *
 * <h2>Nada de anotaciones de validación</h2>
 *
 * La validación de negocio no puede vivir en anotaciones por campo: tiene que
 * acumular todas las violaciones y devolverlas juntas con su código y su campo
 * (M1), y Bean Validation no da esa forma. Vive entera en el servicio.
 *
 * @param version obligatorio al modificar (D2). En el alta no se manda: nace en
 *     0. Se declara acá y no en un DTO aparte porque el resto del cuerpo es
 *     idéntico, y dos records iguales salvo un campo se desincronizan solos.
 * @param estadoPaciente {@code ACCIDENTADO} o {@code ENFERMEDAD} (FR-005)
 * @param inItinere tres valores posibles: {@code true}, {@code false} y ausente
 *     (FR-004e)
 * @param horaAccidente solo la hora, de 0 a 23, sin minutos (FR-004g)
 * @param grupoEnfermedad código del catálogo, que es lo que el operario tipea
 * @param detalleEnfermedad ídem, y tiene que pertenecer al grupo (FR-010)
 */
public record FichaEntradaDTO(
		Long version,
		Integer legajo,
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
		Integer grupoEnfermedad,
		Integer detalleEnfermedad,
		String observaciones) {

	/**
	 * Pasa al dominio sin decidir nada.
	 *
	 * <p>No completa valores por defecto ni normaliza: un {@code null} que llega
	 * como {@code null} es lo que hace que {@code CAMPO_REQUERIDO} se dispare.
	 * Rellenarlo acá con {@code false} taparía la falta y el operario guardaría
	 * un "no" que nunca respondió, contra FR-004d.
	 *
	 * @param id {@code null} en un alta
	 */
	public FichaMedica aDominio(Long id) {
		return new FichaMedica(
				id,
				version == null ? 0L : version,
				legajo == null ? 0 : legajo,
				fechaEvento,
				estadoPaciente,
				inItinere,
				estabaEnServicio,
				horaAccidente,
				atendidoServicioMedico,
				envioMedicoDomicilio,
				justificado,
				fechaCitacion,
				fechaAlta,
				grupoEnfermedad,
				detalleEnfermedad,
				Observaciones.de(observaciones),
				null);
	}
}
