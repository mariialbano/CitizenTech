package br.com.bairroemfoco.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.bairroemfoco.model.Categoria;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoriaRepositoryTests {

	@Autowired
	private CategoriaRepository repository;

	@Test
	void listaAsCincoCategoriasEmOrdemAlfabetica() {
		assertThat(repository.listarTodas())
				.extracting(Categoria::nome)
				.containsExactly("Buraco na via", "Calçada danificada", "Descarte irregular de lixo",
						"Iluminação pública", "Outros");
	}

	@Test
	void buscaCategoriaExistentePorId() {
		Categoria outros = repository.listarTodas().stream()
				.filter(c -> c.nome().equals("Outros"))
				.findFirst()
				.orElseThrow();

		assertThat(repository.buscarPorId(outros.id())).contains(outros);
	}

	@Test
	void retornaVazioParaCategoriaInexistente() {
		assertThat(repository.buscarPorId(999)).isEmpty();
	}

}
