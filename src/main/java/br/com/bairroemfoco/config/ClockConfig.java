package br.com.bairroemfoco.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação, no fuso de São Paulo (ver docs/modelo-dados.md, seção Datas).
 * Quem gera datas recebe este Clock por injeção, o que permite fixar o horário nos testes.
 */
@Configuration
public class ClockConfig {

	public static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");

	/**
	 * {@code tickSeconds}: o relógio avança de segundo em segundo, a mesma precisão
	 * do formato gravado no banco ({@code YYYY-MM-DD HH:MM:SS}).
	 */
	@Bean
	public Clock clock() {
		return Clock.tickSeconds(FUSO_HORARIO);
	}

}
