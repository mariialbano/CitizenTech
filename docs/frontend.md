# Frontend — Bairro em Foco

Interface web em **HTML, CSS e JavaScript nativos**, servida pelo próprio Spring Boot a partir de
`src/main/resources/static/` e consumindo a [API REST](api.md). Não há frameworks, etapa de build nem
dependências instaladas: basta iniciar a aplicação e acessar `http://localhost:8080`. A única biblioteca é o
**Chart.js**, usado no painel e incluído como cópia local (ver [Painel de indicadores](#painel-de-indicadores)).

## Estrutura

```
src/main/resources/static/
├── index.html               # Início: protocolo, indicadores, como funciona, categorias
├── registrar.html           # Cadastro de ocorrência
├── ocorrencias.html         # Listagem com filtros
├── ocorrencia.html          # Detalhe, histórico e atualização de status (?id=N)
├── painel.html              # Painel de indicadores (cartões, gráficos e tabelas)
├── css/estilos.css          # Tokens de tema, base, layout, componentes e páginas
├── js/
│   ├── api.js               # ÚNICO módulo que faz requisições HTTP (fetch)
│   ├── dominio.js           # Rótulos de status, ações por status e limites dos campos
│   ├── ui.js                # Componentes: selos, alertas, estados, avisos, datas, números, tabelas
│   ├── estatisticas.js      # Cálculo dos indicadores (funções puras, sem DOM nem rede)
│   ├── graficos.js          # Integração com o Chart.js: tema a partir das variáveis CSS
│   └── paginas/             # Um script por página
│       ├── inicio.js
│       ├── registrar.js
│       ├── ocorrencias.js
│       ├── ocorrencia.js
│       └── painel.js
├── img/logo.svg, img/favicon.svg
└── vendor/chartjs/4.6.1/    # Chart.js oficial, sem alterações, e sua licença (MIT)
    ├── chart.umd.min.js
    └── LICENSE.md
```

Cada página carrega um único script com `<script type="module">`. Os módulos importam o que precisam de
`api.js`, `dominio.js` e `ui.js`. Por serem *ES modules*, as páginas precisam ser abertas pelo servidor
(`http://localhost:8080/...`): abrir o arquivo HTML direto do disco (`file://`) não funciona.

## Navegação

```
index.html ──Registrar──▶ registrar.html ──sucesso──▶ ocorrencia.html?id=N
    │                                                        ▲
    ├──Consultar protocolo───────────────────────────────────┤
    ├──Ver ocorrências──▶ ocorrencias.html?status=&categoriaId= ──card──┘
    └──Ver o painel completo──▶ painel.html ──cartão de status──▶ ocorrencias.html?status=...
```

- O cabeçalho é o mesmo nas cinco páginas (Início · Registrar · Ocorrências · Painel). O link da página atual tem `aria-current="page"`.
- Os filtros da listagem ficam **na URL**: podem ser compartilhados, sobrevivem ao recarregamento e o
  "Voltar à lista" do detalhe retorna à lista com os mesmos filtros.

## Decisões técnicas

| Decisão | Motivo |
|---|---|
| Várias páginas, sem SPA | Sem roteador, sem build. Voltar e avançar do navegador funcionam naturalmente, cada página tem endereço próprio e o código é simples de explicar. |
| JavaScript nativo com *ES modules* | Organização em módulos sem bundler nem dependências |
| `fetch` somente em `api.js` | Um único ponto trata tempo limite, rede indisponível e o formato de erro (RFC 9457). As páginas só lidam com `ApiError`. |
| Fonte do sistema (`system-ui`) | Nenhuma requisição externa: carrega instantaneamente e funciona offline |
| Ícones SVG próprios | Sem biblioteca de ícones. No JavaScript, são criados elemento a elemento (`createElementNS`). |
| Mobile first | Estilos base para celular. Pontos de quebra em 640px e 960px. |

### Regras de negócio: o servidor decide

`dominio.js` contém o mínimo de conhecimento do domínio **apenas para orientar a interface**:

- **Ações por status:** em vez de oferecer os três status para qualquer ocorrência, a página mostra só os
  botões que fazem sentido a partir do status atual, por exemplo, nenhum botão para uma ocorrência resolvida.
- **Limites dos campos:** permitem avisar sobre um erro antes de enviar e configurar o `maxlength`.

A validação definitiva e as regras de transição continuam **no backend**. Se o servidor recusar algo
(400 ou 422), a interface mostra a mensagem dele. A interface nunca decide sozinha que uma operação foi válida.

### Datas e horário

A API envia datas como `LocalDateTime` **sem fuso horário**, por exemplo `"2026-11-14T08:00:00"`, que
representa o horário local de **São Paulo** em que o fato ocorreu.

O frontend **não converte** esse valor com `new Date()`. Ele apenas reorganiza o texto
(`formatarDataHora` em `ui.js`), produzindo `"14/11/2026 às 08:00"`. Assim:

- o horário exibido é **exatamente** o registrado, em qualquer fuso horário em que o navegador esteja;
- não há risco de um navegador em outro fuso deslocar o horário, nem de problemas com horário de verão.

O valor original fica no atributo `datetime` do elemento `<time>`, para leitores de tela e buscadores.

### Segurança: proteção contra XSS

Descrição, bairro e endereço são textos digitados por qualquer pessoa e exibidos para todas as outras.
Para que um texto como `<script>...</script>` **nunca seja executado**:

- todo dado da API é inserido com **`textContent`** ou em elementos criados com `document.createElement`;
- os cards da listagem vêm de **`<template>`** do próprio HTML, clonados e preenchidos com `textContent`;
- links são montados apenas com o `id` numérico, nunca com texto digitado.

O teste `PaginasEstaticasTests` **falha a build** se algum script usar `innerHTML`, `outerHTML`,
`insertAdjacentHTML`, `document.write` ou `eval`, e também se alguma página, e não o `api.js`, chamar `fetch`.

## Painel de indicadores

`painel.html` mostra seis cartões (total, abertas, em andamento, resolvidas, taxa de resolução e tempo médio de
resolução) e cinco gráficos. Cada gráfico tem uma **tabela equivalente** ("Ver dados em tabela").

### De onde vêm os números

Os indicadores são calculados **no navegador, com os dados reais da API**: `GET /api/ocorrencias` (todas as
ocorrências, pois a listagem não é paginada) e `GET /api/categorias`. Não existe nenhum valor fixo ou fictício.
Os cálculos ficam em `estatisticas.js`, como funções puras, e seguem as regras das consultas documentadas em
[modelo-dados.md](modelo-dados.md#consultas-do-dashboard):

| Indicador | Regra |
|---|---|
| Total e contagem por status | Todas as ocorrências retornadas pela API |
| Taxa de resolução | Resolvidas ÷ total × 100. Exibe "—" sem ocorrências. |
| Tempo médio de resolução | Média de `resolvidoEm − criadoEm`, apenas das resolvidas. Exibido em horas (abaixo de 1 dia) ou em dias. |
| Por categoria | Inclui as categorias **sem** ocorrências, com zero, como o `LEFT JOIN` + `COALESCE` da consulta SQL |
| Bairros (top 10) | Agrupamento igual ao do banco (`COLLATE NOCASE`): "Centro" e "CENTRO" são o mesmo bairro, "Sé" e "SÉ" não. Exibe a grafia mais frequente. |
| Novas por dia | Hoje e os 29 dias anteriores, **no horário de São Paulo**, com zero nos dias sem registro |
| Tempo médio por categoria | Somente as categorias com ocorrências resolvidas |

Por que no navegador, e não em um endpoint novo? A API já fornece tudo de que o painel precisa, e calcular no
navegador **não exigiu nenhuma mudança no backend**. Com um volume grande de ocorrências, o caminho é criar um
endpoint de estatísticas que use as consultas SQL já especificadas e devolva só os números.

### Como o Chart.js foi incluído

| Opção avaliada | Por que não, ou por que sim |
|---|---|
| CDN | Exigiria internet e um domínio externo a cada acesso, o que contraria a decisão de funcionar offline |
| WebJar (dependência Maven) | Alteraria o `pom.xml` e o build Java por causa de uma biblioteca só do frontend |
| **Cópia local fixa** ✅ | Funciona offline, versão fixa, nenhuma mudança no build |

O arquivo `vendor/chartjs/4.6.1/chart.umd.min.js` é o **arquivo oficial do pacote npm `chart.js@4.6.1`, sem
alterações**. O pacote foi baixado do registro npm e conferido com o hash de integridade (`sha512`) publicado.
A versão minificada já inclui a única dependência da biblioteca. A licença MIT acompanha o arquivo. O *source map*
não foi incluído (975 KB), porque só é usado pelas ferramentas de desenvolvedor.

A página carrega o Chart.js como script clássico (`defer`), antes do módulo `painel.js`. O tema dos gráficos
(`graficos.js`) lê as **mesmas variáveis CSS** da interface, como cores de status, primária, destaque e fonte,
então a identidade visual tem uma única fonte de verdade. A pasta `vendor/` fica fora de `js/`, então as regras
automáticas do projeto (sem `innerHTML`, `fetch` só em `api.js`) continuam valendo para o código próprio.

**Se o Chart.js não carregar,** o painel continua útil: mostra um aviso, oculta as áreas de gráfico e abre as tabelas.

### Acessibilidade dos gráficos

- Cada `<canvas>` tem `role="img"` e um `aria-label` com o resumo dos dados, gerado a partir dos valores reais.
- A tabela equivalente de cada gráfico pode ser aberta e lida por leitores de tela e por teclado.
- Cores de status iguais às do resto da interface. Legendas sempre com texto.
- Em gráficos estreitos (celular), nomes longos nos eixos são abreviados com "…". O nome completo aparece no tooltip e na tabela.
- Sem animações quando o sistema pede movimento reduzido.

### Atualização de status pública (limitação do MVP)

O MVP **não tem autenticação**. Por isso, a seção "Atualizar andamento" da página de detalhe fica disponível
a qualquer pessoa, e a própria página informa isso. Toda alteração fica registrada no histórico. Em uma
versão futura, essa ação deve exigir login de um perfil de gestão.

## Estados da interface

| Situação | Comportamento |
|---|---|
| Carregando | Esqueletos (blocos animados) na lista, no detalhe e nos indicadores |
| Enviando | Botão desabilitado com "Enviando…" ou "Atualizando…", o que impede envio duplicado |
| Erro de validação (navegador ou 400) | Resumo no topo, mensagem abaixo de cada campo, `aria-invalid` e foco no primeiro campo inválido. A mensagem some quando o campo é editado. |
| 404 no detalhe | "Protocolo Nº N não encontrado" com campo para consultar outro número |
| 409 / 422 na atualização | Alerta com a mensagem do servidor e recarregamento automático dos dados atuais |
| 500 | Mensagem genérica e "Tentar novamente" |
| Sem conexão ou tempo esgotado (15s) | "Sem conexão" e "Tentar novamente" |
| Lista vazia | Mensagens diferentes para "nenhuma ocorrência" e "nenhuma com esses filtros" |
| Categorias indisponíveis | No cadastro, o formulário fica bloqueado com alerta e "Tentar novamente". Na lista, o filtro fica desabilitado e a lista continua funcionando. |
| Sucesso | No cadastro, um painel com o protocolo em destaque e botão de copiar. Na atualização, um aviso temporário. |
| Painel sem ocorrências | "Ainda não há dados para o painel" com botão para registrar |
| Painel sem nenhuma resolvida | Tempo médio "—" e o cartão por categoria informa "Nenhuma ocorrência foi resolvida ainda" |
| Falha ao atualizar o painel | Alerta, mantendo os dados da última atualização |
| Chart.js indisponível | Aviso, áreas de gráfico ocultas e tabelas abertas com os mesmos dados |

Ações definitivas ("Marcar como resolvida") pedem confirmação em um `<dialog>` nativo.

## Acessibilidade

- HTML semântico, `lang="pt-BR"` e link "Pular para o conteúdo"
- `<label>` em todos os campos. Ajuda e erros ligados aos campos por `aria-describedby`.
- Mensagens dinâmicas anunciadas com `aria-live`. Alertas de erro usam `role="alert"`.
- O status nunca é indicado só por cor: o selo sempre tem ícone e rótulo.
- Foco visível em todos os elementos interativos e alvos de toque com pelo menos 44px
- Animações desligadas quando o sistema pede movimento reduzido (`prefers-reduced-motion`)

## Testes

### Automatizados

`PaginasEstaticasTests` (JUnit + MockMvc), executado com `.\mvnw.cmd test`:

- `/` encaminha para `index.html`. As cinco páginas são servidas como HTML, com `lang="pt-BR"`, *charset*, *viewport* e script de módulo.
- O menu de todas as páginas leva às cinco páginas.
- CSS, scripts, imagens e o Chart.js são servidos com o `Content-Type` correto.
- O Chart.js é a versão 4.6.1 fixada, acompanhada da licença, e é a referenciada pelo painel.
- Todos os `href` e `src` locais das páginas e todos os `import` dos módulos apontam para arquivos existentes.
- Só `api.js` faz requisições (`fetch`).
- Nenhum script usa `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` ou `eval`.

### Roteiro de testes manuais

Inicie a aplicação com um banco descartável, para não misturar os testes com os dados de desenvolvimento:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:sqlite:./target/teste-manual.db"
```

Abra `http://localhost:8080`. Para simular falhas de rede, use as DevTools do navegador (F12), aba
**Network**: o modo **Offline** simula a falta de conexão e **Block request URL** bloqueia uma rota específica.

**1. Página inicial**

| # | Passo | Resultado esperado |
|---|---|---|
| 1.1 | Abrir a página com o banco vazio | Indicadores 0 / 0 / 0, "0 ocorrências registradas no total", 5 categorias listadas |
| 1.2 | Digitar `abc` em "Já tem um protocolo?" e consultar | Mensagem "Informe o número do protocolo (somente números)" |
| 1.3 | Digitar o número de uma ocorrência existente e consultar | Abre o detalhe da ocorrência |
| 1.4 | Clicar em um indicador | Abre a lista já filtrada por aquele status |

**2. Cadastro**

| # | Passo | Resultado esperado |
|---|---|---|
| 2.1 | Enviar o formulário vazio | Resumo "Corrija os 4 campos destacados", erro abaixo de cada campo, foco na categoria |
| 2.2 | Digitar em um campo com erro | O erro daquele campo desaparece |
| 2.3 | Preencher o bairro só com espaços e enviar | "O bairro é obrigatório" |
| 2.4 | Descrição com 9 caracteres | "A descrição deve ter entre 10 e 1000 caracteres". O contador fica laranja. |
| 2.5 | Preencher tudo corretamente, com bairro `"  Vila   Nova "` | Painel de sucesso com o protocolo. No detalhe, o bairro aparece como "Vila Nova". |
| 2.6 | "Copiar protocolo" e "Registrar outra" | Aviso de copiado. O formulário volta limpo. |
| 2.7 | Bloquear `/api/categorias` nas DevTools e recarregar | Alerta "Não foi possível carregar as categorias" e formulário bloqueado. Desbloquear e clicar em "Tentar novamente" recupera. |

**3. Listagem**

| # | Passo | Resultado esperado |
|---|---|---|
| 3.1 | Abrir a lista com o banco vazio | "Nenhuma ocorrência registrada ainda" com botão para registrar |
| 3.2 | Com dados, mudar o filtro de status | A lista atualiza sem botão e a URL passa a ter `?status=...` |
| 3.3 | Combinar filtros sem resultado | "Nenhuma ocorrência com esses filtros" e "Limpar filtros" |
| 3.4 | Recarregar a página com filtros | Os filtros continuam aplicados |
| 3.5 | Acessar `ocorrencias.html?status=XYZ&categoriaId=999` | Filtros em "Todos" e URL corrigida |
| 3.6 | Abrir um card e clicar em "Voltar à lista" | Volta à lista com os mesmos filtros |
| 3.7 | Ativar o modo **Offline** e clicar em "Tentar novamente" | "Sem conexão". Ao voltar online e tentar de novo, a lista carrega. |

**4. Detalhe e atualização de status**

| # | Passo | Resultado esperado |
|---|---|---|
| 4.1 | Abrir uma ocorrência Aberta | Botões "Iniciar atendimento" e "Marcar como resolvida". Histórico com "Ocorrência registrada (Aberta)". |
| 4.2 | "Iniciar atendimento" | Selo "Em andamento", aviso de sucesso, novo item no topo do histórico |
| 4.3 | "Marcar como resolvida" e depois "Cancelar" (ou Esc) | Nada muda |
| 4.4 | "Marcar como resolvida" e "Confirmar resolução" | Selo "Resolvida", data de resolução e mensagem "O status é definitivo". Nenhum botão de ação. |
| 4.5 | Abrir a mesma ocorrência Aberta em **duas abas**, resolver na aba A e clicar em "Iniciar atendimento" na aba B | Alerta "A ocorrência já foi resolvida…" e a aba B recarrega os dados, mostrando "Resolvida" |
| 4.6 | Acessar `ocorrencia.html?id=99999` | "Protocolo Nº 99999 não encontrado" com campo para consultar outro |
| 4.7 | Acessar `ocorrencia.html?id=abc` | "Protocolo inválido" |

**5. Segurança (XSS)**

Cadastre uma ocorrência com os textos abaixo. Eles têm o tamanho mínimo exigido e podem ser colados como estão.

| Campo | Texto |
|---|---|
| Descrição | `<img src=x onerror="alert('XSS descrição')">Buraco <b>grande</b> perto da escola` |
| Bairro | `<script>alert('XSS bairro')</script>Centro` |
| Endereço | `"><svg onload=alert('XSS endereço')>` |

| # | Verificação | Resultado esperado |
|---|---|---|
| 5.1 | Enviar o cadastro | Sucesso. **Nenhum `alert` aparece.** |
| 5.2 | Abrir o detalhe | Os textos aparecem **literalmente**, com os sinais `<` e `>` visíveis. Nenhum `alert`, nenhuma imagem quebrada, nenhum texto em negrito vindo do `<b>`. |
| 5.3 | Abrir a lista e a página inicial | O card mostra os textos literais. Nenhum `alert`. |
| 5.4 | DevTools, aba **Elements**, no parágrafo da descrição | O conteúdo é um único nó de texto, sem elementos `<img>` ou `<b>` |
| 5.5 | Acessar `ocorrencia.html?id=<script>alert(1)</script>` | "Protocolo inválido". Nenhum `alert`. |

**6. Painel de indicadores**

| # | Passo | Resultado esperado |
|---|---|---|
| 6.1 | Abrir `painel.html` com o banco vazio | "Ainda não há dados para o painel" com botão para registrar |
| 6.2 | Registrar uma ocorrência e clicar em "Atualizar dados" | Total 1, Abertas 1, taxa 0%, tempo médio "—" e "Nenhuma ocorrência foi resolvida ainda" no gráfico por categoria |
| 6.3 | Registrar mais algumas, mudar status e atualizar | Cartões e gráficos conferem com a lista de ocorrências. "Outros" aparece com zero se não tiver registros. |
| 6.4 | Registrar "Centro" e "CENTRO" | Os dois contam como um único bairro no gráfico de bairros |
| 6.5 | Abrir "Ver dados em tabela" em cada gráfico | Mesmos números do gráfico |
| 6.6 | Clicar no cartão "Abertas" | Abre a lista filtrada por "Aberta" |
| 6.7 | Com o painel aberto, ativar **Offline** e clicar em "Atualizar dados" | Alerta "Não foi possível atualizar o painel". Os dados anteriores continuam visíveis. |
| 6.8 | Bloquear `chart.umd.min.js` nas DevTools e recarregar com o cache desativado | Aviso "Gráficos indisponíveis". Cartões e tabelas continuam com os dados. |
| 6.9 | Passar o mouse ou tocar nas barras e fatias | Tooltip com o valor e o nome completo |

**7. Responsividade**

Nas DevTools, ative o modo dispositivo (Ctrl+Shift+M) e repita a navegação em **360px**, **768px** e **1280px**.

| # | Verificação | Resultado esperado |
|---|---|---|
| 7.1 | Todas as páginas | Sem rolagem horizontal. O menu com 4 itens cabe sem quebrar o layout. |
| 7.2 | Lista | 1 coluna de cards no celular, 2 no tablet, 3 no desktop |
| 7.3 | Detalhe | No celular, histórico e "Atualizar andamento" ficam um embaixo do outro. No desktop, lado a lado. |
| 7.4 | Formulário | Campos com a largura toda no celular. As dicas vão para baixo do formulário. |
| 7.5 | Painel | Cartões em 2 colunas no celular e 3 a partir do tablet. Gráficos em 1 coluna até o desktop, depois em 2, com o gráfico diário na largura toda. Nenhum gráfico ultrapassa o cartão. |
| 7.6 | Navegação só com o teclado (Tab, Enter, Esc) | Foco sempre visível. O diálogo de confirmação fecha com Esc. "Ver dados em tabela" abre com Enter. |
