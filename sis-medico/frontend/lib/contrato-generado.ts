import { z } from "zod";

export const FichaEntradaDTO = z
  .object({
    version: z.number().int(),
    legajo: z.number().int(),
    fechaEvento: z.string(),
    estadoPaciente: z.enum(["ACCIDENTADO", "ENFERMEDAD"]),
    inItinere: z.boolean(),
    estabaEnServicio: z.boolean(),
    horaAccidente: z.number().int(),
    atendidoServicioMedico: z.boolean(),
    envioMedicoDomicilio: z.boolean(),
    justificado: z.boolean(),
    fechaCitacion: z.string(),
    fechaAlta: z.string(),
    grupoEnfermedad: z.number().int(),
    detalleEnfermedad: z.number().int(),
    observaciones: z.string(),
  })
  .partial()
  .passthrough();
export const Advertencia = z
  .object({ campo: z.string(), codigo: z.string(), mensaje: z.string() })
  .partial()
  .passthrough();
export const AuditoriaDTO = z
  .object({
    creadaPor: z.string(),
    creadaEn: z.string().datetime({ offset: true }),
    modificadaPor: z.string(),
    modificadaEn: z.string().datetime({ offset: true }),
  })
  .partial()
  .passthrough();
export const CodigoConDescripcion = z
  .object({ id: z.number().int(), descripcion: z.string() })
  .partial()
  .passthrough();
export const FichaSalidaDTO = z
  .object({
    id: z.number().int(),
    version: z.number().int(),
    legajo: z.number().int(),
    fechaEvento: z.string(),
    estadoPaciente: z.enum(["ACCIDENTADO", "ENFERMEDAD"]),
    inItinere: z.boolean(),
    estabaEnServicio: z.boolean(),
    horaAccidente: z.number().int(),
    atendidoServicioMedico: z.boolean(),
    envioMedicoDomicilio: z.boolean(),
    justificado: z.boolean(),
    fechaCitacion: z.string(),
    fechaAlta: z.string(),
    diasPerdidos: z.number().int(),
    grupoEnfermedad: CodigoConDescripcion,
    detalleEnfermedad: CodigoConDescripcion,
    observaciones: z.string(),
    incompleta: z.boolean(),
    motivosInconsistencia: z.array(z.string()),
    auditoria: AuditoriaDTO,
  })
  .partial()
  .passthrough();
export const RespuestaDeEscrituraDTO = z
  .object({ datos: FichaSalidaDTO, advertencias: z.array(Advertencia) })
  .partial()
  .passthrough();
export const GrupoEnfermedad = z
  .object({ id: z.number().int(), descripcion: z.string() })
  .partial()
  .passthrough();
export const DetalleEnfermedad = z
  .object({
    id: z.number().int(),
    grupoEnfermedadId: z.number().int(),
    descripcion: z.string(),
  })
  .partial()
  .passthrough();
export const Empleado = z
  .object({
    legajo: z.number().int(),
    apellido: z.string(),
    nombre: z.string(),
    seccion: z.string(),
    categoriaLaboral: z.string(),
  })
  .partial()
  .passthrough();
