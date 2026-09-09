"use client";

/**
 * El formulario de la ficha médica.
 *
 * ## Orden de tabulación explícito
 *
 * Cada control lleva su `tabIndex`, siguiendo el orden visual de la pantalla de
 * terminal, no el orden del DOM (FR-040). No es lo mismo: el layout agrupa los
 * campos en columnas y bloques por legibilidad, y si el foco siguiera el DOM el
 * operario saltaría de una columna a otra en medio de la carga.
 *
 * El operario que hoy usa la pantalla vieja tiene el orden en los dedos. Cambiarlo
 * lo obliga a mirar dónde quedó el foco en cada campo, y ahí se va SC-001.
 *
 * ## Lo que la pantalla anticipa y lo que no decide
 *
 * Las dependencias entre campos (FR-023 a FR-025) se reflejan acá deshabilitando
 * y autocompletando controles, para que el operario vea el efecto mientras
 * escribe. Eso es comodidad: el servidor las aplica igual, sin mirar lo enviado
 * (FR-018). Ninguna regla vive solo acá.
 *
 * Los días perdidos se muestran calculados y nunca son editables (FR-006). El
 * valor que manda es el del backend; mientras se escribe se anticipa el mismo
 * cálculo para que el campo no quede en blanco.
 */

import { useEffect } from "react";
import { useFormContext, useWatch } from "react-hook-form";

import { MAXIMO_OBSERVACIONES, type FormularioFicha, type Violacion } from "@/lib/esquemas";

import { SelectorCatalogo } from "./SelectorCatalogo";

type Props = {
  /** Violaciones del último 422, para repartirlas junto a cada control (T050). */
  violaciones: Violacion[];
  deshabilitado: boolean;
};

/** Orden visual de la pantalla vieja. El 1 es el legajo, en el buscador. */
const ORDEN = {
  fechaEvento: 2,
  estadoPaciente: 3,
  inItinere: 4,
  estabaEnServicio: 5,
  horaAccidente: 6,
  atendidoServicioMedico: 7,
  envioMedicoDomicilio: 8,
  justificado: 9,
  fechaCitacion: 10,
  fechaAlta: 11,
  grupoEnfermedad: 12,
  detalleEnfermedad: 13,
  observaciones: 14,
  guardar: 15,
} as const;

