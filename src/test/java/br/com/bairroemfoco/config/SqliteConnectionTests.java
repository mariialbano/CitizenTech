package br.com.bairroemfoco.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Infraestrutura do banco: conexão, inicialização, persistência e transações.
 */
@SpringBootTest
@ActiveProfiles("test")
class SqliteConnectionTests {

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Value("${spring.datasource.url}")
	private String url;

	@Test
	void pragmaForeignKeysEstaAtivo() {
		int foreignKeys = jdbcClient.sql("PRAGMA foreign_keys").query(Integer.class).single();

		assertThat(foreignKeys).isEqualTo(1);
	}

	@Test
	void scriptsPodemSerExecutadosVariasVezesSemDuplicarCategorias() {
		int antes = contarCategorias();

		ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
				new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"));
		populator.setSqlScriptEncoding("UTF-8");
		populator.execute(dataSource);
		populator.execute(dataSource);

		assertThat(antes).isEqualTo(5);
		assertThat(contarCategorias()).isEqualTo(antes);
	}

	@Test
	void indicesForamCriados() {
		var indices = jdbcClient.sql("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'")
				.query(String.class)
				.list();

		assertThat(indices).containsExactlyInAnyOrder(
				"idx_historico_ocorrencia", "idx_ocorrencia_status", "idx_ocorrencia_categoria");
	}

	@Test
	void dadosSaoPersistidosNoArquivo() throws Exception {
		String nome = "Teste de persistência " + System.nanoTime();
		jdbcClient.sql("INSERT INTO categoria (nome) VALUES (:nome)").param("nome", nome).update();

		try {
			// Conexão nova, fora do pool do Spring: só enxerga o que foi gravado no arquivo .db
			try (Connection conexao = DriverManager.getConnection(url);
					PreparedStatement stmt = conexao.prepareStatement("SELECT COUNT(*) FROM categoria WHERE nome = ?")) {
				stmt.setString(1, nome);
				try (ResultSet rs = stmt.executeQuery()) {
					rs.next();
					assertThat(rs.getInt(1)).isEqualTo(1);
				}
			}
		}
		finally {
			jdbcClient.sql("DELETE FROM categoria WHERE nome = :nome").param("nome", nome).update();
		}
	}

	@Test
	void transacaoDesfazOcorrenciaQuandoHistoricoFalha() {
		String descricao = "Teste de rollback " + System.nanoTime();

		// Mesmo padrão que o serviço usará: ocorrência + histórico na mesma transação.
		// O histórico é inválido (status_novo nulo), então a ocorrência também deve ser desfeita.
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			jdbcClient.sql("""
					INSERT INTO ocorrencia (categoria_id, descricao, bairro, endereco_referencia, criado_em, atualizado_em)
					VALUES ((SELECT id FROM categoria WHERE nome = 'Outros'), :descricao, 'Centro', 'Praça Central',
					        '2026-11-10 08:00:00', '2026-11-10 08:00:00')
					""").param("descricao", descricao).update();
			jdbcClient.sql("""
					INSERT INTO historico_status (ocorrencia_id, status_anterior, status_novo, alterado_em)
					VALUES (last_insert_rowid(), NULL, NULL, '2026-11-10 08:00:00')
					""").update();
		})).isInstanceOf(DataAccessException.class).hasMessageContaining("SQLITE_CONSTRAINT_NOTNULL");

		int gravadas = jdbcClient.sql("SELECT COUNT(*) FROM ocorrencia WHERE descricao = :descricao")
				.param("descricao", descricao)
				.query(Integer.class)
				.single();
		assertThat(gravadas).isZero();
	}

	private int contarCategorias() {
		return jdbcClient.sql("SELECT COUNT(*) FROM categoria").query(Integer.class).single();
	}

}
