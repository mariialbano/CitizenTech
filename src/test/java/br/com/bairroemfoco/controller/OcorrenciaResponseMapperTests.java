package br.com.bairroemfoco.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.bairroemfoco.dto.CategoriaResponse;
import br.com.bairroemfoco.dto.OcorrenciaResponse;
import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;
import br.com.bairroemfoco.service.CategoriaService;

class OcorrenciaResponseMapperTests {

	private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 8, 0, 0);

	private CategoriaService categoriaService;
	private OcorrenciaResponseMapper mapper;

	@BeforeEach
	void configurar() {
		categoriaService = mock(CategoriaService.class);
		mapper = new OcorrenciaResponseMapper(categoriaService);
		when(categoriaService.listarTodas()).thenReturn(List.of(
				new Categoria(1L, "Buraco na via"), new Categoria(5L, "Outros")));
	}

	@Test
	void copiaTodosOsCamposEIncluiACategoria() {
		Ocorrencia ocorrencia = new Ocorrencia(42L, 5, "Lixo acumulado na esquina", "Centro", "Esquina da padaria",
				StatusOcorrencia.ABERTO, AGORA, AGORA, null);

		assertThat(mapper.paraResposta(ocorrencia)).isEqualTo(new OcorrenciaResponse(42, new CategoriaResponse(5, "Outros"),
				"Lixo acumulado na esquina", "Centro", "Esquina da padaria", StatusOcorrencia.ABERTO, AGORA, AGORA, null));
	}

	@Test
	void carregaAsCategoriasUmaUnicaVezPorLista() {
		List<OcorrenciaResponse> respostas = mapper.paraRespostas(List.of(ocorrencia(1L, 1), ocorrencia(2L, 5),
				ocorrencia(3L, 1)));

		assertThat(respostas).extracting(r -> r.categoria().nome())
				.containsExactly("Buraco na via", "Outros", "Buraco na via");
		verify(categoriaService, times(1)).listarTodas();
	}

	@Test
	void listaVaziaNaoConsultaCategorias() {
		assertThat(mapper.paraRespostas(List.of())).isEmpty();
		verifyNoInteractions(categoriaService);
	}

	@Test
	void categoriaAusenteEhFalhaInterna() {
		assertThatThrownBy(() -> mapper.paraResposta(ocorrencia(1L, 99))).isInstanceOf(IllegalStateException.class);
	}

	private static Ocorrencia ocorrencia(long id, long categoriaId) {
		return new Ocorrencia(id, categoriaId, "Descrição de teste", "Centro", "Rua das Flores",
				StatusOcorrencia.ABERTO, AGORA, AGORA, null);
	}

}
