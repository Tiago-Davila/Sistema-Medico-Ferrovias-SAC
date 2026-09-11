/**
 * Los esquemas que usa la pantalla.
 *
 * ## De dónde salen
 *
 * De `contrato-generado.ts`, que se deriva del contrato de OpenAPI que expone el
 * backend. La cadena entera es:
 *
 *     controladores y DTO de Spring
 *       -> /v3/api-docs
 *       -> lib/contrato-openapi.json   (lo vuelca ContratoOpenApiTest)
 *       -> lib/contrato-generado.ts    (npm run generar-contrato)
 *       -> este archivo
 *
 * Nada de esto se escribe a mano. Si el backend agrega, saca o renombra un
 * campo, el contrato cambia y TypeScript rompe acá. Escrito a mano, el frontend
 * seguiría compilando y mandando un campo que ya no existe.
 *
 * ## Lo que el contrato NO trae, y por qué está bien
 *
 * El contrato trae la **forma**: nombres de campo, tipos y enums. No trae las
 * reglas de negocio, y no puede traerlas. Las reglas de esta feature son
 * acumulativas, dependen de las otras fichas del legajo y del reloj del
 * servidor: si una fecha de evento es futura depende de qué hora es en
 * Argentina, y si una ficha se solapa depende de qué más tiene cargado ese
 * empleado.
 *
 * Por eso acá **no se reimplementa ninguna regla del backend**. Lo que se agrega
 * es exclusivamente lo que hace falta para que el formulario funcione antes de
 * llegar al servidor: que un campo obligatorio de la pantalla esté completo y
 * que una fecha tenga forma de fecha.
 *
 * El backend es la autoridad (FR-018) y cada rechazo suyo se muestra tal como
 * viene. Duplicar acá el tope de 999 días o la excluyencia de fechas nos dejaría
 * con dos copias de la misma regla, y la divergencia sería cuestión de tiempo:
 * el operario vería un error distinto del que aplica el servidor, que es peor
 * que no validar en el cliente.
 */

import { z } from "zod";

import {
  Advertencia,
  AuditoriaDTO,
  CodigoConDescripcion,
  DetalleEnfermedad,
  Empleado,
  FichaEntradaDTO,
  FichaResumenDTO,
  FichaSalidaDTO,
  GrupoEnfermedad,
  RespuestaDeEscrituraDTO,
} from "./contrato-generado";

// Reexportados con los nombres del dominio. La forma es la del contrato.
export const esquemaEmpleado = Empleado;
export const esquemaGrupoEnfermedad = GrupoEnfermedad;
export const esquemaDetalleEnfermedad = DetalleEnfermedad;
export const esquemaAdvertencia = Advertencia;
export const esquemaFichaEntrada = FichaEntradaDTO;

const codigoConDescripcionNullable = CodigoConDescripcion.extend({
  descripcion: z.string().nullable().optional(),
});

const auditoriaNullable = AuditoriaDTO.extend({
  modificadaPor: z.string().nullable().optional(),
  modificadaEn: z.string().datetime({ offset: true }).nullable().optional(),
}).nullable();

// El contrato generado no distingue bien nullable de opcional. El backend sí devuelve null en
// fichas históricas y en fichas sin alta/citación, así que la pantalla acepta esos nulls al leer.
export const esquemaFicha = FichaSalidaDTO.extend({
  estadoPaciente: z.enum(["ACCIDENTADO", "ENFERMEDAD"]).nullable().optional(),
  inItinere: z.boolean().nullable().optional(),
  estabaEnServicio: z.boolean().nullable().optional(),
  horaAccidente: z.number().int().nullable().optional(),
  atendidoServicioMedico: z.boolean().nullable().optional(),
  envioMedicoDomicilio: z.boolean().nullable().optional(),
  justificado: z.boolean().nullable().optional(),
  fechaCitacion: z.string().nullable().optional(),
  fechaAlta: z.string().nullable().optional(),
  diasPerdidos: z.number().int().nullable().optional(),
  grupoEnfermedad: codigoConDescripcionNullable.nullable().optional(),
  detalleEnfermedad: codigoConDescripcionNullable.nullable().optional(),
  observaciones: z.string().nullable().optional(),
  motivosInconsistencia: z.array(z.string()).optional(),
  auditoria: auditoriaNullable.optional(),
});

export const esquemaResumenDeFicha = FichaResumenDTO.extend({
  estadoPaciente: z.enum(["ACCIDENTADO", "ENFERMEDAD"]).nullable().optional(),
  fechaCitacion: z.string().nullable().optional(),
  fechaAlta: z.string().nullable().optional(),
  diasPerdidos: z.number().int().nullable().optional(),
});

export const esquemaRespuestaDeEscritura = RespuestaDeEscrituraDTO.extend({
  datos: esquemaFicha.optional(),
  advertencias: z.array(Advertencia).optional(),
});

export type Empleado = z.infer<typeof esquemaEmpleado>;
export type GrupoEnfermedad = z.infer<typeof esquemaGrupoEnfermedad>;
export type DetalleEnfermedad = z.infer<typeof esquemaDetalleEnfermedad>;
export type Ficha = z.infer<typeof esquemaFicha>;
export type ResumenDeFicha = z.infer<typeof esquemaResumenDeFicha>;
export type RespuestaDeEscritura = z.infer<typeof esquemaRespuestaDeEscritura>;
export type Advertencia = z.infer<typeof esquemaAdvertencia>;

