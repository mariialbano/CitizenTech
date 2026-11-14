package br.com.bairroemfoco.model;

import java.time.LocalDateTime;

/**
 * Linha da tabela {@code historico_status}.
 *
 * @param id              gerado pelo banco ({@code null} antes de inserir)
 * @param statusAnterior  {@code null} no registro de criação da ocorrência
 */
public record HistoricoStatus(
		Long id,
		long ocorrenciaId,
		StatusOcorrencia statusAnterior,
		StatusOcorrencia statusNovo,
		LocalDateTime alteradoEm) {
}
