package br.com.bairroemfoco.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import br.com.bairroemfoco.dto.NovaOcorrencia;

/**
 * Casos do handler que nenhum endpoint atual provoca. Os demais são cobertos pelos testes de controller.
 */
class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	/**
	 * Nenhum endpoint usa @Valid hoje (a validação do cadastro fica no serviço), mas um uso futuro
	 * deve gerar o mesmo formato da DadosInvalidosException: 400 com uma mensagem por campo.
	 */
	@Test
	void validacaoComValidGeraOMesmoFormatoDeDadosInvalidos() throws Exception {
		BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(
				new NovaOcorrencia(null, "curta", "Centro", "Rua das Flores"), "novaOcorrencia");
		resultado.addError(new FieldError("novaOcorrencia", "descricao", "A descrição deve ter entre 10 e 1000 caracteres"));
		resultado.addError(new FieldError("novaOcorrencia", "descricao", "Segunda mensagem do mesmo campo"));
		resultado.addError(new FieldError("novaOcorrencia", "categoriaId", "A categoria é obrigatória"));
		MethodArgumentNotValidException excecao = new MethodArgumentNotValidException(
				new MethodParameter(getClass().getDeclaredMethod("endpointDeExemplo", NovaOcorrencia.class), 0), resultado);

		ResponseEntity<Object> resposta = handler.handleMethodArgumentNotValid(excecao, new HttpHeaders(),
				HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		ProblemDetail problema = (ProblemDetail) resposta.getBody();
		assertThat(problema.getTitle()).isEqualTo("Dados inválidos");
		assertThat(problema.getDetail()).isEqualTo("Um ou mais campos estão inválidos.");
		assertThat(problema.getProperties()).containsEntry("erros", Map.of(
				"descricao", "A descrição deve ter entre 10 e 1000 caracteres",
				"categoriaId", "A categoria é obrigatória"));
	}

	@SuppressWarnings("unused")
	private void endpointDeExemplo(NovaOcorrencia dados) {
	}

}
