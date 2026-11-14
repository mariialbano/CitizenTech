package br.com.bairroemfoco.exception;

import java.util.Map;
import java.util.TreeMap;

/**
 * Dados de entrada inválidos. Traz uma mensagem por campo, para que o formulário
 * possa indicar exatamente o que corrigir.
 */
public class DadosInvalidosException extends NegocioException {

	private final Map<String, String> erros;

	public DadosInvalidosException(Map<String, String> erros) {
		super("Dados inválidos: " + new TreeMap<>(erros));
		this.erros = Map.copyOf(erros);
	}

	public static DadosInvalidosException doCampo(String campo, String mensagem) {
		return new DadosInvalidosException(Map.of(campo, mensagem));
	}

	/** Mensagens de erro indexadas pelo nome do campo. */
	public Map<String, String> getErros() {
		return erros;
	}

}
