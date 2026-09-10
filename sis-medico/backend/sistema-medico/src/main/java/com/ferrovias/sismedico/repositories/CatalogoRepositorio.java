package com.ferrovias.sismedico.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.ferrovias.sismedico.models.DetalleEnfermedad;
import com.ferrovias.sismedico.models.GrupoEnfermedad;

// Consulta del catálogo de grupos y detalles de enfermedad, sin caché: son pocas filas y estables.
@Repository
public class CatalogoRepositorio {

	private static final RowMapper<GrupoEnfermedad> A_GRUPO =
			(fila, numero) -> new GrupoEnfermedad(fila.getInt("id"), fila.getString("descripcion"));

	private static final RowMapper<DetalleEnfermedad> A_DETALLE =
			(fila, numero) -> new DetalleEnfermedad(
					fila.getInt("id"),
					fila.getInt("grupo_enfermedad_id"),
					fila.getString("descripcion"));

	private final JdbcTemplate jdbc;

	public CatalogoRepositorio(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<GrupoEnfermedad> grupos() {
		return jdbc.query("SELECT id, descripcion FROM grupo_enfermedad ORDER BY id", A_GRUPO);
	}

	public List<DetalleEnfermedad> detallesDe(int grupoEnfermedadId) {
		return jdbc.query("""
				SELECT id, grupo_enfermedad_id, descripcion
				FROM detalle_enfermedad
				WHERE grupo_enfermedad_id = ?
				ORDER BY id
				""", A_DETALLE, grupoEnfermedadId);
	}

	public boolean existeGrupo(int grupoEnfermedadId) {
		Integer cuantos = jdbc.queryForObject(
				"SELECT COUNT(*) FROM grupo_enfermedad WHERE id = ?", Integer.class, grupoEnfermedadId);
		return cuantos != null && cuantos > 0;
	}

	// El detalle tiene que existir y pertenecer al grupo elegido.
	public boolean detallePerteneceAlGrupo(int detalleEnfermedadId, int grupoEnfermedadId) {
		Integer cuantos = jdbc.queryForObject("""
				SELECT COUNT(*) FROM detalle_enfermedad
				WHERE id = ? AND grupo_enfermedad_id = ?
				""", Integer.class, detalleEnfermedadId, grupoEnfermedadId);
		return cuantos != null && cuantos > 0;
	}

	// Vacío si el código quedó huérfano; no es un error, la ficha se muestra igual sin descripción.
	public Optional<GrupoEnfermedad> buscarGrupo(int grupoEnfermedadId) {
		return jdbc.query("SELECT id, descripcion FROM grupo_enfermedad WHERE id = ?",
				A_GRUPO, grupoEnfermedadId).stream().findFirst();
	}

	public Optional<DetalleEnfermedad> buscarDetalle(int detalleEnfermedadId) {
		return jdbc.query("""
				SELECT id, grupo_enfermedad_id, descripcion
				FROM detalle_enfermedad WHERE id = ?
				""", A_DETALLE, detalleEnfermedadId).stream().findFirst();
	}
}
