# API REST — Bairro em Foco

Base: `http://localhost:8080/api`. Todas as requisições e respostas usam JSON.
Modelo de dados e regras de negócio: [modelo-dados.md](modelo-dados.md).

## Convenções

- **Protocolo:** o `id` da ocorrência é o número de protocolo mostrado ao cidadão.
- **Datas:** formato ISO-8601 sem fuso (`2026-11-14T08:00:00`), sempre no **horário de São Paulo**.
- **Status:** `ABERTO`, `EM_ANDAMENTO` ou `RESOLVIDO`. O frontend é responsável pelos rótulos de exibição.
- **Privacidade:** não há dados pessoais. Campos extras enviados no JSON (ex.: `nome`, `email`) são **ignorados**.
- **Erros:** formato `ProblemDetail` da RFC 9457, com `Content-Type: application/problem+json` (ver [Erros](#erros)).

## Endpoints

| Método | Rota | Sucesso | Erros |
|---|---|---|---|
| `GET` | `/api/categorias` | 200 | — |
| `POST` | `/api/ocorrencias` | 201 + `Location` | 400 |
| `GET` | `/api/ocorrencias` | 200 | 400 |
| `GET` | `/api/ocorrencias/{id}` | 200 | 400, 404 |
| `GET` | `/api/ocorrencias/{id}/historico` | 200 | 400, 404 |
| `PATCH` | `/api/ocorrencias/{id}/status` | 200 | 400, 404, 409, 422 |

### Listar categorias

`GET /api/categorias` lista as categorias em ordem alfabética. É usado no `<select>` do formulário.

```json
[
  { "id": 1, "nome": "Buraco na via" },
  { "id": 4, "nome": "Calçada danificada" },
  { "id": 3, "nome": "Descarte irregular de lixo" },
  { "id": 2, "nome": "Iluminação pública" },
  { "id": 5, "nome": "Outros" }
]
```

### Cadastrar ocorrência

`POST /api/ocorrencias`

```json
{
  "categoriaId": 1,
  "descricao": "Buraco grande na faixa da direita",
  "bairro": "  Vila   Nova ",
  "enderecoReferencia": "Rua das Flores, 100"
}
```

| Campo | Obrigatório | Regra |
|---|---|---|
| `categoriaId` | sim | Deve existir em `/api/categorias` |
| `descricao` | sim | 10 a 1000 caracteres |
| `bairro` | sim | 2 a 80 caracteres. Os espaços extras são removidos. |
| `enderecoReferencia` | sim | 5 a 200 caracteres |

Os espaços nas pontas de todos os textos são removidos **antes** da validação. Toda ocorrência começa com status `ABERTO`, e qualquer `status` enviado no corpo é ignorado.

Resposta: **201 Created**

```http
Location: http://localhost:8080/api/ocorrencias/1
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

A existência da categoria só é verificada depois que os demais campos são válidos. Por isso, o erro `categoriaId: "Categoria não encontrada"` aparece sozinho.

### Listar ocorrências

`GET /api/ocorrencias?status=ABERTO&categoriaId=1`

Os dois filtros são opcionais e podem ser combinados. A lista vem das ocorrências **mais recentes para as mais antigas**, e cada item tem o mesmo formato do cadastro. Quando nada é encontrado, a resposta é `200 []`, inclusive para uma `categoriaId` inexistente.

Um `status` desconhecido retorna **400**, com a lista de valores aceitos em `erros.status`.

### Buscar ocorrência

`GET /api/ocorrencias/{id}` → **200** com o mesmo formato do cadastro. Se o protocolo não existir → **404**.

### Histórico de uma ocorrência

`GET /api/ocorrencias/{id}/historico` → **200** com a linha do tempo, da alteração mais antiga para a mais recente:

```json
[
  { "statusAnterior": null,           "statusNovo": "ABERTO",       "alteradoEm": "2026-11-14T08:00:00" },
  { "statusAnterior": "ABERTO",       "statusNovo": "EM_ANDAMENTO", "alteradoEm": "2026-11-15T09:30:00" },
  { "statusAnterior": "EM_ANDAMENTO", "statusNovo": "RESOLVIDO",    "alteradoEm": "2026-11-18T17:00:00" }
]
```

O `statusAnterior` é `null` no registro de criação.

### Atualizar status

`PATCH /api/ocorrencias/{id}/status`

```json
{ "status": "EM_ANDAMENTO" }
```

A resposta é **200** com a ocorrência atualizada: novo `status` e novo `atualizadoEm`, além do `resolvidoEm` quando o novo status é `RESOLVIDO`. A alteração também é registrada no histórico.

Transições permitidas: `ABERTO → EM_ANDAMENTO`, `ABERTO → RESOLVIDO` e `EM_ANDAMENTO → RESOLVIDO`.
`RESOLVIDO` é definitivo. Qualquer outra transição retorna **422**.

## Erros

Todas as respostas de erro seguem a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):

| Campo | Sempre presente | Descrição |
|---|---|---|
| `title` | sim | Resumo do tipo de erro |
| `status` | sim | Código HTTP |
| `detail` | sim | Explicação para o usuário |
| `instance` | sim | Caminho da requisição |
| `erros` | só em 400 por campo | Mapa `campo → mensagem`, com os mesmos nomes de campo do JSON |
| `statusAtual`, `statusSolicitado` | só em 422 | Transição que foi recusada |

O campo `type` é omitido. Pela RFC 9457, um `type` ausente equivale a `"about:blank"`, o que significa que o significado do erro é o do próprio código HTTP.

| Código | Situação |
|---|---|
| 400 | Dados inválidos, JSON malformado, tipo errado (ex.: `"categoriaId": "abc"`, `?status=FECHADO`, `/ocorrencias/abc`) |
| 404 | Ocorrência ou rota inexistente |
| 405 / 415 | Método ou `Content-Type` não suportado (mensagens técnicas do Spring, em inglês) |
| 409 | O status foi alterado por outra operação durante a requisição. Consulte e tente novamente. |
| 422 | Transição de status não permitida |
| 500 | Erro inesperado. A mensagem é genérica e os detalhes ficam só no log do servidor. |

**400 — dados inválidos**
```json
{
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Um ou mais campos estão inválidos.",
  "instance": "/api/ocorrencias",
  "erros": {
    "descricao": "A descrição deve ter entre 10 e 1000 caracteres",
    "bairro": "O bairro é obrigatório",
    "enderecoReferencia": "O endereço ou ponto de referência deve ter entre 5 e 200 caracteres"
  }
}
```

**400 — JSON malformado ou corpo ausente** (sem `erros`)
```json
{
  "title": "Dados inválidos",
  "status": 400,
  "detail": "O corpo da requisição está ausente ou não é um JSON válido.",
  "instance": "/api/ocorrencias"
}
```

**404 — ocorrência não encontrada**
```json
{
  "title": "Ocorrência não encontrada",
  "status": 404,
  "detail": "Ocorrência não encontrada: protocolo 999",
  "instance": "/api/ocorrencias/999"
}
```

**409 — conflito de atualização**
```json
{
  "title": "Conflito de atualização",
  "status": 409,
  "detail": "A ocorrência 1 foi alterada por outra operação. Consulte o status atual e tente novamente.",
  "instance": "/api/ocorrencias/1/status"
}
```

**422 — transição não permitida**
```json
{
  "title": "Transição de status não permitida",
  "status": 422,
  "detail": "A ocorrência já foi resolvida e não pode ser reaberta nem alterada",
  "instance": "/api/ocorrencias/1/status",
  "statusAtual": "RESOLVIDO",
  "statusSolicitado": "ABERTO"
}
```

**500 — erro inesperado**
```json
{
  "title": "Erro interno",
  "status": 500,
  "detail": "Ocorreu um erro inesperado. Tente novamente mais tarde.",
  "instance": "/api/ocorrencias/1"
}
```

## Testando manualmente (Windows)

Com a aplicação rodando (`.\mvnw.cmd spring-boot:run`), no **PowerShell**:

```powershell
$api = "http://localhost:8080/api"

# Categorias
Invoke-RestMethod "$api/categorias"

# Cadastrar
$nova = @{ categoriaId = 1; descricao = "Buraco grande na faixa da direita";
           bairro = "  Vila   Nova "; enderecoReferencia = "Rua das Flores, 100" } | ConvertTo-Json
$criada = Invoke-RestMethod "$api/ocorrencias" -Method Post -ContentType "application/json; charset=utf-8" `
          -Body ([Text.Encoding]::UTF8.GetBytes($nova))
$criada

# Consultar, listar e mudar status
Invoke-RestMethod "$api/ocorrencias/$($criada.id)"
Invoke-RestMethod "$api/ocorrencias?status=ABERTO"
Invoke-RestMethod "$api/ocorrencias/$($criada.id)/status" -Method Patch -ContentType "application/json" `
                  -Body '{"status":"EM_ANDAMENTO"}'
Invoke-RestMethod "$api/ocorrencias/$($criada.id)/historico"
```

No PowerShell 5.1, o `Invoke-RestMethod` lança exceção em respostas 4xx/5xx. Para ver o corpo de um erro, use `curl.exe -i`, que vem no Windows 11:

```powershell
curl.exe -i "$api/ocorrencias/999"
```

As requisições acima gravam dados no banco de desenvolvimento (`data/bairro-em-foco.db`). Para testar sem afetá-lo, inicie a aplicação com um banco descartável:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:sqlite:./target/teste-manual.db"
```
