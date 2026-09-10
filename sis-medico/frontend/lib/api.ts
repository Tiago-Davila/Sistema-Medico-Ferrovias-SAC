/**
 * Cliente HTTP contra la API.
 *
 * <p>No decide nada: manda, recibe y traduce el formato de error del contrato.
 * Toda regla vive en el backend (FR-018).
 */

import {
  esquemaEmpleado,
  esquemaDetalleEnfermedad,
  esquemaFicha,
  esquemaGrupoEnfermedad,
  esquemaProblema,
  esquemaRespuestaDeEscritura,
  esquemaResumenDeFicha,
  type DetalleEnfermedad,
  type Empleado,
  type Ficha,
  type GrupoEnfermedad,
  type Problema,
  type RespuestaDeEscritura,
  type ResumenDeFicha,
  type Violacion,
} from "./esquemas";

const BASE = process.env.NEXT_PUBLIC_API_BASE ?? "/api";

/**
 * Un rechazo del backend, ya desarmado.
 *
 * `violaciones` viene siempre completa: son todas las que el servidor detectó,
 * no la primera (M1). La pantalla las reparte junto a cada control.
 */
export class ErrorDeApi extends Error {
  constructor(
    readonly status: number,
    readonly problema: Problema | null,
  ) {
    super(problema?.title ?? `La API respondió ${status}.`);
    this.name = "ErrorDeApi";
  }

  get violaciones(): Violacion[] {
    return this.problema?.violaciones ?? [];
  }

  /** 409: otro operario guardó sobre la misma ficha (FR-037). */
  get esConflicto() {
    return this.status === 409;
  }

  /** 503: el padrón externo no responde. */
  get padronCaido() {
    return this.status === 503;
  }

  get noExiste() {
    return this.status === 404;
  }
}

async function pedir(ruta: string, init?: RequestInit): Promise<Response> {
  const respuesta = await fetch(`${BASE}${ruta}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!respuesta.ok) {
    let problema: Problema | null = null;
    try {
      problema = esquemaProblema.parse(await respuesta.json());
    } catch {
      // Una respuesta de error sin cuerpo entendible sigue siendo un error. Se
      // conserva el status, que es lo que la pantalla necesita para decidir.
      problema = null;
    }
    throw new ErrorDeApi(respuesta.status, problema);
  }

  return respuesta;
}

/** FR-001: confirmar la identidad antes de cargar nada. */
export async function buscarEmpleado(legajo: number): Promise<Empleado> {
  const respuesta = await pedir(`/empleados/${legajo}`);
  return esquemaEmpleado.parse(await respuesta.json());
}

export async function traerGrupos(): Promise<GrupoEnfermedad[]> {
  const respuesta = await pedir("/enfermedades/grupos");
  return esquemaGrupoEnfermedad.array().parse(await respuesta.json());
}

export async function traerDetalles(grupo: number): Promise<DetalleEnfermedad[]> {
  const respuesta = await pedir(`/enfermedades/grupos/${grupo}/detalles`);
  return esquemaDetalleEnfermedad.array().parse(await respuesta.json());
}

/**
 * Alta de una ficha.
 *
 * Un solo viaje. La respuesta trae la ficha guardada y las advertencias
 * (M2): una advertencia no cambia el resultado y no abre una confirmación.
 */
export async function crearFicha(cuerpo: unknown): Promise<RespuestaDeEscritura> {
  const respuesta = await pedir("/fichas", {
    method: "POST",
    body: JSON.stringify(cuerpo),
  });
  return esquemaRespuestaDeEscritura.parse(await respuesta.json());
}

/**
 * Fichas ya cargadas de un empleado (US2-1).
 *
 * Vienen todas, de la más reciente a la más vieja, sin paginado ni filtros: quien pagina es el
 * backend o nadie, y acá es nadie (FR-031b, FR-031c).
 */
export async function traerFichasDe(legajo: number): Promise<ResumenDeFicha[]> {
  const respuesta = await pedir(`/empleados/${legajo}/fichas`);
  return esquemaResumenDeFicha.array().parse(await respuesta.json());
}

/**
 * Una ficha completa, tal como está guardada.
 *
 * Puede venir con días perdidos negativos, códigos sin descripción o booleanos en null: son
 * fichas históricas y se muestran igual (FR-028). Acá no se corrige ni se completa nada.
 */
export async function traerFicha(id: number): Promise<Ficha> {
  const respuesta = await pedir(`/fichas/${id}`);
  return esquemaFicha.parse(await respuesta.json());
}

/**
 * Modificación de una ficha.
 *
 * `version` viaja en el cuerpo y es obligatoria: es la que el servidor compara para no dejar que
 * dos operarios se pisen (FR-037). Ante 409 el llamador tiene que reabrir la ficha, no reintentar.
 */
export async function actualizarFicha(id: number, cuerpo: unknown): Promise<RespuestaDeEscritura> {
  const respuesta = await pedir(`/fichas/${id}`, {
    method: "PUT",
    body: JSON.stringify(cuerpo),
  });
  return esquemaRespuestaDeEscritura.parse(await respuesta.json());
}

/** Baja de una ficha (FR-039b). Responde 204, sin cuerpo. */
export async function eliminarFicha(id: number, version: number): Promise<void> {
  await pedir(`/fichas/${id}?version=${version}`, { method: "DELETE" });
}
