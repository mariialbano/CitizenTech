package br.com.bairroemfoco.exception;

/**
 * O status da ocorrência mudou entre a leitura e a gravação, por outra operação.
 * Nada é gravado; o cliente deve consultar o status atual e tentar novamente.
 */
public class ConflitoAtualizacaoException extends NegocioException {

	private final long id;

	public ConflitoAtualizacaoException(long id) {
		super("A ocorrência " + id + " foi alterada por outra operação. Consulte o status atual e tente novamente.");
		this.id = id;
	}

	public long getId() {
		return id;
	}

}
