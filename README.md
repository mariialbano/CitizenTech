# Bairro em Foco

Aplicação web colaborativa para **registrar e acompanhar problemas de infraestrutura urbana**, como buracos nas ruas, iluminação pública danificada, descarte irregular de lixo e calçadas danificadas.

Projeto acadêmico desenvolvido para a **Global Solution 2026 da FIAP**, com o tema **CitizenTech**: tecnologia a serviço da cidadania e da participação do cidadão na melhoria do espaço urbano.

> **Status:** MVP concluído: backend (banco de dados, regras de negócio e API REST), interface web e painel de indicadores com Chart.js, com 175 testes automatizados.

---

## Sumário

- [O problema](#o-problema)
- [A solução](#a-solução)
- [Funcionalidades](#funcionalidades)
- [Interface web](#interface-web)
- [Tecnologias](#tecnologias)
- [Arquitetura](#arquitetura)
- [Modelo de dados](#modelo-de-dados)
- [API REST](#api-rest)
- [Como executar](#como-executar)
- [Testes](#testes)
- [Decisões técnicas](#decisões-técnicas)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Limitações do MVP](#limitações-do-mvp)
- [Próximos passos](#próximos-passos)
- [Documentação complementar](#documentação-complementar)
- [Equipe](#equipe)

---

## O problema

Problemas de infraestrutura no bairro, como um buraco na rua, um poste apagado ou lixo acumulado, costumam ser percebidos por muitos moradores. Mesmo assim:

- não existe um lugar simples e centralizado para registrar o problema;
- quem registra raramente consegue **acompanhar** se o problema está sendo resolvido;
- não há visibilidade sobre **quais problemas são mais frequentes** e **em quais bairros**.

## A solução

O **Bairro em Foco** permite que qualquer pessoa:

1. **Registre** uma ocorrência informando a categoria, uma descrição, o bairro e um endereço ou ponto de referência;
2. **Receba um número de protocolo** para acompanhar a ocorrência;
3. **Consulte o andamento** (`ABERTO` → `EM_ANDAMENTO` → `RESOLVIDO`) e o **histórico completo** de alterações;
4. **Visualize indicadores** sobre as ocorrências: totais por status na página inicial e um painel com gráficos por status, categoria, bairro e período, taxa e tempo médio de resolução.

**Privacidade desde o início:** o registro **não coleta nenhum dado pessoal** (nome, e-mail, telefone ou IP). O cidadão acompanha a ocorrência apenas pelo número de protocolo.

## Funcionalidades

| Funcionalidade | Situação |
|---|---|
| Cadastro de ocorrências com validação e normalização dos dados | ✅ API e interface |
| Consulta por protocolo e listagem com filtros por status e categoria | ✅ API e interface |
| Atualização de status com regras de transição | ✅ API e interface |
| Histórico de todas as alterações de status | ✅ API e interface |
| Indicadores por status na página inicial | ✅ Interface |
| Painel de indicadores com gráficos (Chart.js) | ✅ Interface |

**Categorias:** Buraco na via · Iluminação pública · Descarte irregular de lixo · Calçada danificada · Outros

**Ciclo de vida do status:**

```mermaid
stateDiagram-v2
    [*] --> ABERTO : registro
    ABERTO --> EM_ANDAMENTO
    ABERTO --> RESOLVIDO
    EM_ANDAMENTO --> RESOLVIDO
    RESOLVIDO --> [*]
```

`RESOLVIDO` é um estado **definitivo**: uma ocorrência resolvida não pode ser reaberta.

## Interface web

Cinco páginas, servidas pelo próprio Spring Boot em `http://localhost:8080`:

| Página | O que oferece |
|---|---|
| **Início** (`/`) | Apresentação, consulta por número de protocolo, indicadores de ocorrências abertas, em andamento e resolvidas, e as categorias |
| **Registrar** (`/registrar.html`) | Formulário com validação imediata, erros indicados em cada campo e o protocolo exibido em destaque ao final |
| **Ocorrências** (`/ocorrencias.html`) | Lista em cards com filtros por status e categoria. Os filtros ficam na URL e podem ser compartilhados. |
| **Detalhe** (`/ocorrencia.html?id=N`) | Dados da ocorrência, linha do tempo do histórico e atualização de status, mostrando só as transições permitidas e pedindo confirmação para resolver |
| **Painel** (`/painel.html`) | Cartões com total, abertas, em andamento, resolvidas, taxa e tempo médio de resolução, e 5 gráficos: por status, por categoria (separada por status), novas ocorrências nos últimos 30 dias, bairros com mais ocorrências e tempo médio de resolução por categoria. Cada gráfico tem uma tabela equivalente. |

A interface é **responsiva** (celular, tablet e desktop) e foi pensada para ser acessível: navegação por teclado, mensagens anunciadas a leitores de tela e status indicados por cor, ícone e texto. Ela também trata estados de **carregamento**, **lista vazia**, **erro de validação**, **protocolo não encontrado**, **conflito de atualização**, **erro do servidor** e **falta de conexão**.

É feita com HTML, CSS e JavaScript **nativos**, sem frameworks nem etapa de build. A única biblioteca é o **Chart.js**, usado só no painel. Detalhes e roteiro de testes manuais em [docs/frontend.md](docs/frontend.md).

## Tecnologias

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1 (Spring MVC, Bean Validation) |
| Banco de dados | SQLite (arquivo local, sem servidor) |
| Acesso a dados | JDBC com `JdbcClient` do Spring, com SQL escrito à mão e **sem JPA/Hibernate** |
| Build | Maven Wrapper (não exige Maven instalado) |
| Testes | JUnit 5, AssertJ, Mockito, MockMvc |
| Frontend | HTML, CSS e JavaScript nativos (*ES modules*), sem frameworks |
| Gráficos | Chart.js 4.6.1 (licença MIT), cópia local em `static/vendor/` |

O projeto não usa Docker nem servidor de banco de dados externo: basta ter o Java 21 instalado.

## Arquitetura

Aplicação monolítica em camadas. O próprio Spring Boot serve a API e os arquivos do frontend, a partir de uma única aplicação e de um único comando para executar.

```mermaid
flowchart LR
    U[Navegador<br/>HTML + JS] -->|HTTP / JSON| C[Controller<br/>REST]
    C --> S[Service<br/>regras de negócio]
    S --> R[Repository<br/>JDBC + SQL]
    R --> DB[(SQLite)]
    C -.erros.-> H[GlobalExceptionHandler<br/>ProblemDetail]
```

| Camada | Responsabilidade |
|---|---|
| `controller` | Expõe os endpoints REST e converte modelos em DTOs de resposta |
| `service` | Regras de negócio: validação, normalização, transições de status, transações |
| `repository` | Acesso ao SQLite com SQL explícito, uma classe por tabela |
| `model` | Records que espelham as tabelas e o enum `StatusOcorrencia` |
| `dto` | Formato de entrada e saída da API (os records de `model` nunca são expostos) |
| `exception` | Exceções de negócio e tratamento global de erros |
| `config` | Relógio da aplicação (fuso de São Paulo) e verificação do banco na inicialização |

## Modelo de dados

```mermaid
erDiagram
    CATEGORIA ||--o{ OCORRENCIA : "classifica"
    OCORRENCIA ||--|{ HISTORICO_STATUS : "registra"

    CATEGORIA {
        INTEGER id PK
        TEXT nome UK
    }
    OCORRENCIA {
        INTEGER id PK "número de protocolo"
        INTEGER categoria_id FK
        TEXT descricao
        TEXT bairro
        TEXT endereco_referencia
        TEXT status
        TEXT criado_em
        TEXT atualizado_em
        TEXT resolvido_em "só quando RESOLVIDO"
    }
    HISTORICO_STATUS {
        INTEGER id PK
        INTEGER ocorrencia_id FK
        TEXT status_anterior "nulo na criação"
        TEXT status_novo
        TEXT alterado_em
    }
```

A integridade é garantida também no banco, e não só no Java:

- **chaves estrangeiras** ativadas em toda conexão (`PRAGMA foreign_keys=ON`) e `ON DELETE RESTRICT`;
- **restrições `CHECK`** de tamanho dos textos, de valores válidos de status e de coerência de datas (por exemplo, `resolvido_em` existe se e somente se o status é `RESOLVIDO`);
- **índices** nas chaves estrangeiras e no status, usados pelos filtros e pelo histórico.

Detalhes completos, incluindo as consultas planejadas para o dashboard, estão em [docs/modelo-dados.md](docs/modelo-dados.md).

## API REST

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/categorias` | Lista as categorias |
| `POST` | `/api/ocorrencias` | Registra uma ocorrência (retorna `201` e o protocolo) |
| `GET` | `/api/ocorrencias?status=&categoriaId=` | Lista as ocorrências, com filtros opcionais |
| `GET` | `/api/ocorrencias/{id}` | Consulta uma ocorrência pelo protocolo |
| `GET` | `/api/ocorrencias/{id}/historico` | Linha do tempo de status |
| `PATCH` | `/api/ocorrencias/{id}/status` | Atualiza o status |

**Exemplo: registrar uma ocorrência**

```http
POST /api/ocorrencias
Content-Type: application/json

{
  "categoriaId": 1,
  "descricao": "Buraco grande na faixa da direita",
  "bairro": "Vila Nova",
  "enderecoReferencia": "Rua das Flores, 100"
}
```

```json
{
  "id": 1,
  "categoria": { "id": 1, "nome": "Buraco na via" },
  "descricao": "Buraco grande na faixa da direita",
  "bairro": "Vila Nova",
  "enderecoReferencia": "Rua das Flores, 100",
  "status": "ABERTO",
  "criadoEm": "2026-11-14T08:00:00",
  "atualizadoEm": "2026-11-14T08:00:00",
  "resolvidoEm": null
}
```

Os erros seguem o padrão **[RFC 9457 – Problem Details](https://www.rfc-editor.org/rfc/rfc9457)** (`application/problem+json`), com mensagens em português e erros indicados por campo:

```json
{
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos estão inválidos.",
  "instance": "/api/ocorrencias",
  "erros": { "descricao": "A descrição deve ter entre 10 e 1000 caracteres" }
}
```

| Código | Situação |
|---|---|
| 400 | Dados inválidos, JSON malformado ou tipo incorreto |
| 404 | Ocorrência não encontrada |
| 409 | Conflito: o status foi alterado por outra operação |
| 422 | Transição de status não permitida |
| 500 | Erro inesperado. A mensagem é genérica e nenhum detalhe interno é exposto. |

A documentação completa, com todos os exemplos e um roteiro de testes manuais, está em [docs/api.md](docs/api.md).

## Como executar

### Pré-requisitos

- **Java 21** (JDK). Para verificar: `java -version`
- Não é necessário instalar o Maven nem um servidor de banco de dados.

### Passo a passo

```bash
git clone <url-do-repositório>
cd <pasta-do-projeto>
```

**Windows (PowerShell):**

```powershell
.\mvnw.cmd spring-boot:run
```

**Linux / macOS:**

```bash
./mvnw spring-boot:run
```

Acesse **http://localhost:8080** no navegador para usar a interface. A API fica em `http://localhost:8080/api`. Por exemplo:

```bash
curl http://localhost:8080/api/categorias
```

Na primeira execução, o banco é criado automaticamente em `data/bairro-em-foco.db`, já com as categorias iniciais. O log de inicialização confirma a conexão:

```
SQLite conectado: ...\data\bairro-em-foco.db | foreign_keys=ON | categorias cadastradas: 5
```

### Recriar o banco de desenvolvimento

O `schema.sql` só cria o que ainda não existe e **nunca apaga dados**. Para começar com um banco limpo, pare a aplicação e renomeie (ou apague) `data/bairro-em-foco.db`. O procedimento seguro está descrito em [docs/modelo-dados.md](docs/modelo-dados.md#evolução-do-schema-e-recriação-do-banco-de-desenvolvimento).

## Testes

```powershell
.\mvnw.cmd test        # Windows
./mvnw test            # Linux / macOS
```

**175 testes automatizados**, todos passando:

| Área | Testes | O que garantem |
|---|---|---|
| Banco de dados | 38 | Restrições `NOT NULL`, `CHECK`, `UNIQUE`, chaves estrangeiras, índices, idempotência dos scripts, persistência |
| Repositories | 29 | Inserções, consultas, filtros, ordenação, atualização de status, conversão de datas |
| Services | 38 | Validação, normalização do bairro, todas as transições permitidas e proibidas, conflito de atualização, **rollback** da transação |
| API REST | 37 | Contrato HTTP de cada endpoint, formato JSON, códigos de erro, `ProblemDetail`, ausência de detalhes internos em erros 500, fluxo completo até o banco |
| Frontend | 30 | Páginas e recursos servidos com o tipo correto, menu completo em todas as páginas, links e imports existentes, `fetch` só no cliente da API, nenhum uso de `innerHTML` (proteção contra XSS), Chart.js na versão fixada e com licença |
| Aplicação | 3 | Inicialização do contexto e relógio da aplicação |

Os testes usam um **banco SQLite separado** em `target/`, recriado a cada execução, e nunca alteram o banco de desenvolvimento.

A interface também tem um **roteiro de testes manuais** (fluxos, estados de erro, responsividade e XSS) em [docs/frontend.md](docs/frontend.md#roteiro-de-testes-manuais).

## Decisões técnicas

- **JDBC puro em vez de JPA.** O SQL fica explícito e fácil de explicar, o comportamento é previsível e o projeto é adequado ao escopo. O `JdbcClient` do Spring evita código repetitivo sem esconder as consultas.
- **SQLite.** Banco em um único arquivo, sem instalação nem servidor. É ideal para um projeto acadêmico e para rodar em qualquer máquina com Java.
- **Validação em duas camadas.** As mensagens amigáveis vêm do Bean Validation no Java, e as restrições `CHECK` no banco são a última linha de defesa.
- **Normalização antes da validação.** Os espaços extras são removidos, e no bairro `"  Vila   Nova "` vira `"Vila Nova"`. Isso evita que o mesmo bairro apareça duplicado nas estatísticas.
- **Histórico transacional.** A ocorrência e o seu registro de histórico são gravados na **mesma transação**: ou as duas gravações acontecem, ou nenhuma. Há testes que provocam uma falha no histórico e verificam o rollback.
- **Proteção contra atualização concorrente.** A mudança de status só é aplicada se o status atual ainda for o esperado (`UPDATE ... WHERE status = :statusAtual`). Caso contrário, a API responde `409 Conflict`.
- **Datas com um relógio único.** As datas são geradas por um `Clock` no fuso `America/Sao_Paulo`, com precisão de segundos, o que permite fixar o horário nos testes. No banco, ficam no formato `YYYY-MM-DD HH:MM:SS`, compatível com as funções de data do SQLite.
- **Uma conexão no pool.** O SQLite aceita uma escrita por vez, e uma única conexão evita erros de `database is locked`.
- **Erros padronizados (RFC 9457).** As exceções de negócio e os erros do próprio Spring seguem o mesmo formato, e erros inesperados nunca expõem SQL, stack traces ou mensagens internas.
- **Frontend sem frameworks.** São páginas HTML independentes com JavaScript nativo em módulos. Um único módulo (`api.js`) faz todas as requisições e trata os erros da API.
- **Proteção contra XSS.** Todo texto digitado pelos usuários é exibido com `textContent`, nunca interpretado como HTML. Um teste automatizado impede o uso de `innerHTML` nos scripts.
- **Horário exibido sem conversão.** A API envia o horário local de São Paulo sem fuso, e a interface apenas o formata (`14/11/2026 às 08:00`). Assim, o horário mostrado é o registrado, em qualquer fuso em que o navegador esteja.
- **O servidor decide as regras.** A interface mostra só as ações de status possíveis e valida os campos antes de enviar, para orientar o usuário. A validação definitiva continua no backend, e qualquer recusa do servidor é exibida.
- **Painel calculado com os dados reais da API.** Os indicadores são calculados no navegador a partir de `GET /api/ocorrencias`, que retorna todas as ocorrências. Por isso, o painel não exigiu nenhuma mudança no backend.
- **Chart.js como cópia local.** O arquivo oficial da versão 4.6.1, sem alterações e com hash de integridade verificado, fica em `static/vendor/`. Ele funciona offline, não depende de CDN e não alterou o `pom.xml`. Se o Chart.js não carregar, o painel mostra os mesmos dados em tabelas.

## Estrutura do projeto

```
├── data/                        # banco SQLite de desenvolvimento (ignorado pelo Git)
├── docs/
│   ├── api.md                   # documentação da API REST
│   ├── frontend.md              # interface web, decisões e roteiro de testes manuais
│   └── modelo-dados.md          # modelo de dados, decisões e consultas do dashboard
├── src/
│   ├── main/
│   │   ├── java/br/com/bairroemfoco/
│   │   │   ├── config/          # Clock, verificação do banco na inicialização
│   │   │   ├── controller/      # endpoints REST
│   │   │   ├── dto/             # entrada e saída da API
│   │   │   ├── exception/       # exceções de negócio e GlobalExceptionHandler
│   │   │   ├── model/           # records das tabelas e enum de status
│   │   │   ├── repository/      # acesso ao SQLite via JdbcClient
│   │   │   └── service/         # regras de negócio
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── schema.sql       # criação das tabelas e índices
│   │       ├── data.sql         # categorias iniciais
│   │       └── static/          # frontend: 5 páginas HTML, css/, js/, img/ e vendor/ (Chart.js)
│   └── test/                    # testes unitários e de integração
├── mvnw, mvnw.cmd               # Maven Wrapper
└── pom.xml
```

## Limitações do MVP

- **Atualização de status pública.** Não há autenticação, então **qualquer pessoa** pode atualizar o andamento de uma ocorrência pela página de detalhe. A própria página informa isso, e toda alteração fica registrada no histórico. Em uma versão futura, essa ação deve ser restrita a um perfil de gestão com login.
- **Listagem sem paginação.** A API retorna todas as ocorrências de uma vez, o que é adequado ao volume de um MVP. O painel também depende disso: com um volume grande, o caminho é um endpoint de estatísticas no backend, usando as consultas SQL já especificadas em [docs/modelo-dados.md](docs/modelo-dados.md#consultas-do-dashboard).
- **Bairro como texto livre.** Espaços extras são removidos e o agrupamento ignora maiúsculas e minúsculas em letras sem acento. Grafias diferentes, como "Sé" e "Se" ou "SÉ" e "sé", ainda contam como bairros distintos.

## Próximos passos

- [x] **Frontend** em HTML, CSS e JavaScript: registro, consulta por protocolo, listagem com filtros, detalhe com histórico e atualização de status.
- [x] **Painel de indicadores** com Chart.js: totais por status, ocorrências por categoria e por bairro, evolução diária, taxa de resolução e tempo médio de resolução.

**Possíveis evoluções:** autenticação para a gestão do andamento, paginação da listagem e endpoint de estatísticas para grandes volumes.

**Fora do escopo do MVP:** autenticação, upload de imagens, geolocalização automática e integração com órgãos públicos.

## Documentação complementar

- [docs/modelo-dados.md](docs/modelo-dados.md): tabelas, restrições, índices, datas, ciclo de vida do status e consultas do dashboard
- [docs/api.md](docs/api.md): endpoints, exemplos de requisição e resposta, formato de erros e testes manuais
- [docs/frontend.md](docs/frontend.md): estrutura da interface, painel de indicadores, decisões técnicas, estados, acessibilidade e roteiro de testes manuais

## Equipe

- Danilo Senna - RM 561588
- Jéssica Brito - RM 565848
- Mariana Albano - RM 564677

---

Projeto acadêmico, FIAP, Global Solution 2026, tema CitizenTech.
