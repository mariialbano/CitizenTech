package br.com.bairroemfoco.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import br.com.bairroemfoco.dto.NovaOcorrencia;
import br.com.bairroemfoco.exception.ConflitoAtualizacaoException;
import br.com.bairroemfoco.exception.DadosInvalidosException;
import br.com.bairroemfoco.exception.OcorrenciaNaoEncontradaException;
import br.com.bairroemfoco.exception.TransicaoStatusInvalidaException;
import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;
import br.com.bairroemfoco.service.CategoriaService;
import br.com.bairroemfoco.service.OcorrenciaService;

/**
 * Contrato HTTP de /api/ocorrencias: rotas, códigos, formato do JSON e conversão de erros.
 * Somente a camada web é carregada; os serviços são simulados.
 */
@WebMvcTest(OcorrenciaController.class)
@Import(OcorrenciaResponseMapper.class)
class OcorrenciaControllerTests {

	private static final LocalDateTime CRIADO_EM = LocalDateTime.of(2026, 11, 10, 8, 0, 0);
	private static final LocalDateTime RESOLVIDO_EM = LocalDateTime.of(2026, 11, 12, 17, 30, 0);
	private static final Categoria BURACO = new Categoria(1L, "Buraco na via");
	private static final Categoria OUTROS = new Categoria(5L, "Outros");

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private OcorrenciaService ocorrenciaService;

	@MockitoBean
	private CategoriaService categoriaService;

	@BeforeEach
	void configurarCategorias() {
		when(categoriaService.listarTodas()).thenReturn(List.of(BURACO, OUTROS));
	}

	// ---------- POST /api/ocorrencias ----------

