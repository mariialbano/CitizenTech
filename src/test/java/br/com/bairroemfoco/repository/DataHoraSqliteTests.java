package br.com.bairroemfoco.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

class DataHoraSqliteTests {

	@Test
	void converteParaOFormatoDoBanco() {
		LocalDateTime dataHora = LocalDateTime.of(2026, 11, 10, 8, 5, 9);

		assertThat(DataHoraSqlite.paraTexto(dataHora)).isEqualTo("2026-11-10 08:05:09");
	}

	@Test
	void converteDoFormatoDoBanco() {
		assertThat(DataHoraSqlite.deTexto("2026-11-10 08:05:09")).isEqualTo(LocalDateTime.of(2026, 11, 10, 8, 5, 9));
	}

	@Test
	void descartaFracoesDeSegundo() {
		LocalDateTime comNanos = LocalDateTime.of(2026, 11, 10, 8, 5, 9, 987_654_321);

		assertThat(DataHoraSqlite.paraTexto(comNanos)).isEqualTo("2026-11-10 08:05:09");
	}

	@Test
	void nuloPermaneceNulo() {
		assertThat(DataHoraSqlite.paraTexto(null)).isNull();
		assertThat(DataHoraSqlite.deTexto(null)).isNull();
	}

	@Test
	void rejeitaFormatoDiferente() {
		// Formato ISO com "T", como o de CURRENT_TIMESTAMP em outros bancos: não é o padrão adotado
		assertThatThrownBy(() -> DataHoraSqlite.deTexto("2026-11-10T08:05:09"))
				.isInstanceOf(DateTimeParseException.class);
	}

}
