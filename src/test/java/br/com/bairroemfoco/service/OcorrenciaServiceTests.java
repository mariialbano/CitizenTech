package br.com.bairroemfoco.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import br.com.bairroemfoco.config.ClockConfig;
import br.com.bairroemfoco.dto.NovaOcorrencia;
import br.com.bairroemfoco.exception.ConflitoAtualizacaoException;
import br.com.bairroemfoco.exception.DadosInvalidosException;
import br.com.bairroemfoco.exception.OcorrenciaNaoEncontradaException;
import br.com.bairroemfoco.exception.TransicaoStatusInvalidaException;
import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;
import br.com.bairroemfoco.repository.CategoriaRepository;
import br.com.bairroemfoco.repository.HistoricoStatusRepository;
import br.com.bairroemfoco.repository.OcorrenciaRepository;

/**
 * Testes unitários das regras do serviço, com repositórios simulados (Mockito) e relógio fixo.
 * Os fluxos completos no banco estão em OcorrenciaServiceIntegrationTests.
 */
class OcorrenciaServiceTests {

	/** 11:00 UTC = 08:00 em São Paulo */
	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 8, 0, 0);
	private static final LocalDateTime ONTEM = AGORA.minusDays(1);
	private static final long CATEGORIA_ID = 1;
	private static final long OCORRENCIA_ID = 42;

	private static final jakarta.validation.ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

	private OcorrenciaRepository ocorrenciaRepository;
	private HistoricoStatusRepository historicoRepository;
	private CategoriaRepository categoriaRepository;
	private OcorrenciaService service;

	@BeforeEach
	void configurar() {
		ocorrenciaRepository = mock(OcorrenciaRepository.class);
		historicoRepository = mock(HistoricoStatusRepository.class);
		categoriaRepository = mock(CategoriaRepository.class);
		Validator validator = FABRICA.getValidator();
		Clock relogioFixo = Clock.fixed(Instant.parse("2026-11-10T11:00:00Z"), ClockConfig.FUSO_HORARIO);
		service = new OcorrenciaService(ocorrenciaRepository, historicoRepository, categoriaRepository, validator,
				relogioFixo);

		when(categoriaRepository.buscarPorId(CATEGORIA_ID)).thenReturn(Optional.of(new Categoria(CATEGORIA_ID, "Outros")));
		when(ocorrenciaRepository.inserir(any())).thenAnswer(chamada -> comId(chamada.getArgument(0)));
	}

	@AfterAll
	static void fecharFabrica() {
		FABRICA.close();
	}

	// ---------- normalização do bairro ----------

	@ParameterizedTest(name = "[{0}] -> [{1}]")
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = {
			"Centro                 | Centro",
			"\"  Vila   Nova  \"    | Vila Nova",
			"\"Jardim\tAmérica\"    | Jardim América",
			"\"Jardim\n  América\"  | Jardim América",
			"\"Vila Mariana\"  | Vila Mariana",
			"\"  Sé \t\"       | Sé" })
	void normalizaBairro(String informado, String esperado) {
		assertThat(OcorrenciaService.normalizarBairro(informado)).isEqualTo(esperado);
	}

	@Test
	void cadastroGravaBairroNormalizadoETextosAparados() {
		Ocorrencia gravada = service.cadastrar(new NovaOcorrencia(CATEGORIA_ID, "  Buraco grande na faixa  ",
				"  Vila \t  Nova ", "  Rua das Flores, 100  "));

		assertThat(gravada.bairro()).isEqualTo("Vila Nova");
		assertThat(gravada.descricao()).isEqualTo("Buraco grande na faixa");
		assertThat(gravada.enderecoReferencia()).isEqualTo("Rua das Flores, 100");
	}

	// ---------- cadastro ----------

	@Test
	void cadastroUsaODataDoRelogioEGravaHistoricoInicialComAMesmaData() {
		Ocorrencia gravada = service.cadastrar(valida());

		assertThat(gravada.status()).isEqualTo(StatusOcorrencia.ABERTO);
		assertThat(gravada.criadoEm()).isEqualTo(AGORA);
		assertThat(gravada.atualizadoEm()).isEqualTo(AGORA);
		assertThat(gravada.resolvidoEm()).isNull();

		HistoricoStatus historico = historicoGravado();
		assertThat(historico.ocorrenciaId()).isEqualTo(gravada.id());
		assertThat(historico.statusAnterior()).isNull();
		assertThat(historico.statusNovo()).isEqualTo(StatusOcorrencia.ABERTO);
		assertThat(historico.alteradoEm()).isEqualTo(AGORA);
	}

	@Test
	void cadastroInvalidoListaTodosOsCamposENaoAcessaOBanco() {
		NovaOcorrencia invalida = new NovaOcorrencia(null, "curta", "A", "Rua");

		assertThatThrownBy(() -> service.cadastrar(invalida))
				.isInstanceOfSatisfying(DadosInvalidosException.class, e -> assertThat(e.getErros())
						.containsOnlyKeys("categoriaId", "descricao", "bairro", "enderecoReferencia"));
		verifyNoInteractions(categoriaRepository, ocorrenciaRepository, historicoRepository);
	}

	@Test
	void campoSoComEspacosGeraUmaUnicaMensagemDeObrigatorio() {
		NovaOcorrencia invalida = new NovaOcorrencia(CATEGORIA_ID, "Buraco grande na faixa", "   ", "Rua das Flores");

		assertThatThrownBy(() -> service.cadastrar(invalida))
				.isInstanceOfSatisfying(DadosInvalidosException.class, e -> assertThat(e.getErros())
						.containsExactly(java.util.Map.entry("bairro", "O bairro é obrigatório")));
	}

	@Test
	void limitesDeTamanhoSaoValidadosAposANormalizacao() {
		// 9 caracteres úteis + espaços: inválido mesmo com mais de 10 caracteres no total
		NovaOcorrencia invalida = new NovaOcorrencia(CATEGORIA_ID, "  123456789  ", "Centro", "Rua das Flores");

		assertThatThrownBy(() -> service.cadastrar(invalida))
				.isInstanceOfSatisfying(DadosInvalidosException.class,
						e -> assertThat(e.getErros()).containsOnlyKeys("descricao"));
	}

	@Test
	void cadastroNuloEhRejeitado() {
		assertThatThrownBy(() -> service.cadastrar(null)).isInstanceOf(DadosInvalidosException.class);
		verifyNoInteractions(ocorrenciaRepository, historicoRepository);
	}

	@Test
	void categoriaInexistenteEhRejeitadaSemGravar() {
		NovaOcorrencia comCategoriaInexistente = new NovaOcorrencia(999L, "Buraco grande na faixa", "Centro",
				"Rua das Flores");

		assertThatThrownBy(() -> service.cadastrar(comCategoriaInexistente))
				.isInstanceOfSatisfying(DadosInvalidosException.class, e -> assertThat(e.getErros())
						.containsExactly(java.util.Map.entry("categoriaId", "Categoria não encontrada")));
		verifyNoInteractions(ocorrenciaRepository, historicoRepository);
	}

	// ---------- mudança de status ----------

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({ "ABERTO, EM_ANDAMENTO", "ABERTO, RESOLVIDO", "EM_ANDAMENTO, RESOLVIDO" })
	void transicaoPermitidaAtualizaEGravaHistoricoComAMesmaData(StatusOcorrencia atual, StatusOcorrencia novo) {
		existente(atual);
		when(ocorrenciaRepository.atualizarStatus(OCORRENCIA_ID, atual, novo, AGORA)).thenReturn(true);

		service.atualizarStatus(OCORRENCIA_ID, novo);

		verify(ocorrenciaRepository).atualizarStatus(OCORRENCIA_ID, atual, novo, AGORA);
		HistoricoStatus historico = historicoGravado();
		assertThat(historico.ocorrenciaId()).isEqualTo(OCORRENCIA_ID);
		assertThat(historico.statusAnterior()).isEqualTo(atual);
		assertThat(historico.statusNovo()).isEqualTo(novo);
		assertThat(historico.alteradoEm()).isEqualTo(AGORA);
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"ABERTO, ABERTO",
			"EM_ANDAMENTO, ABERTO",
			"EM_ANDAMENTO, EM_ANDAMENTO",
			"RESOLVIDO, ABERTO",
			"RESOLVIDO, EM_ANDAMENTO",
			"RESOLVIDO, RESOLVIDO" })
	void transicaoProibidaEhRejeitadaSemGravar(StatusOcorrencia atual, StatusOcorrencia novo) {
		existente(atual);

		assertThatThrownBy(() -> service.atualizarStatus(OCORRENCIA_ID, novo))
				.isInstanceOfSatisfying(TransicaoStatusInvalidaException.class, e -> {
					assertThat(e.getStatusAtual()).isEqualTo(atual);
					assertThat(e.getStatusSolicitado()).isEqualTo(novo);
				});
		verify(ocorrenciaRepository, never()).atualizarStatus(anyLong(), any(), any(), any());
		verifyNoInteractions(historicoRepository);
	}

	@Test
	void ocorrenciaResolvidaInformaQueNaoPodeSerReaberta() {
		existente(StatusOcorrencia.RESOLVIDO);

		assertThatThrownBy(() -> service.atualizarStatus(OCORRENCIA_ID, StatusOcorrencia.ABERTO))
				.isInstanceOf(TransicaoStatusInvalidaException.class)
				.hasMessageContaining("não pode ser reaberta");
	}

	@Test
	void conflitoDeAtualizacaoNaoGravaHistorico() {
		existente(StatusOcorrencia.ABERTO);
		// Outra operação mudou o status entre a leitura e o UPDATE: nenhuma linha é afetada
		when(ocorrenciaRepository.atualizarStatus(OCORRENCIA_ID, StatusOcorrencia.ABERTO,
				StatusOcorrencia.EM_ANDAMENTO, AGORA)).thenReturn(false);

		assertThatThrownBy(() -> service.atualizarStatus(OCORRENCIA_ID, StatusOcorrencia.EM_ANDAMENTO))
				.isInstanceOf(ConflitoAtualizacaoException.class);
		verifyNoInteractions(historicoRepository);
	}

	@Test
	void atualizarOcorrenciaInexistenteEhRejeitado() {
		when(ocorrenciaRepository.buscarPorId(OCORRENCIA_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.atualizarStatus(OCORRENCIA_ID, StatusOcorrencia.RESOLVIDO))
				.isInstanceOf(OcorrenciaNaoEncontradaException.class);
		verify(ocorrenciaRepository, never()).atualizarStatus(anyLong(), any(), any(), any());
		verifyNoInteractions(historicoRepository);
	}

	@Test
	void novoStatusNuloEhRejeitado() {
		assertThatThrownBy(() -> service.atualizarStatus(OCORRENCIA_ID, null))
				.isInstanceOfSatisfying(DadosInvalidosException.class,
						e -> assertThat(e.getErros()).containsOnlyKeys("status"));
		verifyNoInteractions(ocorrenciaRepository, historicoRepository);
	}

	// ---------- utilitários ----------

	private static NovaOcorrencia valida() {
		return new NovaOcorrencia(CATEGORIA_ID, "Buraco grande na faixa da direita", "Centro", "Rua das Flores, 100");
	}

	private void existente(StatusOcorrencia status) {
		LocalDateTime resolvidoEm = status == StatusOcorrencia.RESOLVIDO ? ONTEM : null;
		when(ocorrenciaRepository.buscarPorId(OCORRENCIA_ID)).thenReturn(Optional.of(new Ocorrencia(OCORRENCIA_ID,
				CATEGORIA_ID, "Buraco grande na faixa da direita", "Centro", "Rua das Flores, 100", status, ONTEM,
				ONTEM, resolvidoEm)));
	}

	private HistoricoStatus historicoGravado() {
		ArgumentCaptor<HistoricoStatus> captor = ArgumentCaptor.forClass(HistoricoStatus.class);
		verify(historicoRepository).inserir(captor.capture());
		return captor.getValue();
	}

	private static Ocorrencia comId(Ocorrencia o) {
		return new Ocorrencia(OCORRENCIA_ID, o.categoriaId(), o.descricao(), o.bairro(), o.enderecoReferencia(),
				o.status(), o.criadoEm(), o.atualizadoEm(), o.resolvidoEm());
	}

}
