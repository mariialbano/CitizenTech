package br.com.bairroemfoco.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import br.com.bairroemfoco.model.Categoria;

/**
 * Acesso à tabela {@code categoria}. As categorias são carregadas pelo data.sql
 * e, no MVP, apenas consultadas pela aplicação.
 */
@Repository
public class CategoriaRepository {

	private static final RowMapper<Categoria> MAPEADOR = (rs, linha) -> new Categoria(
			rs.getLong("id"),
			rs.getString("nome"));

	private final JdbcClient jdbcClient;

	public CategoriaRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<Categoria> listarTodas() {
		return jdbcClient.sql("SELECT id, nome FROM categoria ORDER BY nome")
				.query(MAPEADOR)
				.list();
	}

	public Optional<Categoria> buscarPorId(long id) {
		return jdbcClient.sql("SELECT id, nome FROM categoria WHERE id = :id")
				.param("id", id)
				.query(MAPEADOR)
				.optional();
	}

}
