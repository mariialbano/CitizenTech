package br.com.bairroemfoco.dto;

import java.time.LocalDateTime;

import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Ocorrência na API. O {@code id} é o número de protocolo mostrado ao cidadão.
 * As datas estão no horário de São Paulo.
 */
public record OcorrenciaResponse(
		long id,
		CategoriaResponse categoria,
		String descricao,
		String bairro,
		String enderecoReferencia,
		StatusOcorrencia status,
		LocalDateTime criadoEm,
		LocalDateTime atualizadoEm,
		LocalDateTime resolvidoEm) {

	/**
	 * @param categoria a categoria correspondente a {@code ocorrencia.categoriaId()}
	 */
	public static OcorrenciaResponse de(Ocorrencia ocorrencia, Categoria categoria) {
		return new OcorrenciaResponse(ocorrencia.id(), CategoriaResponse.de(categoria), ocorrencia.descricao(),
				ocorrencia.bairro(), ocorrencia.enderecoReferencia(), ocorrencia.status(), ocorrencia.criadoEm(),
				ocorrencia.atualizadoEm(), ocorrencia.resolvidoEm());
	}

}
