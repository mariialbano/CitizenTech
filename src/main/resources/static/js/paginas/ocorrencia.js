/**
 * Detalhe de uma ocorrência (ocorrencia.html?id=42): dados, linha do tempo e atualização de status.
 *
 * Os botões de ação seguem ACOES_POR_STATUS apenas para orientar a interface. Quem valida a
 * transição é o servidor: se ele recusar (422) ou houver conflito (409), a página mostra a
 * mensagem recebida e recarrega os dados atuais.
 */

import { atualizarStatus, buscarOcorrencia, listarHistorico } from '../api.js';
import { ACOES_POR_STATUS, STATUS, rotuloStatus } from '../dominio.js';
import {
  avisar, criarEstado, definirCarregando, descreverErro, el, elementoDataHora, formatarDataHora, icone,
  mostrarAlerta, seloStatus,
} from '../ui.js';

const carregando = document.getElementById('carregando');
const estado = document.getElementById('estado');
const detalhe = document.getElementById('detalhe');
const alertaAndamento = document.getElementById('andamento-alerta');
const acoes = document.getElementById('andamento-acoes');
const dialogo = document.getElementById('dialogo-confirmacao');

const textoId = new URLSearchParams(window.location.search).get('id') ?? '';
const id = /^\d+$/.test(textoId) && Number(textoId) > 0 ? Number(textoId) : null;

// ---------- estados da página ----------

function mostrar(secao) {
  carregando.hidden = secao !== carregando;
  estado.hidden = secao !== estado;
  detalhe.hidden = secao !== detalhe;
}

function mostrarNaoEncontrada() {
  document.title = 'Ocorrência não encontrada · Bairro em Foco';
  const formulario = el('form', { classe: 'consulta__formulario' }, [
    el('label', { classe: 'visualmente-oculto', texto: 'Número do protocolo', atributos: { for: 'outro-protocolo' } }),
    el('input', { classe: 'entrada', atributos: {
      id: 'outro-protocolo', name: 'id', inputmode: 'numeric', autocomplete: 'off', placeholder: 'Ex.: 42', required: '',
      pattern: '[0-9]+',
    } }),
    el('button', { classe: 'botao botao--primario', texto: 'Consultar', atributos: { type: 'submit' } }),
  ]);
  formulario.action = 'ocorrencia.html';
  formulario.method = 'get';

  const bloco = criarEstado({
    icone: 'busca',
    titulo: id ? `Protocolo Nº ${id} não encontrado` : 'Protocolo inválido',
    mensagem: 'Confira o número informado no registro ou consulte outro protocolo.',
    acoes: [{ rotulo: 'Ver todas as ocorrências', href: 'ocorrencias.html', secundaria: true }],
  });
  bloco.querySelector('.estado__mensagem').after(formulario);
  estado.replaceChildren(bloco);
  mostrar(estado);
}

function mostrarErro(erro) {
  const { titulo, mensagem } = descreverErro(erro);
  estado.replaceChildren(criarEstado({
    icone: erro.status === 0 ? 'conexao' : 'erro',
    titulo: erro.status === 0 ? titulo : 'Não foi possível carregar a ocorrência',
    mensagem,
    acoes: [{ rotulo: 'Tentar novamente', aoClicar: carregar }],
  }));
  mostrar(estado);
}

// ---------- dados ----------

function preencherOcorrencia(ocorrencia) {
  document.title = `Ocorrência Nº ${ocorrencia.id} · Bairro em Foco`;
  document.getElementById('titulo-ocorrencia').textContent = `Ocorrência Nº ${ocorrencia.id}`;
  document.getElementById('selo-status').replaceChildren(seloComTamanho(ocorrencia.status));
  document.getElementById('categoria').textContent = ocorrencia.categoria.nome;
  document.getElementById('bairro').textContent = ocorrencia.bairro;
  document.getElementById('endereco').textContent = ocorrencia.enderecoReferencia;
  document.getElementById('descricao').textContent = ocorrencia.descricao;
  document.getElementById('criado-em').replaceChildren(elementoDataHora(ocorrencia.criadoEm));
  document.getElementById('atualizado-em').replaceChildren(elementoDataHora(ocorrencia.atualizadoEm));
  document.getElementById('bloco-resolvido-em').hidden = !ocorrencia.resolvidoEm;
  document.getElementById('resolvido-em').replaceChildren(elementoDataHora(ocorrencia.resolvidoEm));
  document.getElementById('andamento-status').replaceChildren(seloStatus(ocorrencia.status));
  preencherAcoes(ocorrencia);
}

function seloComTamanho(status) {
  const selo = seloStatus(status);
  selo.classList.add('selo--grande');
  return selo;
}

