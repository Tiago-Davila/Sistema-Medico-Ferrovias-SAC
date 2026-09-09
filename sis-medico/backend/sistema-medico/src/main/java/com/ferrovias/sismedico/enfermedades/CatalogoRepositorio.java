package com.ferrovias.sismedico.enfermedades;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Consulta del catálogo de enfermedades.
 *
 * <p>Clase concreta, sin interfaz. No hay una segunda implementación ni motivo
 * para preverla, y una interfaz de una sola implementación es complejidad sin
 * uso (Principio V). Para los tests unitarios alcanza con simularla.
 *
 * <p><b>Sin caché.</b> Son un puñado de filas y la consulta es trivial; agregar
 * caché sin un problema de performance medido es exactamente lo que el
 * Principio V prohíbe. Además una caché acá tendría que invalidarse cuando el
 * catálogo cambie, y el catálogo cambia por migración.
 *
 * <p>Las consultas de este repositorio se usan <b>al escribir</b>, para FR-009 y
 * FR-010, y para poblar la pantalla. Nunca al leer una ficha: una ficha con
 * código huérfano se muestra igual, con el código y sin descripción (FR-028b).
 */
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

	/** Todos los grupos, para la pantalla. Son pocos y estables. */
	public List<GrupoEnfermedad> grupos() {
		return jdbc.query("SELECT id, descripcion FROM grupo_enfermedad ORDER BY id", A_GRUPO);
	}

	/** Detalles de un grupo, para la selección por código tipeado. */
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

	/**
	 * FR-010: el detalle tiene que pertenecer al grupo elegido.
	 *
	 * <p>Devuelve {@code false} tanto si el detalle no existe como si existe
	 * pero cuelga de otro grupo. Para el operario es el mismo problema —el
	 * código que tipeó no va con ese grupo— y distinguirlos daría dos mensajes
	 * para una sola corrección.
	 */
	public boolean detallePerteneceAlGrupo(int detalleEnfermedadId, int grupoEnfermedadId) {
		Integer cuantos = jdbc.queryForObject("""
				SELECT COUNT(*) FROM detalle_enfermedad
				WHERE id = ? AND grupo_enfermedad_id = ?
				""", Integer.class, detalleEnfermedadId, grupoEnfermedadId);
		return cuantos != null && cuantos > 0;
	}

	/**
	 * Descripción de un grupo, si el catálogo lo tiene.
	 *
	 * <p>Vacío cuando el código quedó huérfano. Al leer una ficha eso <b>no</b>
	 * es un error: FR-028b manda mostrar el código tal cual, sin descripción, y
	 * señalar la ficha como inconsistente.
	 */
	public Optional<GrupoEnfermedad> buscarGrupo(int grupoEnfermedadId) {
		return jdbc.query("SELECT id, descripcion FROM grupo_enfermedad WHERE id = ?",
				A_GRUPO, grupoEnfermedadId).stream().findFirst();
	}

	/** Ídem {@link #buscarGrupo}, para el detalle. */
	public Optional<DetalleEnfermedad> buscarDetalle(int detalleEnfermedadId) {
		return jdbc.query("""
				SELECT id, grupo_enfermedad_id, descripcion
				FROM detalle_enfermedad WHERE id = ?
				""", A_DETALLE, detalleEnfermedadId).stream().findFirst();
	}
}
