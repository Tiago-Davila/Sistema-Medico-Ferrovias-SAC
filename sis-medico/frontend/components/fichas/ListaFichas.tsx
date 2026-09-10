"use client";

/**
 * Las fichas ya cargadas del empleado (US2-1).
 *
 * ## Navegación por teclado
 *
 * La lista es un `listbox` con foco itinerante: un solo elemento de la lista entra en el orden de
 * tabulación y adentro se recorre con las flechas, Inicio y Fin. Enter o Espacio abren la ficha
 * enfocada. Poner cada fila en el orden de tabulación obligaría al operario a pasar por las
 * treinta fichas de un empleado con historia larga para llegar al formulario.
 *
 * Va **después** del botón de guardar en el orden de tabulación. Consultar y cargar son dos
 * tareas distintas: quien está cargando fichas nuevas de una pila de partes no tiene que
 * tropezarse con el histórico en el camino (FR-040).
 *
 * ## Navegar no exige completar nada
 *
 * Moverse entre fichas son puros GET (US2-3). Ninguna ficha, por incompleta que esté, puede
 * impedir abrir la de al lado: la validación corre al guardar y solo al guardar (FR-030).
 *
 * ## La marca de incompleta
 *
 * Viaja en el listado y no solo al abrir la ficha (FR-029). Si estuviera solo adentro, el
 * operario tendría que abrirlas una por una para descubrir cuál necesita atención, y una ficha
 * sin fecha de fin bloquea todo el legajo hasta que alguien la complete.
 */

import { useRef, useState } from "react";

import type { ResumenDeFicha } from "@/lib/esquemas";

import { ConfirmacionEnLinea } from "./ConfirmacionEnLinea";

type Props = {
  fichas: ResumenDeFicha[];
  cargando: boolean;
  /** Id de la ficha abierta en el formulario, para marcarla en la lista. */
  abiertaId: number | null;
  onAbrir: (id: number) => void;
  onEliminar: (ficha: ResumenDeFicha) => void;
  eliminando: boolean;
  /** Orden de tabulación: después del botón de guardar. */
  orden: number;
};

/** "2026-03-09" -> "09/03/2026". Sin Date: parsear una fecha sin hora corre el día por zona. */
function comoFecha(iso?: string | null): string {
  if (!iso) return "—";
  const [anio, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${anio}`;
}

const ESTADOS: Record<string, string> = {
  ACCIDENTADO: "Accidentado",
  ENFERMEDAD: "Enfermedad",
};

export function ListaFichas({
  fichas,
  cargando,
  abiertaId,
  onAbrir,
  onEliminar,
  eliminando,
  orden,
}: Props) {
  const [enfocada, setEnfocada] = useState(0);
  const [aConfirmar, setAConfirmar] = useState<number | null>(null);
  const filas = useRef<(HTMLLIElement | null)[]>([]);

  function mover(destino: number) {
    const acotado = Math.max(0, Math.min(destino, fichas.length - 1));
    setEnfocada(acotado);
    filas.current[acotado]?.focus();
  }

  function alTeclear(e: React.KeyboardEvent<HTMLUListElement>) {
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        mover(enfocada + 1);
        break;
      case "ArrowUp":
        e.preventDefault();
        mover(enfocada - 1);
        break;
      case "Home":
        e.preventDefault();
        mover(0);
        break;
      case "End":
        e.preventDefault();
        mover(fichas.length - 1);
        break;
      case "Enter":
      case " ":
        e.preventDefault();
        if (fichas[enfocada]?.id !== undefined) {
          onAbrir(fichas[enfocada].id!);
        }
        break;
      default:
        break;
    }
  }

  if (cargando) {
    return (
      <p role="status" className="mt-4 text-sm text-neutral-600">
        Buscando fichas…
      </p>
    );
  }

  // US2-4: dicho con todas las letras. Una lista vacía sin texto se lee como "todavía está
  // cargando" o como "algo falló", y el operario no sabe si puede cargar la ficha nueva.
  if (fichas.length === 0) {
    return (
      <p role="status" className="mt-4 border-l-4 border-neutral-400 bg-neutral-50 px-3 py-2 text-sm">
        Este empleado no tiene ninguna ficha cargada.
      </p>
    );
  }

  return (
    <section className="mt-4">
      <h2 className="text-sm font-medium">
        Fichas cargadas ({fichas.length})
        <span className="ml-3 font-normal text-neutral-600">
          Flechas para recorrer, Enter para abrir.
        </span>
      </h2>

      <ul
        role="listbox"
        aria-label="Fichas del empleado"
        tabIndex={-1}
        onKeyDown={alTeclear}
        className="mt-2 divide-y divide-neutral-200 border border-neutral-300"
      >
        {fichas.map((ficha, indice) => {
          if (aConfirmar === ficha.id) {
            return (
              <li key={ficha.id}>
                <ConfirmacionEnLinea
                  mensaje={`¿Eliminar la ficha del ${comoFecha(ficha.fechaEvento)}?`}
                  trabajando={eliminando}
                  onConfirmar={() => onEliminar(ficha)}
                  onCancelar={() => {
                    setAConfirmar(null);
                    mover(indice);
                  }}
                />
              </li>
            );
          }

          return (
            <li
              key={ficha.id}
              ref={(nodo) => {
                filas.current[indice] = nodo;
              }}
              role="option"
              aria-selected={ficha.id === abiertaId}
              // Foco itinerante: una sola fila entra en el orden de tabulación.
              tabIndex={indice === enfocada ? orden : -1}
              onFocus={() => setEnfocada(indice)}
              onClick={() => ficha.id !== undefined && onAbrir(ficha.id)}
              className={`flex flex-wrap items-center gap-x-4 gap-y-1 px-3 py-2 text-sm ${
                ficha.id === abiertaId ? "bg-neutral-800 text-white" : "hover:bg-neutral-100"
              }`}
            >
              <span className="w-24 font-mono">{comoFecha(ficha.fechaEvento)}</span>

              <span className="w-28">
                {ficha.estadoPaciente ? ESTADOS[ficha.estadoPaciente] : "—"}
              </span>

              <span className="w-40">
                {ficha.fechaAlta
                  ? `Alta ${comoFecha(ficha.fechaAlta)}`
                  : ficha.fechaCitacion
                    ? `Citación ${comoFecha(ficha.fechaCitacion)}`
                    : "Sin fecha de fin"}
              </span>

              {/* FR-020c: el valor real, aunque sea negativo. Topearlo escondería el dato roto. */}
              <span className="w-28 font-mono">
                {ficha.diasPerdidos === undefined || ficha.diasPerdidos === null
                  ? "—"
                  : `${ficha.diasPerdidos} días`}
              </span>

              {/* FR-029: la marca no impide nada, solo avisa. */}
              {ficha.incompleta && (
                <span
                  className={`border px-1 text-xs ${
                    ficha.id === abiertaId
                      ? "border-amber-300 text-amber-200"
                      : "border-amber-600 bg-amber-50 text-amber-800"
                  }`}
                >
                  incompleta
                </span>
              )}

              <button
                type="button"
                // Fuera del orden de tabulación general: se llega por la fila, no tabulando por
                // un botón de eliminar en cada una de las treinta fichas del empleado.
                tabIndex={-1}
                onClick={(e) => {
                  e.stopPropagation();
                  setAConfirmar(ficha.id ?? null);
                }}
                className={`ml-auto border px-2 py-0.5 text-xs ${
                  ficha.id === abiertaId ? "border-neutral-400" : "border-neutral-400"
                }`}
              >
                Eliminar
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
