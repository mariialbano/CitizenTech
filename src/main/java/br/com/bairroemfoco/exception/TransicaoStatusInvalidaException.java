package br.com.bairroemfoco.exception;

import br.com.bairroemfoco.model.StatusOcorrencia;

/**
 * Mudança de status não permitida pelo ciclo de vida da ocorrência
 * (ver docs/modelo-dados.md, seção "Ciclo de vida do status").
 */
public class TransicaoStatusInvalidaException extends NegocioException {

	private final StatusOcorrencia statusAtual;
	private final StatusOcorrencia statusSolicitado;

	public TransicaoStatusInvalidaException(StatusOcorrencia statusAtual, StatusOcorrencia statusSolicitado) {
		super(mensagem(statusAtual, statusSolicitado));
		this.statusAtual = statusAtual;
		this.statusSolicitado = statusSolicitado;
	}

	private static String mensagem(StatusOcorrencia atual, StatusOcorrencia solicitado) {
		if (atual == StatusOcorrencia.RESOLVIDO) {
			return "A ocorrência já foi resolvida e não pode ser reaberta nem alterada";
		}
		if (atual == solicitado) {
			return "A ocorrência já está com o status " + atual;
		}
		return "Não é permitido mudar o status de " + atual + " para " + solicitado;
	}

	public StatusOcorrencia getStatusAtual() {
		return statusAtual;
	}

	public StatusOcorrencia getStatusSolicitado() {
		return statusSolicitado;
	}

}
