package br.com.bairroemfoco.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import br.com.bairroemfoco.dto.NovaOcorrencia;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Prova que ocorrência e histórico são gravados na mesma transação: se o histórico falhar,
 * a ocorrência também é desfeita.
 *
 * Esta classe NÃO é @Transactional, para que a transação do próprio serviço seja confirmada
 * ou desfeita de verdade. Por isso os dados são apagados ao final de cada teste.
 *
 * A falha é simulada com uma trigger TEMPORÁRIA, que existe só na conexão atual e não altera
 * o schema. Funciona porque o pool tem uma única conexão (maximum-pool-size=1): o teste e o
 * serviço usam a mesma conexão.
 */
@SpringBootTest
@ActiveProfiles("test")
class OcorrenciaServiceTransacaoTests {

	@Autowired
	private OcorrenciaService service;

	@Autowired
	private JdbcClient jdbcClient;

	@AfterEach
	void limpar() {
		jdbcClient.sql("DROP TRIGGER IF EXISTS temp.falha_historico").update();
		jdbcClient.sql("DELETE FROM historico_status").update();
		jdbcClient.sql("DELETE FROM ocorrencia").update();
	}

	@Test
	void falhaNoHistoricoDesfazOCadastroDaOcorrencia() {
		simularFalhaNoHistorico();

		assertThatThrownBy(() -> service.cadastrar(valida()))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("Falha simulada");

		assertThat(contar("ocorrencia")).isZero();
		assertThat(contar("historico_status")).isZero();
	}

	@Test
	void falhaNoHistoricoDesfazAMudancaDeStatus() {
		Ocorrencia antes = service.cadastrar(valida());
		simularFalhaNoHistorico();

		assertThatThrownBy(() -> service.atualizarStatus(antes.id(), StatusOcorrencia.RESOLVIDO))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("Falha simulada");

		assertThat(service.buscarPorId(antes.id())).isEqualTo(antes);
		assertThat(contar("historico_status")).isEqualTo(1);
	}

	private void simularFalhaNoHistorico() {
		jdbcClient.sql("""
				CREATE TEMP TRIGGER falha_historico BEFORE INSERT ON historico_status
				BEGIN
				    SELECT RAISE(ABORT, 'Falha simulada no histórico');
				END
				""").update();
	}

	private NovaOcorrencia valida() {
		long categoriaId = jdbcClient.sql("SELECT id FROM categoria WHERE nome = 'Outros'").query(Long.class).single();
		return new NovaOcorrencia(categoriaId, "Lixo acumulado na esquina", "Centro", "Esquina da padaria");
	}

	private int contar(String tabela) {
		return jdbcClient.sql("SELECT COUNT(*) FROM " + tabela).query(Integer.class).single();
	}

}
