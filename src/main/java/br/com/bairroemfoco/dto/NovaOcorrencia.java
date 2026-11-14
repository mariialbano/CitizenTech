package br.com.bairroemfoco.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados informados pelo cidadão ao registrar uma ocorrência.
 * Os limites são os mesmos dos CHECK do schema.sql.
 */
public record NovaOcorrencia(

		@NotNull(message = "A categoria é obrigatória")
		Long categoriaId,

		@NotBlank(message = "A descrição é obrigatória")
		@Size(min = 10, max = 1000, message = "A descrição deve ter entre {min} e {max} caracteres")
		String descricao,

		@NotBlank(message = "O bairro é obrigatório")
		@Size(min = 2, max = 80, message = "O bairro deve ter entre {min} e {max} caracteres")
		String bairro,

		@NotBlank(message = "O endereço ou ponto de referência é obrigatório")
		@Size(min = 5, max = 200, message = "O endereço ou ponto de referência deve ter entre {min} e {max} caracteres")
		String enderecoReferencia) {
}
