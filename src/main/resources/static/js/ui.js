/**
 * Componentes e utilitários de interface compartilhados pelas páginas.
 *
 * Segurança: todo texto vindo da API (descrição, bairro, endereço...) é inserido com
 * textContent, nunca como HTML. Isso impede que um texto digitado por alguém seja
 * executado como código na página de outra pessoa (XSS).
 */

import { ApiError } from './api.js';
import { STATUS } from './dominio.js';

// ---------- criação de elementos ----------

/**
 * Cria um elemento com classe, texto e atributos. O texto é sempre tratado como texto puro.
 * @param {string} tag
 * @param {{classe?: string, texto?: string|number, atributos?: Object<string,string>}} [opcoes]
 * @param {Array<Node|string>} [filhos]
 */
export function el(tag, { classe, texto, atributos } = {}, filhos = []) {
  const elemento = document.createElement(tag);
  if (classe) elemento.className = classe;
  if (texto !== undefined && texto !== null) elemento.textContent = String(texto);
  for (const [nome, valor] of Object.entries(atributos ?? {})) {
    elemento.setAttribute(nome, valor);
  }
  elemento.append(...filhos);
  return elemento;
}

/** Clona o conteúdo de um <template> da página. */
export function clonarModelo(id) {
  return document.getElementById(id).content.firstElementChild.cloneNode(true);
}

// ---------- ícones ----------

const SVG = 'http://www.w3.org/2000/svg';

/** Ícones de traço (24×24), definidos como dados e criados elemento a elemento, sem interpretar HTML. */
const ICONES = {
  alerta: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'M12 7.5v5.5M12 16.5v.01' }]],
  relogio: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'M12 7v5l3.5 2' }]],
  check: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'm8 12.5 2.7 2.7L16.5 9.5' }]],
  erro: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'm9 9 6 6M15 9l-6 6' }]],
  info: [['circle', { cx: 12, cy: 12, r: 9 }], ['path', { d: 'M12 11v5.5M12 7.5v.01' }]],
  pin: [['path', { d: 'M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z' }],
    ['circle', { cx: 12, cy: 10, r: 2.4 }]],
  vazio: [['path', { d: 'M4 13.5 6.5 5h11l2.5 8.5V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-5.5Z' }],
    ['path', { d: 'M4 13.5h4.5l1.5 2.5h4l1.5-2.5H20' }]],
  busca: [['circle', { cx: 11, cy: 11, r: 6.5 }], ['path', { d: 'm20 20-4.2-4.2' }]],
  conexao: [['path', { d: 'M3 9.5a13 13 0 0 1 18 0M6.5 13a8 8 0 0 1 11 0M10 16.5a3 3 0 0 1 4 0M12 20v.01' }]],
};

export function icone(nome, classe = 'icone') {
  const svg = document.createElementNS(SVG, 'svg');
  for (const [nomeAtributo, valor] of Object.entries({
    viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2',
    'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true', focusable: 'false',
    class: classe,
  })) {
    svg.setAttribute(nomeAtributo, valor);
  }
  for (const [tag, atributos] of ICONES[nome] ?? ICONES.info) {
    const forma = document.createElementNS(SVG, tag);
    for (const [nomeAtributo, valor] of Object.entries(atributos)) forma.setAttribute(nomeAtributo, valor);
    svg.append(forma);
  }
  return svg;
}

// ---------- datas ----------

const PADRAO_DATA_HORA = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

/**
 * Formata uma data da API ("2026-11-14T08:00:00") como "14/11/2026 às 08:00".
 *
 * A API envia o horário local de São Paulo SEM fuso horário. Por isso o texto é apenas
 * reorganizado, sem passar por Date: assim o horário exibido é exatamente o registrado,
 * em qualquer fuso em que o navegador esteja.
 */
export function formatarDataHora(texto) {
  const partes = PADRAO_DATA_HORA.exec(texto ?? '');
  if (!partes) return '—';
  const [, ano, mes, dia, hora, minuto] = partes;
  return `${dia}/${mes}/${ano} às ${hora}:${minuto}`;
}

