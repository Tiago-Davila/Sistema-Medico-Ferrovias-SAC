package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ferrovias.sismedico.empleados.PadronRepositorio;

/**
 * FR-001, FR-002 y FR-003b, contra la base externa de verdad.
 *
 * <p>El padrón es una segunda base del contenedor, no un simulacro en memoria:
 * lo que se prueba es la consulta entre bases por nombre de tres partes, que es
 * el supuesto sobre el que se apoya D3.
 */
class PadronRepositorioTest extends BaseIntegracion {

	@Autowired
	private PadronRepositorio padron;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void cargarPadron() {
		new CargadorDatosHistoricos(jdbc, reloj).cargarTodo();
	}

	@Test
	void un_legajo_del_padron_devuelve_apellido_y_nombre() {
		// FR-001: es lo que el operario ve para confirmar la identidad antes de
		// cargar nada.
		assertThat(padron.buscar(900001))
				.isPresent()
				.get()
				.satisfies(empleado -> {
					assertThat(empleado.legajo()).isEqualTo(900001);
					assertThat(empleado.apellido()).isNotBlank();
					assertThat(empleado.nombre()).isNotBlank();
				});
	}

	@Test
	void un_legajo_que_no_existe_vuelve_vacio() {
		// FR-002. Vacío, no excepción: no existir es una respuesta legítima.
		assertThat(padron.buscar(1)).isEmpty();
		assertThat(padron.existe(1)).isFalse();
	}

	@Test
	void un_legajo_con_fichas_que_el_padron_ya_no_tiene_vuelve_vacio_sin_fallar() {
		// La referencia colgada que D3 acepta de frente: el padrón puede dar de
		// baja un legajo con fichas cargadas y el sistema no puede impedirlo.
		// Al consultarlo no se rompe nada; al leer la ficha, FR-028 manda
		// mostrarla igual.
		Integer fichas = jdbc.queryForObject(
				"SELECT COUNT(*) FROM ficha_medica WHERE legajo = 990012", Integer.class);

		assertThat(fichas).isPositive();
		assertThat(padron.buscar(990012)).isEmpty();
	}

	@Test
	void el_padron_no_se_copia_al_esquema_propio() {
		// FR-003b y FR-003c. Si alguna vez aparece una tabla `empleado` en
		// nuestro esquema, o una columna con el apellido en ficha_medica, es que
		// alguien empezó a copiar el padrón.
		Integer tablaEmpleado = jdbc.queryForObject(
				"SELECT COUNT(*) FROM sys.tables WHERE name = 'empleado'", Integer.class);
		Integer columnasDelPadron = jdbc.queryForObject("""
				SELECT COUNT(*) FROM sys.columns
				WHERE object_id = OBJECT_ID('ficha_medica')
				  AND name IN ('apellido', 'nombre', 'seccion', 'categoria_laboral')
				""", Integer.class);

		assertThat(tablaEmpleado).isZero();
		assertThat(columnasDelPadron).isZero();
	}
}
