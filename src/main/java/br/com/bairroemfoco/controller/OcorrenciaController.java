package br.com.bairroemfoco.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import br.com.bairroemfoco.dto.AtualizacaoStatus;
import br.com.bairroemfoco.dto.HistoricoStatusResponse;
import br.com.bairroemfoco.dto.NovaOcorrencia;
import br.com.bairroemfoco.dto.OcorrenciaResponse;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;
import br.com.bairroemfoco.service.OcorrenciaService;

/**
 * Endpoints de ocorrências (documentados em docs/api.md).
 * A validação dos dados fica no OcorrenciaService, que normaliza os textos antes de validar;
 * por isso não há @Valid aqui. Erros são convertidos pelo GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/ocorrencias")
public class OcorrenciaController {

	private final OcorrenciaService ocorrenciaService;
	private final OcorrenciaResponseMapper mapper;

	public OcorrenciaController(OcorrenciaService ocorrenciaService, OcorrenciaResponseMapper mapper) {
		this.ocorrenciaService = ocorrenciaService;
		this.mapper = mapper;
	}

	@PostMapping
	public ResponseEntity<OcorrenciaResponse> cadastrar(@RequestBody NovaOcorrencia dados) {
		Ocorrencia ocorrencia = ocorrenciaService.cadastrar(dados);
		URI localizacao = ServletUriComponentsBuilder.fromCurrentRequestUri()
				.path("/{id}")
				.buildAndExpand(ocorrencia.id())
				.toUri();
		return ResponseEntity.created(localizacao).body(mapper.paraResposta(ocorrencia));
	}

	@GetMapping("/{id}")
	public OcorrenciaResponse buscarPorId(@PathVariable long id) {
		return mapper.paraResposta(ocorrenciaService.buscarPorId(id));
	}

	@GetMapping
	public List<OcorrenciaResponse> listar(@RequestParam(required = false) StatusOcorrencia status,
			@RequestParam(required = false) Long categoriaId) {
		return mapper.paraRespostas(ocorrenciaService.listar(status, categoriaId));
	}

	@GetMapping("/{id}/historico")
	public List<HistoricoStatusResponse> listarHistorico(@PathVariable long id) {
		return ocorrenciaService.listarHistorico(id).stream()
				.map(HistoricoStatusResponse::de)
				.toList();
	}

	@PatchMapping("/{id}/status")
	public OcorrenciaResponse atualizarStatus(@PathVariable long id, @RequestBody AtualizacaoStatus corpo) {
		return mapper.paraResposta(ocorrenciaService.atualizarStatus(id, corpo.status()));
	}

}