/** Un rechazo del backend, del arreglo `violaciones` del 422. */
export const esquemaViolacion = z.object({
  campo: z.string(),
  codigo: z.string(),
  mensaje: z.string(),
  fichaEnConflicto: z
    .object({
      id: z.number(),
      fechaEvento: z.string(),
      incompleta: z.boolean(),
    })
    .nullish(),
});

export type Violacion = z.infer<typeof esquemaViolacion>;

export const esquemaProblema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  violaciones: z.array(esquemaViolacion).optional(),
});

export type Problema = z.infer<typeof esquemaProblema>;

/** FR-006b. Acá porque la pantalla tiene que mostrar el contador. */
export const MAXIMO_OBSERVACIONES = 500;

/** FR-019. Solo para anticipar el aviso; el backend la devuelve igual. */
export const DIAS_PARA_ADVERTIR_ANTIGUEDAD = 45;

const FECHA = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Lo que el formulario necesita para poder enviarse.
 *
 * Cada campo de acá tiene su equivalente en el backend, nunca al revés. Lo único
 * que se comprueba es presencia y forma:
 *
 * - los obligatorios de FR-004b y FR-004c, para no gastar un viaje al servidor
 *   en un formulario a medio llenar;
 * - que las fechas tengan forma de fecha, que es un error de tipeo y no una
 *   regla de negocio.
 *
 * Lo que NO está acá, a propósito, porque son reglas del servidor: que la fecha
 * del evento no sea futura (FR-007), la excluyencia de citación y alta (FR-008),
 * que el detalle pertenezca al grupo (FR-010), el tope de 999 días (FR-011), la
 * unicidad (FR-013), el solapamiento (FR-014) y la obligatoriedad de in itinere
 * en accidentes (FR-016).
 *
 * El tope de 500 caracteres de observaciones sí está, y es la única excepción:
 * FR-006b pide un contador visible en pantalla, así que la pantalla ya tiene que
 * conocer el número para poder mostrarlo. El backend lo aplica igual.
 */
export const esquemaFormularioFicha = z.object({
  // El legajo se resuelve en el buscador, fuera del formulario visible. Exigirlo acá bloquea el
  // submit antes de que guardar() pueda agregar el legajo confirmado del empleado.
  legajo: z.number().int().positive().optional(),

  fechaEvento: z.string().regex(FECHA, "Poné la fecha del evento."),

  estadoPaciente: z
    .enum(["ACCIDENTADO", "ENFERMEDAD"], {
      message: "Elegí el estado del paciente.",
    })
    .nullable(),

  // Tres estados legítimos (FR-004e): sí, no y sin definir.
  inItinere: z.boolean().nullable(),

  // Los cuatro de FR-004c: obligatorios, con "no" como valor inicial en una ficha nueva.
  //
  // Una ficha histórica puede traerlos sin responder (FR-004d) y entonces el formulario los
  // muestra vacíos: mostrarlos en "no" inventaría un dato que nadie cargó. Ahí estos mensajes
  // son los que le piden al operario completarlos, y solo al guardar (FR-031).
  estabaEnServicio: z.boolean({ message: "Respondé si estaba en servicio." }).nullable(),
  atendidoServicioMedico: z
    .boolean({ message: "Respondé si lo atendió el servicio médico." })
    .nullable(),
  envioMedicoDomicilio: z
    .boolean({ message: "Respondé si se envió médico a domicilio." })
    .nullable(),
  justificado: z.boolean({ message: "Respondé si está justificado." }).nullable(),

  // Solo la hora, sin minutos (FR-004g). Opcional.
  horaAccidente: z.number().int().min(0).max(23).nullable(),

  fechaCitacion: z.string().regex(FECHA).nullable(),
  fechaAlta: z.string().regex(FECHA).nullable(),

  grupoEnfermedad: z.number({ message: "Elegí el grupo de enfermedad." }).int(),
  detalleEnfermedad: z.number({ message: "Elegí el detalle de enfermedad." }).int(),

  observaciones: z.string().max(MAXIMO_OBSERVACIONES).nullable(),
});

export type FormularioFicha = z.infer<typeof esquemaFormularioFicha>;

/**
 * Por qué una ficha guardada quedó señalada (FR-029).
 *
 * Los códigos los decide el backend; acá solo se traducen a algo que el operario pueda leer. Si
 * mañana aparece uno que no está en esta tabla, la pantalla lo muestra crudo en vez de
 * esconderlo: una ficha marcada sin explicación es peor que una explicación fea.
 */
export const MOTIVOS_DE_INCONSISTENCIA: Record<string, string> = {
  SIN_CLASIFICACION: "Sin grupo o detalle de enfermedad",
  CODIGO_HUERFANO: "Código de enfermedad que ya no está en el catálogo",
  SIN_FECHA_FIN: "Sin fecha de citación ni de alta",
  DIAS_PERDIDOS_NEGATIVOS: "La fecha de alta es anterior a la del evento",
  OBSERVACIONES_EXCEDIDAS: `Observaciones de más de ${MAXIMO_OBSERVACIONES} caracteres`,
  CAMPO_SIN_RESPONDER: "Campos de sí/no sin responder",
};

export function explicarMotivo(codigo: string): string {
  return MOTIVOS_DE_INCONSISTENCIA[codigo] ?? codigo;
}
