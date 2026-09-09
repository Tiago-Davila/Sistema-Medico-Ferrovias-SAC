"use client";

/**
 * La única pantalla: cargar una ficha médica.
 *
 * ## Carga encadenada, sin sacar las manos del teclado
 *
 * El operario recibe los partes en papel y los carga uno atrás de otro, muchas
 * veces por día. Todo el diseño de esta pantalla sale de ahí:
 *
 * - Se guarda con un atajo (Ctrl+Enter), sin pasar por el mouse (FR-040).
 * - Al guardar, el formulario queda limpio y el foco vuelve al buscador de
 *   legajo (FR-041). Sin pasos intermedios de navegación.
 * - No hay un solo diálogo modal. Un modal corta la secuencia de carga y obliga
 *   a atenderlo antes de seguir.
 *
 * ## Un solo paso
 *
 * El guardado no tiene confirmación. Las advertencias vienen en la respuesta,
 * con el guardado ya hecho, y se muestran como aviso no bloqueante (M2). Una
 * confirmación en dos pasos exigiría un modal o un segundo viaje, y las dos
 * cosas pelean contra SC-001.
 */

import { useEffect, useRef, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import { BuscadorLegajo } from "@/components/fichas/BuscadorLegajo";
import { FormularioFicha } from "@/components/fichas/FormularioFicha";
import { ErrorDeApi, crearFicha } from "@/lib/api";
import {
  esquemaFormularioFicha,
  type Advertencia,
  type Empleado,
  type FormularioFicha as DatosDelFormulario,
  type Violacion,
} from "@/lib/esquemas";

/** FR-004b y FR-004c: los cuatro de sí/no arrancan en "no". */
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

export default function PantallaDeFichas() {
  const [empleado, setEmpleado] = useState<Empleado | null>(null);
  const [violaciones, setViolaciones] = useState<Violacion[]>([]);
  const [advertencias, setAdvertencias] = useState<Advertencia[]>([]);
  const [avisoDeGuardado, setAvisoDeGuardado] = useState<string | null>(null);
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const campoLegajo = useRef<HTMLInputElement>(null);

  const formulario = useForm<DatosDelFormulario>({
    resolver: zodResolver(esquemaFormularioFicha),
    defaultValues: FORMULARIO_VACIO as DatosDelFormulario,
    mode: "onSubmit",
  });

  async function guardar(datos: DatosDelFormulario) {
    if (!empleado || guardando) return;

    setGuardando(true);
    setViolaciones([]);
    setErrorGeneral(null);
    setAvisoDeGuardado(null);

    try {
      const respuesta = await crearFicha({ ...datos, legajo: empleado.legajo });

      // FR-041: encadenado. Formulario limpio y foco en el buscador, sin ningún
      // paso intermedio.
      formulario.reset(FORMULARIO_VACIO as DatosDelFormulario);
      setEmpleado(null);
      setAdvertencias(respuesta.advertencias ?? []);
      setAvisoDeGuardado(
        `Ficha guardada para el legajo ${respuesta.datos?.legajo}, evento del ${respuesta.datos?.fechaEvento}.`,
      );
      campoLegajo.current?.focus();
    } catch (e) {
      if (e instanceof ErrorDeApi && e.violaciones.length > 0) {
        // Todas juntas (M1). Cada una lleva su campo, y FormularioFicha las
        // reparte junto al control correspondiente.
        setViolaciones(e.violaciones);
      } else if (e instanceof ErrorDeApi && e.padronCaido) {
        setErrorGeneral(
          "El padrón de empleados no está disponible. No se pueden cargar fichas hasta que vuelva.",
        );
      } else if (e instanceof ErrorDeApi) {
        setErrorGeneral(e.message);
      } else {
        setErrorGeneral("No se pudo guardar la ficha.");
      }
    } finally {
      setGuardando(false);
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
   * Violaciones que no corresponden a ningún control del formulario, como
   * LEGAJO_INEXISTENTE. Sin esto se perderían en silencio y el operario vería un
   * rechazo sin explicación.
   */
  const camposDelFormulario = new Set(Object.keys(FORMULARIO_VACIO).concat("legajo"));
  const violacionesSueltas = violaciones.filter((v) => !camposDelFormulario.has(v.campo));

  return (
    <main className="mx-auto max-w-4xl p-6">
      <h1 className="mb-4 text-xl font-semibold">Ficha médica</h1>

      <BuscadorLegajo
        focoRef={campoLegajo}
        onEmpleadoConfirmado={(e) => {
          setEmpleado(e);
          setViolaciones([]);
          setAvisoDeGuardado(null);
          // FR-040: al resolver el legajo el foco salta SOLO a fecha del
          // evento. A ningún otro lado: es el campo que sigue en la carga.
          requestAnimationFrame(() => {
            document.getElementById("fechaEvento")?.focus();
          });
        }}
        onEmpleadoDescartado={() => setEmpleado(null)}
      />

      {/* Aviso de guardado. En línea, no modal: no corta la secuencia. */}
      {avisoDeGuardado && (
        <p role="status" className="mt-4 border-l-4 border-green-600 bg-green-50 px-3 py-2">
          {avisoDeGuardado}
        </p>
      )}

      {/* FR-019: la advertencia no bloquea. El guardado ya ocurrió. */}
      {advertencias.length > 0 && (
        <ul
          role="status"
          className="mt-2 border-l-4 border-amber-500 bg-amber-50 px-3 py-2 text-sm"
        >
          {advertencias.map((a) => (
            <li key={a.codigo}>{a.mensaje}</li>
          ))}
        </ul>
      )}

      {errorGeneral && (
        <p role="alert" className="mt-4 border-l-4 border-red-600 bg-red-50 px-3 py-2">
          {errorGeneral}
        </p>
      )}

      {violacionesSueltas.length > 0 && (
        <ul role="alert" className="mt-4 border-l-4 border-red-600 bg-red-50 px-3 py-2 text-sm">
          {violacionesSueltas.map((v) => (
            <li key={v.codigo}>{v.mensaje}</li>
          ))}
        </ul>
      )}

      <FormProvider {...formulario}>
        <form
          className="mt-6"
          // El handler se arma dentro del callback y no durante el render:
          // handleSubmit toca refs internas de react-hook-form.
          onSubmit={(e) => void formulario.handleSubmit(guardar)(e)}
          // Enter en un campo suelto no guarda: en esta pantalla Enter avanza.
          // Guardar es Ctrl+Enter o el botón.
          onKeyDown={(e) => {
            if (e.key === "Enter" && !(e.ctrlKey || e.metaKey)) {
              const destino = e.target as HTMLElement;
              if (destino.tagName !== "TEXTAREA") {
                e.preventDefault();
              }
            }
          }}
        >
          <FormularioFicha violaciones={violaciones} deshabilitado={!empleado || guardando} />

          <div className="mt-6 flex items-center gap-4">
            <button
              type="submit"
              tabIndex={15}
              disabled={!empleado || guardando}
              className="border border-neutral-800 bg-neutral-800 px-4 py-2 text-white disabled:opacity-40"
            >
              {guardando ? "Guardando…" : "Guardar"}
            </button>
            <span className="text-sm text-neutral-600">
              Ctrl+Enter para guardar. Al guardar, el foco vuelve al legajo.
            </span>
          </div>
        </form>
      </FormProvider>
    </main>
  );
}