/** Elemento <time> com o valor original no atributo datetime (útil para leitores e buscadores). */
export function elementoDataHora(texto) {
  return el('time', { texto: formatarDataHora(texto), atributos: { datetime: texto ?? '' } });
}

/** "2026-11-14" → "14/11" (rótulos curtos de eixo). */
export function formatarDiaCurto(dia) {
  const [, mes, diaDoMes] = dia.split('-');
  return `${diaDoMes}/${mes}`;
}

// ---------- números ----------

const NUMERO = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

export function formatarNumero(valor) {
  return NUMERO.format(valor);
}

/** 60 → "60%", 33.333 → "33,3%"; null → "—". */
export function formatarPercentual(valor) {
  return valor === null ? '—' : `${NUMERO.format(valor)}%`;
}

/** Duração em dias de forma legível: "menos de 1 h", "5 h", "1 dia", "2,5 dias"; null → "—". */
export function formatarDuracao(dias) {
  if (dias === null) return '—';
  const horas = dias * 24;
  if (horas < 1) return 'menos de 1 h';
  if (dias < 1) return `${Math.round(horas)} h`;
  const texto = NUMERO.format(dias);
  return texto === '1' ? '1 dia' : `${texto} dias`;
}

/**
 * Tabela simples com cabeçalho; os valores são inseridos como texto.
 * @param {string} legenda texto do <caption>
 * @param {string[]} cabecalhos
 * @param {Array<Array<string|number>>} linhas
 */
export function criarTabela(legenda, cabecalhos, linhas) {
  return el('table', { classe: 'tabela' }, [
    el('caption', { classe: 'visualmente-oculto', texto: legenda }),
    el('thead', {}, [el('tr', {}, cabecalhos.map((texto) => el('th', { texto, atributos: { scope: 'col' } })))]),
    el('tbody', {}, linhas.map((linha) => el('tr', {}, linha.map((valor, indice) => (indice === 0
      ? el('th', { texto: valor, atributos: { scope: 'row' } })
      : el('td', { texto: valor })))))),
  ]);
}

// ---------- status ----------

export function seloStatus(status) {
  const info = STATUS[status] ?? { rotulo: status, modificador: 'desconhecido', icone: 'info' };
  return el('span', { classe: `selo selo--${info.modificador}` }, [icone(info.icone), el('span', { texto: info.rotulo })]);
}

// ---------- mensagens ----------

/** Título e mensagem amigáveis para qualquer erro capturado. */
export function descreverErro(erro) {
  if (erro instanceof ApiError) {
    return { titulo: erro.title || 'Erro', mensagem: erro.detail || 'Não foi possível concluir a operação.' };
  }
  console.error(erro);
  return { titulo: 'Erro inesperado', mensagem: 'Ocorreu um erro inesperado. Recarregue a página e tente novamente.' };
}

/**
 * Alerta em bloco (erro, sucesso ou info). `acao` opcional: { rotulo, aoClicar }.
 */
export function criarAlerta({ tipo = 'erro', titulo, mensagem, acao }) {
  const iconePorTipo = { erro: 'erro', sucesso: 'check', info: 'info' };
  const corpo = el('div', { classe: 'alerta__corpo' }, [
    titulo ? el('p', { classe: 'alerta__titulo', texto: titulo }) : '',
    mensagem ? el('p', { texto: mensagem }) : '',
  ]);
  if (acao) {
    const botao = el('button', { classe: 'botao botao--secundario botao--pequeno', texto: acao.rotulo, atributos: { type: 'button' } });
    botao.addEventListener('click', acao.aoClicar);
    corpo.append(botao);
  }
  return el('div', {
    classe: `alerta alerta--${tipo}`,
    atributos: { role: tipo === 'erro' ? 'alert' : 'status' },
  }, [icone(iconePorTipo[tipo] ?? 'info', 'icone alerta__icone'), corpo]);
}

