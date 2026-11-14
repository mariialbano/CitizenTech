package br.com.bairroemfoco.dto;

import java.time.LocalDateTime;

import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Item da linha do tempo de uma ocorrência. {@code statusAnterior} é nulo no registro de criação.
 */
public record HistoricoStatusResponse(
		StatusOcorrencia statusAnterior,
		StatusOcorrencia statusNovo,
		LocalDateTime alteradoEm) {

	public static HistoricoStatusResponse de(HistoricoStatus historico) {
		return new HistoricoStatusResponse(historico.statusAnterior(), historico.statusNovo(), historico.alteradoEm());
	}

}
