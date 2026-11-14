-- Categorias iniciais.
-- INSERT OR IGNORE + UNIQUE(nome): se a categoria já existe, a linha é ignorada,
-- então o script pode rodar a cada inicialização sem duplicar registros.

INSERT OR IGNORE INTO categoria (nome) VALUES ('Buraco na via');
INSERT OR IGNORE INTO categoria (nome) VALUES ('Iluminação pública');
INSERT OR IGNORE INTO categoria (nome) VALUES ('Descarte irregular de lixo');
INSERT OR IGNORE INTO categoria (nome) VALUES ('Calçada danificada');
INSERT OR IGNORE INTO categoria (nome) VALUES ('Outros');
