package br.com.bairroemfoco.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Registra no log, ao iniciar a aplicação, o estado da conexão com o SQLite.
 * Falha a inicialização se as chaves estrangeiras não estiverem ativas.
 */
@Component
public class DatabaseStartupCheck implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DatabaseStartupCheck.class);

	private final JdbcClient jdbcClient;

	public DatabaseStartupCheck(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void run(ApplicationArguments args) {
		String arquivo = jdbcClient.sql("SELECT file FROM pragma_database_list WHERE name = 'main'")
				.query(String.class)
				.single();
		int foreignKeys = jdbcClient.sql("PRAGMA foreign_keys").query(Integer.class).single();
		int categorias = jdbcClient.sql("SELECT COUNT(*) FROM categoria").query(Integer.class).single();

		if (foreignKeys != 1) {
			throw new IllegalStateException("PRAGMA foreign_keys não está ativo na conexão do SQLite");
		}

		log.info("SQLite conectado: {} | foreign_keys=ON | categorias cadastradas: {}", arquivo, categorias);
	}

}
