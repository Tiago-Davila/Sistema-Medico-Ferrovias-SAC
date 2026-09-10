package com.ferrovias.sismedico.dtos;

import java.util.List;

// La envoltura de toda respuesta exitosa de escritura: la ficha guardada y sus advertencias.
// Intencional: advertencias siempre está presente, vacía si no hay ninguna, para que el frontend
// no tenga que manejar el caso de campo ausente aparte. Nunca cambia el código de estado HTTP.
public record RespuestaDeEscrituraDTO(FichaSalidaDTO datos, List<Advertencia> advertencias) {

	public RespuestaDeEscrituraDTO {
		advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
	}
}