export function FormularioFicha({ violaciones, deshabilitado }: Props) {
  const { register, control, setValue, formState } = useFormContext<FormularioFicha>();

  const estado = useWatch({ control, name: "estadoPaciente" });
  const inItinere = useWatch({ control, name: "inItinere" });
  const fechaEvento = useWatch({ control, name: "fechaEvento" });
  const fechaAlta = useWatch({ control, name: "fechaAlta" });
  const observaciones = useWatch({ control, name: "observaciones" });

  const esAccidente = estado === "ACCIDENTADO";
  const esEnfermedad = estado === "ENFERMEDAD";

  // FR-025: con enfermedad, in itinere y hora del accidente no aplican y quedan
  // vacíos. Se limpian en el formulario, no solo se deshabilitan: un campo
  // deshabilitado que conserva el valor anterior lo manda igual al servidor.
  useEffect(() => {
    if (esEnfermedad) {
      setValue("inItinere", null);
      setValue("horaAccidente", null);
    }
  }, [esEnfermedad, setValue]);

  // FR-023: con accidente, envío de médico a domicilio queda en NO. En "no", no
  // vacío: FR-004c no le admite tercer estado (FR-026b).
  useEffect(() => {
    if (esAccidente) {
      setValue("envioMedicoDomicilio", false);
    }
  }, [esAccidente, setValue]);

  // FR-024: in itinere marcado implica que estaba en servicio, y el campo deja
  // de ser editable. Se preserva tal como está en el sistema de referencia,
  // aunque sea contraintuitivo: está documentado en los supuestos del spec para
  // que no se "corrija" por error.
  useEffect(() => {
    if (inItinere === true) {
      setValue("estabaEnServicio", true);
    }
  }, [inItinere, setValue]);

  const diasPerdidos = calcularDiasPerdidos(fechaEvento, fechaAlta);
  const largoObservaciones = observaciones?.length ?? 0;

  function violacionDe(campo: keyof FormularioFicha) {
    return violaciones.find((v) => v.campo === campo);
  }

  /** Muestra el rechazo del backend, y si no hay, el del formulario. */
  function Error_({ campo }: { campo: keyof FormularioFicha }) {
    const delServidor = violacionDe(campo)?.mensaje;
    const delFormulario = formState.errors[campo]?.message;
    const mensaje = delServidor ?? delFormulario;
    if (!mensaje) return null;
    return (
      <p role="alert" className="text-sm text-red-700">
        {mensaje}
      </p>
    );
  }

  const claseCampo = "border border-neutral-400 px-2 py-1";

  return (
    <fieldset disabled={deshabilitado} className="grid grid-cols-2 gap-x-8 gap-y-4">
      {/* ---------------------------------------------------- Evento */}
      <div className="flex flex-col gap-1">
        <label htmlFor="fechaEvento" className="text-sm font-medium">
          Fecha del evento
        </label>
        <input
          id="fechaEvento"
          type="date"
          tabIndex={ORDEN.fechaEvento}
          className={claseCampo}
          {...register("fechaEvento")}
        />
        <Error_ campo="fechaEvento" />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="estadoPaciente" className="text-sm font-medium">
          Estado del paciente
        </label>
        <select
          id="estadoPaciente"
          tabIndex={ORDEN.estadoPaciente}
          className={claseCampo}
          {...register("estadoPaciente")}
        >
          {/* FR-005: exactamente dos valores. */}
          <option value="ACCIDENTADO">Accidentado</option>
          <option value="ENFERMEDAD">Enfermedad</option>
        </select>
        <Error_ campo="estadoPaciente" />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="inItinere" className="text-sm font-medium">
          In itinere
        </label>
        <select
          id="inItinere"
          tabIndex={ORDEN.inItinere}
          disabled={!esAccidente}
          className={claseCampo}
          {...register("inItinere", {
            setValueAs: (v) => (v === "" ? null : v === "true"),
          })}
        >
          {/* FR-004e: el único campo con tres estados legítimos. */}
          <option value="">(sin definir)</option>
          <option value="true">Sí</option>
          <option value="false">No</option>
        </select>
        <Error_ campo="inItinere" />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="horaAccidente" className="text-sm font-medium">
          Hora del accidente
        </label>
        <input
          id="horaAccidente"
          type="number"
          min={0}
          max={23}
          tabIndex={ORDEN.horaAccidente}
          disabled={!esAccidente}
          className={claseCampo}
          // FR-004g: solo la hora, sin minutos.
          {...register("horaAccidente", {
            setValueAs: (v) => (v === "" || v === null ? null : Number(v)),
          })}
        />
        <Error_ campo="horaAccidente" />
      </div>

      {/* ------------------------------------------------- Sí o no */}
      <CampoSiNo
        id="estabaEnServicio"
        etiqueta="Estaba en servicio"
        orden={ORDEN.estabaEnServicio}
        // FR-024: lo fija in itinere y deja de ser editable.
        deshabilitado={inItinere === true}
        registro={register("estabaEnServicio")}
      />

      <CampoSiNo
        id="atendidoServicioMedico"
        etiqueta="Atendido por servicio médico"
        orden={ORDEN.atendidoServicioMedico}
        registro={register("atendidoServicioMedico")}
      />

      <CampoSiNo
        id="envioMedicoDomicilio"
        etiqueta="Envío de médico a domicilio"
        orden={ORDEN.envioMedicoDomicilio}
        // FR-023: con accidente no aplica y queda en no.
        deshabilitado={esAccidente}
        registro={register("envioMedicoDomicilio")}
      />

      <CampoSiNo
        id="justificado"
        etiqueta="Justificado"
        orden={ORDEN.justificado}
        registro={register("justificado")}
      />

      {/* -------------------------------------------------- Fechas */}
      <div className="flex flex-col gap-1">
        <label htmlFor="fechaCitacion" className="text-sm font-medium">
          Fecha de citación
        </label>
        <input
          id="fechaCitacion"
          type="date"
          tabIndex={ORDEN.fechaCitacion}
          className={claseCampo}
          {...register("fechaCitacion", { setValueAs: (v) => (v === "" ? null : v) })}
        />
        <Error_ campo="fechaCitacion" />
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="fechaAlta" className="text-sm font-medium">
          Fecha de alta
        </label>
        <input
          id="fechaAlta"
          type="date"
          tabIndex={ORDEN.fechaAlta}
          className={claseCampo}
          {...register("fechaAlta", { setValueAs: (v) => (v === "" ? null : v) })}
        />
        <Error_ campo="fechaAlta" />
      </div>

      <div className="flex flex-col gap-1">
        <span className="text-sm font-medium">Días perdidos</span>
        {/* FR-006: nunca editable. Sin tabIndex: el foco no se detiene en un
            valor que no se puede tocar. */}
        <output className="border border-neutral-200 bg-neutral-100 px-2 py-1 font-mono">
          {diasPerdidos ?? "—"}
        </output>
      </div>

      {/* --------------------------------------------- Clasificación */}
      <SelectorCatalogo
        ordenGrupo={ORDEN.grupoEnfermedad}
        ordenDetalle={ORDEN.detalleEnfermedad}
        violacionGrupo={violacionDe("grupoEnfermedad")?.mensaje}
        violacionDetalle={violacionDe("detalleEnfermedad")?.mensaje}
      />

      {/* -------------------------------------------------- Texto */}
      <div className="col-span-2 flex flex-col gap-1">
        <label htmlFor="observaciones" className="text-sm font-medium">
          Observaciones
        </label>
        <textarea
          id="observaciones"
          rows={3}
          tabIndex={ORDEN.observaciones}
          className={claseCampo}
          {...register("observaciones", { setValueAs: (v) => (v === "" ? null : v) })}
        />
        {/* FR-006b: contador visible mientras se escribe. */}
        <span
          className={
            largoObservaciones > MAXIMO_OBSERVACIONES
              ? "text-sm text-red-700"
              : "text-sm text-neutral-500"
          }
        >
          {largoObservaciones} / {MAXIMO_OBSERVACIONES}
        </span>
        <Error_ campo="observaciones" />
      </div>
    </fieldset>
  );
}

