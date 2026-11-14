package br.com.bairroemfoco.model;

/**
 * Status de uma ocorrência. Os nomes são gravados como texto no banco e devem
 * corresponder aos valores aceitos pelos CHECK do schema.sql.
 */
public enum StatusOcorrencia {

	ABERTO,
	EM_ANDAMENTO,
	RESOLVIDO

}
