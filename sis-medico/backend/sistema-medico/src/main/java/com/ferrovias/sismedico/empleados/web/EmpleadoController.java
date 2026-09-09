package com.ferrovias.sismedico.empleados.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ferrovias.sismedico.comun.RecursoInexistenteException;
import com.ferrovias.sismedico.empleados.Empleado;
import com.ferrovias.sismedico.empleados.PadronRepositorio;

/**
 * Consulta del padrón de empleados. Solo lectura, y solo esto.
 *
 * <p>No hay alta, modificación ni baja de empleados, y no las va a haber:
 * FR-003 lo prohíbe explícitamente. El alta de personal ocurre íntegramente
 * fuera de esta aplicación.
 */
@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

	private final PadronRepositorio padron;

	public EmpleadoController(PadronRepositorio padron) {
		this.padron = padron;
	}

	/**
	 * Confirmación de identidad antes de cargar nada (FR-001, US1-1, US1-2).
	 *
	 * <p>Es el primer paso del flujo del operario: tipea el legajo y confirma
	 * contra el apellido y el nombre que es la persona correcta. Por eso la
	 * pantalla no habilita la carga hasta que esto responde.
	 *
	 * <p><b>404</b> si el legajo no existe en el padrón (FR-002) y <b>503</b> si
	 * el padrón no responde, que traduce el manejador global. La diferencia
	 * importa: decirle al operario que el empleado no existe cuando lo que pasa
	 * es que la base externa está caída lo manda a buscar un problema que no
	 * tiene.
	 */
	@GetMapping("/{legajo}")
	public Empleado buscar(@PathVariable int legajo) {
		return padron.buscar(legajo).orElseThrow(() -> new RecursoInexistenteException(
				"El legajo " + legajo + " no existe en el padrón."));
	}
}
