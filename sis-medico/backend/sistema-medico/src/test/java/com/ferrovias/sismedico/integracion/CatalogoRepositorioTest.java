package com.ferrovias.sismedico.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ferrovias.sismedico.repositories.CatalogoRepositorio;

/** El catálogo que dejó la semilla de V2, consultado como lo consulta FR-010. */
class CatalogoRepositorioTest extends BaseIntegracion {

	@Autowired
	private CatalogoRepositorio catalogo;

	@Test
	void trae_los_grupos_de_la_semilla() {
		assertThat(catalogo.grupos()).hasSize(27);
		assertThat(catalogo.grupos()).first()
				.satisfies(grupo -> assertThat(grupo.id()).isEqualTo(100));
	}

	@Test
	void trae_los_detalles_de_un_grupo() {
		assertThat(catalogo.detallesDe(100))
				.hasSize(7)
				.allSatisfy(detalle -> assertThat(detalle.grupoEnfermedadId()).isEqualTo(100));
	}

	@Test
	void un_detalle_del_grupo_elegido_pasa() {
		assertThat(catalogo.detallePerteneceAlGrupo(1001, 100)).isTrue();
	}

	@Test
	void un_detalle_de_otro_grupo_no_pasa() {
		// FR-010: el caso que la regla existe para atrapar.
		assertThat(catalogo.detallePerteneceAlGrupo(2001, 100)).isFalse();
	}

	@Test
	void un_detalle_inexistente_tampoco_pasa() {
		// Para el operario es el mismo problema que el anterior: el código que
		// tipeó no va con ese grupo.
		assertThat(catalogo.detallePerteneceAlGrupo(999999, 100)).isFalse();
	}

	@Test
	void un_codigo_huerfano_se_busca_sin_error_y_vuelve_vacio() {
		// FR-028b: al leer una ficha con código huérfano no se falla; se muestra
		// el código sin descripción.
		assertThat(catalogo.buscarGrupo(88888)).isEmpty();
		assertThat(catalogo.buscarDetalle(888888)).isEmpty();
		assertThat(catalogo.existeGrupo(88888)).isFalse();
	}

	@Test
	void un_codigo_del_catalogo_devuelve_su_descripcion() {
		assertThat(catalogo.buscarGrupo(100)).isPresent();
		assertThat(catalogo.buscarDetalle(1001))
				.get()
				.satisfies(detalle -> assertThat(detalle.grupoEnfermedadId()).isEqualTo(100));
	}
}
