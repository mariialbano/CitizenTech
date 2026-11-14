package br.com.bairroemfoco.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.bairroemfoco.dto.NovaOcorrencia;
import br.com.bairroemfoco.exception.DadosInvalidosException;
import br.com.bairroemfoco.exception.OcorrenciaNaoEncontradaException;
import br.com.bairroemfoco.exception.TransicaoStatusInvalidaException;
import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;
import br.com.bairroemfoco.repository.CategoriaRepository;

/**
 * Fluxos completos do serviço sobre o SQLite de teste. Cada teste é desfeito ao final.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OcorrenciaServiceIntegrationTests {

	@Autowired
	private OcorrenciaService service;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private JdbcClient jdbcClient;

	@Autowired
	private Clock clock;

	private long categoriaId;

	@BeforeEach
	void escolherCategoria() {
		categoriaId = categoriaRepository.listarTodas().stream()
				.filter(c -> c.nome().equals("Buraco na via"))
				.map(Categoria::id)
				.findFirst()
				.orElseThrow();
	}

	// ---------- cadastro ----------

	@Test
	void cadastroValidoGravaOcorrenciaEHistoricoInicial() {
		LocalDateTime antes = LocalDateTime.now(clock);

		Ocorrencia gravada = service.cadastrar(valida());

		assertThat(service.buscarPorId(gravada.id())).isEqualTo(gravada);
		assertThat(gravada.status()).isEqualTo(StatusOcorrencia.ABERTO);
		assertThat(gravada.criadoEm()).isBetween(antes, LocalDateTime.now(clock));
		assertThat(gravada.atualizadoEm()).isEqualTo(gravada.criadoEm());
		assertThat(gravada.resolvidoEm()).isNull();

		List<HistoricoStatus> historico = service.listarHistorico(gravada.id());
		assertThat(historico).singleElement().satisfies(h -> {
			assertThat(h.statusAnterior()).isNull();
			assertThat(h.statusNovo()).isEqualTo(StatusOcorrencia.ABERTO);
			assertThat(h.alteradoEm()).isEqualTo(gravada.criadoEm());
		});
	}

	@Test
	void bairroEhGravadoNormalizado() {
		Ocorrencia gravada = service.cadastrar(new NovaOcorrencia(categoriaId, "Buraco grande na faixa da direita",
				"  Vila \t\t Nova  ", "Rua das Flores, 100"));

		assertThat(service.buscarPorId(gravada.id()).bairro()).isEqualTo("Vila Nova");
	}

	@Test
	void cadastroInvalidoNaoGravaDados() {
		assertThatThrownBy(() -> service.cadastrar(new NovaOcorrencia(categoriaId, "curta", "Centro", "Rua das Flores")))
				.isInstanceOf(DadosInvalidosException.class);

		assertThat(contar("ocorrencia")).isZero();
		assertThat(contar("historico_status")).isZero();
	}

	@Test
	void categoriaInexistenteNaoGravaDados() {
		assertThatThrownBy(() -> service.cadastrar(
				new NovaOcorrencia(999L, "Buraco grande na faixa da direita", "Centro", "Rua das Flores")))
				.isInstanceOfSatisfying(DadosInvalidosException.class,
						e -> assertThat(e.getErros()).containsOnlyKeys("categoriaId"));

		assertThat(contar("ocorrencia")).isZero();
		assertThat(contar("historico_status")).isZero();
	}

	// ---------- mudança de status ----------

	@Test
	void fluxoCompletoAbertoEmAndamentoResolvido() {
		long id = service.cadastrar(valida()).id();

		Ocorrencia emAndamento = service.atualizarStatus(id, StatusOcorrencia.EM_ANDAMENTO);
		assertThat(emAndamento.status()).isEqualTo(StatusOcorrencia.EM_ANDAMENTO);
		assertThat(emAndamento.resolvidoEm()).isNull();

		Ocorrencia resolvida = service.atualizarStatus(id, StatusOcorrencia.RESOLVIDO);
		assertThat(resolvida.status()).isEqualTo(StatusOcorrencia.RESOLVIDO);
		assertThat(resolvida.resolvidoEm()).isEqualTo(resolvida.atualizadoEm());

		List<HistoricoStatus> historico = service.listarHistorico(id);
		assertThat(historico).extracting(HistoricoStatus::statusAnterior, HistoricoStatus::statusNovo)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(null, StatusOcorrencia.ABERTO),
						org.assertj.core.groups.Tuple.tuple(StatusOcorrencia.ABERTO, StatusOcorrencia.EM_ANDAMENTO),
						org.assertj.core.groups.Tuple.tuple(StatusOcorrencia.EM_ANDAMENTO, StatusOcorrencia.RESOLVIDO));
		// A data da ocorrência e a do último registro do histórico são a mesma
		assertThat(historico.getLast().alteradoEm()).isEqualTo(resolvida.atualizadoEm());
	}

	@Test
	void abertoPodeSerResolvidoDiretamente() {
		long id = service.cadastrar(valida()).id();

		Ocorrencia resolvida = service.atualizarStatus(id, StatusOcorrencia.RESOLVIDO);

		assertThat(resolvida.status()).isEqualTo(StatusOcorrencia.RESOLVIDO);
		assertThat(resolvida.resolvidoEm()).isNotNull();
		assertThat(service.listarHistorico(id)).hasSize(2);
	}

	@Test
	void transicaoProibidaNaoAlteraNadaNoBanco() {
		long id = service.cadastrar(valida()).id();
		service.atualizarStatus(id, StatusOcorrencia.EM_ANDAMENTO);
		Ocorrencia antes = service.buscarPorId(id);

		assertThatThrownBy(() -> service.atualizarStatus(id, StatusOcorrencia.ABERTO))
				.isInstanceOf(TransicaoStatusInvalidaException.class);

		assertThat(service.buscarPorId(id)).isEqualTo(antes);
		assertThat(service.listarHistorico(id)).hasSize(2);
	}

	@Test
	void ocorrenciaResolvidaNaoPodeSerReaberta() {
		long id = service.cadastrar(valida()).id();
		Ocorrencia resolvida = service.atualizarStatus(id, StatusOcorrencia.RESOLVIDO);

		for (StatusOcorrencia novo : StatusOcorrencia.values()) {
			assertThatThrownBy(() -> service.atualizarStatus(id, novo))
					.isInstanceOf(TransicaoStatusInvalidaException.class);
		}

		assertThat(service.buscarPorId(id)).isEqualTo(resolvida);
		assertThat(service.listarHistorico(id)).hasSize(2);
	}

	// ---------- consultas e ocorrência inexistente ----------

	@Test
	void ocorrenciaInexistenteEhTratadaEmTodasAsOperacoes() {
		assertThatThrownBy(() -> service.buscarPorId(999)).isInstanceOf(OcorrenciaNaoEncontradaException.class);
		assertThatThrownBy(() -> service.listarHistorico(999)).isInstanceOf(OcorrenciaNaoEncontradaException.class);
		assertThatThrownBy(() -> service.atualizarStatus(999, StatusOcorrencia.RESOLVIDO))
				.isInstanceOf(OcorrenciaNaoEncontradaException.class);
		assertThat(contar("historico_status")).isZero();
	}

	@Test
	void listarAplicaOsFiltrosDoRepositorio() {
		long aberta = service.cadastrar(valida()).id();
		long resolvida = service.cadastrar(valida()).id();
		service.atualizarStatus(resolvida, StatusOcorrencia.RESOLVIDO);

		assertThat(service.listar(null, null)).extracting(Ocorrencia::id).containsExactly(resolvida, aberta);
		assertThat(service.listar(StatusOcorrencia.ABERTO, categoriaId)).extracting(Ocorrencia::id)
				.containsExactly(aberta);
	}

	private NovaOcorrencia valida() {
		return new NovaOcorrencia(categoriaId, "Buraco grande na faixa da direita", "Centro", "Rua das Flores, 100");
	}

	private int contar(String tabela) {
		return jdbcClient.sql("SELECT COUNT(*) FROM " + tabela).query(Integer.class).single();
	}

}
