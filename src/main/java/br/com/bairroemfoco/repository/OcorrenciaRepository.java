package br.com.bairroemfoco.repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Acesso à tabela {@code ocorrencia}.
 * Não valida regras de negócio (transições de status, normalização do bairro, gravação do
 * histórico): isso cabe ao serviço, que chama este repositório dentro de uma transação.
 */
@Repository
public class OcorrenciaRepository {

	private static final String SELECT = """
			SELECT id, categoria_id, descricao, bairro, endereco_referencia, status,
			       criado_em, atualizado_em, resolvido_em
			  FROM ocorrencia
			""";

	private static final RowMapper<Ocorrencia> MAPEADOR = (rs, linha) -> new Ocorrencia(
			rs.getLong("id"),
			rs.getLong("categoria_id"),
			rs.getString("descricao"),
			rs.getString("bairro"),
			rs.getString("endereco_referencia"),
			StatusOcorrencia.valueOf(rs.getString("status")),
			DataHoraSqlite.deTexto(rs.getString("criado_em")),
			DataHoraSqlite.deTexto(rs.getString("atualizado_em")),
			DataHoraSqlite.deTexto(rs.getString("resolvido_em")));

	private final JdbcClient jdbcClient;

	public OcorrenciaRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	/**
	 * Insere uma nova ocorrência e a devolve como ficou gravada, com o id gerado.
	 */
	public Ocorrencia inserir(Ocorrencia ocorrencia) {
		if (ocorrencia.id() != null) {
			throw new IllegalArgumentException("Uma ocorrência nova não deve ter id: " + ocorrencia.id());
		}
		KeyHolder chave = new GeneratedKeyHolder();
		jdbcClient.sql("""
				INSERT INTO ocorrencia (categoria_id, descricao, bairro, endereco_referencia, status,
				                        criado_em, atualizado_em, resolvido_em)
				VALUES (:categoriaId, :descricao, :bairro, :enderecoReferencia, :status,
				        :criadoEm, :atualizadoEm, :resolvidoEm)
				""")
				.param("categoriaId", ocorrencia.categoriaId())
				.param("descricao", ocorrencia.descricao())
				.param("bairro", ocorrencia.bairro())
				.param("enderecoReferencia", ocorrencia.enderecoReferencia())
				.param("status", ocorrencia.status().name())
				.param("criadoEm", DataHoraSqlite.paraTexto(ocorrencia.criadoEm()))
				.param("atualizadoEm", DataHoraSqlite.paraTexto(ocorrencia.atualizadoEm()))
				.param("resolvidoEm", DataHoraSqlite.paraTexto(ocorrencia.resolvidoEm()))
				.update(chave);
		return buscarPorId(chave.getKey().longValue()).orElseThrow();
	}

	public Optional<Ocorrencia> buscarPorId(long id) {
		return jdbcClient.sql(SELECT + " WHERE id = :id")
				.param("id", id)
				.query(MAPEADOR)
				.optional();
	}

	/**
	 * Lista as ocorrências, das mais recentes para as mais antigas (o id cresce com o tempo).
	 * Filtros nulos são ignorados. Só trechos fixos de SQL são concatenados; os valores
	 * vão sempre como parâmetros.
	 */
	public List<Ocorrencia> listar(StatusOcorrencia status, Long categoriaId) {
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
		Map<String, Object> parametros = new HashMap<>();
		if (status != null) {
			sql.append(" AND status = :status");
			parametros.put("status", status.name());
		}
		if (categoriaId != null) {
			sql.append(" AND categoria_id = :categoriaId");
			parametros.put("categoriaId", categoriaId);
		}
		sql.append(" ORDER BY id DESC");

		return jdbcClient.sql(sql.toString())
				.params(parametros)
				.query(MAPEADOR)
				.list();
	}

	/**
	 * Muda o status somente se o status atual ainda for {@code statusAtual}.
	 * Atualiza {@code atualizado_em} e preenche {@code resolvido_em} apenas quando o novo status
	 * é RESOLVIDO, mantendo a coerência exigida pelo CHECK do schema.
	 *
	 * @return {@code false} se a ocorrência não existe ou se o status já foi alterado
	 */
	public boolean atualizarStatus(long id, StatusOcorrencia statusAtual, StatusOcorrencia novoStatus,
			LocalDateTime alteradoEm) {
		int linhas = jdbcClient.sql("""
				UPDATE ocorrencia
				   SET status = :novoStatus,
				       atualizado_em = :alteradoEm,
				       resolvido_em = CASE WHEN :novoStatus = 'RESOLVIDO' THEN :alteradoEm END
				 WHERE id = :id AND status = :statusAtual
				""")
				.param("id", id)
				.param("statusAtual", statusAtual.name())
				.param("novoStatus", novoStatus.name())
				.param("alteradoEm", DataHoraSqlite.paraTexto(alteradoEm))
				.update();
		return linhas == 1;
	}

}
