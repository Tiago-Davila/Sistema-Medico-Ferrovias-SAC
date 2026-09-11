"use client";

/**
 * La confirmación de borrado de FR-038.
 *
 * ## Por qué no es un diálogo modal
 *
 * FR-038 exige confirmar antes de eliminar y el diseño de esta pantalla prohíbe los modales. Las
 * dos cosas parecen incompatibles y no lo son: lo que hace daño no es preguntar, es el diálogo
 * que se planta encima de todo, roba el foco y obliga a atenderlo antes de seguir. En una
 * pantalla de carga encadenada eso corta la secuencia y se lleva puesto SC-001.
 *
 * Esto es una fila más de la lista, en el lugar exacto de la ficha que se va a eliminar. Se opera
 * entera con el teclado: el foco entra en "Eliminar", Tab pasa a "Cancelar", Escape cancela sin
 * tocar nada. Si el operario se va a otra parte de la pantalla, la fila se queda ahí sin
 * bloquearlo.
 *
 * ## El foco arranca en el botón destructivo, a propósito
 *
 * Porque el operario llegó hasta acá pidiendo eliminar: Enter confirma lo que ya pidió, y no lo
 * obliga a tabular para completar su propia decisión. Escape sigue estando a una tecla.
 */

import { useEffect, useRef } from "react";

type Props = {
  mensaje: string;
  onConfirmar: () => void;
  onCancelar: () => void;
  trabajando?: boolean;
};

export function ConfirmacionEnLinea({ mensaje, onConfirmar, onCancelar, trabajando }: Props) {
  const confirmar = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmar.current?.focus();
  }, []);

  return (
    <div
      role="alertdialog"
      aria-label="Confirmar eliminación"
      className="flex flex-wrap items-center gap-3 bg-red-50 px-4 py-3"
      onKeyDown={(e) => {
        if (e.key === "Escape") {
          e.preventDefault();
          // Escape no elimina nada y no deja la fila a medias: vuelve al listado como estaba.
          onCancelar();
        }
      }}
    >
      <span className="text-sm">{mensaje}</span>

      <button
        ref={confirmar}
        type="button"
        disabled={trabajando}
        onClick={onConfirmar}
        className="rounded-lg bg-red-700 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-red-800 disabled:opacity-40"
      >
        {trabajando ? "Eliminando…" : "Eliminar"}
      </button>

      <button
        type="button"
        disabled={trabajando}
        onClick={onCancelar}
        className="rounded-lg border border-line bg-white px-3 py-1.5 text-sm font-medium text-ink"
      >
        Cancelar
      </button>

      <span className="text-xs text-muted">Escape cancela.</span>
    </div>
  );
}
