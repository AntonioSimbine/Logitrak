/* Logitrack — W9 gestão da frota */
(function () {
    'use strict';

    let veiculos = [];

    function renderizar() {
        const termo = (document.getElementById('pesquisaVeiculos').value || '')
            .trim().toLowerCase();
        const lista = veiculos.filter(function (v) {
            if (!termo) {
                return true;
            }
            return (v.matricula + ' ' + v.modelo).toLowerCase().includes(termo);
        });
        const tbody = document.getElementById('tabelaVeiculos');

        document.getElementById('kpiFrota').textContent = veiculos.length;
        document.getElementById('kpiDisponiveis').textContent = veiculos.filter(function (v) {
            return v.estado === 'DISPONIVEL';
        }).length;
        document.getElementById('kpiTransito').textContent = veiculos.filter(function (v) {
            return v.estado === 'EM_TRANSITO';
        }).length;
        document.getElementById('kpiManutencao').textContent = veiculos.filter(function (v) {
            return v.estado === 'MANUTENCAO';
        }).length;

        if (!lista.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="empty">'
                + (veiculos.length ? 'Nenhum veículo corresponde à pesquisa.'
                    : 'Ainda não existem veículos registados.')
                + '</td></tr>';
            return;
        }

        tbody.innerHTML = lista.map(function (v) {
            return '<tr>'
                + '<td><span class="badge-id mono">' + LT.escape(v.matricula) + '</span></td>'
                + '<td><strong>' + LT.escape(v.modelo) + '</strong></td>'
                + '<td>' + LT.fmt.numero(v.capacidadeCargaToneladas, 1) + ' t</td>'
                + '<td>' + LT.chip(String(v.estado).toLowerCase(), v.estadoEtiqueta) + '</td>'
                + '<td><div class="table-actions">'
                + '<button class="btn small ghost" type="button" data-acao="editar" data-id="' + v.id + '">Editar</button>'
                + '<button class="btn small ghost" type="button" data-acao="eliminar" data-id="' + v.id + '">Eliminar</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    async function carregar() {
        const botao = document.getElementById('btnAtualizar');
        botao.disabled = true;
        try {
            veiculos = await LT.api('/api/v1/veiculos');
            renderizar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    function abrirNovo() {
        document.getElementById('formVeiculo').reset();
        document.getElementById('veiculoId').value = '';
        document.getElementById('tituloModalVeiculo').textContent = 'Novo veículo';
        document.getElementById('estadoVeiculo').value = 'DISPONIVEL';
        LT.abrirModal('modalVeiculo');
    }

    function abrirEdicao(id) {
        const v = veiculos.find(function (item) { return item.id === id; });
        if (!v) {
            return;
        }
        document.getElementById('veiculoId').value = v.id;
        document.getElementById('tituloModalVeiculo').textContent =
            'Editar ' + v.matricula;
        document.getElementById('matriculaVeiculo').value = v.matricula;
        document.getElementById('modeloVeiculo').value = v.modelo;
        document.getElementById('capacidadeVeiculo').value = v.capacidadeCargaToneladas;
        document.getElementById('estadoVeiculo').value = v.estado;
        LT.abrirModal('modalVeiculo');
    }

    async function guardar(ev) {
        ev.preventDefault();
        const id = document.getElementById('veiculoId').value;
        const pedido = {
            matricula: document.getElementById('matriculaVeiculo').value.trim(),
            modelo: document.getElementById('modeloVeiculo').value.trim(),
            capacidadeCargaToneladas: Number(document.getElementById('capacidadeVeiculo').value),
            estado: document.getElementById('estadoVeiculo').value
        };
        const botao = document.getElementById('btnGuardarVeiculo');
        botao.disabled = true;
        try {
            if (id) {
                await LT.api('/api/v1/veiculos/' + id, {method: 'PUT', body: pedido});
            } else {
                await LT.api('/api/v1/veiculos', {method: 'POST', body: pedido});
            }
            LT.fecharModal('modalVeiculo');
            LT.toast('Veículo guardado',
                pedido.matricula + ' foi gravado com sucesso.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    async function eliminar(id) {
        const v = veiculos.find(function (item) { return item.id === id; });
        if (!v || !window.confirm('Eliminar o veículo ' + v.matricula + '?')) {
            return;
        }
        try {
            await LT.api('/api/v1/veiculos/' + id, {method: 'DELETE'});
            LT.toast('Veículo eliminado', v.matricula + ' foi removido da frota.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    }

    document.getElementById('btnNovoVeiculo').addEventListener('click', abrirNovo);
    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('formVeiculo').addEventListener('submit', guardar);
    document.getElementById('pesquisaVeiculos').addEventListener('input', renderizar);
    document.querySelectorAll('[data-fechar]').forEach(function (botao) {
        botao.addEventListener('click', function () {
            LT.fecharModal(botao.dataset.fechar);
        });
    });

    document.getElementById('tabelaVeiculos').addEventListener('click', function (ev) {
        const botao = ev.target.closest('button[data-acao]');
        if (!botao) {
            return;
        }
        const id = Number(botao.dataset.id);
        if (botao.dataset.acao === 'editar') {
            abrirEdicao(id);
        } else if (botao.dataset.acao === 'eliminar') {
            eliminar(id);
        }
    });

    carregar();
})();
