package br.com.bairroemfoco.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.bairroemfoco.dto.CategoriaResponse;
import br.com.bairroemfoco.service.CategoriaService;

/**
 * Categorias disponíveis, usadas pelo formulário de cadastro.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

	private final CategoriaService categoriaService;

	public CategoriaController(CategoriaService categoriaService) {
		this.categoriaService = categoriaService;
	}

	@GetMapping
	public List<CategoriaResponse> listar() {
		return categoriaService.listarTodas().stream()
				.map(CategoriaResponse::de)
				.toList();
	}

}
