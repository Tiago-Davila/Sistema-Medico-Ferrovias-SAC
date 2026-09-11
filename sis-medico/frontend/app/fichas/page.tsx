"use client";

/**
 * La única pantalla: cargar, consultar, editar y eliminar fichas médicas.
 *
 * ## Carga encadenada, sin sacar las manos del teclado
 *
 * El operario recibe los partes en papel y los carga uno atrás de otro, muchas veces por día.
 * Todo el diseño de esta pantalla sale de ahí:
 *
 * - Se guarda con un atajo (Ctrl+Enter), sin pasar por el mouse (FR-040).
 * - Al guardar una ficha nueva, el formulario queda limpio y el foco vuelve al buscador de
 *   legajo (FR-041). Sin pasos intermedios de navegación.
 * - No hay un solo diálogo modal, ni siquiera para confirmar una eliminación. Un modal corta la
 *   secuencia de carga y obliga a atenderlo antes de seguir; la confirmación de FR-038 es una
 *   fila en línea dentro del listado.
 *
 * ## Un solo paso
 *
 * El guardado no tiene confirmación. Las advertencias vienen en la respuesta, con el guardado ya
 * hecho, y se muestran como aviso no bloqueante (M2). Una confirmación en dos pasos exigiría un
 * modal o un segundo viaje, y las dos cosas pelean contra SC-001.
 *
 * ## Dos modos sobre el mismo formulario
 *
 * Cargar una ficha nueva y editar una existente usan los mismos controles y las mismas reglas
 * (FR-017). Lo único que cambia es a dónde va el guardado y si viaja la versión. Duplicar la
 * pantalla en una de alta y otra de edición habría duplicado también las dependencias entre
 * campos, que es donde están las reglas más fáciles de desincronizar.
 *
 * ## Lo que esta pantalla no ofrece
 *
 * Reasignar una ficha a otro legajo. La API lo permite (FR-003d) y está probado del lado del
 * backend, pero acá no hay control para hacerlo: el legajo que se manda al guardar es el de la
 * ficha abierta, nunca el que quedó tipeado en el buscador. Cambiar de empleado mientras se edita
 * y que la ficha se mude sin decir nada sería un movimiento silencioso de datos clínicos.
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import { AppHeader } from "@/components/AppHeader";
import { BuscadorLegajo } from "@/components/fichas/BuscadorLegajo";
import { FormularioFicha } from "@/components/fichas/FormularioFicha";
import { ListaFichas } from "@/components/fichas/ListaFichas";
import {
  ErrorDeApi,
  actualizarFicha,
  crearFicha,
  eliminarFicha,
  traerFicha,
  traerFichasDe,
} from "@/lib/api";
import {
  esquemaFormularioFicha,
  type Advertencia,
  type Empleado,
  type Ficha,
  type FormularioFicha as DatosDelFormulario,
  type ResumenDeFicha,
  type Violacion,
} from "@/lib/esquemas";

/** FR-004b y FR-004c: en una ficha nueva los cuatro de sí/no arrancan en "no". */
const FORMULARIO_VACIO: Partial<DatosDelFormulario> = {
  fechaEvento: "",
  estadoPaciente: "ENFERMEDAD",
  inItinere: null,
  estabaEnServicio: false,
  atendidoServicioMedico: false,
  envioMedicoDomicilio: false,
  justificado: false,
  horaAccidente: null,
  fechaCitacion: null,
  fechaAlta: null,
  observaciones: null,
};

/** La ficha abierta para editar: lo que hace falta recordar además de los campos del formulario. */
type EnEdicion = {
  id: number;
  version: number;
  motivosInconsistencia: string[];
};

/**
 * Pasa una ficha guardada a los valores del formulario.
 *
 * Intencional: no arregla nada en el camino. Un booleano en null llega como null y no como false
 * (FR-004d), un código huérfano llega con su número, y una ficha sin fecha de fin llega sin
 * ninguna de las dos. Todo eso se muestra tal cual (FR-028) y recién molesta al guardar (FR-031).
 */
