/**
 * Painel de indicadores: busca as ocorrências e categorias REAIS da API, calcula os indicadores
 * (estatisticas.js) e os exibe em cartões, gráficos (Chart.js) e tabelas equivalentes.
 *
 * Cada gráfico tem uma tabela com os mesmos dados ("Ver dados em tabela"): ela torna o conteúdo
 * acessível a leitores de tela e mantém o painel útil se o Chart.js não carregar.
 */

import { listarCategorias, listarOcorrencias } from '../api.js';
import { STATUS, rotuloStatus } from '../dominio.js';
import {
  bairrosComMaisOcorrencias, contarPorStatus, hojeEmSaoPaulo, novasPorDia, porCategoria, taxaDeResolucao,
  tempoMedioDeResolucao, tempoMedioPorCategoria,
} from '../estatisticas.js';
import { aplicarTema, coresDeStatus, corDoTema, desenhar, graficosDisponiveis } from '../graficos.js';
import {
  criarEstado, criarTabela, definirCarregando, descreverErro, formatarDiaCurto, formatarDuracao, formatarNumero,
  formatarPercentual, mostrarAlerta,
} from '../ui.js';

const DIAS_NA_SERIE = 30;
const ALTURA_POR_BARRA = 34;

const carregando = document.getElementById('carregando');
const estado = document.getElementById('estado');
const painel = document.getElementById('painel');
const alertaPainel = document.getElementById('alerta-painel');
const alertaGraficos = document.getElementById('alerta-graficos');
const botaoAtualizar = document.getElementById('botao-atualizar');
const atualizadoEm = document.getElementById('atualizado-em');

const usarGraficos = graficosDisponiveis();
if (usarGraficos) aplicarTema();

// ---------- estados da página ----------

function mostrar(secao) {
  carregando.hidden = secao !== carregando;
  estado.hidden = secao !== estado;
  painel.hidden = secao !== painel;
  botaoAtualizar.hidden = secao === carregando;
}

function mostrarSemDados() {
  estado.replaceChildren(criarEstado({
    icone: 'vazio',
    titulo: 'Ainda não há dados para o painel',
    mensagem: 'Os indicadores e gráficos aparecem assim que a primeira ocorrência for registrada.',
    acoes: [{ rotulo: 'Registrar ocorrência', href: 'registrar.html' }],
  }));
  mostrar(estado);
}

function mostrarErro(erro) {
  const { titulo, mensagem } = descreverErro(erro);
  estado.replaceChildren(criarEstado({
    icone: erro.status === 0 ? 'conexao' : 'erro',
    titulo: erro.status === 0 ? titulo : 'Não foi possível carregar o painel',
    mensagem,
    acoes: [{ rotulo: 'Tentar novamente', aoClicar: () => carregar() }],
  }));
  mostrar(estado);
}

function marcarAtualizacao() {
  const agora = new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'America/Sao_Paulo', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).formatToParts(new Date());
  const parte = (tipo) => agora.find((p) => p.type === tipo).value;
  atualizadoEm.textContent = `Atualizado em ${parte('day')}/${parte('month')}/${parte('year')} às ${parte('hour')}:${parte('minute')}.`;
}

// ---------- cartões ----------

function preencherIndicadores(ocorrencias) {
  const contagem = contarPorStatus(ocorrencias);
  document.getElementById('kpi-total').textContent = formatarNumero(ocorrencias.length);
  for (const status of Object.keys(STATUS)) {
    document.getElementById(`kpi-${status}`).textContent = formatarNumero(contagem[status]);
  }
  document.getElementById('kpi-taxa').textContent = formatarPercentual(taxaDeResolucao(ocorrencias));
  const tempoMedio = tempoMedioDeResolucao(ocorrencias);
  document.getElementById('kpi-tempo').textContent = formatarDuracao(tempoMedio);
  document.getElementById('kpi-tempo-nota').textContent = tempoMedio === null
    ? 'nenhuma ocorrência resolvida ainda'
    : 'entre o registro e a resolução';
}

// ---------- gráficos + tabelas ----------

/** Preenche a tabela de um gráfico e o texto alternativo do canvas. */
function descrever(id, legenda, cabecalhos, linhas, resumo) {
  document.getElementById(`t-${id}`).replaceChildren(criarTabela(legenda, cabecalhos, linhas));
  document.getElementById(`g-${id}`).setAttribute('aria-label', `${legenda}. ${resumo}`);
}

function ajustarAltura(id, quantidadeDeBarras) {
  const area = document.getElementById(`g-${id}`).parentElement;
  area.style.setProperty('--altura-grafico', `${Math.max(220, quantidadeDeBarras * ALTURA_POR_BARRA + 70)}px`);
}

const eixoInteiro = { beginAtZero: true, ticks: { precision: 0 }, grid: { color: () => corDoTema('--cor-borda') } };

