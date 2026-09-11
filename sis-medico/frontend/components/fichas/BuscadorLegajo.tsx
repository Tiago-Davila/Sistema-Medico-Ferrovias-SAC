"use client";

/**
 * Primer paso del flujo: el operario tipea un legajo y confirma la identidad
 * (FR-001, US1-1, US1-2).
 *
 * Es el campo que recibe el foco al abrir la pantalla y al que vuelve después de
 * cada guardado (FR-041), porque la carga es encadenada: el operario tiene una
 * pila de partes en papel y va uno atrás de otro.
 */

import { useEffect, useRef, useState } from "react";

import { ErrorDeApi, buscarEmpleado } from "@/lib/api";
import type { Empleado } from "@/lib/esquemas";

type Props = {
  /** Se llama cuando el legajo se resolvió contra el padrón. */
  onEmpleadoConfirmado: (empleado: Empleado) => void;
  /** Se llama cuando el operario borra o cambia el legajo ya resuelto. */
  onEmpleadoDescartado: () => void;
  /** La pantalla lo usa para devolver el foco acá tras guardar (FR-041). */
  focoRef?: React.RefObject<HTMLInputElement | null>;
};

export function BuscadorLegajo({
  onEmpleadoConfirmado,
  onEmpleadoDescartado,
  focoRef,
}: Props) {
  const [texto, setTexto] = useState("");
  const [empleado, setEmpleado] = useState<Empleado | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(false);
  const propio = useRef<HTMLInputElement>(null);
  const campo = focoRef ?? propio;

  useEffect(() => {
    campo.current?.focus();
    // Solo al montar: es el foco inicial de la pantalla.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function resolver() {
    // FR-004f: el legajo es un entero y los ceros a la izquierda no significan
    // nada. 000482 y 482 son el mismo empleado, así que parsear alcanza.
    const legajo = Number.parseInt(texto, 10);
    if (!Number.isFinite(legajo) || legajo <= 0) {
      return;
    }

    setBuscando(true);
    setError(null);
    try {
      const encontrado = await buscarEmpleado(legajo);
      setEmpleado(encontrado);
      onEmpleadoConfirmado(encontrado);
    } catch (e) {
      setEmpleado(null);
      onEmpleadoDescartado();
      if (e instanceof ErrorDeApi && e.noExiste) {
        // US1-2 y FR-002: se informa y no se habilita la carga.
        setError(`El legajo ${legajo} no existe en el padrón.`);
      } else if (e instanceof ErrorDeApi && e.padronCaido) {
        // El operario tiene que saber que el problema no es el legajo que
        // tipeó, sino que el padrón no está respondiendo.
        setError(
          "El padrón de empleados no está disponible. No se pueden cargar fichas nuevas hasta que vuelva.",
        );
      } else {
        setError("No se pudo consultar el padrón.");
      }
    } finally {
      setBuscando(false);
    }
  }

  return (
    <section className="rounded-2xl border border-line bg-surface-muted p-4 sm:p-5">
      <div className="mb-4 flex items-baseline gap-3">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand text-xs font-semibold text-white">
          1
        </span>
        <div>
          <h2 className="text-sm font-semibold text-ink">Identificar empleado</h2>
          <p className="text-xs text-muted">Ingresá el legajo para comenzar una ficha.</p>
        </div>
      </div>
      <div className="flex flex-wrap items-end gap-x-4 gap-y-3">
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-muted">Legajo</span>
          <input
            ref={campo}
            // Primero en el orden de tabulación: es donde arranca la carga.
            tabIndex={1}
            inputMode="numeric"
            autoComplete="off"
            className="w-40 rounded-lg border border-line bg-white px-3 py-2 font-mono text-lg font-medium text-ink shadow-sm transition-colors placeholder:text-muted focus:border-brand"
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value);
              if (empleado) {
                // Cambiar el legajo invalida la identidad confirmada. Dejarla en
                // pantalla mientras se tipea otro número haría que el operario
                // cargue contra el empleado equivocado.
                setEmpleado(null);
                onEmpleadoDescartado();
              }
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                // Sin submit: en esta pantalla Enter avanza, no guarda.
                e.preventDefault();
                void resolver();
              }
            }}
            onBlur={() => {
              if (!empleado && texto.trim() !== "") {
                void resolver();
              }
            }}
            aria-invalid={error !== null}
            aria-describedby={error ? "error-legajo" : undefined}
          />
        </label>

        <div className="min-h-11 pb-1 pt-1 text-lg" aria-live="polite">
          {buscando && <span className="text-sm text-muted">Buscando en el padrón…</span>}

          {empleado && (
            <span className="block">
              <strong>
                {empleado.apellido}, {empleado.nombre}
              </strong>
              <span className="mt-0.5 block text-sm font-normal text-muted sm:ml-3 sm:inline">
                {empleado.seccion} <span aria-hidden="true">·</span> {empleado.categoriaLaboral}
              </span>
            </span>
          )}
        </div>
      </div>

      {error && (
        <p id="error-legajo" role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
        </p>
      )}
    </section>
  );
}
