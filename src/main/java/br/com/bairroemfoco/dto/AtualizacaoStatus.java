package br.com.bairroemfoco.dto;

import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Corpo de {@code PATCH /api/ocorrencias/{id}/status}. A validação (status obrigatório e
 * transição permitida) é feita pelo OcorrenciaService.
 */
public record AtualizacaoStatus(StatusOcorrencia status) {
}
