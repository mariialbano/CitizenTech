package br.com.bairroemfoco.dto;

import br.com.bairroemfoco.model.Categoria;

/**
 * Categoria na API: usada em {@code GET /api/categorias} e dentro de {@link OcorrenciaResponse}.
 */
public record CategoriaResponse(long id, String nome) {

	public static CategoriaResponse de(Categoria categoria) {
		return new CategoriaResponse(categoria.id(), categoria.nome());
	}

}
