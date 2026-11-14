package br.com.bairroemfoco.exception;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Converte exceções em respostas {@link ProblemDetail} (RFC 9457, {@code application/problem+json}).
 *
 * A classe base já trata os erros do próprio Spring MVC (rota inexistente, 405, 415...) no mesmo
 * formato. Aqui ficam as exceções de negócio, os erros de entrada com mensagem por campo
 * e o tratamento genérico de erros inesperados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	static final String TITULO_DADOS_INVALIDOS = "Dados inválidos";
	static final String DETALHE_DADOS_INVALIDOS = "Um ou mais campos estão inválidos.";
	static final String DETALHE_ERRO_INTERNO = "Ocorreu um erro inesperado. Tente novamente mais tarde.";

	// ---------- exceções de negócio ----------

	@ExceptionHandler(DadosInvalidosException.class)
	public ProblemDetail tratarDadosInvalidos(DadosInvalidosException e) {
		return dadosInvalidos(e.getErros());
	}

	@ExceptionHandler(OcorrenciaNaoEncontradaException.class)
	public ProblemDetail tratarNaoEncontrada(OcorrenciaNaoEncontradaException e) {
		return problema(HttpStatus.NOT_FOUND, "Ocorrência não encontrada", e.getMessage());
	}

	@ExceptionHandler(ConflitoAtualizacaoException.class)
	public ProblemDetail tratarConflito(ConflitoAtualizacaoException e) {
		return problema(HttpStatus.CONFLICT, "Conflito de atualização", e.getMessage());
	}

	@ExceptionHandler(TransicaoStatusInvalidaException.class)
	public ProblemDetail tratarTransicaoInvalida(TransicaoStatusInvalidaException e) {
		ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_CONTENT, "Transição de status não permitida",
				e.getMessage());
		problema.setProperty("statusAtual", e.getStatusAtual());
		problema.setProperty("statusSolicitado", e.getStatusSolicitado());
		return problema;
	}

	// ---------- erros inesperados ----------

	/**
	 * Qualquer exceção não prevista (inclusive erros do banco) vira 500 com mensagem genérica.
	 * Detalhes como SQL e stack trace vão apenas para o log do servidor.
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail tratarErroInesperado(Exception e, HttpServletRequest request) {
		log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), e);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", DETALHE_ERRO_INTERNO);
	}

	// ---------- erros de entrada detectados pelo Spring ----------

	/** {@code @Valid} em um corpo de requisição: mesmo formato da DadosInvalidosException. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> erros = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(erro -> erros.putIfAbsent(erro.getField(),
				Objects.requireNonNullElse(erro.getDefaultMessage(), "Valor inválido")));
		return handleExceptionInternal(ex, dadosInvalidos(erros), headers, HttpStatus.BAD_REQUEST, request);
	}

	/** Corpo ausente, JSON malformado ou valor de tipo errado em um campo do JSON. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problema;
		if (ex.getCause() instanceof MismatchedInputException erroDeTipo && !erroDeTipo.getPath().isEmpty()) {
			problema = dadosInvalidos(Map.of(caminho(erroDeTipo), mensagemDeTipo(erroDeTipo.getTargetType())));
		}
		else {
			problema = problema(HttpStatus.BAD_REQUEST, TITULO_DADOS_INVALIDOS,
					"O corpo da requisição está ausente ou não é um JSON válido.");
		}
		return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
	}

	/** Parâmetro de URL ou de consulta com tipo errado, ex.: {@code ?status=FECHADO} ou {@code /ocorrencias/abc}. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String parametro = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
		ProblemDetail problema = dadosInvalidos(
				Map.of(Objects.requireNonNullElse(parametro, "parametro"), mensagemDeTipo(ex.getRequiredType())));
		return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
	}

	// ---------- utilitários ----------

	private static ProblemDetail dadosInvalidos(Map<String, String> erros) {
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, TITULO_DADOS_INVALIDOS, DETALHE_DADOS_INVALIDOS);
		problema.setProperty("erros", erros);
		return problema;
	}

	private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
		problema.setTitle(titulo);
		return problema;
	}

	/** Caminho do campo no JSON, ex.: {@code status} ou {@code categoria.id}. */
	private static String caminho(JacksonException erro) {
		return erro.getPath().stream()
				.map(JacksonException.Reference::getPropertyName)
				.filter(Objects::nonNull)
				.collect(Collectors.joining("."));
	}

	/** Mensagem sem repetir o valor recebido, indicando o que é aceito. */
	private static String mensagemDeTipo(Class<?> tipoEsperado) {
		if (tipoEsperado != null && tipoEsperado.isEnum()) {
			String aceitos = Arrays.stream(tipoEsperado.getEnumConstants())
					.map(Object::toString)
					.collect(Collectors.joining(", "));
			return "Valor inválido. Valores aceitos: " + aceitos;
		}
		if (tipoEsperado != null && (Number.class.isAssignableFrom(tipoEsperado) || isNumeroPrimitivo(tipoEsperado))) {
			return "Valor inválido. Informe um número inteiro";
		}
		return "Valor inválido";
	}

	private static boolean isNumeroPrimitivo(Class<?> tipo) {
		return tipo == long.class || tipo == int.class;
	}

}
