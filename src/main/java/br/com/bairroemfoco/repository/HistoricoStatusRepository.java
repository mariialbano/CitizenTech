package br.com.bairroemfoco.repository;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Acesso à tabela {@code historico_status}. O histórico só recebe inserções:
 * registros não são alterados nem excluídos.
 */
@Repository
public class HistoricoStatusRepository {

	private static final String SELECT = """
			SELECT id, ocorrencia_id, status_anterior, status_novo, alterado_em
			  FROM historico_status
			""";

	private static final RowMapper<HistoricoStatus> MAPEADOR = (rs, linha) -> {
		String statusAnterior = rs.getString("status_anterior");
		return new HistoricoStatus(
				rs.getLong("id"),
				rs.getLong("ocorrencia_id"),
				statusAnterior == null ? null : StatusOcorrencia.valueOf(statusAnterior),
				StatusOcorrencia.valueOf(rs.getString("status_novo")),
				DataHoraSqlite.deTexto(rs.getString("alterado_em")));
	};

	private final JdbcClient jdbcClient;

	public HistoricoStatusRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	/**
	 * Insere um registro de histórico e o devolve como ficou gravado, com o id gerado.
	 */
	public HistoricoStatus inserir(HistoricoStatus historico) {
		if (historico.id() != null) {
			throw new IllegalArgumentException("Um registro de histórico novo não deve ter id: " + historico.id());
		}
		KeyHolder chave = new GeneratedKeyHolder();
		jdbcClient.sql("""
				INSERT INTO historico_status (ocorrencia_id, status_anterior, status_novo, alterado_em)
				VALUES (:ocorrenciaId, :statusAnterior, :statusNovo, :alteradoEm)
				""")
				.param("ocorrenciaId", historico.ocorrenciaId())
				.param("statusAnterior", historico.statusAnterior() == null ? null : historico.statusAnterior().name())
				.param("statusNovo", historico.statusNovo().name())
				.param("alteradoEm", DataHoraSqlite.paraTexto(historico.alteradoEm()))
				.update(chave);
		return jdbcClient.sql(SELECT + " WHERE id = :id")
				.param("id", chave.getKey().longValue())
				.query(MAPEADOR)
				.single();
	}

	/**
	 * Linha do tempo de uma ocorrência, da mais antiga para a mais recente.
	 * O id desempata alterações registradas no mesmo segundo.
	 */
	public List<HistoricoStatus> listarPorOcorrencia(long ocorrenciaId) {
		return jdbcClient.sql(SELECT + " WHERE ocorrencia_id = :ocorrenciaId ORDER BY alterado_em, id")
				.param("ocorrenciaId", ocorrenciaId)
				.query(MAPEADOR)
				.list();
	}

}
