/* Logitrack — W6 indicadores de desempenho */
(function () {
    'use strict';

    const CORES = {
        navy: '#0b1f3a',
        orange: '#f26b2e',
        green: '#1c8058',
        red: '#c0392b',
        blue: '#2563eb',
        line: '#e4e9f0',
        muted: '#6b7a90'
    };

    const graficos = {volume: null, tempo: null, prazos: null};

    function configurarChartJs() {
        if (!window.Chart) {
            return false;
        }
        window.Chart.defaults.font.family = '"Segoe UI", "Inter", Arial, sans-serif';
        window.Chart.defaults.color = CORES.muted;
        window.Chart.defaults.plugins.legend.labels.usePointStyle = true;
        window.Chart.defaults.plugins.legend.labels.boxWidth = 8;
        window.Chart.defaults.plugins.legend.labels.padding = 16;
        return true;
    }

    function semDados(canvas, mensagem) {
        if (!canvas) {
            return;
        }
        const pai = canvas.parentElement;
        if (pai) {
            pai.innerHTML = '<div class="empty" style="height:100%;display:grid;place-items:center">'
                + LT.escape(mensagem) + '</div>';
        }
    }

    function criarVolume(dados) {
        const canvas = document.getElementById('graficoVolume');
        if (!dados.length) {
            semDados(canvas, 'Ainda não existem carregamentos concluídos.');
            return;
        }
        if (graficos.volume) {
            graficos.volume.destroy();
        }
        graficos.volume = new window.Chart(canvas, {
            type: 'bar',
            data: {
                labels: dados.map(function (d) { return d.fabrica; }),
                datasets: [{
                    label: 'Toneladas',
                    data: dados.map(function (d) { return d.toneladas; }),
                    backgroundColor: 'rgba(242, 107, 46, .82)',
                    hoverBackgroundColor: CORES.orange,
                    borderRadius: 7,
                    maxBarThickness: 54
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {display: false},
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                return ' ' + LT.fmt.numero(ctx.parsed.y, 1) + ' t';
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: {color: '#eef2f7'},
                        ticks: {
                            callback: function (v) { return LT.fmt.numero(v, 0) + ' t'; }
                        }
                    },
                    x: {grid: {display: false}}
                }
            }
        });
    }

    function criarTempo(dados) {
        const canvas = document.getElementById('graficoTempo');
        if (!dados.length || !dados.some(function (d) {
            return d.horas !== null && d.horas !== undefined;
        })) {
            semDados(canvas, 'Sem dados de tempo de ciclo para apresentar.');
            return;
        }
        if (graficos.tempo) {
            graficos.tempo.destroy();
        }
        const contexto = canvas.getContext('2d');
        const gradiente = contexto.createLinearGradient(0, 0, 0, 280);
        gradiente.addColorStop(0, 'rgba(37, 99, 235, .26)');
        gradiente.addColorStop(1, 'rgba(37, 99, 235, 0)');

        graficos.tempo = new window.Chart(canvas, {
            type: 'line',
            data: {
                labels: dados.map(function (d) {
                    return d.dia ? d.dia.charAt(0).toUpperCase() + d.dia.slice(1) : '—';
                }),
                datasets: [{
                    label: 'Tempo médio (horas)',
                    data: dados.map(function (d) { return d.horas; }),
                    borderColor: CORES.blue,
                    backgroundColor: gradiente,
                    borderWidth: 2.5,
                    fill: true,
                    tension: .35,
                    pointRadius: 4,
                    pointBackgroundColor: '#fff',
                    pointBorderColor: CORES.blue,
                    pointBorderWidth: 2,
                    pointHoverRadius: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {display: false},
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                const valor = ctx.parsed.y;
                                return valor === null || valor === undefined
                                    ? ' Sem dados' : ' ' + LT.fmt.numero(valor, 1) + ' horas';
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: {color: '#eef2f7'},
                        ticks: {
                            callback: function (v) { return LT.fmt.numero(v, 0) + ' h'; }
                        }
                    },
                    x: {grid: {display: false}}
                }
            }
        });
    }

    function criarPrazos(prazos) {
        const canvas = document.getElementById('graficoPrazos');
        const total = (prazos.noPrazo || 0) + (prazos.atrasados || 0);
        if (!total) {
            semDados(canvas, 'Sem carregamentos concluídos para calcular prazos.');
            return;
        }
        if (graficos.prazos) {
            graficos.prazos.destroy();
        }
        graficos.prazos = new window.Chart(canvas, {
            type: 'doughnut',
            data: {
                labels: ['Dentro do prazo', 'Fora do prazo'],
                datasets: [{
                    data: [prazos.noPrazo, prazos.atrasados],
                    backgroundColor: [CORES.green, CORES.red],
                    hoverBackgroundColor: ['#166a48', '#a93226'],
                    borderWidth: 4,
                    borderColor: '#fff',
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '68%',
                plugins: {
                    legend: {position: 'bottom'},
                    tooltip: {
                        callbacks: {
                            label: function (ctx) {
                                const valor = ctx.parsed;
                                const percentagem = total ? (valor / total) * 100 : 0;
                                return ' ' + ctx.label + ': ' + valor
                                    + ' (' + LT.fmt.numero(percentagem, 1) + '%)';
                            }
                        }
                    }
                }
            }
        });
    }

    function renderizarTabela(dados) {
        const tbody = document.getElementById('tabelaFabricas');
        if (!dados.length) {
            tbody.innerHTML = '<tr><td colspan="3" class="empty">Sem volume registado.</td></tr>';
            return;
        }
        const total = dados.reduce(function (soma, d) { return soma + d.toneladas; }, 0);
        const ordenado = dados.slice().sort(function (a, b) {
            return b.toneladas - a.toneladas;
        });
        tbody.innerHTML = ordenado.map(function (d) {
            const percentagem = total > 0 ? (d.toneladas / total) * 100 : 0;
            return '<tr>'
                + '<td><strong>' + LT.escape(d.fabrica) + '</strong></td>'
                + '<td>' + LT.fmt.numero(d.toneladas, 1) + ' t</td>'
                + '<td style="min-width:180px">'
                + '<div style="display:flex;align-items:center;gap:10px">'
                + '<div style="flex:1;height:7px;background:#eef2f7;border-radius:99px;overflow:hidden">'
                + '<div style="height:100%;width:' + percentagem.toFixed(1)
                + '%;background:linear-gradient(90deg,#f26b2e,#ff9a66);border-radius:99px"></div>'
                + '</div>'
                + '<span class="small muted">' + LT.fmt.numero(percentagem, 1) + '%</span>'
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    function renderizar(dados) {
        document.getElementById('kpiTotal').textContent = LT.fmt.numero(dados.totalCarregamentos, 0);
        document.getElementById('kpiToneladas').innerHTML = LT.fmt.numero(dados.totalToneladas, 1)
            + ' <small>t</small>';
        const tempoMedio = dados.tempoMedioHoras;
        document.getElementById('kpiTempo').innerHTML =
            tempoMedio === null || tempoMedio === undefined
                ? 'sem dados'
                : LT.fmt.numero(tempoMedio, 1) + ' <small>h</small>';
        const percentagem = dados.cumprimentoPrazos.percentagem;
        document.getElementById('kpiCumprimento').innerHTML =
            percentagem === null || percentagem === undefined
                ? 'sem dados'
                : LT.fmt.numero(percentagem, 1) + ' <small>%</small>';
        document.getElementById('kpiCumprimentoDetalhe').textContent =
            dados.cumprimentoPrazos.noPrazo + ' dentro do prazo · '
            + dados.cumprimentoPrazos.atrasados + ' fora do prazo';

        const fases = dados.resumo || {};
        const comFases = Number(fases.registosComFases || 0);
        const abrangencia = document.getElementById('fasesAbrangenciaKpi');
        if (abrangencia) {
            abrangencia.textContent = comFases
                ? comFases + ' registos com horários' : 'Sem dados';
        }
        [['kpiFaseEspera', fases.tempoMedioEsperaHoras],
         ['kpiFaseCarregamento', fases.tempoMedioCarregamentoHoras],
         ['kpiFaseTransporte', fases.tempoMedioTransporteHoras]].forEach(function (par) {
            const el = document.getElementById(par[0]);
            if (el) {
                el.textContent = par[1] === null || par[1] === undefined
                    ? 'sem horário registado' : LT.fmt.numero(par[1], 1) + ' h';
            }
        });

        document.getElementById('atualizadoEm').textContent =
            'Atualizado ' + LT.fmt.relativo(new Date());

        if (!configurarChartJs()) {
            semDados(document.getElementById('graficoVolume'), 'Os gráficos precisam de ligação à Internet. Verifique a ligação e atualize a página.');
            semDados(document.getElementById('graficoTempo'), 'Os gráficos precisam de ligação à Internet. Verifique a ligação e atualize a página.');
            semDados(document.getElementById('graficoPrazos'), 'Os gráficos precisam de ligação à Internet. Verifique a ligação e atualize a página.');
        } else {
            criarVolume(dados.volumePorFabrica || []);
            criarTempo(dados.tempoMedioPorDia || []);
            criarPrazos(dados.cumprimentoPrazos || {noPrazo: 0, atrasados: 0});
        }
        renderizarTabela(dados.volumePorFabrica || []);
    }

    async function carregar() {
        const botao = document.getElementById('btnAtualizar');
        botao.disabled = true;
        try {
            const dados = await LT.api('/api/v1/kpis');
            renderizar(dados);
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    carregar();
})();
