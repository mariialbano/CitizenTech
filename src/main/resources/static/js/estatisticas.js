/**
 * Cálculo dos indicadores a partir dos dados REAIS da API (GET /api/ocorrencias e /api/categorias).
 * Funções puras: não acessam a página nem a rede, apenas transformam listas em números.
 *
 * A API não pagina a listagem, então ela contém todas as ocorrências cadastradas e os
 * indicadores refletem o total da plataforma. As regras de agrupamento seguem as consultas
 * documentadas em docs/modelo-dados.md ("Consultas do dashboard").
 */

import { STATUS } from './dominio.js';

const MS_POR_DIA = 24 * 60 * 60 * 1000;
const PADRAO_DATA_HORA = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/;

/**
 * Converte "2026-11-14T08:00:00" (horário local de São Paulo, sem fuso) em milissegundos,
 * tratando o valor como UTC. Assim, diferenças entre duas datas da API são exatas, em qualquer
 * fuso do navegador, sem conversões nem efeitos de horário de verão.
 */
export function lerDataHora(texto) {
  const partes = PADRAO_DATA_HORA.exec(texto ?? '');
  if (!partes) return null;
  const [, ano, mes, dia, hora, minuto, segundo = '0'] = partes;
  return Date.UTC(Number(ano), Number(mes) - 1, Number(dia), Number(hora), Number(minuto), Number(segundo));
}

/** Data de hoje em São Paulo ("2026-11-14"), o mesmo referencial das datas da API. */
export function hojeEmSaoPaulo(agora = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(agora);
}

/** { ABERTO: n, EM_ANDAMENTO: n, RESOLVIDO: n }, sempre com os três status (zero quando não houver). */
export function contarPorStatus(ocorrencias) {
  const contagem = Object.fromEntries(Object.keys(STATUS).map((status) => [status, 0]));
  for (const { status } of ocorrencias) {
    if (status in contagem) contagem[status] += 1;
  }
  return contagem;
}

/** Percentual de resolvidas (0–100) ou null quando não há ocorrências. */
export function taxaDeResolucao(ocorrencias) {
  if (ocorrencias.length === 0) return null;
  return (contarPorStatus(ocorrencias).RESOLVIDO / ocorrencias.length) * 100;
}

function diasAteResolver(ocorrencia) {
  const inicio = lerDataHora(ocorrencia.criadoEm);
  const fim = lerDataHora(ocorrencia.resolvidoEm);
  return inicio === null || fim === null ? null : (fim - inicio) / MS_POR_DIA;
}

/** Média de dias entre registro e resolução, considerando só as resolvidas; null se nenhuma. */
export function tempoMedioDeResolucao(ocorrencias) {
  const duracoes = ocorrencias.map(diasAteResolver).filter((dias) => dias !== null);
  if (duracoes.length === 0) return null;
  return duracoes.reduce((soma, dias) => soma + dias, 0) / duracoes.length;
}

/**
 * Ocorrências por categoria e status. Inclui categorias sem ocorrências (com zeros), como o
 * LEFT JOIN + COALESCE da consulta documentada. Ordena pelo total, depois pelo nome.
 * @param {Array} categorias lista de /api/categorias; se vazia, usa as categorias presentes nas ocorrências
 */
export function porCategoria(ocorrencias, categorias = []) {
  const linhas = new Map();
  const garantir = (id, nome) => {
    if (!linhas.has(id)) linhas.set(id, { id, nome, total: 0, ...contarPorStatus([]) });
    return linhas.get(id);
  };
  for (const categoria of categorias) garantir(categoria.id, categoria.nome);
  for (const { categoria, status } of ocorrencias) {
    const linha = garantir(categoria.id, categoria.nome);
    linha.total += 1;
    if (status in STATUS) linha[status] += 1;
  }
  return [...linhas.values()].sort((a, b) => b.total - a.total || a.nome.localeCompare(b.nome, 'pt-BR'));
}

/**
 * Agrupa bairros como o banco faz (COLLATE NOCASE): ignora maiúsculas/minúsculas apenas em
 * letras sem acento. O nome exibido é a grafia mais frequente do grupo.
 */
function chaveDeBairro(bairro) {
  return bairro.replace(/[A-Z]/g, (letra) => letra.toLowerCase());
}

/** Os `limite` bairros com mais ocorrências: [{ bairro, total }]. */
export function bairrosComMaisOcorrencias(ocorrencias, limite = 10) {
  const grupos = new Map();
  for (const { bairro } of ocorrencias) {
    const chave = chaveDeBairro(bairro);
    const grupo = grupos.get(chave) ?? { total: 0, grafias: new Map() };
    grupo.total += 1;
    grupo.grafias.set(bairro, (grupo.grafias.get(bairro) ?? 0) + 1);
    grupos.set(chave, grupo);
  }
  return [...grupos.values()]
    .map(({ total, grafias }) => ({
      bairro: [...grafias.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'pt-BR'))[0][0],
      total,
    }))
    .sort((a, b) => b.total - a.total || a.bairro.localeCompare(b.bairro, 'pt-BR'))
    .slice(0, limite);
}

/**
 * Novas ocorrências por dia nos últimos `dias` dias (incluindo hoje), com zero nos dias sem registro.
 * @returns {Array<{ dia: string, total: number }>} dia no formato "AAAA-MM-DD"
 */
export function novasPorDia(ocorrencias, hoje, dias = 30) {
  const [ano, mes, dia] = hoje.split('-').map(Number);
  const fim = Date.UTC(ano, mes - 1, dia);
  const serie = new Map();
  for (let i = dias - 1; i >= 0; i -= 1) {
    serie.set(new Date(fim - i * MS_POR_DIA).toISOString().slice(0, 10), 0);
  }
  for (const { criadoEm } of ocorrencias) {
    const diaDoRegistro = (criadoEm ?? '').slice(0, 10);
    if (serie.has(diaDoRegistro)) serie.set(diaDoRegistro, serie.get(diaDoRegistro) + 1);
  }
  return [...serie.entries()].map(([d, total]) => ({ dia: d, total }));
}

/** Tempo médio de resolução por categoria (somente categorias com ocorrências resolvidas). */
export function tempoMedioPorCategoria(ocorrencias) {
  const grupos = new Map();
  for (const ocorrencia of ocorrencias) {
    const dias = diasAteResolver(ocorrencia);
    if (dias === null) continue;
    const grupo = grupos.get(ocorrencia.categoria.id) ?? { nome: ocorrencia.categoria.nome, soma: 0, quantidade: 0 };
    grupo.soma += dias;
    grupo.quantidade += 1;
    grupos.set(ocorrencia.categoria.id, grupo);
  }
  return [...grupos.values()]
    .map(({ nome, soma, quantidade }) => ({ nome, dias: soma / quantidade, quantidade }))
    .sort((a, b) => b.dias - a.dias || a.nome.localeCompare(b.nome, 'pt-BR'));
}
