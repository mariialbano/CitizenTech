-- Modelo de dados do Bairro em Foco (documentado em docs/modelo-dados.md).
-- Somente comandos IF NOT EXISTS: executado a cada inicialização sem apagar dados.
-- Atenção: IF NOT EXISTS não altera tabelas já existentes. Mudanças de estrutura
-- exigem recriar o banco de desenvolvimento ou um script de migração.
--
-- Datas: TEXT no formato 'YYYY-MM-DD HH:MM:SS', horário de São Paulo, geradas pela aplicação.
-- Status: ABERTO, EM_ANDAMENTO ou RESOLVIDO (RESOLVIDO é definitivo).

CREATE TABLE IF NOT EXISTS categoria (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT    NOT NULL UNIQUE COLLATE NOCASE
                 CHECK (nome = trim(nome) AND length(nome) BETWEEN 1 AND 60)
);

CREATE TABLE IF NOT EXISTS ocorrencia (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    categoria_id        INTEGER NOT NULL REFERENCES categoria (id) ON DELETE RESTRICT,
    descricao           TEXT    NOT NULL CHECK (length(trim(descricao)) BETWEEN 10 AND 1000),
    bairro              TEXT    NOT NULL COLLATE NOCASE
                                CHECK (bairro = trim(bairro)
                                       AND instr(bairro, '  ') = 0
                                       AND length(bairro) BETWEEN 2 AND 80),
    endereco_referencia TEXT    NOT NULL CHECK (length(trim(endereco_referencia)) BETWEEN 5 AND 200),
    status              TEXT    NOT NULL DEFAULT 'ABERTO'
                                CHECK (status IN ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO')),
    criado_em           TEXT    NOT NULL,
    atualizado_em       TEXT    NOT NULL,
    resolvido_em        TEXT,
    CHECK ((status = 'RESOLVIDO') = (resolvido_em IS NOT NULL)),
    CHECK (atualizado_em >= criado_em),
    CHECK (resolvido_em IS NULL OR resolvido_em >= criado_em)
);

CREATE TABLE IF NOT EXISTS historico_status (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    ocorrencia_id   INTEGER NOT NULL REFERENCES ocorrencia (id) ON DELETE RESTRICT,
    status_anterior TEXT    CHECK (status_anterior IN ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO')),
    status_novo     TEXT    NOT NULL CHECK (status_novo IN ('ABERTO', 'EM_ANDAMENTO', 'RESOLVIDO')),
    alterado_em     TEXT    NOT NULL,
    CHECK (status_anterior IS NULL OR status_anterior <> status_novo)
);

-- O SQLite não indexa chaves estrangeiras automaticamente
CREATE INDEX IF NOT EXISTS idx_historico_ocorrencia ON historico_status (ocorrencia_id, alterado_em);
CREATE INDEX IF NOT EXISTS idx_ocorrencia_status    ON ocorrencia (status);
CREATE INDEX IF NOT EXISTS idx_ocorrencia_categoria ON ocorrencia (categoria_id);
