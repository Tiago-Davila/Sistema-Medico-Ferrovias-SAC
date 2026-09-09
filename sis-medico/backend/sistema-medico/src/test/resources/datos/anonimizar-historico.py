#!/usr/bin/env python3
"""
Anonimiza el volcado del sistema de referencia y produce el juego de datos de
prueba (FR-036b, FR-036c).

    python3 anonimizar-historico.py ../../../../assets/Accidentes.csv

Entrada: el volcado crudo, que NO está versionado y no debe estarlo. Trae
legajos reales, diagnósticos reales y números de siniestro de la ART.

Salida, versionada:
    fichas-historicas.csv   fichas anonimizadas
    empleados-padron.csv    padrón ficticio para esos legajos

Qué se conserva y qué no
------------------------
FR-036c manda conservar fechas, estados y códigos de enfermedad tal cual, que es
lo que hace falta para probar el comportamiento, y reemplazar legajos y nombres.
Acá se va un paso más allá con dos cosas:

* Las observaciones se reemplazan por texto sintético **del mismo largo**. Son
  diagnósticos reales ("ARTRITIS REUMATOIDEA REAGUDIZADA", "POST ABORTO",
  evaluaciones psiquiátricas). Aunque el legajo pase a ser ficticio, meterlas al
  historial de git sería poner datos clínicos en un lugar del que no salen más.
  Conservar el largo alcanza para lo único que los tests necesitan de ese campo:
  medir el máximo real (T074) y ejercitar el tope de 500 de FR-006b.

* Las tres columnas de la ART —aseguradora y número de siniestro— se descartan
  enteras. Están fuera del alcance de la feature: no hay dónde cargarlas ni por
  qué guardarlas.

El remapeo de legajos es estable dentro de una corrida: dos fichas del mismo
empleado siguen siendo del mismo empleado, que es lo que hace falta para probar
unicidad y solapamiento.

Las cuatro columnas de sí/no
----------------------------
El volcado trae cuatro columnas booleanas y la ficha tiene cinco campos de ese
tipo más la hora del accidente. No hay definición de columnas, así que no se
sabe cuál es cuál: la única fuerte es que la segunda solo tiene valor en las
fichas de accidente, lo que la haría "in itinere".

Se copian tal cual con nombres neutros (indicador1..indicador4) en lugar de
adivinarles el significado. El cargador no las mapea a ningún campo y las deja
en NULL, que es exactamente lo que FR-004d contempla para el histórico. Cuando
llegue la definición de columnas, cambia el cargador y no hay que volver a pedir
el volcado.
"""

import csv
import sys
from pathlib import Path

SALIDA = Path(__file__).parent

# Apellidos y nombres inventados. La combinación es determinista a partir del
# legajo ficticio, para que las corridas den siempre lo mismo.
APELLIDOS = [
    "Sosa", "Quiroga", "Ledesma", "Barrios", "Ocampo", "Vera", "Maidana",
    "Cabrera", "Villalba", "Aguirre", "Peralta", "Zalazar", "Ibarra", "Correa",
    "Miranda", "Escobar", "Bustos", "Rolón", "Alvarenga", "Gauna",
]
NOMBRES = [
    "Ramón", "Elsa", "Hugo", "Norma", "Osvaldo", "Mirta", "Rubén", "Alicia",
    "Daniel", "Estela", "Jorge", "Silvia", "Raúl", "Beatriz", "Carlos",
    "Susana", "Néstor", "Graciela", "Alberto", "Nélida",
]
SECCIONES = [
    "Vía y Obras", "Tráfico", "Material Rodante", "Señalamiento",
    "Estaciones", "Administración",
]
CATEGORIAS_LABORALES = [
    "Oficial", "Medio Oficial", "Ayudante", "Capataz", "Administrativo",
]

PRIMER_LEGAJO_FICTICIO = 900001


def texto_sintetico(original: str, indice: int) -> str:
    """Texto sin contenido clínico, del mismo largo que el original."""
    if original == "NULL" or original == "":
        return ""
    base = f"OBSERVACION DE PRUEBA {indice}"
    largo = len(original)
    if len(base) >= largo:
        return base[:largo]
    return base + " " + "X" * (largo - len(base) - 1)


def main(ruta_volcado: str) -> None:
    filas = list(csv.reader(open(ruta_volcado, encoding="utf-8"), delimiter=";"))

    legajos = {}
    for fila in filas:
        real = fila[0]
        if real not in legajos:
            legajos[real] = PRIMER_LEGAJO_FICTICIO + len(legajos)

    with open(SALIDA / "fichas-historicas.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow([
            "legajo", "fecha_evento", "fecha_citacion", "fecha_alta",
            "estado_paciente", "indicador1", "indicador2", "indicador3",
            "indicador4", "grupo_enfermedad_id", "detalle_enfermedad_id",
            "observaciones",
        ])
        for i, fila in enumerate(filas):
            w.writerow([
                legajos[fila[0]],
                fila[1], fila[2], fila[3], fila[4],
                fila[5], fila[6], fila[7], fila[8],
                fila[9], fila[10],
                texto_sintetico(fila[11], i),
            ])

    with open(SALIDA / "empleados-padron.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\n")
        w.writerow(["legajo", "apellido", "nombre", "seccion", "categoria_laboral"])
        for n, ficticio in enumerate(sorted(legajos.values())):
            w.writerow([
                ficticio,
                APELLIDOS[n % len(APELLIDOS)],
                NOMBRES[(n * 7) % len(NOMBRES)],
                SECCIONES[n % len(SECCIONES)],
                CATEGORIAS_LABORALES[n % len(CATEGORIAS_LABORALES)],
            ])

    print(f"{len(filas)} fichas y {len(legajos)} empleados anonimizados.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
