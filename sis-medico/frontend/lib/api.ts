/**
 * Cliente HTTP contra la API.
 *
 * <p>No decide nada: manda, recibe y traduce el formato de error del contrato.
 * Toda regla vive en el backend (FR-018).
 */

import {
  esquemaEmpleado,
  esquemaDetalleEnfermedad,
  esquemaGrupoEnfermedad,
  esquemaProblema,
  esquemaRespuestaDeEscritura,
  type DetalleEnfermedad,
  type Empleado,
  type GrupoEnfermedad,
  type Problema,
  type RespuestaDeEscritura,
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