/** Substitui o conteúdo de um contêiner por um alerta (ou o esvazia, se `opcoes` for null). */
export function mostrarAlerta(conteiner, opcoes) {
  conteiner.replaceChildren(...(opcoes ? [criarAlerta(opcoes)] : []));
}

/**
 * Bloco de estado (vazio, erro, não encontrado), com ícone, título, mensagem e ações.
 * Cada ação: { rotulo, href } (link) ou { rotulo, aoClicar } (botão); `secundaria` muda o estilo.
 */
export function criarEstado({ icone: nomeIcone = 'vazio', titulo, mensagem, acoes = [] }) {
  const botoes = acoes.map(({ rotulo, href, aoClicar, secundaria }) => {
    const classe = `botao ${secundaria ? 'botao--secundario' : 'botao--primario'}`;
    if (href) return el('a', { classe, texto: rotulo, atributos: { href } });
    const botao = el('button', { classe, texto: rotulo, atributos: { type: 'button' } });
    botao.addEventListener('click', aoClicar);
    return botao;
  });
  return el('div', { classe: 'estado' }, [
    el('div', { classe: 'estado__icone' }, [icone(nomeIcone)]),
    el('h2', { classe: 'estado__titulo', texto: titulo }),
    mensagem ? el('p', { classe: 'estado__mensagem', texto: mensagem }) : '',
    botoes.length ? el('div', { classe: 'estado__acoes' }, botoes) : '',
  ]);
}

/** Aviso temporário no canto da tela, anunciado por leitores de tela. */
export function avisar(mensagem, tipo = 'sucesso') {
  let regiao = document.getElementById('avisos');
  if (!regiao) {
    regiao = el('div', { classe: 'avisos', atributos: { id: 'avisos', 'aria-live': 'polite' } });
    document.body.append(regiao);
  }
  const aviso = el('div', { classe: `aviso aviso--${tipo}` }, [
    icone(tipo === 'sucesso' ? 'check' : 'info'), el('span', { texto: mensagem }),
  ]);
  regiao.append(aviso);
  setTimeout(() => aviso.classList.add('aviso--saindo'), 4000);
  setTimeout(() => aviso.remove(), 4400);
}

// ---------- botões e formulários ----------

/** Desabilita o botão e troca o texto enquanto uma operação está em andamento. */
export function definirCarregando(botao, carregando, textoCarregando = 'Carregando…') {
  if (carregando) {
    botao.dataset.textoOriginal = botao.textContent;
    botao.textContent = textoCarregando;
    botao.disabled = true;
    botao.setAttribute('aria-busy', 'true');
  } else {
    if (botao.dataset.textoOriginal) botao.textContent = botao.dataset.textoOriginal;
    botao.disabled = false;
    botao.removeAttribute('aria-busy');
  }
}

/**
 * Convenção: cada campo tem um elemento <p id="erro-{name}" class="campo__erro" hidden>.
 */
export function limparErroCampo(controle) {
  controle.removeAttribute('aria-invalid');
  const destino = document.getElementById(`erro-${controle.name}`);
  if (destino) {
    destino.textContent = '';
    destino.hidden = true;
  }
}

export function limparErrosFormulario(formulario) {
  for (const controle of formulario.elements) {
    if (controle.name) limparErroCampo(controle);
  }
}

/**
 * Mostra cada mensagem abaixo do campo de mesmo nome e foca o primeiro campo inválido.
 * @param {Object<string,string>} erros mapa campo → mensagem (mesmo formato do backend)
 * @returns {Object<string,string>} erros que não correspondem a nenhum campo do formulário
 */
export function mostrarErrosFormulario(formulario, erros) {
  const naoMapeados = {};
  for (const [campo, mensagem] of Object.entries(erros)) {
    const controle = formulario.elements.namedItem(campo);
    const destino = document.getElementById(`erro-${campo}`);
    if (controle && destino) {
      controle.setAttribute('aria-invalid', 'true');
      destino.textContent = mensagem;
      destino.hidden = false;
    } else {
      naoMapeados[campo] = mensagem;
    }
  }
  formulario.querySelector('[aria-invalid="true"]')?.focus();
  return naoMapeados;
}
