import { NextRequest } from "next/server";

// Proxy de desarrollo: reenvía /api/* al backend agregando el Basic Auth del
// perfil `local` (ver application-local.yml). El navegador nunca ve
// credenciales ni las pide: en producción esto lo reemplaza el mecanismo real
// (AD/OIDC, FR-034), no es el flujo final.
const BACKEND_BASE = process.env.BACKEND_API_BASE ?? "http://localhost:5173/api";
const BACKEND_USER = process.env.BACKEND_DEV_USER;
const BACKEND_PASS = process.env.BACKEND_DEV_PASS;

export const dynamic = "force-dynamic";

async function proxy(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const { path } = await params;
  const destino = `${BACKEND_BASE}/${path.join("/")}${req.nextUrl.search}`;

  const encabezados = new Headers();
  const contentType = req.headers.get("content-type");
  if (contentType) encabezados.set("content-type", contentType);
  if (BACKEND_USER && BACKEND_PASS) {
    encabezados.set(
      "authorization",
      `Basic ${Buffer.from(`${BACKEND_USER}:${BACKEND_PASS}`).toString("base64")}`,
    );
  }

  const sinCuerpo = req.method === "GET" || req.method === "HEAD";
  const respuesta = await fetch(destino, {
    method: req.method,
    headers: encabezados,
    body: sinCuerpo ? undefined : await req.arrayBuffer(),
  });

  const encabezadosSalida = new Headers();
  const contentTypeSalida = respuesta.headers.get("content-type");
  if (contentTypeSalida) encabezadosSalida.set("content-type", contentTypeSalida);

  return new Response(await respuesta.arrayBuffer(), {
    status: respuesta.status,
    headers: encabezadosSalida,
  });
}

export {
  proxy as GET,
  proxy as POST,
  proxy as PUT,
  proxy as PATCH,
  proxy as DELETE,
};
