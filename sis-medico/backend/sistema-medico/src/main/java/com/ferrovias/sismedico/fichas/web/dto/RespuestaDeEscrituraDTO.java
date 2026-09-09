package com.ferrovias.sismedico.fichas.web.dto;

import java.util.List;

import com.ferrovias.sismedico.comun.Advertencia;

/**
 * La envoltura de toda respuesta exitosa de escritura.
 *
 * <p>Misma forma en el alta y en la modificación, así el frontend mira un solo
 * lugar para las advertencias.
 *
 * <p>{@code advertencias} <b>siempre está presente</b>, vacía si no hay
 * ninguna. Un arreglo vacío y un campo ausente obligan a dos ramas distintas en
 * el cliente, y la segunda es la que nadie prueba.
 *
 * <p>Una advertencia no cambia el código de estado: el guardado ya ocurrió (M2).
 * No hay confirmación en dos pasos, porque exigiría un modal o un segundo viaje
 * y las dos cosas pelean contra la carga por teclado y contra SC-001.
 */
public record RespuestaDeEscrituraDTO(FichaSalidaDTO datos, List<Advertencia> advertencias) {

	public RespuestaDeEscrituraDTO {
		advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
	}
}
