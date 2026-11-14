package br.com.bairroemfoco;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Frontend estático (src/main/resources/static): as páginas e recursos são servidos pelo Spring
 * com o tipo correto, as referências locais existem e as regras de segurança do JavaScript
 * são respeitadas (fetch só em api.js, nenhuma inserção de HTML a partir de texto).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginasEstaticasTests {

	private static final List<String> PAGINAS = List.of("index.html", "registrar.html", "ocorrencias.html",
			"ocorrencia.html", "painel.html");

	private static final String CHART_JS = "vendor/chartjs/4.6.1/chart.umd.min.js";

	/** href="..." ou src="..." locais (sem esquema, sem âncora pura). */
	private static final Pattern REFERENCIA_HTML = Pattern.compile("(?:href|src)=\"(?!https?:|data:|#|mailto:)([^\"?#]+)");

	/** import ... from './modulo.js' */
	private static final Pattern IMPORT_JS = Pattern.compile("from\\s+'(\\.{1,2}/[^']+)'");

	@Autowired
	private MockMvcTester mvc;

	@Test
	void raizEncaminhaParaAPaginaInicial() {
		assertThat(mvc.get().uri("/")).hasStatusOk().hasForwardedUrl("index.html");
	}

	@ParameterizedTest
	@ValueSource(strings = { "index.html", "registrar.html", "ocorrencias.html", "ocorrencia.html", "painel.html" })
	void paginaEhServidaComoHtmlEmPortugues(String pagina) {
		assertThat(mvc.get().uri("/" + pagina))
				.hasStatusOk()
				.hasContentTypeCompatibleWith(MediaType.TEXT_HTML)
				.bodyText()
				.contains("<html lang=\"pt-BR\">", "<meta charset=\"utf-8\">", "name=\"viewport\"",
						"<script type=\"module\" src=\"js/paginas/");
	}

	@ParameterizedTest
	@ValueSource(strings = { "index.html", "registrar.html", "ocorrencias.html", "ocorrencia.html", "painel.html" })
	void menuPrincipalLevaATodasAsPaginas(String pagina) throws IOException {
		String html = ler(new ClassPathResource("static/" + pagina));
		String menu = html.substring(html.indexOf("<nav class=\"navegacao\""), html.indexOf("</nav>"));
		assertThat(menu).contains("href=\"index.html\"", "href=\"registrar.html\"", "href=\"ocorrencias.html\"",
				"href=\"painel.html\"");
	}

	/** Chart.js é uma cópia local fixa e sem alterações do pacote oficial (licença MIT). */
	@Test
	void chartJsEhACopiaLocalDaVersaoFixadaComLicenca() throws IOException {
		assertThat(ler(new ClassPathResource("static/" + CHART_JS))).startsWith("/*!\n * Chart.js v4.6.1");
		assertThat(ler(new ClassPathResource("static/vendor/chartjs/4.6.1/LICENSE.md"))).contains("The MIT License");
		assertThat(ler(new ClassPathResource("static/painel.html"))).contains("src=\"" + CHART_JS + "\"");
	}

	@ParameterizedTest
	@CsvSource({
			"/css/estilos.css,              text/css",
			"/js/api.js,                    text/javascript",
			"/js/dominio.js,                text/javascript",
			"/js/ui.js,                     text/javascript",
			"/js/estatisticas.js,           text/javascript",
			"/js/graficos.js,               text/javascript",
			"/js/paginas/painel.js,         text/javascript",
			"/vendor/chartjs/4.6.1/chart.umd.min.js, text/javascript",
			"/js/paginas/inicio.js,         text/javascript",
			"/js/paginas/registrar.js,      text/javascript",
			"/js/paginas/ocorrencias.js,    text/javascript",
			"/js/paginas/ocorrencia.js,     text/javascript",
			"/img/logo.svg,                 image/svg+xml",
			"/img/favicon.svg,              image/svg+xml" })
	void recursoEhServidoComOTipoCorreto(String caminho, String tipo) {
		assertThat(mvc.get().uri(caminho)).hasStatusOk().hasContentTypeCompatibleWith(tipo);
	}

	@Test
	void todasAsReferenciasLocaisDasPaginasExistem() throws IOException {
		List<String> ausentes = new ArrayList<>();
		for (String pagina : PAGINAS) {
			Matcher referencias = REFERENCIA_HTML.matcher(ler(new ClassPathResource("static/" + pagina)));
			while (referencias.find()) {
				if (!new ClassPathResource("static/" + referencias.group(1)).exists()) {
					ausentes.add(pagina + " -> " + referencias.group(1));
				}
			}
		}
		assertThat(ausentes).isEmpty();
	}

	@Test
	void todosOsImportsDosModulosExistem() throws IOException {
		List<String> ausentes = new ArrayList<>();
		for (Resource modulo : scripts()) {
			Matcher imports = IMPORT_JS.matcher(ler(modulo));
			while (imports.find()) {
				if (!modulo.createRelative(imports.group(1)).exists()) {
					ausentes.add(modulo.getFilename() + " -> " + imports.group(1));
				}
			}
		}
		assertThat(ausentes).isEmpty();
	}

	@Test
	void somenteApiJsFazRequisicoesHttp() throws IOException {
		List<String> usamFetch = new ArrayList<>();
		for (Resource modulo : scripts()) {
			if (ler(modulo).contains("fetch(") && !"api.js".equals(modulo.getFilename())) {
				usamFetch.add(modulo.getFilename());
			}
		}
		assertThat(usamFetch).isEmpty();
	}

	/** Proteção contra XSS: textos vindos da API são inseridos com textContent, nunca como HTML. */
	@Test
	void nenhumScriptInsereHtmlAPartirDeTexto() throws IOException {
		List<String> violacoes = new ArrayList<>();
		for (Resource modulo : scripts()) {
			String codigo = ler(modulo);
			for (String proibido : List.of("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(")) {
				if (codigo.contains(proibido)) {
					violacoes.add(modulo.getFilename() + " usa " + proibido);
				}
			}
		}
		assertThat(violacoes).isEmpty();
	}

	private static Resource[] scripts() throws IOException {
		Resource[] modulos = new PathMatchingResourcePatternResolver().getResources("classpath:static/js/**/*.js");
		assertThat(modulos).hasSizeGreaterThanOrEqualTo(7);
		return modulos;
	}

	private static String ler(Resource recurso) throws IOException {
		return recurso.getContentAsString(StandardCharsets.UTF_8);
	}

}
