package com.ferrovias.sismedico.integracion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de todos los tests que necesitan una base de datos.
 *
 * <p>La imagen de SQL Server pesa más de 2 GB y tarda decenas de segundos en
 * estar lista. Sin cuidado, el ciclo de trabajo se vuelve inusable y la
 * tentación pasa a ser saltearse los tests de base, que son justo los que
 * cubren el índice único filtrado, el solapamiento, el bloqueo optimista y la
 * lectura tolerante. De ahí las tres medidas de esta clase:
 *
 * <ol>
 *   <li><b>Un solo contenedor para toda la suite.</b> Campo estático arrancado
 *       una vez, no uno por clase de test. Por eso no se usa {@code @Container}
 *       de JUnit, que lo reinicia por clase.
 *   <li><b>Reuso entre corridas.</b> Con {@code testcontainers.reuse.enable=true}
 *       en {@code ~/.testcontainers.properties}, el contenedor sobrevive al fin
 *       de la JVM y el arranque se paga una vez por sesión de trabajo. Sin esa
 *       propiedad, Testcontainers ignora el {@code withReuse} y todo sigue
 *       funcionando, solo que más lento.
 *   <li><b>Suites separadas en Gradle.</b> {@code test} no toca Docker;
 *       {@code integrationTest} es el que corre esto.
 * </ol>
 *
 * <p>Lo que se reusa es el <b>contenedor</b>, no los datos: las dos bases se
 * recrean en cada arranque de la JVM. Reusar datos entre corridas haría que un
 * test pase o falle según lo que dejó la corrida anterior.
 *
 * <p>El padrón vive en una <b>segunda base del mismo contenedor</b>, no en un
 * simulacro en memoria. Es la única forma de probar de verdad la consulta entre
 * bases por nombre de tres partes, que es como la aplicación lo consulta en
 * producción (D3, FR-003b). Esa base es un doble del sistema externo: su
 * esquema no nos pertenece, por eso está en {@code src/test/resources} y no en
 * {@code db/migration}.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegracion {

	/** Base propia de la aplicación. La migra Flyway. */
	public static final String BASE_APLICACION = "sismedico";

	/** Base externa del padrón. No la migramos: la consultamos. */
	public static final String BASE_PADRON = "padron";

	private static final MSSQLServerContainer SQL_SERVER = new MSSQLServerContainer(
			DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest"))
			.acceptLicense()
			.withReuse(true);

	static {
		SQL_SERVER.start();
		recrearBases();
		crearPadron();
	}

	@DynamicPropertySource
	static void configurarConexion(DynamicPropertyRegistry registro) {
		registro.add("spring.datasource.url", () -> urlDe(BASE_APLICACION));
		registro.add("spring.datasource.username", SQL_SERVER::getUsername);
		registro.add("spring.datasource.password", SQL_SERVER::getPassword);
	}

	/** URL de conexión a una base concreta del contenedor. */
	public static String urlDe(String base) {
		return SQL_SERVER.getJdbcUrl() + ";databaseName=" + base;
	}

	public static String usuario() {
		return SQL_SERVER.getUsername();
	}

	public static String clave() {
		return SQL_SERVER.getPassword();
	}

	/**
	 * Deja las dos bases vacías. Se ejecuta una vez por JVM, no por clase de
	 * test: recrear una base de SQL Server cuesta segundos y las clases de test
	 * limpian sus propias filas.
	 */
	private static void recrearBases() {
		ejecutarSobreMaster(List.of(
				"ALTER DATABASE " + BASE_APLICACION + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE",
				"DROP DATABASE " + BASE_APLICACION,
				"ALTER DATABASE " + BASE_PADRON + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE",
				"DROP DATABASE " + BASE_PADRON),
				true);
		ejecutarSobreMaster(List.of(
				"CREATE DATABASE " + BASE_APLICACION,
				"CREATE DATABASE " + BASE_PADRON),
				false);
	}

	/** Crea el doble del padrón externo a partir del script de test. */
	private static void crearPadron() {
		String script = leerRecurso("/padron/esquema-padron.sql");
		try (Connection conexion = DriverManager.getConnection(urlDe(BASE_PADRON), usuario(), clave());
				Statement sentencia = conexion.createStatement()) {
			for (String bloque : script.split("(?m)^\\s*GO\\s*$")) {
				if (!bloque.isBlank()) {
					sentencia.execute(bloque);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo crear la base del padrón de prueba", e);
		}
	}

	private static void ejecutarSobreMaster(List<String> sentencias, boolean tolerarFallos) {
		try (Connection conexion = DriverManager.getConnection(
				SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword());
				Statement sentencia = conexion.createStatement()) {
			for (String sql : sentencias) {
				try {
					sentencia.execute(sql);
				} catch (Exception e) {
					// La base puede no existir todavía: es el caso normal en la
					// primera corrida contra un contenedor nuevo.
					if (!tolerarFallos) {
						throw e;
					}
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("No se pudieron preparar las bases de prueba", e);
		}
	}

	private static String leerRecurso(String ruta) {
		try (var entrada = BaseIntegracion.class.getResourceAsStream(ruta)) {
			if (entrada == null) {
				throw new IllegalStateException("Falta el recurso de prueba " + ruta);
			}
			return new String(entrada.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer " + ruta, e);
		}
	}
}
