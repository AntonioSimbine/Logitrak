/* Logitrack — W4 alertas de SLA */
(function () {
    'use strict';

    let alertas = [];
    let filtroLido = 'todos';

    function aplicarFiltros() {
        const termo = (document.getElementById('pesquisaAlertas').value || '').toLowerCase().trim();
        return alertas.filter(function (a) {
            if (filtroLido === 'nao-lidos' && a.lido) {
                return false;
            }
            if (filtroLido === 'lidos' && !a.lido) {
                return false;
            }
            if (!termo) {
                return true;
            }
            return [a.guiaNumero, a.fabrica, a.motorista, a.veiculo, a.mensagem]
                .join(' ').toLowerCase().indexOf(termo) >= 0;
        });
    }

    function renderizarKpis() {
        const naoLidos = alertas.filter(function (a) { return !a.lido; }).length;
        const maior = alertas.reduce(function (max, a) {
            return Math.max(max, a.minutosExcedidos || 0);
        }, 0);
        const fabricas = new Set(alertas.map(function (a) { return a.fabrica; }).filter(Boolean));
        document.getElementById('kpiNaoLidos').textContent = naoLidos;
        document.getElementById('kpiTotal').textContent = alertas.length;
        document.getElementById('kpiMaior').innerHTML = maior + '<small>min</small>';
        document.getElementById('kpiFabricas').textContent = fabricas.size;
        LT.atualizarBadge(naoLidos);
    }

    function renderizarTabela() {
        const lista = aplicarFiltros();
        const tbody = document.getElementById('tabelaAlertas');
        if (!lista.length) {
            tbody.innerHTML = '<tr><td colspan="8" class="empty">Sem alertas para os filtros selecionados.</td></tr>';
        } else {
            tbody.innerHTML = lista.map(function (a) {
                return '<tr>'
                    + '<td><a class="guia" href="' + LT.urlCarregamento(a.carregamentoId) + '">'
                    + LT.escape(a.guiaNumero || '—') + '</a>'
                    + '<div class="sub">' + LT.escape(a.mensagem || '') + '</div></td>'
                    + '<td>' + LT.escape(a.fabrica || '—') + '</td>'
                    + '<td>' + LT.escape(a.motorista || '—')
                    + '<div class="sub">' + LT.escape(a.veiculo || '') + '</div></td>'
                    + '<td>' + LT.fmt.dataHora(a.horaLimite) + '</td>'
                    + '<td><span style="color:#c0392b;font-weight:750">+'
                    + (a.minutosExcedidos || 0) + ' min</span></td>'
                    + '<td>' + LT.fmt.dataHora(a.dataHoraEmissao) + '</td>'
                    + '<td>' + (a.lido
                        ? '<span class="chip no">LIDO</span>'
                        : '<span class="chip sim">NÃO LIDO</span>') + '</td>'
                    + '<td><div class="table-actions">'
                    + '<a class="btn small" data-abrir-alerta="' + a.id + '" href="' + LT.urlCarregamento(a.carregamentoId) + '">'
                    + 'Abrir</a>'
                    + (a.lido ? '' : '<button class="btn small ghost" data-lido="' + a.id
                        + '">Marcar lido</button>')
                    + '</div></td></tr>';
            }).join('');
        }

        tbody.querySelectorAll('[data-abrir-alerta]').forEach(function (link) {
            link.addEventListener('click', async function (ev) {
                const alertaId = link.dataset.abrirAlerta;
                const alerta = alertas.find(function (a) { return String(a.id) === String(alertaId); });
                if (!alerta || alerta.lido) return;
                ev.preventDefault();
                try {
                    const atualizado = await LT.api('/api/v1/alertas/' + alertaId, {
                        method: 'PATCH', body: {lido: true}
                    });
                    const indice = alertas.findIndex(function (a) { return String(a.id) === String(alertaId); });
                    if (indice >= 0) alertas[indice] = atualizado;
                    renderizarKpis();
                    window.location.href = link.href;
                } catch (e) {
                    LT.erro(e);
                    // Se não foi possível gravar o estado de leitura, não ocultar o erro.
                }
            });
        });

        tbody.querySelectorAll('[data-lido]').forEach(function (botao) {
            botao.addEventListener('click', async function () {
                try {
                    const atualizado = await LT.api('/api/v1/alertas/' + botao.dataset.lido, {
                        method: 'PATCH',
                        body: {lido: true}
                    });
                    const indice = alertas.findIndex(function (a) { return a.id === atualizado.id; });
                    if (indice >= 0) {
                        alertas[indice] = atualizado;
                    }
                    renderizarKpis();
                    renderizarTabela();
                    LT.toast('Alerta marcado como lido', 'O incidente foi reconhecido.', 'success');
                } catch (e) {
                    LT.erro(e);
                }
            });
        });
        document.getElementById('infoAlertas').textContent = lista.length
            + ' alerta(s) apresentados de ' + alertas.length;
    }

    async function carregar() {
        try {
            const resposta = await LT.api('/api/v1/alertas?page=0&size=200');
            alertas = resposta.content || [];
            renderizarKpis();
            renderizarTabela();
        } catch (e) {
            LT.erro(e);
        }
    }

    document.getElementById('filtroLido').addEventListener('click', function (ev) {
        const botao = ev.target.closest('button[data-filtro]');
        if (!botao) {
            return;
        }
        document.querySelectorAll('#filtroLido button').forEach(function (b) {
            b.classList.remove('active');
        });
        botao.classList.add('active');
        filtroLido = botao.dataset.filtro;
        renderizarTabela();
    });
    document.getElementById('pesquisaAlertas').addEventListener('input',
        LT.debounce(renderizarTabela, 180));
    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('btnMarcarTodos').addEventListener('click', async function () {
        const naoLidos = alertas.filter(function (a) { return !a.lido; });
        if (!naoLidos.length) {
            LT.toast('Nada a fazer', 'Todos os alertas já estão lidos.', 'success');
            return;
        }
        for (const alerta of naoLidos) {
            try {
                await LT.api('/api/v1/alertas/' + alerta.id, {
                    method: 'PATCH', body: {lido: true}
                });
                alerta.lido = true;
            } catch (e) {
                LT.erro(e);
            }
        }
        renderizarKpis();
        renderizarTabela();
        LT.toast('Alertas atualizados', naoLidos.length + ' alerta(s) marcados como lidos.', 'success');
    });

    LT.on('alerta', function (alerta) {
        alertas.unshift(alerta);
        renderizarKpis();
        renderizarTabela();
    });

    carregar();
})();