/** Linha do tempo: a API envia da mais antiga para a mais recente; exibimos a mais recente primeiro. */
function preencherHistorico(historico) {
  const itens = [...historico].reverse().map((registro) => {
    const titulo = registro.statusAnterior
      ? `${rotuloStatus(registro.statusAnterior)} → ${rotuloStatus(registro.statusNovo)}`
      : `Ocorrência registrada (${rotuloStatus(registro.statusNovo)})`;
    const modificador = STATUS[registro.statusNovo]?.modificador ?? '';
    return el('li', { classe: 'linha-do-tempo__item' }, [
      el('span', { classe: `linha-do-tempo__marcador linha-do-tempo__marcador--${modificador}`, atributos: { 'aria-hidden': 'true' } }),
      el('p', { classe: 'linha-do-tempo__titulo', texto: titulo }),
      el('p', { classe: 'linha-do-tempo__data' }, [elementoDataHora(registro.alteradoEm)]),
    ]);
  });
  document.getElementById('historico').replaceChildren(...itens);
}

// ---------- ações de status ----------

function preencherAcoes(ocorrencia) {
  const disponiveis = ACOES_POR_STATUS[ocorrencia.status] ?? [];
  if (disponiveis.length === 0) {
    acoes.replaceChildren(el('p', { classe: 'andamento__final' }, [
      icone('check'),
      el('span', { texto: ocorrencia.resolvidoEm
        ? `Resolvida em ${formatarDataHora(ocorrencia.resolvidoEm)}. O status é definitivo.`
        : 'Não há alterações de status disponíveis.' }),
    ]));
    return;
  }
  acoes.replaceChildren(...disponiveis.map((acao, indice) => {
    const botao = el('button', {
      classe: `botao botao--bloco ${indice === 0 ? 'botao--primario' : 'botao--secundario'}`,
      texto: acao.rotulo,
      atributos: { type: 'button' },
    });
    botao.addEventListener('click', () => executarAcao(acao, botao));
    return botao;
  }));
}

// ---------- confirmação ----------

/**
 * Resposta da confirmação em aberto. A decisão vem dos eventos síncronos `submit` (botões) e
 * `cancel` (tecla Esc), e não do evento `close`: este é assíncrono e o navegador pode descartá-lo
 * se o diálogo for reaberto rapidamente, o que deixaria uma confirmação antiga pendente e
 * faria uma única confirmação disparar duas atualizações.
 */
let responderConfirmacao = null;

function encerrarConfirmacao(confirmado) {
  const responder = responderConfirmacao;
  responderConfirmacao = null;
  responder?.(confirmado);
}

dialogo.addEventListener('submit', (evento) => encerrarConfirmacao(evento.submitter?.value === 'confirmar'));
dialogo.addEventListener('cancel', () => encerrarConfirmacao(false));

/** Abre o diálogo de confirmação e resolve com true somente se a pessoa confirmar. */
function confirmar() {
  encerrarConfirmacao(false);
  return new Promise((resolver) => {
    responderConfirmacao = resolver;
    dialogo.showModal();
  });
}

async function executarAcao(acao, botao) {
  if (acao.confirmar && !(await confirmar())) return;

  mostrarAlerta(alertaAndamento, null);
  const botoes = [...acoes.querySelectorAll('button')];
  botoes.forEach((b) => { b.disabled = true; });
  definirCarregando(botao, true, 'Atualizando…');
  try {
    const atualizada = await atualizarStatus(id, acao.status);
    preencherOcorrencia(atualizada);
    preencherHistorico(await listarHistorico(id));
    avisar(`Status atualizado para “${rotuloStatus(atualizada.status)}”.`);
  } catch (erro) {
    await tratarErroDeAtualizacao(erro);
  } finally {
    // Em caso de sucesso os botões já foram recriados; restaurar os antigos não tem efeito visível.
    definirCarregando(botao, false);
    botoes.forEach((b) => { b.disabled = false; });
  }
}

async function tratarErroDeAtualizacao(erro) {
  if (erro.status === 404) {
    mostrarNaoEncontrada();
    return;
  }
  const { titulo, mensagem } = descreverErro(erro);
  const recarregar = erro.status === 409 || erro.status === 422;
  mostrarAlerta(alertaAndamento, {
    tipo: 'erro',
    titulo,
    mensagem: recarregar ? `${mensagem} Os dados abaixo foram atualizados.` : mensagem,
  });
  if (recarregar) await carregar({ manterAlerta: true });
  alertaAndamento.focus();
}

// ---------- carregamento ----------

async function carregar({ manterAlerta = false } = {}) {
  if (!manterAlerta) {
    mostrarAlerta(alertaAndamento, null);
    mostrar(carregando);
  }
  try {
    const [ocorrencia, historico] = await Promise.all([buscarOcorrencia(id), listarHistorico(id)]);
    preencherOcorrencia(ocorrencia);
    preencherHistorico(historico);
    mostrar(detalhe);
  } catch (erro) {
    if (erro.status === 404) mostrarNaoEncontrada();
    else mostrarErro(erro);
  }
}

/** "Voltar à lista" preserva os filtros quando a pessoa veio da listagem. */
function configurarVoltar() {
  try {
    const origem = new URL(document.referrer);
    if (origem.origin === window.location.origin && origem.pathname.endsWith('/ocorrencias.html')) {
      document.getElementById('voltar').href = `ocorrencias.html${origem.search}`;
    }
  } catch {
    // Sem referrer válido: mantém o link padrão.
  }
}

configurarVoltar();
if (id) carregar();
else mostrarNaoEncontrada();