/**
 * Rótulos do eixo de categorias/bairros: em gráficos estreitos (celular), nomes longos são
 * abreviados com "…" para não serem cortados. O nome completo segue no tooltip e na tabela.
 */
function rotuloDoEixo(valor) {
  const rotulo = this.getLabelForValue(valor);
  const limite = this.chart.width < 420 ? 15 : 30;
  return rotulo.length > limite ? `${rotulo.slice(0, limite - 1)}…` : rotulo;
}
const eixoDeNomes = { grid: { display: false }, ticks: { callback: rotuloDoEixo } };

function graficoStatus(ocorrencias) {
  const contagem = contarPorStatus(ocorrencias);
  const status = Object.keys(STATUS);
  const percentual = (valor) => formatarPercentual((valor / ocorrencias.length) * 100);
  descrever('status', 'Ocorrências por status', ['Status', 'Quantidade', 'Percentual'],
    status.map((s) => [rotuloStatus(s), formatarNumero(contagem[s]), percentual(contagem[s])]),
    status.map((s) => `${rotuloStatus(s)}: ${contagem[s]}`).join('; '));
  if (!usarGraficos) return;
  const cores = coresDeStatus();
  desenhar(document.getElementById('g-status'), {
    type: 'doughnut',
    data: {
      labels: status.map(rotuloStatus),
      datasets: [{ data: status.map((s) => contagem[s]), backgroundColor: status.map((s) => cores[s]),
        borderColor: corDoTema('--cor-superficie'), borderWidth: 3, hoverOffset: 6 }],
    },
    options: {
      cutout: '62%',
      plugins: {
        legend: { position: 'bottom' },
        tooltip: { callbacks: { label: (c) => ` ${c.label}: ${formatarNumero(c.parsed)} (${percentual(c.parsed)})` } },
      },
    },
  });
}

function graficoCategoria(ocorrencias, categorias) {
  const linhas = porCategoria(ocorrencias, categorias);
  const status = Object.keys(STATUS);
  descrever('categoria', 'Ocorrências por categoria',
    ['Categoria', ...status.map(rotuloStatus), 'Total'],
    linhas.map((l) => [l.nome, ...status.map((s) => formatarNumero(l[s])), formatarNumero(l.total)]),
    linhas.map((l) => `${l.nome}: ${l.total}`).join('; '));
  if (!usarGraficos) return;
  ajustarAltura('categoria', linhas.length);
  const cores = coresDeStatus();
  desenhar(document.getElementById('g-categoria'), {
    type: 'bar',
    data: {
      labels: linhas.map((l) => l.nome),
      datasets: status.map((s) => ({ label: rotuloStatus(s), data: linhas.map((l) => l[s]), backgroundColor: cores[s],
        borderRadius: 4, maxBarThickness: 26 })),
    },
    options: {
      indexAxis: 'y',
      scales: { x: { ...eixoInteiro, stacked: true }, y: { ...eixoDeNomes, stacked: true } },
      plugins: { legend: { position: 'bottom' } },
    },
  });
}

function graficoDias(ocorrencias) {
  const serie = novasPorDia(ocorrencias, hojeEmSaoPaulo(), DIAS_NA_SERIE);
  const totalNoPeriodo = serie.reduce((soma, d) => soma + d.total, 0);
  document.getElementById('g-dias-descricao').textContent =
    `Registros por dia, no horário de São Paulo. ${formatarNumero(totalNoPeriodo)} no período.`;
  descrever('dias', `Novas ocorrências por dia nos últimos ${DIAS_NA_SERIE} dias`, ['Dia', 'Novas ocorrências'],
    serie.map((d) => [formatarDiaCurto(d.dia), formatarNumero(d.total)]),
    `${totalNoPeriodo} ocorrências no período; maior número em um dia: ${Math.max(...serie.map((d) => d.total))}`);
  if (!usarGraficos) return;
  const primaria = corDoTema('--cor-primaria');
  desenhar(document.getElementById('g-dias'), {
    type: 'line',
    data: {
      labels: serie.map((d) => formatarDiaCurto(d.dia)),
      datasets: [{ label: 'Novas ocorrências', data: serie.map((d) => d.total), borderColor: primaria,
        backgroundColor: corDoTema('--cor-primaria-clara'), fill: true, cubicInterpolationMode: 'monotone', borderWidth: 2.5,
        pointRadius: serie.map((d) => (d.total > 0 ? 3.5 : 0)), pointHoverRadius: 5,
        pointBackgroundColor: corDoTema('--cor-destaque'), pointBorderColor: primaria }],
    },
    options: {
      interaction: { intersect: false, mode: 'index' },
      scales: {
        x: { grid: { display: false }, ticks: { maxRotation: 0, autoSkip: true, autoSkipPadding: 16, maxTicksLimit: 10 } },
        y: eixoInteiro,
      },
      plugins: { legend: { display: false } },
    },
  });
}

