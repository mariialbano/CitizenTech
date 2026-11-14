/**
 * Integração com o Chart.js (vendor/chartjs/4.6.1/chart.umd.min.js, carregado pela página como
 * script clássico, que expõe o global `Chart`). Aplica a identidade visual a partir das mesmas
 * variáveis CSS usadas pela interface, para que cores e fonte tenham uma única fonte de verdade.
 */

/** O Chart.js pode não estar disponível (arquivo ausente ou bloqueado): a página mostra só as tabelas. */
export function graficosDisponiveis() {
  return typeof window.Chart === 'function';
}

export function corDoTema(variavel) {
  return getComputedStyle(document.documentElement).getPropertyValue(variavel).trim();
}

export function coresDeStatus() {
  return {
    ABERTO: corDoTema('--status-aberto-traco'),
    EM_ANDAMENTO: corDoTema('--status-andamento-traco'),
    RESOLVIDO: corDoTema('--status-resolvido-traco'),
  };
}

/** Configuração global dos gráficos: fonte, cores, idioma e movimento reduzido. */
export function aplicarTema() {
  const { defaults } = window.Chart;
  defaults.font.family = corDoTema('--fonte');
  defaults.font.size = 13;
  defaults.color = corDoTema('--cor-texto-secundario');
  defaults.borderColor = corDoTema('--cor-borda');
  defaults.locale = 'pt-BR';
  defaults.maintainAspectRatio = false;
  defaults.plugins.legend.labels.usePointStyle = true;
  defaults.plugins.legend.labels.padding = 16;
  defaults.plugins.tooltip.backgroundColor = corDoTema('--cor-texto');
  defaults.plugins.tooltip.padding = 10;
  defaults.plugins.tooltip.cornerRadius = 8;
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    defaults.animation = false;
  }
}

/** Cria o gráfico no canvas, substituindo o anterior (necessário ao atualizar o painel). */
export function desenhar(canvas, configuracao) {
  window.Chart.getChart(canvas)?.destroy();
  return new window.Chart(canvas, configuracao);
}
