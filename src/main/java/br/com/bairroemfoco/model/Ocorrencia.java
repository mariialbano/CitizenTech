package br.com.bairroemfoco.model;

import java.time.LocalDateTime;

/**
 * Linha da tabela {@code ocorrencia}.
 *
 * @param id           gerado pelo banco ({@code null} antes de inserir); também é o número de protocolo
 * @param resolvidoEm  preenchido somente quando o status é {@link StatusOcorrencia#RESOLVIDO}
 */
public record Ocorrencia(
		Long id,
		long categoriaId,
		String descricao,
		String bairro,
		String enderecoReferencia,
		StatusOcorrencia status,
		LocalDateTime criadoEm,
		LocalDateTime atualizadoEm,
		LocalDateTime resolvidoEm) {
}
