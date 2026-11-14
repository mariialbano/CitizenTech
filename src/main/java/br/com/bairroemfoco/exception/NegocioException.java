package br.com.bairroemfoco.exception;

/**
 * Base das exceções de regra de negócio. Cada subclasse representa um tipo de erro
 * que o tratamento global de erros poderá converter em uma resposta HTTP específica.
 */
public abstract class NegocioException extends RuntimeException {

	protected NegocioException(String mensagem) {
		super(mensagem);
	}

}
