package br.com.bairroemfoco.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.service.CategoriaService;

@WebMvcTest(CategoriaController.class)
class CategoriaControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private CategoriaService categoriaService;

	@Test
	void listaAsCategoriasNaOrdemDoServico() {
		when(categoriaService.listarTodas()).thenReturn(List.of(
				new Categoria(1L, "Buraco na via"), new Categoria(5L, "Outros")));

		assertThat(mvc.get().uri("/api/categorias"))
				.hasStatusOk()
				.hasContentType(MediaType.APPLICATION_JSON)
				.bodyJson().isStrictlyEqualTo("""
						[
						  { "id": 1, "nome": "Buraco na via" },
						  { "id": 5, "nome": "Outros" }
						]
						""");
	}

	@Test
	void semCategoriasRetornaListaVazia() {
		when(categoriaService.listarTodas()).thenReturn(List.of());

		assertThat(mvc.get().uri("/api/categorias")).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
	}

}
