package br.com.bairroemfoco.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricoStatusRepositoryTests {

	private static final LocalDateTime CRIADO_EM = LocalDateTime.of(2026, 11, 10, 8, 0, 0);
	private static final LocalDateTime EM_ANDAMENTO_EM = LocalDateTime.of(2026, 11, 11, 9, 0, 0);
	private static final LocalDateTime RESOLVIDO_EM = LocalDateTime.of(2026, 11, 12, 17, 30, 0);

	@Autowired
	private HistoricoStatusRepository repository;

	@Autowired
	private OcorrenciaRepository ocorrenciaRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	private long ocorrenciaId;

	@BeforeEach
	void criarOcorrencia() {
		ocorrenciaId = novaOcorrencia();
	}

	@Test
	void inserirRegistroDeCriacaoComStatusAnteriorNulo() {
		HistoricoStatus gravado = repository.inserir(
				new HistoricoStatus(null, ocorrenciaId, null, StatusOcorrencia.ABERTO, CRIADO_EM));

		assertThat(gravado.id()).isPositive();
		assertThat(gravado.ocorrenciaId()).isEqualTo(ocorrenciaId);
		assertThat(gravado.statusAnterior()).isNull();
		assertThat(gravado.statusNovo()).isEqualTo(StatusOcorrencia.ABERTO);
		assertThat(gravado.alteradoEm()).isEqualTo(CRIADO_EM);
	}

	@Test
	void inserirRecusaRegistroQueJaTemId() {
		HistoricoStatus comId = new HistoricoStatus(5L, ocorrenciaId, null, StatusOcorrencia.ABERTO, CRIADO_EM);

		assertThatThrownBy(() -> repository.inserir(comId)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void inserirPropagaViolacaoDeChaveEstrangeira() {
		assertThatThrownBy(() -> repository.inserir(
				new HistoricoStatus(null, 999, null, StatusOcorrencia.ABERTO, CRIADO_EM)))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("FOREIGN KEY constraint failed");
	}

	@Test
	void listarPorOcorrenciaRetornaALinhaDoTempoEmOrdemCronologica() {
		// Inseridos fora de ordem para provar que a ordenação vem da consulta
		repository.inserir(new HistoricoStatus(null, ocorrenciaId, StatusOcorrencia.EM_ANDAMENTO,
				StatusOcorrencia.RESOLVIDO, RESOLVIDO_EM));
		repository.inserir(new HistoricoStatus(null, ocorrenciaId, null, StatusOcorrencia.ABERTO, CRIADO_EM));
		repository.inserir(new HistoricoStatus(null, ocorrenciaId, StatusOcorrencia.ABERTO,
				StatusOcorrencia.EM_ANDAMENTO, EM_ANDAMENTO_EM));

		assertThat(repository.listarPorOcorrencia(ocorrenciaId))
				.extracting(HistoricoStatus::statusNovo)
				.containsExactly(StatusOcorrencia.ABERTO, StatusOcorrencia.EM_ANDAMENTO, StatusOcorrencia.RESOLVIDO);
	}

	@Test
	void alteracoesNoMesmoSegundoMantemAOrdemDeRegistro() {
		repository.inserir(new HistoricoStatus(null, ocorrenciaId, null, StatusOcorrencia.ABERTO, CRIADO_EM));
		repository.inserir(new HistoricoStatus(null, ocorrenciaId, StatusOcorrencia.ABERTO,
				StatusOcorrencia.RESOLVIDO, CRIADO_EM));

		assertThat(repository.listarPorOcorrencia(ocorrenciaId))
				.extracting(HistoricoStatus::statusNovo)
				.containsExactly(StatusOcorrencia.ABERTO, StatusOcorrencia.RESOLVIDO);
	}

	@Test
	void listarPorOcorrenciaNaoMisturaHistoricosDeOutrasOcorrencias() {
		long outraOcorrenciaId = novaOcorrencia();
		repository.inserir(new HistoricoStatus(null, ocorrenciaId, null, StatusOcorrencia.ABERTO, CRIADO_EM));
		repository.inserir(new HistoricoStatus(null, outraOcorrenciaId, null, StatusOcorrencia.ABERTO, CRIADO_EM));
		repository.inserir(new HistoricoStatus(null, outraOcorrenciaId, StatusOcorrencia.ABERTO,
				StatusOcorrencia.EM_ANDAMENTO, EM_ANDAMENTO_EM));

		assertThat(repository.listarPorOcorrencia(ocorrenciaId)).hasSize(1)
				.allSatisfy(h -> assertThat(h.ocorrenciaId()).isEqualTo(ocorrenciaId));
		assertThat(repository.listarPorOcorrencia(outraOcorrenciaId)).hasSize(2);
	}

	@Test
	void listarPorOcorrenciaSemHistoricoRetornaListaVazia() {
		assertThat(repository.listarPorOcorrencia(ocorrenciaId)).isEmpty();
	}

	private long novaOcorrencia() {
		long categoriaId = categoriaRepository.listarTodas().getFirst().id();
		return ocorrenciaRepository.inserir(new Ocorrencia(null, categoriaId, "Poste apagado há uma semana",
				"Centro", "Em frente à padaria", StatusOcorrencia.ABERTO, CRIADO_EM, CRIADO_EM, null)).id();
	}

}