function aFormulario(ficha: Ficha): Partial<DatosDelFormulario> {
  return {
    legajo: ficha.legajo,
    fechaEvento: ficha.fechaEvento ?? "",
    estadoPaciente: ficha.estadoPaciente,
    inItinere: ficha.inItinere ?? null,
    estabaEnServicio: ficha.estabaEnServicio,
    horaAccidente: ficha.horaAccidente ?? null,
    atendidoServicioMedico: ficha.atendidoServicioMedico,
    envioMedicoDomicilio: ficha.envioMedicoDomicilio,
    justificado: ficha.justificado,
    fechaCitacion: ficha.fechaCitacion ?? null,
    fechaAlta: ficha.fechaAlta ?? null,
    grupoEnfermedad: ficha.grupoEnfermedad?.id,
    detalleEnfermedad: ficha.detalleEnfermedad?.id,
    observaciones: ficha.observaciones ?? null,
  };
}

export default function PantallaDeFichas() {
  const [empleado, setEmpleado] = useState<Empleado | null>(null);
  const [fichas, setFichas] = useState<ResumenDeFicha[]>([]);
  const [cargandoFichas, setCargandoFichas] = useState(false);
  const [enEdicion, setEnEdicion] = useState<EnEdicion | null>(null);
  const [violaciones, setViolaciones] = useState<Violacion[]>([]);
  const [advertencias, setAdvertencias] = useState<Advertencia[]>([]);
  const [avisoDeGuardado, setAvisoDeGuardado] = useState<string | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [eliminando, setEliminando] = useState(false);

  const campoLegajo = useRef<HTMLInputElement>(null);

  const formulario = useForm<DatosDelFormulario>({
    resolver: zodResolver(esquemaFormularioFicha),
    defaultValues: FORMULARIO_VACIO as DatosDelFormulario,
    mode: "onSubmit",
  });

  // El legajo llega opcional porque el contrato de OpenAPI no marca ningún campo como
  // obligatorio y todo el esquema generado sale parcial. Sin legajo no hay nada que traer.
  const refrescarFichas = useCallback(async (legajo?: number) => {
    if (legajo === undefined) return;

    setCargandoFichas(true);
    try {
      setFichas(await traerFichasDe(legajo));
    } catch {
      // El listado que no carga no puede impedir cargar una ficha nueva: son dos tareas
      // distintas y la de carga es la que el operario vino a hacer.
      setFichas([]);
      setErrorGeneral("No se pudieron traer las fichas ya cargadas de este empleado.");
    } finally {
      setCargandoFichas(false);
    }
  }, []);

  function volverAFichaNueva() {
    formulario.reset(FORMULARIO_VACIO as DatosDelFormulario);
    setEnEdicion(null);
    setViolaciones([]);
  }

  async function abrir(id: number) {
    setViolaciones([]);
    setAdvertencias([]);
    setErrorGeneral(null);
    setAvisoDeGuardado(null);

    try {
      const ficha = await traerFicha(id);
      // FR-030: abrir es un GET y nada más. Ninguna validación corre acá, ni siquiera sobre una
      // ficha que no cumple una sola de las reglas de hoy.
      formulario.reset(aFormulario(ficha) as DatosDelFormulario);
      setEnEdicion({
        id,
        version: ficha.version ?? 0,
        motivosInconsistencia: ficha.motivosInconsistencia ?? [],
      });
      requestAnimationFrame(() => document.getElementById("fechaEvento")?.focus());
    } catch {
      setErrorGeneral("No se pudo abrir la ficha.");
    }
  }

  async function guardar(datos: DatosDelFormulario) {
    if (guardando) return;
    if (!enEdicion && !empleado) return;

    setGuardando(true);
    setViolaciones([]);
    setErrorGeneral(null);
    setAvisoDeGuardado(null);

    try {
      if (enEdicion) {
        const respuesta = await actualizarFicha(enEdicion.id, {
          ...datos,
          // El legajo de la ficha abierta, no el del buscador: esta pantalla no muda fichas.
          legajo: formulario.getValues("legajo"),
          version: enEdicion.version,
        });

        setAdvertencias(respuesta.advertencias ?? []);
        setAvisoDeGuardado(
          `Ficha del ${respuesta.datos?.fechaEvento} guardada. Días perdidos: ${
            respuesta.datos?.diasPerdidos ?? "—"
          }.`,
        );

        // Ya no queda nada incompleto: acaba de pasar todas las validaciones.
        volverAFichaNueva();
        const legajo = respuesta.datos?.legajo ?? empleado?.legajo;
        if (legajo !== undefined) await refrescarFichas(legajo);
      } else {
        const respuesta = await crearFicha({ ...datos, legajo: empleado!.legajo });

        setAdvertencias(respuesta.advertencias ?? []);
        setAvisoDeGuardado(
          `Ficha guardada para el legajo ${respuesta.datos?.legajo}, evento del ${respuesta.datos?.fechaEvento}.`,
        );

        // FR-041: encadenado. Formulario limpio, empleado descartado y foco en el buscador, sin
        // ningún paso intermedio: el siguiente parte de la pila es de otro empleado.
        volverAFichaNueva();
        setEmpleado(null);
        setFichas([]);
        campoLegajo.current?.focus();
      }
    } catch (e) {
      if (e instanceof ErrorDeApi && e.violaciones.length > 0) {
        // Todas juntas (M1). Cada una lleva su campo, y FormularioFicha las reparte junto al
        // control correspondiente.
        setViolaciones(e.violaciones);
      } else if (e instanceof ErrorDeApi && e.esConflicto) {
        // FR-037. No se recarga la ficha sola: eso borraría lo que el operario acaba de tipear
        // sin preguntarle. Lo que hay en pantalla es suyo hasta que decida qué hacer.
        setErrorGeneral(
          "Otro operario guardó cambios sobre esta ficha mientras la editabas. Tus cambios no se " +
            "aplicaron y los de él no se pisaron. Abrila de nuevo en la lista para ver cómo quedó.",
        );
      } else if (e instanceof ErrorDeApi && e.padronCaido) {
        setErrorGeneral(
          "El padrón de empleados no está disponible. No se pueden cargar ni editar fichas hasta " +
            "que vuelva; las ya cargadas se siguen pudiendo consultar.",
        );
      } else if (e instanceof ErrorDeApi && e.noExiste) {
        setErrorGeneral("Esta ficha ya no existe. Puede haberla eliminado otro operario.");
        volverAFichaNueva();
        if (empleado) await refrescarFichas(empleado.legajo);
      } else if (e instanceof ErrorDeApi) {
        setErrorGeneral(e.message);
      } else {
        setErrorGeneral("No se pudo guardar la ficha.");
      }
    } finally {
      setGuardando(false);
    }
  }

  async function eliminar(ficha: ResumenDeFicha) {
    if (ficha.id === undefined || eliminando) return;

    setEliminando(true);
    setErrorGeneral(null);
    try {
      // La versión sale de la ficha abierta si es la misma; si no, se busca antes de borrar, para
      // que una baja tampoco pise un cambio ajeno.
      const version =
        enEdicion?.id === ficha.id ? enEdicion.version : (await traerFicha(ficha.id)).version ?? 0;

      await eliminarFicha(ficha.id, version);

      if (enEdicion?.id === ficha.id) volverAFichaNueva();
      setAvisoDeGuardado("Ficha eliminada.");
      if (empleado) await refrescarFichas(empleado.legajo);
    } catch (e) {
      if (e instanceof ErrorDeApi && e.esConflicto) {
        setErrorGeneral(
          "No se eliminó: otro operario modificó esta ficha recién. Abrila para ver cómo quedó " +
            "antes de decidir.",
        );
      } else if (e instanceof ErrorDeApi && e.noExiste) {
        setErrorGeneral("Esta ficha ya no existe.");
        if (empleado) await refrescarFichas(empleado.legajo);
      } else {
        setErrorGeneral("No se pudo eliminar la ficha.");
      }
    } finally {
      setEliminando(false);
    }
  }

  // FR-040: guardar sin tocar el mouse, desde cualquier campo del formulario.
  useEffect(() => {
    function atajo(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
        e.preventDefault();
        void formulario.handleSubmit(guardar)();
      }
    }
    window.addEventListener("keydown", atajo);
    return () => window.removeEventListener("keydown", atajo);
  });

  /**
   * Violaciones que no corresponden a ningún control del formulario, como LEGAJO_INEXISTENTE o la
   * versión faltante. Sin esto se perderían en silencio y el operario vería un rechazo sin
   * explicación.
   */
  const camposDelFormulario = new Set(Object.keys(FORMULARIO_VACIO).concat("legajo"));
  const violacionesSueltas = violaciones.filter((v) => !camposDelFormulario.has(v.campo));

  const puedeEditar = enEdicion !== null || empleado !== null;

  return (
    <>
      <AppHeader />
      <main className="mx-auto w-full max-w-6xl flex-1 px-5 py-6 sm:px-8 sm:py-10">
        <div className="overflow-hidden rounded-2xl border border-line bg-surface shadow-[0_18px_48px_rgba(37,53,76,0.09)]">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-line bg-surface-muted px-5 py-3 text-xs text-muted sm:px-7">
            <span className="font-medium text-ink">Carga guiada</span>
            <span>Legajo → datos del evento → clasificación</span>
            <span className="ml-auto rounded-full bg-brand-soft px-2.5 py-1 font-medium text-brand">
              Ctrl + Enter para guardar
            </span>
          </div>
          <div className="p-5 sm:p-7">
          <BuscadorLegajo
            focoRef={campoLegajo}
            onEmpleadoConfirmado={(e) => {
              setEmpleado(e);
              volverAFichaNueva();
              setAvisoDeGuardado(null);
              setErrorGeneral(null);
              void refrescarFichas(e.legajo);
              // FR-040: al resolver el legajo el foco salta SOLO a fecha del evento. A ningún
              // otro lado: es el campo que sigue en la carga.
              requestAnimationFrame(() => {
                document.getElementById("fechaEvento")?.focus();
              });
            }}
            onEmpleadoDescartado={() => {
              setEmpleado(null);
              setFichas([]);
              volverAFichaNueva();
            }}
          />

          {empleado && (
            <ListaFichas
              fichas={fichas}
              cargando={cargandoFichas}
              abiertaId={enEdicion?.id ?? null}
              onAbrir={(id) => void abrir(id)}
              onEliminar={(ficha) => void eliminar(ficha)}
              eliminando={eliminando}
              // Después del botón de guardar: consultar y cargar son dos tareas distintas (FR-040).
              orden={16}
            />
          )}

          {/* Aviso de guardado. En línea, no modal: no corta la secuencia. */}
          {avisoDeGuardado && (
            <p role="status" className="mt-5 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
              {avisoDeGuardado}
            </p>
          )}

          {/* FR-019: la advertencia no bloquea. El guardado ya ocurrió. */}
          {advertencias.length > 0 && (
            <ul
              role="status"
              className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950"
            >
              {advertencias.map((a) => (
                <li key={a.codigo}>{a.mensaje}</li>
              ))}
            </ul>
          )}

          {errorGeneral && (
            <p role="alert" className="mt-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
              {errorGeneral}
            </p>
          )}

          {violacionesSueltas.length > 0 && (
            <ul
              role="alert"
              className="mt-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900"
            >
              {violacionesSueltas.map((v) => (
                <li key={v.codigo}>{v.mensaje}</li>
              ))}
            </ul>
          )}

          <FormProvider {...formulario}>
            <form
              className="mt-7"
              // El handler se arma dentro del callback y no durante el render: handleSubmit toca
              // refs internas de react-hook-form.
              onSubmit={(e) => void formulario.handleSubmit(guardar)(e)}
              // Enter en un campo suelto no guarda: en esta pantalla Enter avanza. Guardar es
              // Ctrl+Enter o el botón.
              onKeyDown={(e) => {
                if (e.key === "Enter" && !(e.ctrlKey || e.metaKey)) {
                  const destino = e.target as HTMLElement;
                  if (destino.tagName !== "TEXTAREA") {
                    e.preventDefault();
                  }
                }
              }}
            >
              <FormularioFicha
                violaciones={violaciones}
                deshabilitado={!puedeEditar || guardando}
                motivosInconsistencia={enEdicion?.motivosInconsistencia}
              />

              <div className="mt-8 flex flex-wrap items-center gap-x-4 gap-y-3 border-t border-line pt-5">
                <button
                  type="submit"
                  tabIndex={15}
                  disabled={!puedeEditar || guardando}
                  className="rounded-lg bg-brand px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-hover disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {guardando ? "Guardando…" : enEdicion ? "Guardar cambios" : "Guardar"}
                </button>

                {enEdicion && (
                  <button
                    type="button"
                    tabIndex={17}
                    onClick={() => {
                      volverAFichaNueva();
                      setAvisoDeGuardado(null);
                      requestAnimationFrame(() =>
                        document.getElementById("fechaEvento")?.focus(),
                      );
                    }}
                    className="rounded-lg border border-line bg-white px-4 py-2.5 text-sm font-medium text-ink transition-colors hover:border-brand hover:text-brand"
                  >
                    Cargar una ficha nueva
                  </button>
                )}

                <span className="text-sm text-muted">
                  {enEdicion
                    ? "Ctrl+Enter para guardar los cambios."
                    : "Ctrl+Enter para guardar. Al guardar, el foco vuelve al legajo."}
                </span>
              </div>
            </form>
          </FormProvider>
          </div>
        </div>
      </main>
    </>
  );
}
