package br.com.bairroemfoco.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.bairroemfoco.model.Categoria;
import br.com.bairroemfoco.repository.CategoriaRepository;

/**
 * Consulta das categorias. No MVP elas são fixas (carregadas pelo data.sql),
 * então o serviço apenas as disponibiliza para a API.
 */
@Service
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;

	public CategoriaService(CategoriaRepository categoriaRepository) {
		this.categoriaRepository = categoriaRepository;
	}

	/** Categorias em ordem alfabética. */
	public List<Categoria> listarTodas() {
		return categoriaRepository.listarTodas();
	}

}
