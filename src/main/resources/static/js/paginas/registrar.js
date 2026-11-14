/**
 * Cadastro de ocorrência: carrega as categorias, valida antes de enviar (orientação),
 * mostra os erros do servidor por campo e exibe o protocolo ao final.
 */

import { cadastrarOcorrencia, listarCategorias } from '../api.js';
import { CAMPOS } from '../dominio.js';
import {
  avisar, definirCarregando, descreverErro, el, limparErroCampo, limparErrosFormulario,
  mostrarAlerta, mostrarErrosFormulario,
} from '../ui.js';

const formulario = document.getElementById('form-ocorrencia');
const campos = document.getElementById('campos-formulario');
const seletorCategoria = formulario.elements.categoriaId;
const descricao = formulario.elements.descricao;
const contador = document.getElementById('contador-descricao');
const alertas = document.getElementById('alertas-formulario');
const botaoEnviar = document.getElementById('botao-enviar');

const areaFormulario = document.getElementById('area-formulario');
const painelSucesso = document.getElementById('painel-sucesso');
const tituloSucesso = document.getElementById('sucesso-titulo');
const numeroProtocolo = document.getElementById('sucesso-protocolo');
const linkAcompanhar = document.getElementById('sucesso-acompanhar');

let protocoloAtual = null;

// ---------- categorias ----------

async function carregarCategorias() {
  campos.disabled = true;
  botaoEnviar.disabled = true;
  seletorCategoria.replaceChildren(el('option', { texto: 'Carregando categorias…', atributos: { value: '' } }));
  mostrarAlerta(alertas, null);
  try {
    const categorias = await listarCategorias();
    seletorCategoria.replaceChildren(
      el('option', { texto: 'Selecione uma categoria', atributos: { value: '' } }),
      ...categorias.map((categoria) => el('option', { texto: categoria.nome, atributos: { value: String(categoria.id) } })),
    );
    campos.disabled = false;
    botaoEnviar.disabled = false;
  } catch (erro) {
    seletorCategoria.replaceChildren(el('option', { texto: 'Categorias indisponíveis', atributos: { value: '' } }));
    mostrarAlerta(alertas, {
      tipo: 'erro',
      titulo: 'Não foi possível carregar as categorias',
      mensagem: descreverErro(erro).mensagem,
      acao: { rotulo: 'Tentar novamente', aoClicar: carregarCategorias },
    });
  }
}

// ---------- validação prévia (orientação; o servidor valida de novo) ----------

function aplicarLimites() {
  for (const [nome, regra] of Object.entries(CAMPOS)) {
    if (regra.max) formulario.elements[nome].maxLength = regra.max;
  }
}

/** Mesmas regras do backend, aplicadas ao texto sem espaços nas pontas. */
function validar() {
  const erros = {};
  if (!seletorCategoria.value) erros.categoriaId = CAMPOS.categoriaId.obrigatorio;
  for (const nome of ['descricao', 'bairro', 'enderecoReferencia']) {
    const regra = CAMPOS[nome];
    const texto = formulario.elements[nome].value.trim();
    if (!texto) erros[nome] = regra.obrigatorio;
    else if (texto.length < regra.min || texto.length > regra.max) erros[nome] = regra.tamanho;
  }
  return erros;
}

/** Resumo no topo + mensagem abaixo de cada campo. Erros sem campo correspondente vão no resumo. */
function mostrarErros(erros) {
  const naoMapeados = Object.values(mostrarErrosFormulario(formulario, erros));
  const quantidade = Object.keys(erros).length - naoMapeados.length;
  const titulo = quantidade === 0 ? 'Dados inválidos'
    : quantidade === 1 ? 'Corrija o campo destacado' : `Corrija os ${quantidade} campos destacados`;
  mostrarAlerta(alertas, {
    tipo: 'erro',
    titulo,
    mensagem: [...naoMapeados, quantidade > 0 ? 'Revise as mensagens abaixo de cada campo e envie novamente.' : '']
      .filter(Boolean).join(' '),
  });
  if (quantidade === 0) alertas.focus();
}

function atualizarContador() {
  const tamanho = descricao.value.trim().length;
  contador.textContent = `${descricao.value.length}/${CAMPOS.descricao.max}`;
  contador.classList.toggle('campo__contador--insuficiente', tamanho > 0 && tamanho < CAMPOS.descricao.min);
}

// ---------- envio ----------

async function enviar(evento) {
  evento.preventDefault();
  if (botaoEnviar.disabled) return;

  limparErrosFormulario(formulario);
  mostrarAlerta(alertas, null);

  const erros = validar();
  if (Object.keys(erros).length > 0) {
    mostrarErros(erros);
    return;
  }

  definirCarregando(botaoEnviar, true, 'Enviando…');
  try {
    const ocorrencia = await cadastrarOcorrencia({
      categoriaId: Number(seletorCategoria.value),
      descricao: descricao.value,
      bairro: formulario.elements.bairro.value,
      enderecoReferencia: formulario.elements.enderecoReferencia.value,
    });
    mostrarSucesso(ocorrencia);
  } catch (erro) {
    if (erro.status === 400 && erro.temErrosDeCampo) {
      mostrarErros(erro.erros);
    } else {
      const { titulo, mensagem } = descreverErro(erro);
      mostrarAlerta(alertas, { tipo: 'erro', titulo, mensagem });
      alertas.focus();
    }
  } finally {
    definirCarregando(botaoEnviar, false);
  }
}

// ---------- sucesso ----------

function mostrarSucesso(ocorrencia) {
  protocoloAtual = ocorrencia.id;
  numeroProtocolo.textContent = `Nº ${ocorrencia.id}`;
  linkAcompanhar.href = `ocorrencia.html?id=${ocorrencia.id}`;
  areaFormulario.hidden = true;
  painelSucesso.hidden = false;
  window.scrollTo({ top: 0 });
  tituloSucesso.focus();
}

function registrarOutra() {
  formulario.reset();
  limparErrosFormulario(formulario);
  mostrarAlerta(alertas, null);
  atualizarContador();
  painelSucesso.hidden = true;
  areaFormulario.hidden = false;
  seletorCategoria.focus();
}

async function copiarProtocolo() {
  try {
    await navigator.clipboard.writeText(String(protocoloAtual));
    avisar(`Protocolo ${protocoloAtual} copiado.`);
  } catch {
    avisar(`Não foi possível copiar. Anote o número ${protocoloAtual}.`, 'info');
  }
}

// ---------- eventos ----------

formulario.addEventListener('submit', enviar);
formulario.addEventListener('input', (evento) => {
  if (evento.target.name) limparErroCampo(evento.target);
});
formulario.addEventListener('change', (evento) => {
  if (evento.target.name) limparErroCampo(evento.target);
});
descricao.addEventListener('input', atualizarContador);
document.getElementById('sucesso-nova').addEventListener('click', registrarOutra);
document.getElementById('sucesso-copiar').addEventListener('click', copiarProtocolo);

aplicarLimites();
atualizarContador();
carregarCategorias();
