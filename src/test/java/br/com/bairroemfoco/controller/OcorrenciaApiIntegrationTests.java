package br.com.bairroemfoco.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * API completa: Controller → Service → Repository → SQLite de teste.
 * Cada teste é desfeito ao final (o MockMvc executa na mesma thread e transação do teste).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OcorrenciaApiIntegrationTests {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcClient jdbcClient;

	private int buraco;
	private int outros;

	@BeforeEach
	void carregarCategoriasPelaApi() throws Exception {
		String categorias = mvc.get().uri("/api/categorias").exchange().getResponse().getContentAsString();
		buraco = idDaCategoria(categorias, "Buraco na via");
		outros = idDaCategoria(categorias, "Outros");
	}

	@Test
	void categoriasRetornaAsCincoEmOrdemAlfabetica() {
		assertThat(mvc.get().uri("/api/categorias"))
				.hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$[*].nome", nomes -> nomes.assertThat().asArray().containsExactly(
						"Buraco na via", "Calçada danificada", "Descarte irregular de lixo", "Iluminação pública",
						"Outros"));
	}

	@Test
	void fluxoCompletoDeCadastroAcompanhamentoEResolucao() throws Exception {
		// Cadastro com bairro a normalizar
		MvcTestResult cadastro = mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON)
				.content(novaOcorrencia(buraco, "  Vila \t  Nova ")).exchange();
		assertThat(cadastro).hasStatus(HttpStatus.CREATED)
				.bodyJson()
				.hasPathSatisfying("$.bairro", v -> v.assertThat().isEqualTo("Vila Nova"))
				.hasPathSatisfying("$.categoria.nome", v -> v.assertThat().isEqualTo("Buraco na via"))
				.hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("ABERTO"));
		String location = cadastro.getResponse().getHeader("Location");
		int id = JsonPath.read(cadastro.getResponse().getContentAsString(), "$.id");
		assertThat(location).isEqualTo("http://localhost/api/ocorrencias/" + id);

		// O Location aponta para o recurso criado, com o mesmo conteúdo
		assertThat(mvc.get().uri(location)).hasStatusOk()
				.bodyJson().isStrictlyEqualTo(cadastro.getResponse().getContentAsString());

		// Acompanhamento até a resolução
		assertThat(patchStatus(id, "EM_ANDAMENTO")).hasStatusOk()
				.bodyJson().hasPathSatisfying("$.resolvidoEm", v -> v.assertThat().isNull());
		assertThat(patchStatus(id, "RESOLVIDO")).hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$.status", v -> v.assertThat().isEqualTo("RESOLVIDO"))
				.hasPathSatisfying("$.resolvidoEm", v -> v.assertThat().isNotNull());

		assertThat(mvc.get().uri("/api/ocorrencias/{id}/historico", id)).hasStatusOk()
				.bodyJson()
				.hasPathSatisfying("$[*].statusNovo", s -> s.assertThat().asArray()
						.containsExactly("ABERTO", "EM_ANDAMENTO", "RESOLVIDO"))
				.hasPathSatisfying("$[0].statusAnterior", s -> s.assertThat().isNull());

		// Resolvida é definitiva
		assertThat(patchStatus(id, "ABERTO")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
				.bodyJson().hasPathSatisfying("$.statusAtual", s -> s.assertThat().isEqualTo("RESOLVIDO"));
		assertThat(mvc.get().uri("/api/ocorrencias/{id}/historico", id)).bodyJson()
				.hasPathSatisfying("$.length()", n -> n.assertThat().isEqualTo(3));
	}

	@Test
	void cadastroInvalidoRetorna400ComTodosOsCamposENaoGrava() {
		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"descricao\": \"curta\", \"bairro\": \"   \", \"enderecoReferencia\": \"Rua\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().hasPathSatisfying("$.erros", erros -> erros.assertThat().asMap()
						.containsOnlyKeys("categoriaId", "descricao", "bairro", "enderecoReferencia")
						.containsEntry("bairro", "O bairro é obrigatório"));

		assertThat(contar("ocorrencia")).isZero();
		assertThat(contar("historico_status")).isZero();
	}

	@Test
	void categoriaInexistenteRetorna400NoCampoCategoria() {
		assertThat(mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON)
				.content(novaOcorrencia(999, "Centro")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.erros.categoriaId").isEqualTo("Categoria não encontrada");
		assertThat(contar("ocorrencia")).isZero();
	}

	@Test
	void ocorrenciaInexistenteRetorna404EmTodosOsEndpoints() {
		assertThat(mvc.get().uri("/api/ocorrencias/999")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/ocorrencias/999/historico")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(patchStatus(999, "RESOLVIDO")).hasStatus(HttpStatus.NOT_FOUND)
				.hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
				.bodyJson().extractingPath("$.detail").isEqualTo("Ocorrência não encontrada: protocolo 999");
	}

	@Test
	void listagemFiltraPorStatusECategoria() throws Exception {
		int buracoAberto = cadastrar(buraco);
		int outrosAberto = cadastrar(outros);
		int buracoResolvido = cadastrar(buraco);
		assertThat(patchStatus(buracoResolvido, "RESOLVIDO")).hasStatusOk();

		assertThat(idsListados("/api/ocorrencias")).containsExactly(buracoResolvido, outrosAberto, buracoAberto);
		assertThat(idsListados("/api/ocorrencias?status=ABERTO")).containsExactly(outrosAberto, buracoAberto);
		assertThat(idsListados("/api/ocorrencias?categoriaId=" + buraco)).containsExactly(buracoResolvido, buracoAberto);
		assertThat(idsListados("/api/ocorrencias?status=ABERTO&categoriaId=" + buraco)).containsExactly(buracoAberto);
		assertThat(idsListados("/api/ocorrencias?categoriaId=999")).isEmpty();
	}

	// ---------- utilitários ----------

	private MvcTestResult patchStatus(int id, String status) {
		return mvc.patch().uri("/api/ocorrencias/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"" + status + "\"}").exchange();
	}

	private int cadastrar(int categoriaId) throws Exception {
		MvcTestResult resultado = mvc.post().uri("/api/ocorrencias").contentType(MediaType.APPLICATION_JSON)
				.content(novaOcorrencia(categoriaId, "Centro")).exchange();
		assertThat(resultado).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
	}

	private List<Integer> idsListados(String uri) throws Exception {
		return JsonPath.read(mvc.get().uri(uri).exchange().getResponse().getContentAsString(), "$[*].id");
	}

	private static String novaOcorrencia(int categoriaId, String bairro) {
		return """
				{
				  "categoriaId": %d,
				  "descricao": "Buraco grande na faixa da direita",
				  "bairro": "%s",
				  "enderecoReferencia": "Rua das Flores, 100"
				}
				""".formatted(categoriaId, bairro.replace("\t", "\\t"));
	}

	private static int idDaCategoria(String json, String nome) {
		List<Integer> ids = JsonPath.read(json, "$[?(@.nome == '" + nome + "')].id");
		return ids.getFirst();
	}

	private int contar(String tabela) {
		return jdbcClient.sql("SELECT COUNT(*) FROM " + tabela).query(Integer.class).single();
	}

}
