/**
 * Listagem de ocorrências com filtros por status e categoria.
 * Os filtros ficam na URL (?status=&categoriaId=): podem ser compartilhados, sobrevivem ao
 * recarregamento e são preservados ao voltar da página de detalhe.
 */

import { listarCategorias, listarOcorrencias } from '../api.js';
import { STATUS } from '../dominio.js';
import {
  clonarModelo, criarEstado, descreverErro, el, elementoDataHora, mostrarAlerta, seloStatus,
} from '../ui.js';

const formFiltros = document.getElementById('form-filtros');
const filtroStatus = document.getElementById('filtro-status');
const filtroCategoria = document.getElementById('filtro-categoria');
const alertaCategorias = document.getElementById('alerta-categorias');
const resumo = document.getElementById('resumo');
const resultados = document.getElementById('resultados');

/** Identifica a requisição mais recente, para descartar respostas antigas que cheguem depois. */
let requisicaoAtual = 0;

// ---------- filtros ↔ URL ----------

function lerFiltrosDaUrl() {
  const parametros = new URLSearchParams(window.location.search);
  const status = parametros.get('status') ?? '';
  const categoriaId = parametros.get('categoriaId') ?? '';
  return {
    status: status in STATUS ? status : '',
    categoriaId: /^\d+$/.test(categoriaId) ? categoriaId : '',
  };
}

function filtrosAtuais() {
  return { status: filtroStatus.value, categoriaId: filtroCategoria.disabled ? lerFiltrosDaUrl().categoriaId : filtroCategoria.value };
}

function atualizarUrl({ status, categoriaId }) {
  const parametros = new URLSearchParams();
  if (status) parametros.set('status', status);
  if (categoriaId) parametros.set('categoriaId', categoriaId);
  const consulta = parametros.toString();
  window.history.replaceState(null, '', consulta ? `?${consulta}` : window.location.pathname);
}

function preencherStatus() {
  filtroStatus.append(...Object.entries(STATUS)
    .map(([valor, { rotulo }]) => el('option', { texto: rotulo, atributos: { value: valor } })));
}

/** Se as categorias não carregarem, a lista continua funcionando, apenas sem esse filtro. */
async function preencherCategorias() {
  try {
    const categorias = await listarCategorias();
    filtroCategoria.replaceChildren(
      el('option', { texto: 'Todas as categorias', atributos: { value: '' } }),
      ...categorias.map((categoria) => el('option', { texto: categoria.nome, atributos: { value: String(categoria.id) } })),
    );
    filtroCategoria.disabled = false;
    mostrarAlerta(alertaCategorias, null);
  } catch (erro) {
    filtroCategoria.disabled = true;
    filtroCategoria.replaceChildren(el('option', { texto: 'Categorias indisponíveis', atributos: { value: '' } }));
    mostrarAlerta(alertaCategorias, {
      tipo: 'info',
      titulo: 'Filtro por categoria indisponível',
      mensagem: descreverErro(erro).mensagem,
    });
  }
}

// ---------- lista ----------

function mostrarCarregando() {
  resultados.setAttribute('aria-busy', 'true');
  resumo.textContent = 'Carregando ocorrências…';
  const grade = el('ul', { classe: 'grade-ocorrencias' });
  for (let i = 0; i < 6; i += 1) grade.append(clonarModelo('modelo-card-esqueleto'));
  resultados.replaceChildren(grade);
}

function criarCard(ocorrencia) {
  const card = clonarModelo('modelo-card');
  const campo = (nome) => card.querySelector(`[data-campo="${nome}"]`);
  card.querySelector('a').href = `ocorrencia.html?id=${encodeURIComponent(ocorrencia.id)}`;
  campo('protocolo').textContent = `Nº ${ocorrencia.id}`;
  campo('status').replaceWith(seloStatus(ocorrencia.status));
  campo('categoria').textContent = ocorrencia.categoria.nome;
  campo('bairro').textContent = ocorrencia.bairro;
  campo('endereco').textContent = ocorrencia.enderecoReferencia;
  campo('descricao').textContent = ocorrencia.descricao;
  campo('data').replaceWith(elementoDataHora(ocorrencia.criadoEm));
  return card;
}

function mostrarLista(ocorrencias, filtros) {
  if (ocorrencias.length === 0) {
    const filtrado = Boolean(filtros.status || filtros.categoriaId);
    resumo.textContent = '';
    resultados.replaceChildren(criarEstado(filtrado
      ? {
        icone: 'busca',
        titulo: 'Nenhuma ocorrência com esses filtros',
        mensagem: 'Tente outro status ou outra categoria.',
        acoes: [{ rotulo: 'Limpar filtros', aoClicar: limparFiltros }],
      }
      : {
        icone: 'vazio',
        titulo: 'Nenhuma ocorrência registrada ainda',
        mensagem: 'Viu algum problema no bairro? Seja a primeira pessoa a registrar.',
        acoes: [{ rotulo: 'Registrar ocorrência', href: 'registrar.html' }],
      }));
    return;
  }
  resumo.textContent = ocorrencias.length === 1 ? '1 ocorrência encontrada' : `${ocorrencias.length} ocorrências encontradas`;
  resultados.replaceChildren(el('ul', { classe: 'grade-ocorrencias' }, ocorrencias.map(criarCard)));
}

async function carregarLista() {
  const id = ++requisicaoAtual;
  const filtros = filtrosAtuais();
  mostrarCarregando();
  try {
    const ocorrencias = await listarOcorrencias(filtros);
    if (id !== requisicaoAtual) return;
    mostrarLista(ocorrencias, filtros);
  } catch (erro) {
    if (id !== requisicaoAtual) return;
    const { titulo, mensagem } = descreverErro(erro);
    resumo.textContent = '';
    resultados.replaceChildren(criarEstado({
      icone: erro.status === 0 ? 'conexao' : 'erro',
      titulo: erro.status === 0 ? titulo : 'Não foi possível carregar as ocorrências',
      mensagem,
      acoes: [{ rotulo: 'Tentar novamente', aoClicar: tentarNovamente }],
    }));
  } finally {
    if (id === requisicaoAtual) resultados.setAttribute('aria-busy', 'false');
  }
}

/** Após uma falha de conexão, tenta também recuperar o filtro de categorias, se ele falhou. */
async function tentarNovamente() {
  if (filtroCategoria.disabled) {
    const categoriaDaUrl = lerFiltrosDaUrl().categoriaId;
    await preencherCategorias();
    if (!filtroCategoria.disabled) filtroCategoria.value = categoriaDaUrl;
  }
  carregarLista();
}

// ---------- eventos ----------

function aoMudarFiltro() {
  atualizarUrl(filtrosAtuais());
  carregarLista();
}

function limparFiltros() {
  filtroStatus.value = '';
  if (!filtroCategoria.disabled) filtroCategoria.value = '';
  atualizarUrl({ status: '', categoriaId: '' });
  carregarLista();
}

formFiltros.addEventListener('change', aoMudarFiltro);
formFiltros.addEventListener('submit', (evento) => evento.preventDefault());
document.getElementById('limpar-filtros').addEventListener('click', limparFiltros);

async function iniciar() {
  mostrarCarregando();
  preencherStatus();
  await preencherCategorias();

  const filtros = lerFiltrosDaUrl();
  filtroStatus.value = filtros.status;
  // Categoria da URL que não existe: o seletor fica em "Todas" e a URL é corrigida logo abaixo.
  if (!filtroCategoria.disabled) filtroCategoria.value = filtros.categoriaId;
  atualizarUrl(filtrosAtuais());
  carregarLista();
}

iniciar();
