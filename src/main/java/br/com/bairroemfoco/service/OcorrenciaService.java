package br.com.bairroemfoco.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.bairroemfoco.dto.NovaOcorrencia;
import br.com.bairroemfoco.exception.ConflitoAtualizacaoException;
import br.com.bairroemfoco.exception.DadosInvalidosException;
import br.com.bairroemfoco.exception.OcorrenciaNaoEncontradaException;
import br.com.bairroemfoco.exception.TransicaoStatusInvalidaException;
import br.com.bairroemfoco.model.HistoricoStatus;
import br.com.bairroemfoco.model.Ocorrencia;
import br.com.bairroemfoco.model.StatusOcorrencia;
import br.com.bairroemfoco.repository.CategoriaRepository;
import br.com.bairroemfoco.repository.HistoricoStatusRepository;
import br.com.bairroemfoco.repository.OcorrenciaRepository;

/**
 * Regras de negócio das ocorrências: cadastro, consulta e mudança de status.
 * Toda gravação na ocorrência é acompanhada do registro correspondente no histórico,
 * na mesma transação.
 */
@Service
public class OcorrenciaService {

	/** Ciclo de vida do status. RESOLVIDO é definitivo. */
	private static final Map<StatusOcorrencia, Set<StatusOcorrencia>> TRANSICOES_PERMITIDAS = Map.of(
			StatusOcorrencia.ABERTO, EnumSet.of(StatusOcorrencia.EM_ANDAMENTO, StatusOcorrencia.RESOLVIDO),
			StatusOcorrencia.EM_ANDAMENTO, EnumSet.of(StatusOcorrencia.RESOLVIDO),
			StatusOcorrencia.RESOLVIDO, EnumSet.noneOf(StatusOcorrencia.class));

	/** Qualquer sequência de espaços em branco, incluindo tabulação, quebra de linha e espaço não separável. */
	private static final Pattern ESPACOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	private final OcorrenciaRepository ocorrenciaRepository;
	private final HistoricoStatusRepository historicoRepository;
	private final CategoriaRepository categoriaRepository;
	private final Validator validator;
	private final Clock clock;

	public OcorrenciaService(OcorrenciaRepository ocorrenciaRepository, HistoricoStatusRepository historicoRepository,
			CategoriaRepository categoriaRepository, Validator validator, Clock clock) {
		this.ocorrenciaRepository = ocorrenciaRepository;
		this.historicoRepository = historicoRepository;
		this.categoriaRepository = categoriaRepository;
		this.validator = validator;
		this.clock = clock;
	}

	/**
	 * Registra uma ocorrência com status ABERTO e o primeiro registro do histórico.
	 *
	 * @throws DadosInvalidosException se algum campo for inválido ou a categoria não existir
	 */
	@Transactional
	public Ocorrencia cadastrar(NovaOcorrencia dados) {
		if (dados == null) {
			throw DadosInvalidosException.doCampo("ocorrencia", "Os dados da ocorrência são obrigatórios");
		}
		NovaOcorrencia normalizada = normalizar(dados);
		validar(normalizada);
		if (categoriaRepository.buscarPorId(normalizada.categoriaId()).isEmpty()) {
			throw DadosInvalidosException.doCampo("categoriaId", "Categoria não encontrada");
		}

		LocalDateTime agora = LocalDateTime.now(clock);
		Ocorrencia ocorrencia = ocorrenciaRepository.inserir(new Ocorrencia(null, normalizada.categoriaId(),
				normalizada.descricao(), normalizada.bairro(), normalizada.enderecoReferencia(),
				StatusOcorrencia.ABERTO, agora, agora, null));
		historicoRepository.inserir(new HistoricoStatus(null, ocorrencia.id(), null, StatusOcorrencia.ABERTO, agora));
		return ocorrencia;
	}

	/**
	 * @throws OcorrenciaNaoEncontradaException se não houver ocorrência com este protocolo
	 */
	public Ocorrencia buscarPorId(long id) {
		return ocorrenciaRepository.buscarPorId(id).orElseThrow(() -> new OcorrenciaNaoEncontradaException(id));
	}

	/**
	 * Lista as ocorrências, das mais recentes para as mais antigas. Filtros nulos são ignorados.
	 */
	public List<Ocorrencia> listar(StatusOcorrencia status, Long categoriaId) {
		return ocorrenciaRepository.listar(status, categoriaId);
	}

	/**
	 * Linha do tempo de uma ocorrência, da mais antiga para a mais recente.
	 *
	 * @throws OcorrenciaNaoEncontradaException se não houver ocorrência com este protocolo
	 */
	public List<HistoricoStatus> listarHistorico(long id) {
		buscarPorId(id);
		return historicoRepository.listarPorOcorrencia(id);
	}

	/**
	 * Muda o status da ocorrência e registra a alteração no histórico, com a mesma data/hora.
	 *
	 * @throws DadosInvalidosException se o novo status não for informado
	 * @throws OcorrenciaNaoEncontradaException se não houver ocorrência com este protocolo
	 * @throws TransicaoStatusInvalidaException se a mudança não for permitida pelo ciclo de vida
	 * @throws ConflitoAtualizacaoException se o status mudou por outra operação durante esta
	 */
	@Transactional
	public Ocorrencia atualizarStatus(long id, StatusOcorrencia novoStatus) {
		if (novoStatus == null) {
			throw DadosInvalidosException.doCampo("status", "O novo status é obrigatório");
		}
		StatusOcorrencia statusAtual = buscarPorId(id).status();
		if (!TRANSICOES_PERMITIDAS.get(statusAtual).contains(novoStatus)) {
			throw new TransicaoStatusInvalidaException(statusAtual, novoStatus);
		}

		LocalDateTime agora = LocalDateTime.now(clock);
		if (!ocorrenciaRepository.atualizarStatus(id, statusAtual, novoStatus, agora)) {
			throw new ConflitoAtualizacaoException(id);
		}
		historicoRepository.inserir(new HistoricoStatus(null, id, statusAtual, novoStatus, agora));
		return buscarPorId(id);
	}

	/**
	 * Remove espaços nas pontas de todos os textos. No bairro, também troca qualquer sequência
	 * de espaços internos por um único espaço. Textos que ficam vazios viram {@code null},
	 * para gerar uma única mensagem de "obrigatório".
	 */
	private static NovaOcorrencia normalizar(NovaOcorrencia dados) {
		return new NovaOcorrencia(dados.categoriaId(), aparar(dados.descricao()), normalizarBairro(dados.bairro()),
				aparar(dados.enderecoReferencia()));
	}

	static String normalizarBairro(String bairro) {
		return bairro == null ? null : aparar(ESPACOS.matcher(bairro).replaceAll(" "));
	}

	private static String aparar(String texto) {
		if (texto == null) {
			return null;
		}
		String aparado = texto.strip();
		return aparado.isEmpty() ? null : aparado;
	}

	private void validar(NovaOcorrencia dados) {
		Set<ConstraintViolation<NovaOcorrencia>> violacoes = validator.validate(dados);
		if (!violacoes.isEmpty()) {
			Map<String, String> erros = new HashMap<>();
			violacoes.forEach(v -> erros.put(v.getPropertyPath().toString(), v.getMessage()));
			throw new DadosInvalidosException(erros);
		}
	}

}
