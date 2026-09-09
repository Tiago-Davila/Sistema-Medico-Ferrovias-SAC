package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica que la aplicación levanta contra una base real y que el esquema que
 * se prueba es el mismo que se despliega, aplicado por Flyway.
 *
 * <p>Reemplaza al {@code contextLoads} que trajo Spring Initializr, que no podía
 * levantar sin base y además no comprobaba nada del esquema.
 */
class ArranqueDeAplicacionTest extends BaseIntegracion {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void flyway_aplica_las_dos_migraciones() {
		var versiones = jdbc.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
				String.class);

		assertThat(versiones).containsExactly("1", "2");
	}

	@Test
	void el_esquema_de_la_aplicacion_no_tiene_tabla_empleado() {
		// FR-003b: el padrón no se copia. Si alguna vez aparece una tabla
		// `empleado` en nuestro esquema, es que alguien empezó a importarlo.
		Integer tablas = jdbc.queryForObject(
				"SELECT COUNT(*) FROM sys.tables WHERE name = 'empleado'", Integer.class);

		assertThat(tablas).isZero();
	}

	@Test
	void el_padron_se_consulta_por_nombre_de_tres_partes_desde_la_misma_conexion() {
		// Es la consulta entre bases que la aplicación hace en producción. Se
		// prueba de verdad, no simulada, porque es el supuesto sobre el que se
		// apoya D3: misma instancia, sin DataSource aparte y sin FK posible.
		Integer empleados = jdbc.queryForObject(
				"SELECT COUNT(*) FROM " + BASE_PADRON + ".dbo.empleado", Integer.class);

		assertThat(empleados).isZero();
	}
}
