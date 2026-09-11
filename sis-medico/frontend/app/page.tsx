import { redirect } from "next/navigation";

// La única pantalla del sistema es /fichas. La raíz no tiene contenido propio.
export default function Home() {
  redirect("/fichas");
}
