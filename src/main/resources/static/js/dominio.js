/**
 * Conhecimento do domínio usado APENAS para orientar a interface: rótulos, quais botões
 * mostrar e validação prévia dos campos. As regras valem no backend (OcorrenciaService),
 * que é sempre quem decide; se o servidor recusar algo, a interface mostra o erro dele.
 * Se as regras do backend mudarem, estes valores devem acompanhar.
 */

/** Como cada status é exibido. A cor nunca aparece sozinha: há sempre rótulo e ícone. */
export const STATUS = Object.freeze({
  ABERTO: { rotulo: 'Aberta', modificador: 'aberto', icone: 'alerta' },
  EM_ANDAMENTO: { rotulo: 'Em andamento', modificador: 'andamento', icone: 'relogio' },
  RESOLVIDO: { rotulo: 'Resolvida', modificador: 'resolvido', icone: 'check' },
});

/**
 * Ações oferecidas a partir de cada status (espelho das transições do backend).
 * `confirmar`: a ação é definitiva e pede confirmação antes de ser enviada.
 */
export const ACOES_POR_STATUS = Object.freeze({
  ABERTO: [
    { status: 'EM_ANDAMENTO', rotulo: 'Iniciar atendimento' },
    { status: 'RESOLVIDO', rotulo: 'Marcar como resolvida', confirmar: true },
  ],
  EM_ANDAMENTO: [
    { status: 'RESOLVIDO', rotulo: 'Marcar como resolvida', confirmar: true },
  ],
  RESOLVIDO: [],
});

/**
 * Limites e mensagens dos campos do cadastro, iguais aos do backend.
 * Os tamanhos valem para o texto sem espaços nas pontas, como no servidor.
 */
export const CAMPOS = Object.freeze({
  categoriaId: {
    obrigatorio: 'A categoria é obrigatória',
  },
  descricao: {
    min: 10, max: 1000,
    obrigatorio: 'A descrição é obrigatória',
    tamanho: 'A descrição deve ter entre 10 e 1000 caracteres',
  },
  bairro: {
    min: 2, max: 80,
    obrigatorio: 'O bairro é obrigatório',
    tamanho: 'O bairro deve ter entre 2 e 80 caracteres',
  },
  enderecoReferencia: {
    min: 5, max: 200,
    obrigatorio: 'O endereço ou ponto de referência é obrigatório',
    tamanho: 'O endereço ou ponto de referência deve ter entre 5 e 200 caracteres',
  },
});

export function rotuloStatus(status) {
  return STATUS[status]?.rotulo ?? status;
}
