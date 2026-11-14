package br.com.bairroemfoco.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.com.bairroemfoco.dto.OcorrenciaResponse;
import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.service.CategoriaService;

/**
 * Único ponto de conversão de {@link Ocorrencia} para {@link OcorrenciaResponse}.
 * Completa cada ocorrência com o nome da categoria, carregando as categorias uma vez por chamada
 * (são poucas e fixas), sem alterar o OcorrenciaService nem os repositórios.
 */
@Component
public class OcorrenciaResponseMapper {

	private final CategoriaService categoriaService;

	public OcorrenciaResponseMapper(CategoriaService categoriaService) {
		this.categoriaService = categoriaService;
	}

	public OcorrenciaResponse paraResposta(Ocorrencia ocorrencia) {
		return paraRespostas(List.of(ocorrencia)).getFirst();
	}

	public List<OcorrenciaResponse> paraRespostas(List<Ocorrencia> ocorrencias) {
		if (ocorrencias.isEmpty()) {
			return List.of();
		}
		Map<Long, Categoria> categorias = categoriaService.listarTodas().stream()
				.collect(Collectors.toMap(Categoria::id, Function.identity()));
		return ocorrencias.stream()
				.map(ocorrencia -> OcorrenciaResponse.de(ocorrencia, categoriaDe(ocorrencia, categorias)))
				.toList();
	}

	private static Categoria categoriaDe(Ocorrencia ocorrencia, Map<Long, Categoria> categorias) {
		Categoria categoria = categorias.get(ocorrencia.categoriaId());
		if (categoria == null) {
			// A chave estrangeira impede esse caso; se ocorrer, é falha interna (resposta 500)
			throw new IllegalStateException("Categoria " + ocorrencia.categoriaId() + " da ocorrência "
					+ ocorrencia.id() + " não encontrada");
		}
		return categoria;
	}

}
