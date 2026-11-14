package br.com.bairroemfoco.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OcorrenciaRepositoryTests {

	private static final LocalDateTime CRIADO_EM = LocalDateTime.of(2026, 11, 10, 8, 0, 0);
	private static final LocalDateTime DEPOIS = LocalDateTime.of(2026, 11, 12, 17, 30, 0);

	@Autowired
	private OcorrenciaRepository repository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	private long buraco;
	private long iluminacao;

	@BeforeEach
	void carregarCategorias() {
		buraco = idCategoria("Buraco na via");
		iluminacao = idCategoria("Iluminação pública");
	}

	@Test
	void inserirGeraIdEGravaTodosOsCampos() {
		Ocorrencia gravada = repository.inserir(nova(buraco, "Centro"));

		assertThat(gravada.id()).isPositive();
		assertThat(gravada).usingRecursiveComparison().ignoringFields("id").isEqualTo(nova(buraco, "Centro"));
		assertThat(repository.buscarPorId(gravada.id())).contains(gravada);
	}

	@Test
	void inserirDescartaFracoesDeSegundo() {
		LocalDateTime comNanos = CRIADO_EM.withNano(123_456_789);
		Ocorrencia ocorrencia = new Ocorrencia(null, buraco, "Buraco grande na faixa da direita", "Centro",
				"Rua das Flores, 100", StatusOcorrencia.ABERTO, comNanos, comNanos, null);

		Ocorrencia gravada = repository.inserir(ocorrencia);

		assertThat(gravada.criadoEm()).isEqualTo(CRIADO_EM);
	}

	@Test
	void inserirRecusaOcorrenciaQueJaTemId() {
		Ocorrencia comId = new Ocorrencia(10L, buraco, "Buraco grande na faixa da direita", "Centro",
				"Rua das Flores, 100", StatusOcorrencia.ABERTO, CRIADO_EM, CRIADO_EM, null);

		assertThatThrownBy(() -> repository.inserir(comId)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void inserirPropagaViolacaoDeChaveEstrangeira() {
		assertThatThrownBy(() -> repository.inserir(nova(999, "Centro")))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("FOREIGN KEY constraint failed");
	}

	@ParameterizedTest
	@EnumSource(StatusOcorrencia.class)
	void todosOsValoresDoEnumSaoAceitosPeloSchema(StatusOcorrencia status) {
		LocalDateTime resolvidoEm = status == StatusOcorrencia.RESOLVIDO ? DEPOIS : null;
		Ocorrencia ocorrencia = new Ocorrencia(null, buraco, "Buraco grande na faixa da direita", "Centro",
				"Rua das Flores, 100", status, CRIADO_EM, CRIADO_EM, resolvidoEm);

		assertThat(repository.inserir(ocorrencia).status()).isEqualTo(status);
	}

	@Test
	void buscarPorIdInexistenteRetornaVazio() {
		assertThat(repository.buscarPorId(999)).isEmpty();
	}

	@Test
	void listarSemFiltrosRetornaDasMaisRecentesParaAsMaisAntigas() {
		long primeira = repository.inserir(nova(buraco, "Centro")).id();
		long segunda = repository.inserir(nova(iluminacao, "Jardim América")).id();
		long terceira = repository.inserir(nova(buraco, "Vila Nova")).id();

		assertThat(repository.listar(null, null)).extracting(Ocorrencia::id)
				.containsExactly(terceira, segunda, primeira);
	}

	@Test
	void listarFiltraPorStatusPorCategoriaOuAmbos() {
		long buracoAberto = repository.inserir(nova(buraco, "Centro")).id();
		long iluminacaoAberta = repository.inserir(nova(iluminacao, "Centro")).id();
		long buracoEmAndamento = repository.inserir(nova(buraco, "Centro")).id();
		repository.atualizarStatus(buracoEmAndamento, StatusOcorrencia.ABERTO, StatusOcorrencia.EM_ANDAMENTO, DEPOIS);

		assertThat(repository.listar(StatusOcorrencia.ABERTO, null)).extracting(Ocorrencia::id)
				.containsExactly(iluminacaoAberta, buracoAberto);
		assertThat(repository.listar(null, buraco)).extracting(Ocorrencia::id)
				.containsExactly(buracoEmAndamento, buracoAberto);
		assertThat(repository.listar(StatusOcorrencia.ABERTO, buraco)).extracting(Ocorrencia::id)
				.containsExactly(buracoAberto);
		assertThat(repository.listar(StatusOcorrencia.RESOLVIDO, null)).isEmpty();
	}

	@Test
	void atualizarStatusParaEmAndamentoNaoPreencheResolucao() {
		long id = repository.inserir(nova(buraco, "Centro")).id();

		boolean atualizou = repository.atualizarStatus(id, StatusOcorrencia.ABERTO, StatusOcorrencia.EM_ANDAMENTO, DEPOIS);

		Ocorrencia depois = repository.buscarPorId(id).orElseThrow();
		assertThat(atualizou).isTrue();
		assertThat(depois.status()).isEqualTo(StatusOcorrencia.EM_ANDAMENTO);
		assertThat(depois.criadoEm()).isEqualTo(CRIADO_EM);
		assertThat(depois.atualizadoEm()).isEqualTo(DEPOIS);
		assertThat(depois.resolvidoEm()).isNull();
	}

	@Test
	void atualizarStatusParaResolvidoPreencheDataDeResolucao() {
		long id = repository.inserir(nova(buraco, "Centro")).id();

		repository.atualizarStatus(id, StatusOcorrencia.ABERTO, StatusOcorrencia.RESOLVIDO, DEPOIS);

		Ocorrencia depois = repository.buscarPorId(id).orElseThrow();
		assertThat(depois.status()).isEqualTo(StatusOcorrencia.RESOLVIDO);
		assertThat(depois.atualizadoEm()).isEqualTo(DEPOIS);
		assertThat(depois.resolvidoEm()).isEqualTo(DEPOIS);
	}

	@Test
	void atualizarStatusNaoAlteraQuandoStatusAtualNaoConfere() {
		long id = repository.inserir(nova(buraco, "Centro")).id();

		boolean atualizou = repository.atualizarStatus(id, StatusOcorrencia.EM_ANDAMENTO, StatusOcorrencia.RESOLVIDO, DEPOIS);

		assertThat(atualizou).isFalse();
		assertThat(repository.buscarPorId(id).orElseThrow().status()).isEqualTo(StatusOcorrencia.ABERTO);
	}

	@Test
	void atualizarStatusDeOcorrenciaInexistenteRetornaFalso() {
		assertThat(repository.atualizarStatus(999, StatusOcorrencia.ABERTO, StatusOcorrencia.RESOLVIDO, DEPOIS)).isFalse();
	}

	private Ocorrencia nova(long categoriaId, String bairro) {
		return new Ocorrencia(null, categoriaId, "Buraco grande na faixa da direita", bairro,
				"Rua das Flores, 100", StatusOcorrencia.ABERTO, CRIADO_EM, CRIADO_EM, null);
	}

	private long idCategoria(String nome) {
		return categoriaRepository.listarTodas().stream()
				.filter(c -> c.nome().equals(nome))
				.map(Categoria::id)
				.findFirst()
				.orElseThrow();
	}

}
