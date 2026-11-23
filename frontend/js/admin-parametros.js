/* Logitrack — W10 parâmetros globais */
(function () {
    'use strict';

    const META = {
        TOLERANCIA_ATRASO_MINUTOS: {
            titulo: 'Tolerância antes do aviso',
            unidade: 'minutos',
            tipo: 'number',
            min: 0,
            max: 10080,
            step: 1
        },
        FREQUENCIA_RECOLHA_GPS_SEGUNDOS: {
            titulo: 'Intervalo de localização',
            unidade: 'segundos',
            tipo: 'number',
            min: 10,
            max: 3600,
            step: 1
        },
        INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS: {
            titulo: 'Envio automático',
            unidade: 'minutos',
            tipo: 'number',
            min: 1,
            max: 1440,
            step: 1
        },
        PRECISAO_MAXIMA_GPS_METROS: {
            titulo: 'Qualidade mínima da localização',
            unidade: 'metros',
            tipo: 'number',
            min: 0.1,
            max: 15,
            step: 0.1
        },
        INTERVALO_VERIFICACAO_SLA_MINUTOS: {
            titulo: 'Verificação do prazo',
            unidade: 'minutos',
            tipo: 'number',
            min: 1,
            max: 1440,
            step: 1
        }
    };

    const DESCRICOES_SIMPLES = {
        TOLERANCIA_ATRASO_MINUTOS:
            'Margem de espera antes de avisar que a viagem está atrasada.',
        FREQUENCIA_RECOLHA_GPS_SEGUNDOS:
            'De quantos em quantos segundos o telemóvel envia a localização.',
        INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS:
            'De quantos em quantos minutos os registos guardados no telemóvel são enviados à central.',
        PRECISAO_MAXIMA_GPS_METROS:
            'A localização só é aceite quando for precisa até este valor. O limite é 15 m.',
        INTERVALO_VERIFICACAO_SLA_MINUTOS:
            'De quantos em quantos minutos o sistema verifica se alguma viagem ultrapassou o prazo.'
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
                + 'Ainda não existem regras configuradas.</div></div>';
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
                + '<h4>' + LT.escape(titulo) + (m.unidade ? ' (' + LT.escape(m.unidade) + ')' : '') + '</h4>'
                + '<p>' + LT.escape(DESCRICOES_SIMPLES[p.chave] || p.descricao || 'Regra usada na opera??o.') + '</p>'
                + '<div class="row">'
                + '<input class="input" type="' + m.tipo + '" value="' + LT.escape(p.valor) + '"'
                + atributos + ' aria-label="' + LT.escape(titulo) + '">'
                + '<button class="btn primary" type="button" data-chave="' + LT.escape(p.chave) + '">'
                + '<svg><use href="#i-save"/></svg> Guardar</button>'
                + '</div>'
                + '<details class="detalhes-tecnicos"><summary>Nome t?cnico</summary><span class="key">' + LT.escape(p.chave) + '</span></details>'
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
            LT.toast('Falta o valor', 'Escreva um valor para ' + meta(chave).titulo + '.', 'error');
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
            LT.toast('Regra atualizada',
                'A regra ' + meta(chave).titulo + ' passou a ter o valor '
                + atualizado.valor + '.', 'success');
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