function graficoBairros(ocorrencias) {
  const linhas = bairrosComMaisOcorrencias(ocorrencias, 10);
  descrever('bairros', 'Bairros com mais ocorrências', ['Bairro', 'Ocorrências'],
    linhas.map((l) => [l.bairro, formatarNumero(l.total)]),
    linhas.map((l) => `${l.bairro}: ${l.total}`).join('; '));
  if (!usarGraficos) return;
  ajustarAltura('bairros', linhas.length);
  desenhar(document.getElementById('g-bairros'), {
    type: 'bar',
    data: {
      labels: linhas.map((l) => l.bairro),
      datasets: [{ label: 'Ocorrências', data: linhas.map((l) => l.total), backgroundColor: corDoTema('--cor-primaria'),
        borderRadius: 4, maxBarThickness: 26 }],
    },
    options: {
      indexAxis: 'y',
      scales: { x: eixoInteiro, y: eixoDeNomes },
      plugins: { legend: { display: false } },
    },
  });
}

function graficoTempo(ocorrencias) {
  const linhas = tempoMedioPorCategoria(ocorrencias);
  const semResolvidas = linhas.length === 0;
  document.getElementById('g-tempo-vazio').hidden = !semResolvidas;
  document.getElementById('g-tempo-area').hidden = semResolvidas || !usarGraficos;
  document.getElementById('g-tempo-dados').hidden = semResolvidas;
  if (semResolvidas) return;
  descrever('tempo', 'Tempo médio de resolução por categoria', ['Categoria', 'Resolvidas', 'Tempo médio'],
    linhas.map((l) => [l.nome, formatarNumero(l.quantidade), formatarDuracao(l.dias)]),
    linhas.map((l) => `${l.nome}: ${formatarDuracao(l.dias)}`).join('; '));
  if (!usarGraficos) return;
  ajustarAltura('tempo', linhas.length);
  desenhar(document.getElementById('g-tempo'), {
    type: 'bar',
    data: {
      labels: linhas.map((l) => l.nome),
      datasets: [{ label: 'Tempo médio (dias)', data: linhas.map((l) => l.dias),
        backgroundColor: corDoTema('--cor-destaque'), borderRadius: 4, maxBarThickness: 26 }],
    },
    options: {
      indexAxis: 'y',
      scales: {
        x: { beginAtZero: true, title: { display: true, text: 'dias' }, grid: { color: () => corDoTema('--cor-borda') } },
        y: eixoDeNomes,
      },
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (c) => ` ${formatarDuracao(c.parsed.x)} (${linhas[c.dataIndex].quantidade} resolvidas)` } },
      },
    },
  });
}

/** Sem Chart.js: esconde as áreas de gráfico e abre as tabelas, que trazem os mesmos dados. */
function prepararModoSemGraficos() {
  if (usarGraficos) return;
  mostrarAlerta(alertaGraficos, {
    tipo: 'info',
    titulo: 'Gráficos indisponíveis',
    mensagem: 'Não foi possível carregar a biblioteca de gráficos. Os mesmos dados são exibidos nas tabelas abaixo.',
  });
  for (const area of document.querySelectorAll('.grafico__area')) area.hidden = true;
  for (const dados of document.querySelectorAll('.grafico__dados')) dados.open = true;
}

// ---------- carregamento ----------

async function carregar() {
  const primeiraCarga = painel.hidden;
  mostrarAlerta(alertaPainel, null);
  if (primeiraCarga) mostrar(carregando);
  else definirCarregando(botaoAtualizar, true, 'Atualizando…');

  try {
    // As categorias só completam a tabela por categoria com as que ainda não têm registros:
    // se falharem, o painel segue com as categorias presentes nas ocorrências.
    const [ocorrencias, categorias] = await Promise.allSettled([listarOcorrencias(), listarCategorias()]);
    if (ocorrencias.status === 'rejected') throw ocorrencias.reason;
    const listaDeCategorias = categorias.status === 'fulfilled' ? categorias.value : [];

    marcarAtualizacao();
    if (ocorrencias.value.length === 0) {
      mostrarSemDados();
      return;
    }
    preencherIndicadores(ocorrencias.value);
    mostrar(painel); // os gráficos são criados com o contêiner já visível, para medirem o tamanho certo
    prepararModoSemGraficos();
    graficoStatus(ocorrencias.value);
    graficoCategoria(ocorrencias.value, listaDeCategorias);
    graficoDias(ocorrencias.value);
    graficoBairros(ocorrencias.value);
    graficoTempo(ocorrencias.value);
  } catch (erro) {
    if (primeiraCarga) {
      mostrarErro(erro);
    } else {
      const { mensagem } = descreverErro(erro);
      mostrarAlerta(alertaPainel, { tipo: 'erro', titulo: 'Não foi possível atualizar o painel',
        mensagem: `${mensagem} Os dados exibidos são da última atualização.` });
    }
  } finally {
    if (!primeiraCarga) definirCarregando(botaoAtualizar, false);
  }
}

botaoAtualizar.addEventListener('click', carregar);
carregar();
