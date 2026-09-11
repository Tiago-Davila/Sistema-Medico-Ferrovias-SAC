/**
 * Cabecera de la única pantalla del sistema.
 *
 * Lleva la marca de Ferrovías SAC (el mismo isologo de ferrovias.com.ar) para
 * que se lea como un sistema propio de la empresa, no como una herramienta
 * genérica. No hay navegación acá: esta pantalla no tiene a dónde ir.
 */
export function AppHeader() {
  return (
    <header className="border-b-4 border-brand bg-white">
      <div className="mx-auto flex max-w-4xl items-center gap-4 px-6 py-4">
        <FerroviasLogo className="h-7 w-auto shrink-0" />
        <div className="h-8 w-px bg-neutral-300" aria-hidden="true" />
        <div>
          <p className="text-xs font-medium tracking-wide text-neutral-500 uppercase">
            Sistema médico
          </p>
          <h1 className="text-sm font-semibold text-neutral-800">Ficha médica del empleado</h1>
        </div>
      </div>
    </header>
  );
}

function FerroviasLogo({ className }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 372.8 101"
      className={className}
      role="img"
      aria-label="Ferrovías"
    >
      <path d="M160.2,73.2c-.5,5.1,1.7,7.7,6.9,7.7s7.5-.9,9.6-1.9l1,6.4c-3,1.4-7.2,2.6-13.2,2.6-10.8,0-15.6-7.1-13.2-18.4,2.3-11.1,9.5-18.2,19.7-18.2s14,6.9,11,18.9h-21.3c0,0-.3,1.1-.5,3M353.3,87.8c-4.3,0-8-1.1-9.8-2.6l4-6.5c1.1,1,3.3,2,5.9,2s4.7-1.6,5.2-3.9c.5-2.4-.5-3.7-3.5-5-6-2.6-6.7-6.2-5.9-9.8,1.3-6,7-10.9,14.1-10.9s7.2.9,9.6,2.6l-3.7,5.8c-.9-.9-2.7-1.8-5-1.8s-4.4,1.6-4.8,3.7c-.5,2.4,1,3.7,3.5,4.9,6.2,2.9,7.1,6.2,6.3,10.4-1.3,6.2-7.3,11.1-15.7,11.1M333,68.7c-6.3.6-10.6,2-11.7,7.7-.9,4.1,1.1,5.9,5.2,5.9s2.9-.3,3.9-.7l2.7-12.8M324.8,87.9c-10.2,0-14.7-3.8-13.2-11.1,1.9-9.1,11.5-11.8,22.4-12.8l.3-1.6c.8-3.7-1.5-4.7-5.2-4.7s-7.1,1.1-9.6,2.2l-1-5.8c2.7-1.1,7.6-2.7,13.9-2.7s13.2,3.3,11.2,12.9l-4.4,21.1c-3.3,1.6-8.3,2.6-14.4,2.6M308.2,48.3h-5.6l6.4-9.4c3.4-1.8,9.1-2,9.5.3l-10.4,9.2M294.2,87.2l7.3-35.3h10.2l-7.3,35.3h-10.2M279.4,87.2h-9.9l-4-35.3h10.8l.8,20.3c0,2.8-.3,5.4-.3,5.4h.1s.7-2.6,2-5.4l9.4-20.3h9.6l-18.5,35.3M247.3,57.6c-4.5,0-6.6,5.5-7.9,11.8-1.3,6.4-1.4,11.5,3.1,11.5s6.5-5.2,7.8-11.5c1.3-6.3,1.5-11.8-3-11.8M241.1,87.9c-10.2,0-14-7.8-11.8-18.4,2.2-10.6,9.2-18.3,19.3-18.3s14.1,7.7,11.9,18.3c-2.2,10.6-9.2,18.4-19.4,18.4M228.9,58.6c-2.3-1.1-4.9-1-6.8-.2l-6,28.8h-10.2l6.9-33.4c4.3-1.9,10-3,19.5-2.5l-3.4,7.2M206.6,58.6c-2.3-1.1-4.9-1-6.8-.2l-6,28.8h-10.2l6.9-33.4c4.3-1.9,10-3,19.5-2.5l-3.4,7.2M174.5,64.8c.5-5-1.3-7.5-4.8-7.5s-6.8,1.7-8.3,7.5h13.1M139.2,49.3l-2.4,11.5h12.7l-1.4,6.9h-12.7l-4,19.5h-10.7l9.3-45.1h26.3l-1.5,7.1h-15.6" />
      <path
        fill="var(--brand)"
        d="M3,76.5c6.5,4.7,14.5,7.3,31.3,8.6l1.8-8.6H3"
      />
      <path fill="var(--brand)" d="M30.9,101l1.8-8.5H0c6.4,4.6,14.4,7.2,30.9,8.5" />
      <path
        fill="var(--brand)"
        d="M10.8,38.3c10,7.2,20.6,9.5,67,9.6l2-9.6H10.8"
      />
      <path fill="var(--brand)" d="M74.4,64l2-9.6H7.6c10,7.2,20.8,9.5,66.8,9.6" />
      <path fill="var(--brand)" d="M18.6,0c12.2,7.4,28.4,9.6,88,9.6l2-9.6H18.6" />
      <path
        fill="var(--brand)"
        d="M103.3,25.7l2-9.6H15.4c12.3,7.4,28.1,9.6,87.9,9.6"
      />
    </svg>
  );
}
