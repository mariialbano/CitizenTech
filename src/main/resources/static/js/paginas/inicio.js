/**
 * Página inicial: consulta por protocolo, indicadores por status e categorias.
 */

import { listarCategorias, listarOcorrencias } from '../api.js';
import { contarPorStatus } from '../estatisticas.js';
import { descreverErro, el, limparErroCampo, mostrarAlerta } from '../ui.js';

const formConsulta = document.getElementById('form-consulta');
const campoProtocolo = document.getElementById('protocolo');
const erroProtocolo = document.getElementById('erro-protocolo');

formConsulta.addEventListener('submit', (evento) => {
  evento.preventDefault();
  const protocolo = campoProtocolo.value.trim().replace(/^n[º°o]?\s*/i, '');
  if (!/^\d+$/.test(protocolo) || Number(protocolo) < 1) {
    campoProtocolo.setAttribute('aria-invalid', 'true');
    erroProtocolo.textContent = 'Informe o número do protocolo (somente números).';
    erroProtocolo.hidden = false;
    campoProtocolo.focus();
    return;
  }
  window.location.href = `ocorrencia.html?id=${Number(protocolo)}`;
});

campoProtocolo.addEventListener('input', () => limparErroCampo(campoProtocolo));

// ---------- indicadores ----------

const alertaIndicadores = document.getElementById('indicadores-alerta');
const totalIndicadores = document.getElementById('indicadores-total');

/**
 * Conta as ocorrências por status a partir da listagem COMPLETA (sem filtros).
 * A API não pagina, então a lista contém todas as ocorrências cadastradas.
 */
async function carregarIndicadores() {
  mostrarAlerta(alertaIndicadores, null);
  try {
    const ocorrencias = await listarOcorrencias();
    for (const [status, quantidade] of Object.entries(contarPorStatus(ocorrencias))) {
      document.querySelector(`.indicador__valor[data-status="${status}"]`).textContent = quantidade;
    }
    totalIndicadores.textContent = ocorrencias.length === 1
      ? '1 ocorrência registrada no total.'
      : `${ocorrencias.length} ocorrências registradas no total.`;
  } catch (erro) {
    for (const valor of document.querySelectorAll('.indicador__valor')) valor.textContent = '—';
    totalIndicadores.textContent = '';
    const { mensagem } = descreverErro(erro);
    mostrarAlerta(alertaIndicadores, {
      tipo: 'erro',
      titulo: 'Não foi possível carregar os indicadores',
      mensagem,
      acao: { rotulo: 'Tentar novamente', aoClicar: carregarIndicadores },
    });
  }
}

// ---------- categorias ----------

/** Seção opcional: se a API falhar, ela simplesmente não aparece. */
async function carregarCategorias() {
  try {
    const categorias = await listarCategorias();
    if (categorias.length === 0) return;
    document.getElementById('lista-categorias')
      .replaceChildren(...categorias.map((categoria) => el('li', { classe: 'etiqueta', texto: categoria.nome })));
    document.getElementById('secao-categorias').hidden = false;
  } catch {
    // Informação complementar: a página continua útil sem ela.
  }
}

carregarIndicadores();
carregarCategorias();
