/**
 * Cliente da API REST (documentada em docs/api.md). É o ÚNICO módulo que usa fetch:
 * as páginas chamam estas funções e tratam {@link ApiError}.
 */

const TEMPO_LIMITE_MS = 15000;

/**
 * Erro de uma chamada à API, com os campos do ProblemDetail (RFC 9457).
 * `status` 0 indica que o servidor não respondeu (sem conexão ou tempo esgotado).
 */
export class ApiError extends Error {
  constructor({ status, title, detail, erros, statusAtual, statusSolicitado }) {
    super(detail || title);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.erros = erros ?? {};
    this.statusAtual = statusAtual ?? null;
    this.statusSolicitado = statusSolicitado ?? null;
  }

  get temErrosDeCampo() {
    return Object.keys(this.erros).length > 0;
  }
}

export function listarCategorias() {
  return requisitar('/api/categorias');
}

/** @param {{categoriaId: number, descricao: string, bairro: string, enderecoReferencia: string}} dados */
export function cadastrarOcorrencia(dados) {
  return requisitar('/api/ocorrencias', { metodo: 'POST', corpo: dados });
}

/** Filtros vazios são omitidos; sem filtros, retorna todas as ocorrências. */
export function listarOcorrencias({ status, categoriaId } = {}) {
  const parametros = new URLSearchParams();
  if (status) parametros.set('status', status);
  if (categoriaId) parametros.set('categoriaId', categoriaId);
  const consulta = parametros.toString();
  return requisitar(`/api/ocorrencias${consulta ? `?${consulta}` : ''}`);
}

export function buscarOcorrencia(id) {
  return requisitar(`/api/ocorrencias/${encodeURIComponent(id)}`);
}

export function listarHistorico(id) {
  return requisitar(`/api/ocorrencias/${encodeURIComponent(id)}/historico`);
}

export function atualizarStatus(id, status) {
  return requisitar(`/api/ocorrencias/${encodeURIComponent(id)}/status`, { metodo: 'PATCH', corpo: { status } });
}

async function requisitar(caminho, { metodo = 'GET', corpo } = {}) {
  const controle = new AbortController();
  const temporizador = setTimeout(() => controle.abort(), TEMPO_LIMITE_MS);
  const cabecalhos = { Accept: 'application/json' };
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';

  let resposta;
  try {
    resposta = await fetch(caminho, {
      method: metodo,
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      signal: controle.signal,
    });
  } catch (erro) {
    throw new ApiError({
      status: 0,
      title: 'Sem conexão',
      detail: erro.name === 'AbortError'
        ? 'O servidor demorou demais para responder. Tente novamente.'
        : 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
    });
  } finally {
    clearTimeout(temporizador);
  }

  const dados = await lerJson(resposta);
  if (!resposta.ok) {
    throw erroDaResposta(resposta.status, dados);
  }
  if (dados === null) {
    throw new ApiError({ status: resposta.status, title: 'Resposta inesperada',
      detail: 'O servidor enviou uma resposta inesperada. Tente novamente.' });
  }
  return dados;
}

/** Retorna o corpo em JSON ou null se não houver um JSON válido (ex.: página de erro em HTML). */
async function lerJson(resposta) {
  const tipo = resposta.headers.get('Content-Type') ?? '';
  if (!tipo.includes('json')) return null;
  try {
    return await resposta.json();
  } catch {
    return null;
  }
}

function erroDaResposta(status, problema) {
  if (problema && typeof problema === 'object' && !Array.isArray(problema)) {
    return new ApiError({ ...problema, status });
  }
  return new ApiError({
    status,
    title: 'Erro',
    detail: status >= 500
      ? 'Ocorreu um erro inesperado. Tente novamente mais tarde.'
      : 'Não foi possível concluir a operação.',
  });
}
