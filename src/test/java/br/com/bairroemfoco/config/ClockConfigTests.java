package br.com.bairroemfoco.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ClockConfigTests {

	private final Clock clock = new ClockConfig().clock();

	@Test
	void usaOFusoDeSaoPaulo() {
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
	}

	@Test
	void geraDatasSemFracoesDeSegundo() {
		assertThat(LocalDateTime.now(clock).getNano()).isZero();
	}

}
