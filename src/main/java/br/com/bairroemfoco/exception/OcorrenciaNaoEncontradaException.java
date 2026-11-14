package br.com.bairroemfoco.exception;

public class OcorrenciaNaoEncontradaException extends NegocioException {

	private final long id;

	public OcorrenciaNaoEncontradaException(long id) {
		super("Ocorrência não encontrada: protocolo " + id);
		this.id = id;
	}

	public long getId() {
		return id;
	}

}
