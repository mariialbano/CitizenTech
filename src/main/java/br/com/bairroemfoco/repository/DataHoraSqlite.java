package br.com.bairroemfoco.repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converte datas entre Java e o formato gravado no SQLite: {@code YYYY-MM-DD HH:MM:SS}.
 * Este formato é ordenável como texto e compatível com date(), strftime() e julianday().
 */
final class DataHoraSqlite {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private DataHoraSqlite() {
	}

	/** Frações de segundo são descartadas, pois o formato do banco não as guarda. */
	static String paraTexto(LocalDateTime dataHora) {
		return dataHora == null ? null : FORMATO.format(dataHora);
	}

	static LocalDateTime deTexto(String texto) {
		return texto == null ? null : LocalDateTime.parse(texto, FORMATO);
	}

}
