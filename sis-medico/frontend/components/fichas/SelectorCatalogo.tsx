"use client";

/**
 * Grupo y detalle de enfermedad, por código tipeado.
 *
 * El operario se sabe los códigos: los usa todos los días en la pantalla vieja,
 * donde se escriben y listo. Obligarlo a abrir una lista y buscar con el mouse
 * sería más lento que el sistema que estamos reemplazando, y ahí se cae SC-001.
 *
 * Así que el campo principal es el número. Se tipea, se resuelve solo y muestra
 * la descripción al lado para que el operario confirme que no se equivocó de
 * código. La lista está al lado, opcional, para quien no se acuerda: es un
 * `datalist`, que no roba el foco ni obliga a elegir de ahí.
 *
 * El detalle depende del grupo, así que se recarga cuando el grupo cambia
 * (FR-010). La validación real de que el detalle pertenece al grupo la hace el
 * backend al guardar: acá solo se ofrecen los que corresponden.
 */

import { useEffect, useState } from "react";
import { useFormContext, useWatch } from "react-hook-form";

import { traerDetalles, traerGrupos } from "@/lib/api";
import type { DetalleEnfermedad, FormularioFicha, GrupoEnfermedad } from "@/lib/esquemas";

type Props = {
  ordenGrupo: number;
  ordenDetalle: number;
  violacionGrupo?: string;
  violacionDetalle?: string;
};

export function SelectorCatalogo({
  ordenGrupo,
  ordenDetalle,
  violacionGrupo,
  violacionDetalle,
}: Props) {
  const { register, control } = useFormContext<FormularioFicha>();

  const [grupos, setGrupos] = useState<GrupoEnfermedad[]>([]);
  const [detalles, setDetalles] = useState<DetalleEnfermedad[]>([]);

  const grupoElegido = useWatch({ control, name: "grupoEnfermedad" });
  const detalleElegido = useWatch({ control, name: "detalleEnfermedad" });

  // Los grupos se cargan una vez al abrir la pantalla: son pocos y estables.
  useEffect(() => {
    void traerGrupos().then(setGrupos).catch(() => setGrupos([]));
  }, []);

  useEffect(() => {
    if (!grupoElegido) {
      setDetalles([]);
      return;
    }
    void traerDetalles(grupoElegido)
      .then(setDetalles)
      .catch(() => setDetalles([]));
  }, [grupoElegido]);

  const descripcionGrupo = grupos.find((g) => g.id === grupoElegido)?.descripcion;
  const descripcionDetalle = detalles.find((d) => d.id === detalleElegido)?.descripcion;

  const claseCampo = "w-32 border border-neutral-400 px-2 py-1 font-mono";

  return (
    <>
      <div className="flex flex-col gap-1">
        <label htmlFor="grupoEnfermedad" className="text-sm font-medium">
          Grupo de enfermedad
        </label>
        <div className="flex items-center gap-3">
          <input
            id="grupoEnfermedad"
            // Texto y no number: el number pone flechitas que cambian el valor
            // con la rueda del mouse, y acá un código equivocado es una ficha
            // mal clasificada.
            type="text"
            inputMode="numeric"
            autoComplete="off"
            list="lista-grupos"
            tabIndex={ordenGrupo}
            className={claseCampo}
            {...register("grupoEnfermedad", {
              setValueAs: (v) => (v === "" || v === null ? null : Number(v)),
            })}
          />
          <datalist id="lista-grupos">
            {grupos.map((grupo) => (
              <option key={grupo.id} value={grupo.id}>
                {grupo.descripcion}
              </option>
            ))}
          </datalist>
          {/* Confirmación de que el código tipeado es el que se quería. */}
          <span className="text-sm text-neutral-700" aria-live="polite">
            {descripcionGrupo ?? (grupoElegido ? "código desconocido" : "")}
          </span>
        </div>
        {violacionGrupo && (
          <p role="alert" className="text-sm text-red-700">
            {violacionGrupo}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="detalleEnfermedad" className="text-sm font-medium">
          Detalle de enfermedad
        </label>
        <div className="flex items-center gap-3">
          <input
            id="detalleEnfermedad"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            list="lista-detalles"
            tabIndex={ordenDetalle}
            className={claseCampo}
            {...register("detalleEnfermedad", {
              setValueAs: (v) => (v === "" || v === null ? null : Number(v)),
            })}
          />
          <datalist id="lista-detalles">
            {detalles.map((detalle) => (
              <option key={detalle.id} value={detalle.id}>
                {detalle.descripcion}
              </option>
            ))}
          </datalist>
          <span className="text-sm text-neutral-700" aria-live="polite">
            {descripcionDetalle ?? (detalleElegido ? "código desconocido" : "")}
          </span>
        </div>
        {violacionDetalle && (
          <p role="alert" className="text-sm text-red-700">
            {violacionDetalle}
          </p>
        )}
      </div>
    </>
  );
}