	@Test
	void cadastrarRetorna201ComLocationEOContratoCompleto() {
		NovaOcorrencia dados = new NovaOcorrencia(1L, "Buraco grande na faixa da direita", "  Vila   Nova ",
				"Rua das Flores, 100");
		when(ocorrenciaService.cadastrar(dados)).thenReturn(ocorrencia(42, StatusOcorrencia.ABERTO));

		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "categoriaId": 1,
				  "descricao": "Buraco grande na faixa da direita",
				  "bairro": "  Vila   Nova ",
				  "enderecoReferencia": "Rua das Flores, 100"
				}
				"""))
				.hasStatus(HttpStatus.CREATED)
				.hasHeader("Location", "http://localhost/api/ocorrencias/42")
				.hasContentType(MediaType.APPLICATION_JSON)
				.bodyJson().isStrictlyEqualTo("""
						{
						  "id": 42,
						  "categoria": { "id": 1, "nome": "Buraco na via" },
						  "descricao": "Buraco grande na faixa da direita",
						  "bairro": "Vila Nova",
						  "enderecoReferencia": "Rua das Flores, 100",
						  "status": "ABERTO",
						  "criadoEm": "2026-11-10T08:00:00",
						  "atualizadoEm": "2026-11-10T08:00:00",
						  "resolvidoEm": null
						}
						""");
	}

	@Test
	void camposExtrasNoCadastroSaoIgnorados() {
		when(ocorrenciaService.cadastrar(any())).thenReturn(ocorrencia(42, StatusOcorrencia.ABERTO));

		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "categoriaId": 1, "descricao": "Buraco grande na faixa", "bairro": "Centro",
				  "enderecoReferencia": "Rua das Flores", "status": "RESOLVIDO",
				  "nome": "Maria", "email": "maria@example.com"
				}
				""")).hasStatus(HttpStatus.CREATED);

		verify(ocorrenciaService).cadastrar(
				new NovaOcorrencia(1L, "Buraco grande na faixa", "Centro", "Rua das Flores"));
	}

	@Test
	void dadosInvalidosRetornam400ComErrosPorCampo() {
		when(ocorrenciaService.cadastrar(any())).thenThrow(new DadosInvalidosException(Map.of(
				"descricao", "A descrição deve ter entre 10 e 1000 caracteres",
				"categoriaId", "Categoria não encontrada")));

		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoriaId\": 999, \"descricao\": \"curta\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().isStrictlyEqualTo("""
						{
						  "title": "Dados inválidos",
						  "status": 400,
						  "detail": "Um ou mais campos estão inválidos.",
						  "instance": "/api/ocorrencias",
						  "erros": {
						    "descricao": "A descrição deve ter entre 10 e 1000 caracteres",
						    "categoriaId": "Categoria não encontrada"
						  }
						}
						""");
	}

	@Test
	void jsonMalformadoRetorna400SemChamarOServico() {
		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON).content("{ \"categoriaId\": "))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson()
				.hasPathSatisfying("$.detail", d -> d.assertThat().isEqualTo(
						"O corpo da requisição está ausente ou não é um JSON válido."))
				.doesNotHavePath("$.erros");
		verifyNoInteractions(ocorrenciaService);
	}

	@Test
	void corpoAusenteRetorna400() {
		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(ocorrenciaService);
	}

	@Test
	void categoriaIdComTextoRetorna400NoCampo() {
		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoriaId\": \"abc\", \"descricao\": \"Buraco grande na faixa\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.erros.categoriaId")
				.isEqualTo("Valor inválido. Informe um número inteiro");
		verifyNoInteractions(ocorrenciaService);
	}

	@Test
	void contentTypeNaoSuportadoRetorna415() {
		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.TEXT_PLAIN).content("texto"))
				.hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(ocorrenciaService);
	}

	// ---------- GET /api/ocorrencias/{id} ----------

	@Test
	void buscarPorIdRetornaOcorrenciaComDatasEmFormatoIso() {
		when(ocorrenciaService.buscarPorId(42)).thenReturn(ocorrencia(42, StatusOcorrencia.RESOLVIDO));

		assertThat(mvc.get().uri("/api/ocorrencias/42"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("RESOLVIDO"))
				.hasPathSatisfying("$.categoria.nome", v -> v.assertThat().isEqualTo("Buraco na via"))
				.hasPathSatisfying("$.resolvidoEm", v -> v.assertThat().isEqualTo("2026-11-12T17:30:00"));
	}

	@Test
	void buscarOcorrenciaInexistenteRetorna404() {
		when(ocorrenciaService.buscarPorId(999)).thenThrow(new OcorrenciaNaoEncontradaException(999));

		assertThat(mvc.get().uri("/api/ocorrencias/999"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().isStrictlyEqualTo("""
						{
						  "title": "Ocorrência não encontrada",
						  "status": 404,
						  "detail": "Ocorrência não encontrada: protocolo 999",
						  "instance": "/api/ocorrencias/999"
						}
						""");
	}

	@Test
	void idNaoNumericoRetorna400() {
		assertThat(mvc.get().uri("/api/ocorrencias/abc"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().extractingPath("$.erros.id").isEqualTo("Valor inválido. Informe um número inteiro");
		verifyNoInteractions(ocorrenciaService);
	}

	// ---------- GET /api/ocorrencias ----------

	@Test
	void listarSemFiltrosRepassaNulosEMantemAOrdem() {
		when(ocorrenciaService.listar(null, null)).thenReturn(List.of(
				ocorrencia(43, StatusOcorrencia.ABERTO), ocorrencia(42, StatusOcorrencia.EM_ANDAMENTO)));

		assertThat(mvc.get().uri("/api/ocorrencias"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$[*].id", ids -> ids.assertThat().asArray().containsExactly(43, 42));
	}

	@Test
	void listarRepassaOsFiltros() {
		when(ocorrenciaService.listar(StatusOcorrencia.ABERTO, 1L)).thenReturn(List.of());

		assertThat(mvc.get().uri("/api/ocorrencias?status=ABERTO&categoriaId=1"))
				.hasStatusOk()
				.bodyJson().isStrictlyEqualTo("[]");
		verify(ocorrenciaService).listar(StatusOcorrencia.ABERTO, 1L);
	}

	@Test
	void listarComStatusDesconhecidoRetorna400ComValoresAceitos() {
		assertThat(mvc.get().uri("/api/ocorrencias?status=FECHADO"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.erros.status")
				.isEqualTo("Valor inválido. Valores aceitos: ABERTO, EM_ANDAMENTO, RESOLVIDO");
		verifyNoInteractions(ocorrenciaService);
	}

	// ---------- GET /api/ocorrencias/{id}/historico ----------

	@Test
	void historicoRetornaALinhaDoTempo() {
		when(ocorrenciaService.listarHistorico(42)).thenReturn(List.of(
				new HistoricoStatus(1L, 42, null, StatusOcorrencia.ABERTO, CRIADO_EM),
				new HistoricoStatus(2L, 42, StatusOcorrencia.ABERTO, StatusOcorrencia.RESOLVIDO, RESOLVIDO_EM)));

		assertThat(mvc.get().uri("/api/ocorrencias/42/historico"))
				.hasStatusOk()
				.bodyJson().isStrictlyEqualTo("""
						[
						  { "statusAnterior": null, "statusNovo": "ABERTO", "alteradoEm": "2026-11-10T08:00:00" },
						  { "statusAnterior": "ABERTO", "statusNovo": "RESOLVIDO", "alteradoEm": "2026-11-12T17:30:00" }
						]
						""");
	}

	@Test
	void historicoDeOcorrenciaInexistenteRetorna404() {
		when(ocorrenciaService.listarHistorico(999)).thenThrow(new OcorrenciaNaoEncontradaException(999));

		assertThat(mvc.get().uri("/api/ocorrencias/999/historico"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	// ---------- PATCH /api/ocorrencias/{id}/status ----------

	@Test
	void atualizarStatusRetornaAOcorrenciaAtualizada() {
		when(ocorrenciaService.atualizarStatus(42, StatusOcorrencia.RESOLVIDO))
				.thenReturn(ocorrencia(42, StatusOcorrencia.RESOLVIDO));

		assertThat(patchStatus(42, "{\"status\": \"RESOLVIDO\"}"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("RESOLVIDO"))
				.hasPathSatisfying("$.resolvidoEm", v -> v.assertThat().isEqualTo("2026-11-12T17:30:00"));
	}

	@Test
	void transicaoInvalidaRetorna422ComStatusAtualESolicitado() {
		when(ocorrenciaService.atualizarStatus(42, StatusOcorrencia.ABERTO)).thenThrow(
				new TransicaoStatusInvalidaException(StatusOcorrencia.RESOLVIDO, StatusOcorrencia.ABERTO));

		assertThat(patchStatus(42, "{\"status\": \"ABERTO\"}"))
				.hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().isStrictlyEqualTo("""
						{
						  "title": "Transição de status não permitida",
						  "status": 422,
						  "detail": "A ocorrência já foi resolvida e não pode ser reaberta nem alterada",
						  "instance": "/api/ocorrencias/42/status",
						  "statusAtual": "RESOLVIDO",
						  "statusSolicitado": "ABERTO"
						}
						""");
	}

	@Test
	void conflitoDeAtualizacaoRetorna409() {
		when(ocorrenciaService.atualizarStatus(42, StatusOcorrencia.EM_ANDAMENTO))
				.thenThrow(new ConflitoAtualizacaoException(42));

		assertThat(patchStatus(42, "{\"status\": \"EM_ANDAMENTO\"}"))
				.hasStatus(HttpStatus.CONFLICT)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().isStrictlyEqualTo("""
						{
						  "title": "Conflito de atualização",
						  "status": 409,
						  "detail": "A ocorrência 42 foi alterada por outra operação. Consulte o status atual e tente novamente.",
						  "instance": "/api/ocorrencias/42/status"
						}
						""");
	}

	@Test
	void atualizarOcorrenciaInexistenteRetorna404() {
		when(ocorrenciaService.atualizarStatus(999, StatusOcorrencia.RESOLVIDO))
				.thenThrow(new OcorrenciaNaoEncontradaException(999));

		assertThat(patchStatus(999, "{\"status\": \"RESOLVIDO\"}")).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void statusDesconhecidoNoCorpoRetorna400SemChamarOServico() {
		assertThat(patchStatus(42, "{\"status\": \"FECHADO\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.erros.status")
				.isEqualTo("Valor inválido. Valores aceitos: ABERTO, EM_ANDAMENTO, RESOLVIDO");
		verifyNoInteractions(ocorrenciaService);
	}

	@Test
	void statusAusenteEhRepassadoAoServicoQueORejeita() {
		when(ocorrenciaService.atualizarStatus(42, null))
				.thenThrow(DadosInvalidosException.doCampo("status", "O novo status é obrigatório"));

		assertThat(patchStatus(42, "{}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.erros.status").isEqualTo("O novo status é obrigatório");
	}

	// ---------- erros gerais ----------

	@Test
	void erroInesperadoRetorna500SemDetalhesInternos() {
		when(ocorrenciaService.buscarPorId(anyLong())).thenThrow(
				new IllegalStateException("[SQLITE_ERROR] SELECT * FROM ocorrencia WHERE segredo = 1"));

		assertThat(mvc.get().uri("/api/ocorrencias/42"))
				.hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().isStrictlyEqualTo("""
						{
						  "title": "Erro interno",
						  "status": 500,
						  "detail": "Ocorreu um erro inesperado. Tente novamente mais tarde.",
						  "instance": "/api/ocorrencias/42"
						}
						""");
	}

	@Test
	void metodoNaoSuportadoRetorna405() {
		assertThat(mvc.delete().uri("/api/ocorrencias/42"))
				.hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(ocorrenciaService);
	}

	@Test
	void rotaInexistenteRetorna404EmJson() {
		assertThat(mvc.get().uri("/api/rota-inexistente"))
				.hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	// ---------- utilitários ----------

	private org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder patchStatus(long id,
			String corpo) {
		return mvc.patch().uri("/api/ocorrencias/{id}/status", id).contentType(MediaType.APPLICATION_JSON).content(corpo);
	}

	private static Ocorrencia ocorrencia(long id, StatusOcorrencia status) {
		boolean resolvida = status == StatusOcorrencia.RESOLVIDO;
		return new Ocorrencia(id, BURACO.id(), "Buraco grande na faixa da direita", "Vila Nova", "Rua das Flores, 100",
				status, CRIADO_EM, resolvida ? RESOLVIDO_EM : CRIADO_EM, resolvida ? RESOLVIDO_EM : null);
	}

}
