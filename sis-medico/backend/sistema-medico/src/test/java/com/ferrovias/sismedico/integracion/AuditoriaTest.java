package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import com.ferrovias.sismedico.auditoria.AuditoriaRepositorio;
import com.ferrovias.sismedico.auditoria.AuditoriaServicio;
import com.ferrovias.sismedico.auditoria.Operacion;
import com.ferrovias.sismedico.fichas.EstadoPaciente;
import com.ferrovias.sismedico.fichas.FichaMedica;
import com.ferrovias.sismedico.fichas.FichaMedicaRepositorio;
import com.ferrovias.sismedico.fichas.Observaciones;

/** FR-032, FR-033 y SC-004. */
class AuditoriaTest extends BaseIntegracion {

	private static final int LEGAJO = 980001;

	@Autowired
	private AuditoriaServicio servicio;

	@Autowired
	private AuditoriaRepositorio repositorio;

	@Autowired
	private FichaMedicaRepositorio fichas;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock reloj;

	@BeforeEach
	void vaciar() {
		new CargadorDatosHistoricos(jdbc, reloj).vaciar();
	}

	private long crearFicha() {
		return fichas.insertar(new FichaMedica(null, 0L, LEGAJO,
				LocalDate.of(2026, 3, 3), EstadoPaciente.ENFERMEDAD,
				null, false, null, true, false, true,
				LocalDate.of(2026, 3, 10), null, 100, 1001,
				new Observaciones("Bronquitis"), null),
				"jperez", Instant.now(reloj));
	}

	@Test
	@WithMockUser(username = "jperez")
	void cada_escritura_deja_su_propio_asiento() {
		// FR-033: las columnas de FR-032 guardan solo la última modificación.
		// Esta tabla es la que conserva el rastro completo.
		long id = crearFicha();

		servicio.registrarAlta(id);
		servicio.registrarModificacion(id);
		servicio.registrarModificacion(id);
		servicio.registrarBaja(id);

		assertThat(repositorio.historialDe(id))
				.extracting(AuditoriaRepositorio.Asiento::operacion)
				.containsExactly(Operacion.ALTA, Operacion.MODIFICACION,
						Operacion.MODIFICACION, Operacion.BAJA);
	}

	@Test
	@WithMockUser(username = "mgomez")
	void el_usuario_sale_del_contexto_de_seguridad_y_no_de_un_parametro() {
		// Un parámetro es algo que alguien puede pasar mal, y en un rastro de
		// auditoría eso significa atribuirle a un operario lo que hizo otro.
		long id = crearFicha();

		servicio.registrarAlta(id);

		assertThat(repositorio.historialDe(id))
				.singleElement()
				.satisfies(asiento -> assertThat(asiento.usuario()).isEqualTo("mgomez"));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_asiento_no_guarda_el_contenido_de_la_ficha() {
		// Un historial con diagnósticos adentro sería una segunda copia de datos
		// clínicos que habría que proteger igual, y con más superficie porque
		// nunca se borra.
		long id = crearFicha();
		servicio.registrarAlta(id);

		var columnas = jdbc.queryForList(
				"SELECT name FROM sys.columns WHERE object_id = OBJECT_ID('ficha_medica_auditoria')",
				String.class);

		assertThat(columnas)
				.containsExactlyInAnyOrder("id", "ficha_id", "operacion", "usuario", "momento");
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_momento_sale_del_reloj_de_la_aplicacion() {
		// D7: un solo reloj para todo el sistema. Con SYSUTCDATETIME() habría
		// dos y ningún test de fechas sería determinista.
		long id = crearFicha();
		Instant antes = Instant.now(reloj);

		servicio.registrarAlta(id);

		assertThat(repositorio.historialDe(id))
				.singleElement()
				.satisfies(asiento -> assertThat(asiento.momento())
						.isBetween(antes.minusSeconds(2), Instant.now(reloj).plusSeconds(2)));
	}

	@Test
	@WithMockUser(username = "jperez")
	void el_historial_no_se_puede_pisar() {
		// Solo inserción: dos asientos de la misma operación conviven en vez de
		// reemplazarse. Un rastro que se puede editar no es un rastro.
		long id = crearFicha();

		servicio.registrarModificacion(id);
		servicio.registrarModificacion(id);

		assertThat(repositorio.historialDe(id)).hasSize(2);
	}
}
