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

import {
  MAXIMO_OBSERVACIONES,
  explicarMotivo,
  type FormularioFicha,
  type Violacion,
} from "@/lib/esquemas";

import { SelectorCatalogo } from "./SelectorCatalogo";

type Props = {
  /** Violaciones del último 422, para repartirlas junto a cada control (T050). */
  violaciones: Violacion[];
  deshabilitado: boolean;
  /**
   * Por qué la ficha abierta quedó señalada (FR-029). Vacío en una ficha nueva.
   *
   * Es un aviso, no un rechazo: la ficha se abre, se recorre y se puede dejar como está. Recién
   * al guardar el backend exige corregir lo que hoy es obligatorio (FR-031).
   */
  motivosInconsistencia?: string[];
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

export function FormularioFicha({
  violaciones,
  deshabilitado,
  motivosInconsistencia = [],
}: Props) {
  const { register, control, setValue, formState } = useFormContext<FormularioFicha>();

  const estado = useWatch({ control, name: "estadoPaciente" });
  const inItinere = useWatch({ control, name: "inItinere" });
  const fechaEvento = useWatch({ control, name: "fechaEvento" });
  const fechaAlta = useWatch({ control, name: "fechaAlta" });
  const observaciones = useWatch({ control, name: "observaciones" });

  // Se miran para poder distinguir "no" de "sin responder" (FR-004d). Un select que no tiene
  // opción para el valor actual muestra la primera, y ahí "sin responder" se leería como "no".
  const estabaEnServicio = useWatch({ control, name: "estabaEnServicio" });
  const atendidoServicioMedico = useWatch({ control, name: "atendidoServicioMedico" });
  const envioMedicoDomicilio = useWatch({ control, name: "envioMedicoDomicilio" });
  const justificado = useWatch({ control, name: "justificado" });

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

  /**
   * El mensaje a mostrar junto a un control.
   *
   * Gana el del servidor: es el que decide si la ficha se guarda (FR-018). El
   * del formulario solo aparece cuando el envío ni siquiera llegó a salir.
   */
  function mensajeDe(campo: keyof FormularioFicha): string | undefined {
    return violacionDe(campo)?.mensaje ?? (formState.errors[campo]?.message as string | undefined);
  }

  const claseCampo =
    "w-full rounded-lg border border-line bg-white px-3 py-2 text-sm text-ink shadow-sm transition-colors focus:border-brand disabled:bg-slate-100 disabled:text-muted";

  return (
    <fieldset disabled={deshabilitado} className="grid grid-cols-1 gap-x-8 gap-y-4 md:grid-cols-2">
      {/* FR-029: se señala, no se bloquea. La ficha está abierta y se puede recorrer igual. */}
      {motivosInconsistencia.length > 0 && (
        <div
          role="status"
          className="col-span-full rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950"
        >
          <strong>Esta ficha viene del sistema anterior y está incompleta.</strong>
          <ul className="mt-1 list-disc pl-5">
            {motivosInconsistencia.map((motivo) => (
              <li key={motivo}>{explicarMotivo(motivo)}</li>
            ))}
          </ul>
          <p className="mt-1 text-neutral-700">
            Se puede consultar tal como está. Si la guardás, el sistema va a pedirte que completes
            lo que hoy es obligatorio.
          </p>
        </div>
      )}

      <CabeceraDeSeccion numero="2" titulo="Datos del evento" descripcion="Información principal de la ficha." />

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
        <ErrorDeCampo mensaje={mensajeDe("fechaEvento")} />
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
          {/* Solo si la ficha importada no trae estado. En una ficha nueva no existe: FR-005
              admite exactamente dos valores y elegir uno es obligatorio. */}
          {!estado && <option value="">(sin definir)</option>}
          {/* FR-005: exactamente dos valores. */}
          <option value="ACCIDENTADO">Accidentado</option>
          <option value="ENFERMEDAD">Enfermedad</option>
        </select>
        <ErrorDeCampo mensaje={mensajeDe("estadoPaciente")} />
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
        <ErrorDeCampo mensaje={mensajeDe("inItinere")} />
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
        <ErrorDeCampo mensaje={mensajeDe("horaAccidente")} />
      </div>

      <CabeceraDeSeccion numero="3" titulo="Situación y atención" descripcion="Marcá las condiciones que correspondan." />

      {/* ------------------------------------------------- Sí o no */}
      <CampoSiNo
        id="estabaEnServicio"
        etiqueta="Estaba en servicio"
        orden={ORDEN.estabaEnServicio}
        valor={estabaEnServicio}
        mensaje={mensajeDe("estabaEnServicio")}
        // FR-024: lo fija in itinere y deja de ser editable.
        deshabilitado={inItinere === true}
        registro={register("estabaEnServicio", { setValueAs: aSiNo })}
      />

      <CampoSiNo
        id="atendidoServicioMedico"
        etiqueta="Atendido por servicio médico"
        orden={ORDEN.atendidoServicioMedico}
        valor={atendidoServicioMedico}
        mensaje={mensajeDe("atendidoServicioMedico")}
        registro={register("atendidoServicioMedico", { setValueAs: aSiNo })}
      />

      <CampoSiNo
        id="envioMedicoDomicilio"
        etiqueta="Envío de médico a domicilio"
        orden={ORDEN.envioMedicoDomicilio}
        valor={envioMedicoDomicilio}
        mensaje={mensajeDe("envioMedicoDomicilio")}
        // FR-023: con accidente no aplica y queda en no.
        deshabilitado={esAccidente}
        registro={register("envioMedicoDomicilio", { setValueAs: aSiNo })}
      />

      <CampoSiNo
        id="justificado"
        etiqueta="Justificado"
        orden={ORDEN.justificado}
        valor={justificado}
        mensaje={mensajeDe("justificado")}
        registro={register("justificado", { setValueAs: aSiNo })}
      />

      <CabeceraDeSeccion numero="4" titulo="Fechas y clasificación" descripcion="Los días perdidos se calculan automáticamente." />

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
        <ErrorDeCampo mensaje={mensajeDe("fechaCitacion")} />
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
        <ErrorDeCampo mensaje={mensajeDe("fechaAlta")} />
      </div>

      <div className="flex flex-col gap-1">
        <span className="text-sm font-medium">Días perdidos</span>
        {/* FR-006: nunca editable. Sin tabIndex: el foco no se detiene en un
            valor que no se puede tocar. */}
        <output className="rounded-lg border border-dashed border-line bg-surface-muted px-3 py-2 font-mono text-sm text-ink">
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
      <div className="col-span-full flex flex-col gap-1 pt-1">
        <label htmlFor="observaciones" className="text-sm font-medium">
          Observaciones
        </label>
        <textarea
          id="observaciones"
          rows={3}
          tabIndex={ORDEN.observaciones}
          className={`${claseCampo} min-h-28 resize-y`}
          {...register("observaciones", { setValueAs: (v) => (v === "" ? null : v) })}
        />
        {/* FR-006b: contador visible mientras se escribe. */}
        <span
          className={
            largoObservaciones > MAXIMO_OBSERVACIONES
              ? "text-sm text-red-700"
              : "text-sm text-muted"
          }
        >
          {largoObservaciones} / {MAXIMO_OBSERVACIONES}
        </span>
        <ErrorDeCampo mensaje={mensajeDe("observaciones")} />
      </div>
    </fieldset>
  );
}

function CabeceraDeSeccion({
  numero,
  titulo,
  descripcion,
}: {
  numero: string;
  titulo: string;
  descripcion: string;
}) {
  return (
    <div className="col-span-full mt-3 flex items-center gap-3 border-t border-line pt-5 first:mt-0 first:border-t-0 first:pt-0">
      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-800 text-xs font-semibold text-white">
        {numero}
      </span>
      <div>
        <h2 className="text-sm font-semibold text-ink">{titulo}</h2>
        <p className="text-xs text-muted">{descripcion}</p>
      </div>
    </div>
  );
}

/**
 * El renglón de error de un control.
 *
 * Definido a nivel de módulo y no dentro de FormularioFicha: un componente
 * creado durante el render es un tipo nuevo en cada pasada, y React desmonta y
 * vuelve a montar el subárbol. En un formulario eso se ve como el foco
 * perdiéndose mientras el operario escribe.
 */
function ErrorDeCampo({ mensaje }: { mensaje?: string }) {
  if (!mensaje) return null;
  return (
    <p role="alert" className="text-sm text-red-700">
      {mensaje}
    </p>
  );
}

/**
 * Lo que el select de sí/no devuelve al formulario.
 *
 * La cadena vacía es "sin responder" y se traduce a null, no a false. Es la diferencia que
 * FR-004d obliga a mantener: una ficha histórica sin responder no es una ficha que respondió que
 * no, y el validador del backend las trata distinto.
 */
function aSiNo(valor: unknown): boolean | null {
  if (valor === "" || valor === null || valor === undefined) return null;
  return valor === true || valor === "true";
}

/**
 * Un campo de sí o no de FR-004c.
 *
 * Va como select y no como checkbox a propósito: un checkbox no distingue "no" de "sin
 * responder", y FR-004d exige que se distingan.
 *
 * La opción "(sin responder)" aparece **solo** cuando el valor actual es null, que es como llegan
 * los campos de una ficha importada. En una ficha nueva no está: el operario elige entre sí y no,
 * que son las dos únicas respuestas que el sistema acepta al guardar.
 */
function CampoSiNo({
  id,
  etiqueta,
  orden,
  registro,
  valor,
  mensaje,
  deshabilitado = false,
}: {
  id: string;
  etiqueta: string;
  orden: number;
  registro: ReturnType<ReturnType<typeof useFormContext<FormularioFicha>>["register"]>;
  valor?: boolean | null;
  mensaje?: string;
  deshabilitado?: boolean;
}) {
  const sinResponder = valor === null || valor === undefined;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium">
        {etiqueta}
      </label>
      <select
        id={id}
        tabIndex={orden}
        disabled={deshabilitado}
        aria-invalid={mensaje !== undefined}
        className={`w-full rounded-lg border px-3 py-2 text-sm shadow-sm transition-colors focus:border-brand disabled:bg-slate-100 disabled:text-muted ${
          sinResponder ? "border-amber-500 bg-amber-50" : "border-line bg-white"
        }`}
        {...registro}
      >
        {sinResponder && <option value="">(sin responder)</option>}
        <option value="false">No</option>
        <option value="true">Sí</option>
      </select>
      <ErrorDeCampo mensaje={mensaje} />
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