/**
 * Un campo de sí o no de FR-004c.
 *
 * Va como par de radios y no como checkbox a propósito: un checkbox no
 * distingue "no" de "sin responder", y FR-004d exige que se distingan. Además
 * el par de radios se responde con las flechas, sin sacar las manos del
 * teclado.
 */
function CampoSiNo({
  id,
  etiqueta,
  orden,
  registro,
  deshabilitado = false,
}: {
  id: string;
  etiqueta: string;
  orden: number;
  registro: ReturnType<ReturnType<typeof useFormContext<FormularioFicha>>["register"]>;
  deshabilitado?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <span className="text-sm font-medium">{etiqueta}</span>
      <select
        id={id}
        tabIndex={orden}
        disabled={deshabilitado}
        className="border border-neutral-400 px-2 py-1"
        {...registro}
      >
        <option value="false">No</option>
        <option value="true">Sí</option>
      </select>
    </div>
  );
}

/**
 * Anticipa los días perdidos para que el campo no quede en blanco mientras el
 * operario escribe.
 *
 * El valor que vale es el que devuelve el backend (FR-020b). Esto es una vista
 * previa, no la fuente de verdad, y por eso no se manda: el DTO de entrada no
 * tiene dónde recibirlo (M4).
 */
function calcularDiasPerdidos(fechaEvento?: string, fechaAlta?: string | null): number | null {
  if (!fechaEvento || !fechaAlta) return null;
  const evento = Date.parse(`${fechaEvento}T00:00:00Z`);
  const alta = Date.parse(`${fechaAlta}T00:00:00Z`);
  if (Number.isNaN(evento) || Number.isNaN(alta)) return null;
  // Sin topear: FR-020c pide el valor real, aunque sea negativo o mayor a 999.
  return Math.round((alta - evento) / 86_400_000);
}
