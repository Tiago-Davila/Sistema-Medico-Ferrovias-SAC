import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

/**
 * Los tests de la pantalla.
 *
 * Hay muy pocos y a propósito: casi toda regla de esta feature vive en el backend (FR-018) y se
 * prueba ahí, donde se aplica. Lo que se prueba acá es lo que **solo** existe en la pantalla y
 * ningún test de Java puede ver: que la confirmación de borrado se pida y se opere con el teclado
 * sin ser un modal (FR-038, US4-1).
 *
 * Duplicar acá las reglas de negocio sería peor que no tener tests de frontend: daría la
 * impresión de que están cubiertas dos veces cuando en realidad estarían escritas dos veces.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["components/**/*.test.tsx", "app/**/*.test.tsx"],
  },
  resolve: {
    alias: {
      "@": fileURLToPath(new URL(".", import.meta.url)),
    },
  },
});
