package br.com.bairroemfoco.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restrições de integridade e relacionamentos do schema.sql.
 * Cada teste roda em uma transação desfeita ao final, então os testes não interferem entre si.
 * O Spring não traduz os códigos de erro do SQLite, por isso as violações são verificadas
 * pelo código de origem (ex.: SQLITE_CONSTRAINT_CHECK) contido na mensagem da exceção.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModeloDadosTests {

	// Inserção inválida gera SQLITE_CONSTRAINT_FOREIGNKEY, mas o ON DELETE RESTRICT gera
	// SQLITE_CONSTRAINT_TRIGGER. As duas mensagens trazem este texto.
	private static final String FK = "FOREIGN KEY constraint failed";
	private static final String CHECK = "SQLITE_CONSTRAINT_CHECK";
	private static final String NOT_NULL = "SQLITE_CONSTRAINT_NOTNULL";
	private static final String UNIQUE = "SQLITE_CONSTRAINT_UNIQUE";

	private static final String CRIADO_EM = "2026-11-10 08:00:00";
	private static final String DEPOIS = "2026-11-12 17:30:00";
	private static final String ANTES = "2026-11-09 08:00:00";

	@Autowired
	private JdbcClient jdbcClient;

	// ---------- categoria ----------

	@Test
	void cincoCategoriasIniciaisSaoCarregadas() {
		List<String> nomes = jdbcClient.sql("SELECT nome FROM categoria").query(String.class).list();

		assertThat(nomes).containsExactlyInAnyOrder("Buraco na via", "Iluminação pública",
				"Descarte irregular de lixo", "Calçada danificada", "Outros");
	}

	@Test
	void nomeDeCategoriaEhUnicoSemDiferenciarMaiusculas() {
		assertViolacao(UNIQUE, () -> jdbcClient.sql("INSERT INTO categoria (nome) VALUES ('OUTROS')").update());
	}

	@Test
	void nomeDeCategoriaNaoAceitaEspacosNasPontas() {
		assertViolacao(CHECK, () -> jdbcClient.sql("INSERT INTO categoria (nome) VALUES (' Praças ')").update());
	}

	@Test
	void categoriaEmUsoNaoPodeSerExcluida() {
		long categoriaId = idCategoria("Buraco na via");
		inserirOcorrencia(ocorrenciaValida());

		assertViolacao(FK, () -> jdbcClient.sql("DELETE FROM categoria WHERE id = :id").param("id", categoriaId).update());
	}

	// ---------- ocorrencia ----------

	@Test
	void ocorrenciaValidaRecebeStatusAbertoPorPadrao() {
		// Sem a coluna status no INSERT, para exercitar o DEFAULT 'ABERTO'
		jdbcClient.sql("""
				INSERT INTO ocorrencia (categoria_id, descricao, bairro, endereco_referencia, criado_em, atualizado_em)
				VALUES (:categoria_id, :descricao, :bairro, :endereco_referencia, :criado_em, :atualizado_em)
				""").params(ocorrenciaValida()).update();

		Map<String, Object> linha = jdbcClient.sql(
				"SELECT status, resolvido_em FROM ocorrencia WHERE id = last_insert_rowid()")
				.query()
				.singleRow();
		assertThat(linha.get("status")).isEqualTo("ABERTO");
		assertThat(linha.get("resolvido_em")).isNull();
	}

	@Test
	void ocorrenciaResolvidaComDataDeResolucaoEhAceita() {
		Map<String, Object> campos = ocorrenciaValida();
		campos.putAll(campos("status", "RESOLVIDO", "atualizado_em", DEPOIS, "resolvido_em", DEPOIS));

		assertThat(inserirOcorrencia(campos)).isPositive();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("ocorrenciasInvalidas")
	void ocorrenciaInvalidaEhRejeitada(String caso, Map<String, Object> alteracoes, String violacao) {
		Map<String, Object> campos = ocorrenciaValida();
		campos.putAll(alteracoes);

		assertViolacao(violacao, () -> inserirOcorrencia(campos));
	}

	static Stream<Arguments> ocorrenciasInvalidas() {
		return Stream.of(
				Arguments.of("categoria ausente", campos("categoria_id", null), NOT_NULL),
				Arguments.of("categoria inexistente", campos("categoria_id", 999), FK),
				Arguments.of("descrição ausente", campos("descricao", null), NOT_NULL),
				Arguments.of("descrição curta", campos("descricao", "Buraco"), CHECK),
				Arguments.of("descrição só com espaços", campos("descricao", " ".repeat(20)), CHECK),
				Arguments.of("descrição longa", campos("descricao", "x".repeat(1001)), CHECK),
				Arguments.of("bairro ausente", campos("bairro", null), NOT_NULL),
				Arguments.of("bairro com espaços nas pontas", campos("bairro", " Centro "), CHECK),
				Arguments.of("bairro com espaços duplos", campos("bairro", "Vila  Nova"), CHECK),
				Arguments.of("bairro curto", campos("bairro", "A"), CHECK),
				Arguments.of("endereço ausente", campos("endereco_referencia", null), NOT_NULL),
				Arguments.of("endereço curto", campos("endereco_referencia", "Rua"), CHECK),
				Arguments.of("status desconhecido", campos("status", "FECHADO"), CHECK),
				Arguments.of("status em minúsculas", campos("status", "aberto"), CHECK),
				Arguments.of("data de criação ausente", campos("criado_em", null), NOT_NULL),
				Arguments.of("atualização anterior à criação", campos("atualizado_em", ANTES), CHECK),
				Arguments.of("resolvida sem data de resolução", campos("status", "RESOLVIDO"), CHECK),
				Arguments.of("aberta com data de resolução", campos("resolvido_em", DEPOIS), CHECK),
				Arguments.of("resolução anterior à criação",
						campos("status", "RESOLVIDO", "resolvido_em", ANTES), CHECK));
	}

	@Test
	void bairroEhAgrupadoSemDiferenciarMaiusculas() {
		inserirOcorrencia(ocorrenciaValida());
		Map<String, Object> outra = ocorrenciaValida();
		outra.put("bairro", "CENTRO");
		inserirOcorrencia(outra);

		List<Integer> totais = jdbcClient.sql("SELECT COUNT(*) FROM ocorrencia GROUP BY bairro")
				.query(Integer.class)
				.list();
		assertThat(totais).containsExactly(2);
	}

	@Test
	void ocorrenciaComHistoricoNaoPodeSerExcluida() {
		long id = inserirOcorrencia(ocorrenciaValida());
		inserirHistorico(id, null, "ABERTO", CRIADO_EM);

		assertViolacao(FK, () -> jdbcClient.sql("DELETE FROM ocorrencia WHERE id = :id").param("id", id).update());
	}

	// ---------- historico_status ----------

	@Test
	void historicoRegistraALinhaDoTempoDaOcorrencia() {
		long id = inserirOcorrencia(ocorrenciaValida());
		inserirHistorico(id, null, "ABERTO", CRIADO_EM);
		inserirHistorico(id, "ABERTO", "EM_ANDAMENTO", "2026-11-11 09:00:00");
		inserirHistorico(id, "EM_ANDAMENTO", "RESOLVIDO", DEPOIS);

		List<String> linhaDoTempo = jdbcClient.sql("""
				SELECT coalesce(status_anterior, '-') || ' > ' || status_novo
				  FROM historico_status WHERE ocorrencia_id = :id ORDER BY alterado_em
				""").param("id", id).query(String.class).list();
		assertThat(linhaDoTempo).containsExactly("- > ABERTO", "ABERTO > EM_ANDAMENTO", "EM_ANDAMENTO > RESOLVIDO");
	}

	@Test
	void historicoExigeOcorrenciaExistente() {
		assertViolacao(FK, () -> inserirHistorico(999, null, "ABERTO", CRIADO_EM));
	}

	@Test
	void historicoNaoAceitaStatusAnteriorIgualAoNovo() {
		long id = inserirOcorrencia(ocorrenciaValida());

		assertViolacao(CHECK, () -> inserirHistorico(id, "ABERTO", "ABERTO", CRIADO_EM));
	}

	@Test
	void historicoNaoAceitaStatusDesconhecido() {
		long id = inserirOcorrencia(ocorrenciaValida());

		assertViolacao(CHECK, () -> inserirHistorico(id, null, "CANCELADO", CRIADO_EM));
		assertViolacao(CHECK, () -> inserirHistorico(id, "PENDENTE", "ABERTO", CRIADO_EM));
	}

	@Test
	void historicoExigeStatusNovoEData() {
		long id = inserirOcorrencia(ocorrenciaValida());

		assertViolacao(NOT_NULL, () -> inserirHistorico(id, null, null, CRIADO_EM));
		assertViolacao(NOT_NULL, () -> inserirHistorico(id, null, "ABERTO", null));
	}

	// ---------- consultas do dashboard ----------

	@Test
	void categoriasSemOcorrenciasRetornamZeroNoDashboard() {
		inserirOcorrencia(ocorrenciaValida());

		List<Map<String, Object>> linhas = jdbcClient.sql("""
				SELECT c.nome,
				       COUNT(o.id)                                AS total,
				       COALESCE(SUM(o.status = 'ABERTO'), 0)       AS abertas,
				       COALESCE(SUM(o.status = 'EM_ANDAMENTO'), 0) AS em_andamento,
				       COALESCE(SUM(o.status = 'RESOLVIDO'), 0)    AS resolvidas
				  FROM categoria c
				  LEFT JOIN ocorrencia o ON o.categoria_id = c.id
				 GROUP BY c.id, c.nome
				""").query().listOfRows();

		assertThat(linhas).hasSize(5);
		Map<String, Object> outros = linhas.stream().filter(l -> "Outros".equals(l.get("nome"))).findFirst().orElseThrow();
		assertThat(outros).containsEntry("total", 0).containsEntry("abertas", 0)
				.containsEntry("em_andamento", 0).containsEntry("resolvidas", 0);
		Map<String, Object> buraco = linhas.stream().filter(l -> "Buraco na via".equals(l.get("nome"))).findFirst().orElseThrow();
		assertThat(buraco).containsEntry("total", 1).containsEntry("abertas", 1);
	}

	// ---------- utilitários ----------

	private Map<String, Object> ocorrenciaValida() {
		return campos(
				"categoria_id", idCategoria("Buraco na via"),
				"descricao", "Buraco grande na faixa da direita",
				"bairro", "Centro",
				"endereco_referencia", "Rua das Flores, 100",
				"status", "ABERTO",
				"criado_em", CRIADO_EM,
				"atualizado_em", CRIADO_EM,
				"resolvido_em", null);
	}

	private long inserirOcorrencia(Map<String, Object> campos) {
		KeyHolder chave = new GeneratedKeyHolder();
		jdbcClient.sql("""
				INSERT INTO ocorrencia (categoria_id, descricao, bairro, endereco_referencia, status,
				                        criado_em, atualizado_em, resolvido_em)
				VALUES (:categoria_id, :descricao, :bairro, :endereco_referencia, :status,
				        :criado_em, :atualizado_em, :resolvido_em)
				""").params(campos).update(chave);
		return chave.getKey().longValue();
	}

	private void inserirHistorico(long ocorrenciaId, String anterior, String novo, String alteradoEm) {
		Map<String, Object> campos = campos("ocorrencia_id", ocorrenciaId, "status_anterior", anterior,
				"status_novo", novo, "alterado_em", alteradoEm);
		jdbcClient.sql("""
				INSERT INTO historico_status (ocorrencia_id, status_anterior, status_novo, alterado_em)
				VALUES (:ocorrencia_id, :status_anterior, :status_novo, :alterado_em)
				""").params(campos).update();
	}

	private long idCategoria(String nome) {
		return jdbcClient.sql("SELECT id FROM categoria WHERE nome = :nome").param("nome", nome).query(Long.class).single();
	}

	/** Map.of não aceita valores nulos, necessários para testar campos ausentes. */
	private static Map<String, Object> campos(Object... chavesEValores) {
		Map<String, Object> mapa = new HashMap<>();
		for (int i = 0; i < chavesEValores.length; i += 2) {
			mapa.put((String) chavesEValores[i], chavesEValores[i + 1]);
		}
		return mapa;
	}

	private static void assertViolacao(String codigo, ThrowingCallable acao) {
		assertThatThrownBy(acao).isInstanceOf(DataAccessException.class).hasMessageContaining(codigo);
	}

}
