/**
 * US4-1 y FR-038: antes de eliminar, el sistema pide confirmación.
 *
 * Es el único criterio de aceptación de la feature que no se puede verificar desde el backend: la
 * API elimina cuando se lo piden y no tiene forma de saber si alguien preguntó antes. La
 * confirmación existe solo acá, así que acá se prueba.
 *
 * Lo que se verifica no es únicamente que pregunte, sino **cómo** pregunta: que se opere entera
 * con el teclado y que no sea un diálogo modal. Las dos cosas están en tensión —FR-038 exige
 * confirmar, el diseño de la pantalla prohíbe los modales— y esa tensión es justo el lugar donde
 * alguien, con la mejor intención, va a resolverlo con un `confirm()` o un modal de la biblioteca
 * de turno.
 */

import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { ConfirmacionEnLinea } from "./ConfirmacionEnLinea";

const MENSAJE = "¿Eliminar la ficha del 12/04/2019?";

describe("ConfirmacionEnLinea", () => {
  it("no elimina nada por el solo hecho de aparecer", () => {
    const confirmar = vi.fn();
    render(
      <ConfirmacionEnLinea mensaje={MENSAJE} onConfirmar={confirmar} onCancelar={vi.fn()} />,
    );

    // US4-1: mostrarse no es confirmar. La baja recién ocurre con un acto explícito del operario.
    expect(screen.getByText(MENSAJE)).toBeInTheDocument();
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("elimina cuando el operario confirma con el teclado", async () => {
    const usuario = userEvent.setup();
    const confirmar = vi.fn();
    render(
      <ConfirmacionEnLinea mensaje={MENSAJE} onConfirmar={confirmar} onCancelar={vi.fn()} />,
    );

    // El foco entra solo en el botón de eliminar: el operario llegó hasta acá pidiendo eliminar,
    // y no tiene que tabular para completar su propia decisión.
    expect(screen.getByRole("button", { name: "Eliminar" })).toHaveFocus();

    await usuario.keyboard("{Enter}");

    expect(confirmar).toHaveBeenCalledTimes(1);
  });

  it("Escape cancela sin eliminar nada", async () => {
    const usuario = userEvent.setup();
    const confirmar = vi.fn();
    const cancelar = vi.fn();
    render(
      <ConfirmacionEnLinea mensaje={MENSAJE} onConfirmar={confirmar} onCancelar={cancelar} />,
    );

    await usuario.keyboard("{Escape}");

    expect(cancelar).toHaveBeenCalledTimes(1);
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("se puede cancelar tabulando, sin tocar el mouse", async () => {
    const usuario = userEvent.setup();
    const confirmar = vi.fn();
    const cancelar = vi.fn();
    render(
      <ConfirmacionEnLinea mensaje={MENSAJE} onConfirmar={confirmar} onCancelar={cancelar} />,
    );

    await usuario.tab();
    expect(screen.getByRole("button", { name: "Cancelar" })).toHaveFocus();

    await usuario.keyboard("{Enter}");

    expect(cancelar).toHaveBeenCalledTimes(1);
    expect(confirmar).not.toHaveBeenCalled();
  });

  it("no bloquea el resto de la pantalla mientras está abierta", async () => {
    const usuario = userEvent.setup();
    render(
      <div>
        <ConfirmacionEnLinea mensaje={MENSAJE} onConfirmar={vi.fn()} onCancelar={vi.fn()} />
        <input aria-label="Otro campo de la pantalla" />
      </div>,
    );

    // La diferencia concreta entre esto y un modal: con un modal abierto, el foco queda atrapado
    // adentro y el resto de la pantalla es inalcanzable hasta responder. Acá el operario se va
    // cuando quiere y la fila lo espera.
    const otroCampo = screen.getByLabelText("Otro campo de la pantalla");
    await usuario.click(otroCampo);

    expect(otroCampo).toHaveFocus();
    expect(screen.getByText(MENSAJE)).toBeInTheDocument();
  });

  it("mientras elimina no acepta un segundo pedido", async () => {
    const usuario = userEvent.setup();
    const confirmar = vi.fn();
    render(
      <ConfirmacionEnLinea
        mensaje={MENSAJE}
        onConfirmar={confirmar}
        onCancelar={vi.fn()}
        trabajando
      />,
    );

    await usuario.click(screen.getByRole("button", { name: "Eliminando…" }));

    expect(confirmar).not.toHaveBeenCalled();
  });
});
