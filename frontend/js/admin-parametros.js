/* Logitrack — W10 parâmetros globais */
(function () {
    'use strict';

    const META = {
        TOLERANCIA_ATRASO_MINUTOS: {
            titulo: 'Tolerância de atraso',
            unidade: 'minutos',
            tipo: 'number',
            min: 0,
            max: 10080,
            step: 1
        },
        FREQUENCIA_RECOLHA_GPS_SEGUNDOS: {
            titulo: 'Frequência de recolha GPS',
            unidade: 'segundos',
            tipo: 'number',
            min: 10,
            max: 3600,
            step: 1
        },
        INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS: {
            titulo: 'Sincronização automática',
            unidade: 'minutos',
            tipo: 'number',
            min: 1,
            max: 1440,
            step: 1
        },
        PRECISAO_MAXIMA_GPS_METROS: {
            titulo: 'Precisão máxima do GPS (teto fixo de 15 m)',
            unidade: 'metros',
            tipo: 'number',
            min: 0.1,
            max: 15,
            step: 0.1
        },
        INTERVALO_VERIFICACAO_SLA_MINUTOS: {
            titulo: 'Verificação do motor SLA',
            unidade: 'minutos',
            tipo: 'number',
            min: 1,
            max: 1440,
            step: 1
        }
    };

    let parametros = [];

    function meta(chave) {
        return META[chave] || {
            titulo: chave.replace(/_/g, ' ').toLowerCase(),
            unidade: '',
            tipo: 'text',
            min: null,
            max: null,
            step: null
        };
    }

    function renderizar() {
        const contentor = document.getElementById('listaParametros');
        if (!parametros.length) {
            contentor.innerHTML = '<div class="param-card"><div class="empty">'
                + 'Nenhum parâmetro configurado.</div></div>';
            return;
        }
        contentor.innerHTML = parametros.map(function (p) {
            const m = meta(p.chave);
            const atributos = ['min', 'max', 'step'].map(function (nome) {
                return m[nome] !== null && m[nome] !== undefined
                    ? ' ' + nome + '="' + m[nome] + '"' : '';
            }).join('');
            const titulo = m.titulo.charAt(0).toUpperCase() + m.titulo.slice(1);
            return '<article class="param-card">'
                + '<span class="key">' + LT.escape(p.chave) + '</span>'
                + '<h4>' + LT.escape(titulo) + (m.unidade ? ' (' + LT.escape(m.unidade) + ')' : '') + '</h4>'
                + '<p>' + LT.escape(p.descricao || 'Parâmetro operacional do sistema.') + '</p>'
                + '<div class="row">'
                + '<input class="input" type="' + m.tipo + '" value="' + LT.escape(p.valor) + '"'
                + atributos + ' aria-label="' + LT.escape(p.chave) + '">'
                + '<button class="btn primary" type="button" data-chave="' + LT.escape(p.chave) + '">'
                + '<svg><use href="#i-save"/></svg> Guardar</button>'
                + '</div>'
                + '</article>';
        }).join('');
    }

    async function carregar() {
        const botao = document.getElementById('btnAtualizar');
        botao.disabled = true;
        try {
            parametros = await LT.api('/api/v1/parametros');
            renderizar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    async function guardar(card, chave) {
        const input = card.querySelector('input');
        const botao = card.querySelector('button');
        const valor = input.value.trim();
        if (!valor) {
            LT.toast('Valor obrigatório', 'Introduza um valor para ' + chave + '.', 'error');
            return;
        }
        botao.disabled = true;
        try {
            const atualizado = await LT.api('/api/v1/parametros/' + encodeURIComponent(chave), {
                method: 'PUT',
                body: {valor: valor}
            });
            const indice = parametros.findIndex(function (p) { return p.chave === chave; });
            if (indice >= 0) {
                parametros[indice] = atualizado;
            }
            LT.toast('Parâmetro atualizado',
                chave + ' = ' + atualizado.valor, 'success');
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('listaParametros').addEventListener('click', function (ev) {
        const botao = ev.target.closest('button[data-chave]');
        if (!botao) {
            return;
        }
        const card = botao.closest('.param-card');
        guardar(card, botao.dataset.chave);
    });

    carregar();
})();
