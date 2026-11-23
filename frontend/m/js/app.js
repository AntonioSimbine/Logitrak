/* ============================================================
   Logitrack — painel do motorista (M1, M2, M5 e M6)
   ============================================================ */
(function () {
    'use strict';

    const LTM = window.LTM;
    const state = LTM.state;
    const $ = LTM.$;
    const $$ = LTM.$$;
    const esc = LTM.esc;
    const fmt = LTM.fmt;
    const LOJA_FILA = LTM.constantes.LOJA_FILA;
    const API = ((window.LOGITRACK && window.LOGITRACK.apiBase) || '') + '/api/v1';

    const LTApp = window.LTApp = window.LTApp || {};

    /* ---------- Navegação entre ecrãs ---------- */
    LTApp.mostrarEcra = function (nome) {
        if (state.ecra === 'camera' && nome !== 'camera'
            && typeof LTApp.pararCamera === 'function') {
            LTApp.pararCamera();
        }
        $$('.screen').forEach(function (ecra) {
            ecra.classList.toggle('active', ecra.id === 'screen-' + nome);
        });
        state.ecra = nome;
        const comNavegacao = nome === 'dashboard' || nome === 'pendentes';
        const nav = $('#bottomNav');
        if (nav) {
            nav.classList.toggle('hidden', !comNavegacao);
        }
        $$('.nav-btn').forEach(function (botao) {
            botao.classList.toggle('active', botao.dataset.nav === nome);
        });
        window.scrollTo(0, 0);
    };

    function voltar() {
        if (state.ecra === 'camera') {
            if (typeof LTApp.fecharCamera === 'function') {
                LTApp.fecharCamera();
            }
            return;
        }
        if (state.ecra === 'detalhe') {
            limparObjectUrlFoto();
            LTApp.mostrarEcra(state.ecraAnterior === 'pendentes' ? 'pendentes' : 'dashboard');
            return;
        }
        if (state.ecra === 'novo' || state.ecra === 'pendentes') {
            LTApp.mostrarEcra('dashboard');
            return;
        }
        LTApp.mostrarEcra('dashboard');
    }

    function mostrarLogin(mensagem) {
        LTApp.mostrarEcra('login');
        const email = $('#loginEmail');
        if (email) {
            email.value = LTM.emailRecordado();
        }
        const senha = $('#loginSenha');
        if (senha) {
            senha.value = '';
        }
        if (mensagem) {
            mostrarErroLogin(mensagem);
        } else {
            esconderErroLogin();
        }
        const nav = $('#bottomNav');
        if (nav) {
            nav.classList.add('hidden');
        }
        LTM.atualizarBarra();
    }

    function mostrarErroLogin(mensagem) {
        const caixa = $('#loginErro');
        if (!caixa) {
            return;
        }
        caixa.textContent = mensagem;
        caixa.classList.remove('hidden');
    }

    function esconderErroLogin() {
        const caixa = $('#loginErro');
        if (caixa) {
            caixa.textContent = '';
            caixa.classList.add('hidden');
        }
    }

    /* ---------- Cabeçalho e indicadores ---------- */
    function atualizarCabecalho() {
        const nome = state.utilizador && state.utilizador.nome
            ? state.utilizador.nome : 'Motorista';
        const nomeEl = $('#dashboardNome');
        if (nomeEl) {
            nomeEl.textContent = nome;
        }
        const avatar = $('#avatarMotorista');
        if (avatar) {
            avatar.textContent = LTM.iniciais(nome);
        }
    }

    function atualizarLinhaSync(pendentes, rejeitados) {
        const linha = $('#dashboardSync');
        if (!linha) {
            return;
        }
        const ligado = LTM.ligado();
        linha.classList.toggle('offline', !ligado);
        let texto;
        if (!ligado) {
            texto = 'Sem ligação'
                + (pendentes + rejeitados
                    ? ' · ' + (pendentes + rejeitados) + ' aguardam envio' : '');
        } else if (pendentes > 0) {
            texto = 'Com ligação — ' + pendentes
                + (pendentes === 1 ? ' registo aguarda envio' : ' registos aguardam envio');
        } else if (rejeitados > 0) {
            texto = 'Com ligação — ' + rejeitados
                + (rejeitados === 1 ? ' registo não aceite' : ' registos não aceites');
        } else if (state.ultimaSync) {
            texto = 'Com ligação — último envio à central ' + tempoRelativo(state.ultimaSync);
        } else {
            texto = 'Com ligação — todos os registos enviados à central';
        }
        linha.innerHTML = '<span class="dot"></span> ' + esc(texto);
    }

    function tempoRelativo(timestamp) {
        const minutos = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
        if (minutos < 1) {
            return 'agora';
        }
        if (minutos === 1) {
            return 'há 1 min';
        }
        if (minutos < 60) {
            return 'há ' + minutos + ' min';
        }
        const horas = Math.floor(minutos / 60);
        return horas === 1 ? 'há 1 h' : 'há ' + horas + ' h';
    }

    function chaveUltimaSync() {
        const id = state.utilizador ? state.utilizador.id : 'anon';
        return 'logitrack.mobile.ultimaSync.' + id;
    }

    function lerUltimaSync() {
        const valor = Number(localStorage.getItem(chaveUltimaSync()));
        return isNaN(valor) ? 0 : valor;
    }

    function gravarUltimaSync(momento) {
        state.ultimaSync = momento;
        try {
            localStorage.setItem(chaveUltimaSync(), String(momento));
        } catch (e) {
            /* ignorado */
        }
    }

    /* ---------- Referências e dashboard ---------- */
    async function carregarReferencias() {
        try {
            const cache = await LTM.cacheGet('referencias');
            if (cache) {
                state.refs = cache;
            }
        } catch (e) {
            console.warn('Cache de referências indisponível', e);
        }
        if (!LTM.ligado()) {
            return state.refs;
        }
        try {
            const refs = await LTM.api('/mobile/referencias');
            state.refs = refs;
            await LTM.cacheSet('referencias', refs);
        } catch (e) {
            console.warn('Referências não atualizadas', e);
        }
        return state.refs;
    }
    LTApp.carregarReferencias = carregarReferencias;

    function chip(estado, etiqueta) {
        const chave = String(estado || '').toLowerCase();
        return '<span class="chip ' + esc(chave) + '">'
            + esc(etiqueta || estado || '—') + '</span>';
    }

    function htmlVazio(titulo, texto, icone, extra) {
        const simbolo = icone || 'i-list';
        return '<div class="empty-state">'
            + '<svg><use href="#' + simbolo + '"/></svg>'
            + '<strong>' + esc(titulo) + '</strong>'
            + '<span>' + esc(texto) + '</span>'
            + (extra || '')
            + '</div>';
    }

    async function carregarDashboard() {
        if (!state.utilizador) {
            return;
        }
        const chave = 'dashboard:' + state.utilizador.id;
        let cache = null;
        try {
            cache = await LTM.cacheGet(chave);
        } catch (e) {
            cache = null;
        }
        if (cache) {
            renderDashboard(cache);
        }
        if (!LTM.ligado()) {
            if (!cache) {
                renderDashboardOffline();
            }
            return;
        }
        try {
            const dados = await LTM.api('/mobile/dashboard');
            await LTM.cacheSet(chave, dados);
            renderDashboard(dados);
        } catch (e) {
            if (!cache) {
                const lista = $('#listaRecentes');
                if (lista) {
                    lista.innerHTML = htmlVazio(
                        'Não foi possível carregar',
                        e.message || 'A central não está a responder. Tente novamente dentro de instantes.',
                        'i-cloud-off');
                }
            }
        }
    }
    LTApp.carregarDashboard = carregarDashboard;

    function renderDashboardOffline() {
        const lista = $('#listaRecentes');
        if (lista) {
            lista.innerHTML = htmlVazio(
                'Ainda não tem registos guardados neste telemóvel',
                'Ligue-se à Internet uma vez para trazer os seus carregamentos para o telemóvel.',
                'i-cloud-off');
        }
        const total = $('#recentesTotal');
        if (total) {
            total.textContent = 'Sem ligação';
        }
    }

    function renderDashboard(dados) {
        if (!dados || !state.utilizador) {
            return;
        }
        atualizarCabecalho();
        const valores = {
            statTransito: dados.emTransito,
            statAtrasados: dados.atrasados,
            statConcluidos: dados.concluidos
        };
        Object.keys(valores).forEach(function (id) {
            const el = document.getElementById(id);
            if (el) {
                el.textContent = fmt.numero(valores[id], 0);
            }
        });
        const total = $('#recentesTotal');
        if (total) {
            total.textContent = fmt.numero(dados.total, 0) + ' no total';
        }
        const lista = $('#listaRecentes');
        if (!lista) {
            return;
        }
        const recentes = dados.recentes || [];
        if (!recentes.length) {
            lista.innerHTML = htmlVazio(
                'Sem carregamentos',
                'Toque em “Novo carregamento” para registar a primeira guia.',
                'i-truck');
            return;
        }
        lista.innerHTML = recentes.map(function (c) {
            return '<button class="recent-item" type="button" data-id="' + esc(c.id) + '">'
                + '<div class="recent-top">'
                + '<span class="recent-guide">' + esc(c.guiaNumero) + '</span>'
                + chip(c.estado, c.estadoEtiqueta) + '</div>'
                + '<div class="recent-meta">'
                + '<span>' + esc(c.tipoCarga) + ' · ' + fmt.numero(c.quantidadeToneladas, 1) + ' t</span>'
                + '<span>' + esc(c.fabricaNome || 'Fábrica') + '</span></div>'
                + '<div class="recent-foot">'
                + '<span>' + esc(c.veiculoMatricula || '') + ' · ' + fmt.relativo(c.dataHoraInicio) + '</span>'
                + '<span class="go">Ver detalhe ›</span></div>'
                + '</button>';
        }).join('');
    }

    /* ---------- Fila offline ---------- */
    async function filaDoUtilizador() {
        if (!state.utilizador) {
            return [];
        }
        const todos = await LTM.dbGetAll(LOJA_FILA);
        return todos.filter(function (item) {
            return String(item.motoristaId) === String(state.utilizador.id);
        }).sort(function (a, b) {
            return (b.criadoEm || 0) - (a.criadoEm || 0);
        });
    }

    function contar(fila) {
        return {
            pendentes: fila.filter(function (i) { return i.estado === 'PENDENTE'; }).length,
            sincronizados: fila.filter(function (i) { return i.estado === 'SINCRONIZADO'; }).length,
            rejeitados: fila.filter(function (i) { return i.estado === 'REJEITADO'; }).length
        };
    }

    async function atualizarResumoFila() {
        if (!state.utilizador) {
            return;
        }
        let fila = [];
        try {
            fila = await filaDoUtilizador();
        } catch (e) {
            console.warn(e);
        }
        const n = contar(fila);
        const acao = n.pendentes + n.rejeitados;

        const badges = {
            badgePendentes: n.pendentes,
            badgeSincronizados: n.sincronizados,
            badgeRejeitados: n.rejeitados
        };
        Object.keys(badges).forEach(function (id) {
            const el = document.getElementById(id);
            if (el) {
                el.textContent = String(badges[id]);
            }
        });

        const navBadge = $('#navBadge');
        if (navBadge) {
            navBadge.textContent = acao > 99 ? '99+' : String(acao);
            navBadge.classList.toggle('hidden', acao === 0);
        }

        const card = $('#dashboardFilaCard');
        if (card) {
            card.classList.toggle('synced', acao === 0);
            const titulo = $('#dashboardFilaTitulo');
            const texto = $('#dashboardFilaTexto');
            const icone = $('#dashboardFilaIcono');
            if (!fila.length) {
                if (titulo) {
                    titulo.textContent = 'Ainda não há registos';
                }
                if (texto) {
                    texto.textContent = 'Quando registar carregamentos, eles aparecem aqui.';
                }
                if (icone) {
                    icone.innerHTML = '<svg><use href="#i-list"/></svg>';
                }
            } else if (acao === 0) {
                if (titulo) {
                    titulo.textContent = 'Tudo enviado à central';
                }
                if (texto) {
                    texto.textContent = state.ultimaSync
                        ? 'Último envio às ' + fmt.hora(state.ultimaSync)
                        : 'Não há registos à espera de envio.';
                }
                if (icone) {
                    icone.innerHTML = '<svg><use href="#i-check"/></svg>';
                }
            } else if (n.rejeitados > 0) {
                if (titulo) {
                    titulo.textContent = n.rejeitados + ' não aceite' + (n.rejeitados === 1 ? '' : 's');
                }
                if (texto) {
                    texto.textContent = 'Abra a lista de registos para corrigir ou apagar.';
                }
                if (icone) {
                    icone.innerHTML = '<svg><use href="#i-alert"/></svg>';
                }
            } else {
                if (titulo) {
                    titulo.textContent = n.pendentes === 1
                        ? '1 carregamento aguarda envio'
                        : n.pendentes + ' carregamentos aguardam envio';
                }
                if (texto) {
                    texto.textContent = 'Guardados no telemóvel. Serão enviados à central quando houver ligação.';
                }
                if (icone) {
                    icone.innerHTML = '<svg><use href="#i-cloud-off"/></svg>';
                }
            }
        }

        const resumo = $('#syncResumo');
        if (resumo) {
            let texto;
            if (!fila.length) {
                texto = 'Não há registos guardados neste telemóvel.';
            } else if (acao === 0) {
                texto = 'Tudo enviado à central'
                    + (state.ultimaSync ? ' · último envio às ' + fmt.hora(state.ultimaSync) : '');
            } else {
                texto = n.pendentes + ' registo'
                    + (n.pendentes === 1 ? ' aguarda envio' : 's aguardam envio');
                if (n.rejeitados) {
                    texto += ' · ' + n.rejeitados + ' não aceite' + (n.rejeitados === 1 ? '' : 's');
                }
            }
            resumo.textContent = texto;
        }

        atualizarLinhaSync(n.pendentes, n.rejeitados);
    }
    LTApp.atualizarResumoFila = atualizarResumoFila;

    function classeItem(item) {
        if (item.estado === 'SINCRONIZADO') {
            return 'sincronizado';
        }
        if (item.estado === 'REJEITADO') {
            return 'rejeitado';
        }
        return 'pendente';
    }

    function etiquetaItem(item) {
        if (item.estado === 'SINCRONIZADO') {
            return 'Enviado à central';
        }
        if (item.estado === 'REJEITADO') {
            return 'Não aceite';
        }
        return 'Aguarda envio';
    }

    function renderItemFila(item) {
        const estado = item.estado || 'PENDENTE';
        let acoes = '';
        if (estado === 'PENDENTE') {
            acoes = '<button class="primary" type="button" data-acao="sync">Enviar agora</button>'
                + '<button class="danger" type="button" data-acao="remove">Apagar</button>';
        } else if (estado === 'REJEITADO') {
            acoes = '<button class="primary" type="button" data-acao="retry">Tentar novamente</button>'
                + '<button class="danger" type="button" data-acao="remove">Apagar</button>';
        } else if (item.carregamentoId) {
            acoes = '<button type="button" data-acao="open">Abrir detalhe</button>'
                + '<button class="danger" type="button" data-acao="remove">Apagar do telemóvel</button>';
        }
        const hora = item.sincronizadoEm || item.criadoEm;
        return '<article class="queue-item ' + classeItem(item) + '" data-uuid="' + esc(item.uuidLocal) + '">'
            + '<div class="qi-head">'
            + '<span class="qi-status"><i></i>' + etiquetaItem(item) + '</span>'
            + '<span class="qi-time">' + (estado === 'SINCRONIZADO' ? 'Enviado ' : '') + fmt.dataHora(hora) + '</span>'
            + '</div>'
            + '<strong class="qi-guide">' + esc(item.guiaNumero) + '</strong>'
            + '<div class="qi-meta"><span>' + esc(item.tipoCarga) + ' · '
            + fmt.numero(item.quantidadeToneladas, 1) + ' t</span>'
            + '<span>' + esc(item.fabricaNome || 'Fábrica') + '</span></div>'
            + '<div class="qi-meta"><span>' + esc(item.veiculoMatricula || '') + '</span>'
            + '<span>Fotografado em ' + fmt.dataHora(item.criadoEm) + '</span></div>'
            + (item.erro ? '<div class="qi-error">' + esc(item.erro) + '</div>' : '')
            + (acoes ? '<div class="qi-actions">' + acoes + '</div>' : '')
            + '</article>';
    }

    async function renderFila() {
        if (!state.utilizador) {
            return;
        }
        const lista = $('#listaFila');
        if (!lista) {
            return;
        }
        let fila = [];
        try {
            fila = await filaDoUtilizador();
        } catch (e) {
            lista.innerHTML = htmlVazio('Não foi possível ler os registos guardados', e.message, 'i-alert');
            return;
        }
        const filtrada = fila.filter(function (item) {
            return (item.estado || 'PENDENTE') === state.filaFiltro;
        });
        if (!filtrada.length) {
            const textos = {
                PENDENTE: ['Sem registos para enviar', 'Não há registos à espera de envio neste telemóvel.'],
                SINCRONIZADO: ['Sem registos enviados à central', 'Os registos aceites pela central aparecem aqui.'],
                REJEITADO: ['Sem registos não aceites', 'Não há registos com problemas.']
            };
            const t = textos[state.filaFiltro] || ['Sem registos', ''];
            lista.innerHTML = htmlVazio(t[0], t[1], 'i-list');
            return;
        }
        lista.innerHTML = filtrada.map(renderItemFila).join('');
    }
    LTApp.renderFila = renderFila;

    async function sincronizarItem(item) {
        const payload = {
            uuidLocal: item.uuidLocal,
            guiaNumero: item.guiaNumero,
            tipoCarga: item.tipoCarga,
            quantidadeToneladas: item.quantidadeToneladas,
            fabricaId: item.fabricaId || null,
            veiculoId: item.veiculoId,
            latitude: item.latitude,
            longitude: item.longitude,
            precisaoMetros: item.precisaoMetros,
            dataHoraInicio: item.dataHoraInicio,
            fotoBase64: item.fotoBase64,
            confirmarForaGeofence: !!item.confirmarForaGeofence
        };
        item.tentativas = (item.tentativas || 0) + 1;
        item.ultimaTentativa = new Date().toISOString();
        try {
            const resumo = await LTM.api('/carregamentos', {method: 'POST', body: payload});
            item.estado = 'SINCRONIZADO';
            item.erro = null;
            item.carregamentoId = resumo && resumo.id ? resumo.id : null;
            item.sincronizadoEm = new Date().toISOString();
            await LTM.dbPut(LOJA_FILA, item);
            return 'ok';
        } catch (e) {
            if (e.status === 401) {
                item.erro = 'A sessão terminou. Entre novamente com ligação à Internet.';
                await LTM.dbPut(LOJA_FILA, item);
                return 'sessao';
            }
            if (e.status === 400 || e.status === 422) {
                item.estado = 'REJEITADO';
                item.erro = e.message;
                item.rejeitadoEm = new Date().toISOString();
                await LTM.dbPut(LOJA_FILA, item);
                return 'rejeitado';
            }
            item.erro = e.message || 'A central não está a responder. Os registos ficam guardados no telemóvel. Tente novamente dentro de instantes.';
            await LTM.dbPut(LOJA_FILA, item);
            return 'falha';
        }
    }

    async function sincronizarFila(silencioso) {
        if (!state.utilizador) {
            return;
        }
        if (state.sincronizando) {
            if (!silencioso) {
                LTM.toast('Envio em curso…', 'Aguarde. Os registos estão a ser enviados à central.', 'warn');
            }
            return;
        }
        if (!LTM.ligado()) {
            if (!silencioso) {
                LTM.toast('Sem ligação', 'Os registos continuam guardados no telemóvel. '
                    + 'Serão enviados à central quando a ligação voltar.', 'warn');
            }
            return;
        }
        if (!state.token) {
            return;
        }
        let fila = [];
        try {
            fila = (await filaDoUtilizador()).filter(function (i) {
                return i.estado === 'PENDENTE';
            });
        } catch (e) {
            LTM.toast('Não foi possível ler os registos', e.message, 'error');
            return;
        }
        if (!fila.length) {
            if (!silencioso) {
                LTM.toast('Não há registos para enviar', 'Quando registar carregamentos, envie-os à central aqui.', 'success');
            }
            await atualizarResumoFila();
            return;
        }

        state.sincronizando = true;
        state.ultimaTentativaSync = Date.now();
        const botao = $('#btnSincronizar');
        if (botao) {
            botao.classList.add('syncing');
            botao.disabled = true;
        }
        let ok = 0;
        let rejeitados = 0;
        let falhas = 0;
        let sessao = false;

        for (let i = 0; i < fila.length; i++) {
            const resultado = await sincronizarItem(fila[i]);
            if (resultado === 'ok') {
                ok++;
            } else if (resultado === 'rejeitado') {
                rejeitados++;
            } else if (resultado === 'sessao') {
                sessao = true;
                break;
            } else {
                falhas++;
            }
            await atualizarResumoFila();
            if (state.ecra === 'pendentes') {
                await renderFila();
            }
        }

        state.sincronizando = false;
        if (botao) {
            botao.classList.remove('syncing');
            botao.disabled = false;
        }
        if (ok > 0) {
            gravarUltimaSync(Date.now());
            await carregarDashboard();
        }
        await atualizarResumoFila();
        if (state.ecra === 'pendentes') {
            await renderFila();
        }

        if (!silencioso || rejeitados > 0 || falhas > 0) {
            if (sessao) {
                return;
            }
            if (rejeitados > 0) {
                LTM.toast('Alguns registos não foram aceites',
                    rejeitados + (rejeitados === 1 ? ' registo precisa' : ' registos precisam')
                    + ' de correção. Abra a lista "Não aceites" e corrija os dados.',
                    'error');
            } else if (falhas > 0) {
                LTM.toast('Nem todos os registos foram enviados',
                    ok + (ok === 1 ? ' enviado' : ' enviados') + ' à central; '
                    + falhas + (falhas === 1 ? ' ficou guardado' : ' ficaram guardados')
                    + ' no telemóvel e ' + (falhas === 1 ? 'será enviado' : 'serão enviados') + ' de novo.',
                    'warn');
            } else {
                LTM.toast('Envio concluído',
                    ok + ' carregamento(s) recebido(s) pela central.', 'success');
            }
        }
    }
    LTApp.sincronizarFila = sincronizarFila;

    async function verificarSincronizacaoAutomatica() {
        if (!state.utilizador || state.sincronizando || !LTM.ligado()) {
            return;
        }
        let fila;
        try {
            fila = (await filaDoUtilizador()).filter(function (i) {
                return i.estado === 'PENDENTE';
            });
        } catch (e) {
            return;
        }
        if (!fila.length) {
            return;
        }
        if (Date.now() - state.ultimaTentativaSync < LTM.intervaloSyncMinutos() * 60000) {
            return;
        }
        await sincronizarFila(true);
    }

    /* ---------- Detalhe (M6) ---------- */
    function limparObjectUrlFoto() {
        if (state.objectUrlFoto) {
            try {
                URL.revokeObjectURL(state.objectUrlFoto);
            } catch (e) {
                /* ignorado */
            }
            state.objectUrlFoto = null;
        }
    }

    function renderInfo(rotulo, valor) {
        return '<div><span>' + esc(rotulo) + '</span><strong>' + esc(valor) + '</strong></div>';
    }

    function renderDetalhe(d) {
        const conteudo = $('#detalheConteudo');
        if (!conteudo || !d) {
            return;
        }
        const estadoClasse = String(d.estado || '').toLowerCase();
        const atraso = Number(d.atrasoMinutos || 0);
        const doc = d.documento;
        const timeline = (d.timeline && d.timeline.length) ? d.timeline : [];
        const etapasHtml = timeline.map(function (etapa) {
            return '<div class="tl-item ' + (etapa.concluida ? 'done' : '') + '">'
                + '<span class="tl-dot"><svg><use href="#i-check"/></svg></span>'
                + '<div class="tl-body"><strong>' + esc(etapa.titulo) + '</strong>'
                + '<span>' + (etapa.dataHora ? fmt.dataHora(etapa.dataHora) : 'Aguarda') + '</span>'
                + '</div></div>';
        }).join('');

        const posicao = d.ultimaPosicao
            ? '<div class="detail-card"><h2>Última localização registada</h2><div class="gps-grid">'
            + renderInfo('Latitude', fmt.numero(d.ultimaPosicao.latitude, 6))
            + renderInfo('Longitude', fmt.numero(d.ultimaPosicao.longitude, 6))
            + renderInfo('Qualidade da localização', fmt.metros(d.ultimaPosicao.precisaoMetros))
            + renderInfo('Registada', fmt.dataHora(d.ultimaPosicao.timestamp))
            + '</div></div>'
            : '';

        conteudo.innerHTML = '<div class="detail-hero">'
            + '<div class="dh-top"><div>'
            + '<span class="dh-label">Guia de transporte</span>'
            + '<h1>' + esc(d.guiaNumero) + '</h1>'
            + '<span class="dh-factory"><svg><use href="#i-pin"/></svg>'
            + esc(d.fabrica ? d.fabrica.nome : 'Sem fábrica') + '</span>'
            + '</div><span class="chip ' + esc(estadoClasse) + '">'
            + esc(LTM.textoEstado(d.estado, d.estadoEtiqueta) || '—') + '</span></div>'
            + '<div class="detail-grid">'
            + renderInfo('Tipo de carga', d.tipoCarga)
            + renderInfo('Quantidade', fmt.numero(d.quantidadeToneladas, 1) + ' t')
            + renderInfo('Início', fmt.dataHora(d.dataHoraInicio))
            + renderInfo(d.dataHoraFim ? 'Concluído' : 'Prazo da viagem',
                fmt.dataHora(d.dataHoraFim || d.horaLimite))
            + renderInfo('Veículo', d.veiculo ? d.veiculo.matricula : '—')
            + renderInfo('Motorista', d.motorista ? d.motorista.nome : '—')
            + '</div></div>'
            + (atraso > 0
                ? '<div class="detail-alert"><svg><use href="#i-alert"/></svg><span>'
                + 'O prazo definido para a viagem foi ultrapassado há ' + esc(fmt.numero(Math.round(atraso / 60), 0)) + ' h '
                + '(' + esc(fmt.numero(atraso, 0)) + ' min).</span></div>'
                : '')
            + (d.alertasAbertos > 0 && atraso === 0
                ? '<div class="detail-alert"><svg><use href="#i-alert"/></svg><span>'
                + 'Há ' + esc(d.alertasAbertos) + ' alerta(s) por ver neste carregamento.</span></div>'
                : '')
            + '<div class="detail-card"><h2>Fotografia da guia</h2>'
            + (doc
                ? '<div class="guide-photo" id="guiaFoto">A carregar a fotografia…</div>'
                : '<div class="guide-photo">Ainda não há fotografia da guia.</div>')
            + '</div>'
            + posicao
            + '<div class="detail-card"><h2>Progresso</h2><div class="timeline">'
            + (etapasHtml || '<div class="tl-body"><strong>Sem etapas registadas</strong></div>')
            + '</div></div>'
            + '<div class="detail-card"><h2>Identificação</h2><div class="gps-grid">'
            + renderInfo('Número do registo', d.id)
            + renderInfo('Número no telemóvel', d.uuidLocal || '—')
            + '</div></div>';

        if (doc && doc.id) {
            carregarGuia(doc.id);
        }
    }

    async function carregarGuia(documentoId) {
        const contentor = document.getElementById('guiaFoto');
        if (!contentor) {
            return;
        }
        try {
            const resposta = await fetch(API + '/documentos/' + documentoId + '/ficheiro', {
                headers: state.token ? {'Authorization': 'Bearer ' + state.token} : {},
                cache: 'no-store'
            });
            if (!resposta.ok) {
                throw new Error('A fotografia da guia não está disponível.');
            }
            const blob = await resposta.blob();
            limparObjectUrlFoto();
            state.objectUrlFoto = URL.createObjectURL(blob);
            contentor.innerHTML = '';
            const imagem = document.createElement('img');
            imagem.alt = 'Fotografia da guia de transporte';
            imagem.src = state.objectUrlFoto;
            contentor.appendChild(imagem);
        } catch (e) {
            contentor.textContent = LTM.ligado()
                ? 'Não foi possível mostrar a fotografia. Tente novamente.'
                : 'A fotografia aparece quando houver ligação à central.';
        }
    }

    async function abrirDetalhe(id, origem) {
        if (!id) {
            return;
        }
        state.detalheId = id;
        state.ecraAnterior = origem || 'dashboard';
        LTApp.mostrarEcra('detalhe');
        const conteudo = $('#detalheConteudo');
        if (conteudo) {
            conteudo.innerHTML = '<div class="empty-state"><div class="spinner"></div>'
                + '<span>A carregar o detalhe…</span></div>';
        }
        limparObjectUrlFoto();
        const chave = 'detalhe:' + state.utilizador.id + ':' + id;
        let cache = null;
        try {
            cache = await LTM.cacheGet(chave);
        } catch (e) {
            cache = null;
        }
        if (cache) {
            renderDetalhe(cache);
        }
        if (!LTM.ligado()) {
            if (!cache && conteudo) {
                conteudo.innerHTML = htmlVazio('Sem ligação', 
                    'Este detalhe ainda não está guardado no telemóvel. Ligue-se à Internet para o ver.', 'i-cloud-off');
            }
            return;
        }
        try {
            const detalhe = await LTM.api('/mobile/carregamentos/' + id);
            await LTM.cacheSet(chave, detalhe);
            renderDetalhe(detalhe);
        } catch (e) {
            if (!cache && conteudo && state.ecra === 'detalhe') {
                conteudo.innerHTML = htmlVazio('Não foi possível carregar',
                    e.message || 'A central não está a responder. Tente novamente dentro de instantes.', 'i-alert');
            }
        }
    }
    LTApp.abrirDetalhe = abrirDetalhe;

    /* ---------- Login (M1) ---------- */
    async function entrarNaApp() {
        atualizarCabecalho();
        state.ultimaSync = lerUltimaSync();
        state.ultimaTentativaSync = 0;
        LTApp.mostrarEcra('dashboard');
        await atualizarResumoFila();
        await carregarDashboard();
        carregarReferencias();
        if (LTM.ligado()) {
            setTimeout(function () {
                sincronizarFila(true);
            }, 900);
        }
    }

    async function submeterLogin(evento) {
        evento.preventDefault();
        esconderErroLogin();
        const email = $('#loginEmail').value.trim();
        const senha = $('#loginSenha').value;
        if (!email || !senha) {
            mostrarErroLogin('Preencha o e-mail e a palavra-passe.');
            return;
        }
        const botao = $('#btnEntrar');
        const rotulo = botao.querySelector('span');
        botao.disabled = true;
        if (rotulo) {
            rotulo.textContent = 'A ENTRAR…';
        }
        try {
            if (LTM.ligado()) {
                try {
                    const auth = await LTM.api('/auth/login', {
                        method: 'POST',
                        body: {email: email, senha: senha},
                        naoDesautenticar: true
                    });
                    LTM.guardarSessao(auth);
                    state.utilizador = auth.utilizador;
                    LTM.registarEmail(email);
                    try {
                        await LTM.guardarCredencial(email, senha);
                    } catch (e) {
                        console.warn('Credencial offline não guardada', e);
                    }
                    await entrarNaApp();
                    LTM.toast('Sessão iniciada', 'Bem-vindo, ' + auth.utilizador.nome + '.', 'success');
                    return;
                } catch (e) {
                    if (e.status !== 0) {
                        mostrarErroLogin(e.message || 'Não foi possível entrar. Verifique o e-mail e a palavra-passe.');
                        return;
                    }
                }
            }

            // Sem servidor: valida a credencial guardada e reutiliza o token offline.
            await LTM.verificarCredencialOffline(email, senha);
            let sessao = LTM.lerSessao();
            if (!LTM.sessaoValida(sessao)) {
                sessao = LTM.lerTokenOffline();
            }
            if (!LTM.sessaoValida(sessao)) {
                throw new Error('A sessão guardada no telemóvel terminou. Ligue-se à Internet e entre novamente.');
            }
            LTM.restaurarSessao(sessao);
            state.utilizador = sessao.utilizador;
            LTM.registarEmail(email);
            await entrarNaApp();
            LTM.toast('Sem ligação',
                'Entrou neste telemóvel. Os registos serão enviados à central quando houver ligação.',
                'warn');
        } catch (e) {
            mostrarErroLogin(e.message || 'Não foi possível entrar. Verifique o e-mail e a palavra-passe.');
        } finally {
            botao.disabled = false;
            if (rotulo) {
                rotulo.textContent = 'ENTRAR';
            }
        }
    }

    /* ---------- Ações da fila ---------- */
    async function sincronizarUm(uuid) {
        if (!LTM.ligado()) {
            LTM.toast('Sem ligação', 'O registo continua guardado no telemóvel e será enviado quando houver ligação.', 'warn');
            return;
        }
        const item = await LTM.dbGet(LOJA_FILA, uuid);
        if (!item || String(item.motoristaId) !== String(state.utilizador.id)) {
            return;
        }
        const resultado = await sincronizarItem(item);
        await atualizarResumoFila();
        await renderFila();
        if (resultado === 'ok') {
            gravarUltimaSync(Date.now());
            LTM.toast('Registo enviado à central', 'Guia ' + item.guiaNumero + ' enviada.', 'success');
            await carregarDashboard();
        } else if (resultado === 'rejeitado') {
            LTM.toast('Registo não aceite', item.erro || 'Corrija os dados e tente novamente.', 'error');
        } else if (resultado === 'falha') {
            LTM.toast('Não foi possível enviar o registo', item.erro || 'A central não está a responder. Tente novamente dentro de instantes.', 'error');
        }
    }

    async function tratarAcaoFila(evento) {
        const botao = evento.target.closest('[data-acao]');
        if (!botao) {
            return;
        }
        const artigo = botao.closest('[data-uuid]');
        if (!artigo) {
            return;
        }
        const uuid = artigo.getAttribute('data-uuid');
        const acao = botao.getAttribute('data-acao');
        const item = await LTM.dbGet(LOJA_FILA, uuid);
        if (!item || String(item.motoristaId) !== String(state.utilizador.id)) {
            return;
        }
        if (acao === 'open') {
            if (item.carregamentoId) {
                abrirDetalhe(item.carregamentoId, 'pendentes');
            }
            return;
        }
        if (acao === 'remove') {
            if (!window.confirm('Apagar o registo da guia ' + item.guiaNumero + ' deste telemóvel?')) {
                return;
            }
            await LTM.dbDelete(LOJA_FILA, uuid);
            await atualizarResumoFila();
            await renderFila();
            LTM.toast('Registo apagado', 'O registo foi apagado deste telemóvel.', 'success');
            return;
        }
        if (acao === 'retry') {
            item.estado = 'PENDENTE';
            item.erro = null;
            await LTM.dbPut(LOJA_FILA, item);
            await atualizarResumoFila();
            await renderFila();
            await sincronizarUm(uuid);
            return;
        }
        if (acao === 'sync') {
            botao.disabled = true;
            await sincronizarUm(uuid);
        }
    }

    /* ---------- Callbacks de rede/sessão ---------- */
    LTM.aoMudarLigacao = function (ligado) {
        atualizarResumoFila();
        if (ligado && navigator.onLine && state.utilizador) {
            // A reposição da rede deve desencadear uma tentativa imediata, sem
            // respeitar o intervalo normal de sincronização periódica.
            setTimeout(function () {
                if (LTM.ligado()) sincronizarFila(true);
            }, 250);
        }
    };

    LTM.aoReconectar = function () {
        if (!state.utilizador) {
            return;
        }
        LTM.toast('Ligação restabelecida', 'A enviar à central os registos guardados no telemóvel…', 'success');
        atualizarResumoFila();
        setTimeout(function () {
            if (navigator.onLine && LTM.ligado()) sincronizarFila(true);
        }, 250);
    };

    LTM.aoExpirarSessao = function () {
        LTM.toast('A sessão terminou', 'Entre novamente com ligação à Internet.', 'warn');
        mostrarLogin('A sessão terminou. Ligue-se à Internet e entre novamente.');
    };

    /* ---------- Eventos ---------- */
    function ligarEventos() {
        const formLogin = $('#formLogin');
        if (formLogin) {
            formLogin.addEventListener('submit', submeterLogin);
        }

        const mostrarSenha = $('#btnMostrarSenha');
        if (mostrarSenha) {
            mostrarSenha.addEventListener('click', function () {
                const input = $('#loginSenha');
                const visivel = input.type === 'text';
                input.type = visivel ? 'password' : 'text';
                mostrarSenha.textContent = visivel ? 'Ver' : 'Ocultar';
            });
        }

        $$('[data-voltar]').forEach(function (botao) {
            botao.addEventListener('click', voltar);
        });

        const sair = $('#btnSair');
        if (sair) {
            sair.addEventListener('click', function () {
                if (!window.confirm('Terminar sessão neste telemóvel? Os registos ainda não enviados ficam guardados.')) {
                    return;
                }
                if (typeof LTApp.pararCamera === 'function') {
                    LTApp.pararCamera();
                }
                LTM.limparSessao();
                state.objectUrlFoto = null;
                mostrarLogin();
                LTM.toast('Sessão terminada', 'Pode entrar novamente neste telemóvel com a sua palavra-passe.', 'success');
            });
        }

        const sincronizarTopo = $('#btnSincronizarTopo');
        if (sincronizarTopo) {
            sincronizarTopo.addEventListener('click', function () {
                sincronizarFila(false);
            });
        }

        const novo = $('#btnNovoCarregamento');
        if (novo) {
            novo.addEventListener('click', function () {
                if (typeof LTApp.abrirNovo === 'function') {
                    LTApp.abrirNovo();
                }
            });
        }

        const filaCard = $('#dashboardFilaCard');
        if (filaCard) {
            filaCard.addEventListener('click', function () {
                LTApp.mostrarEcra('pendentes');
                renderFila();
            });
        }

        const sincronizar = $('#btnSincronizar');
        if (sincronizar) {
            sincronizar.addEventListener('click', function () {
                sincronizarFila(false);
            });
        }

        $$('#filtroFila button').forEach(function (botao) {
            botao.addEventListener('click', function () {
                state.filaFiltro = botao.dataset.filtro;
                $$('#filtroFila button').forEach(function (b) {
                    b.classList.toggle('active', b === botao);
                });
                renderFila();
            });
        });

        const listaFila = $('#listaFila');
        if (listaFila) {
            listaFila.addEventListener('click', tratarAcaoFila);
        }

        const listaRecentes = $('#listaRecentes');
        if (listaRecentes) {
            listaRecentes.addEventListener('click', function (evento) {
                const item = evento.target.closest('[data-id]');
                if (item) {
                    abrirDetalhe(item.getAttribute('data-id'), 'dashboard');
                }
            });
        }

        $$('.nav-btn').forEach(function (botao) {
            botao.addEventListener('click', function () {
                const destino = botao.dataset.nav;
                LTApp.mostrarEcra(destino);
                if (destino === 'pendentes') {
                    renderFila();
                } else if (destino === 'dashboard') {
                    carregarDashboard();
                }
                atualizarResumoFila();
            });
        });

        const atualizarDetalhe = $('#btnAtualizarDetalhe');
        if (atualizarDetalhe) {
            atualizarDetalhe.addEventListener('click', function () {
                if (state.detalheId) {
                    abrirDetalhe(state.detalheId, state.ecraAnterior);
                }
            });
        }

        setInterval(verificarSincronizacaoAutomatica, 60000);
    }

    /* ---------- Arranque ---------- */
    async function iniciar() {
        await LTM.abrirBD();
        LTM.iniciarMonitorLigacao();
        LTM.registarServiceWorker();
        ligarEventos();

        const sessao = LTM.lerSessao();
        if (sessao && LTM.sessaoValida(sessao)) {
            LTM.restaurarSessao(sessao);
            state.utilizador = sessao.utilizador;
            await entrarNaApp();
        } else {
            if (sessao) {
                LTM.limparSessao();
            }
            mostrarLogin(sessao && !LTM.ligado()
                ? 'A sessão guardada neste telemóvel terminou. Ligue-se à Internet e entre novamente.'
                : null);
        }
        if (typeof LTApp.iniciarRegisto === 'function') {
            LTApp.iniciarRegisto();
        }
    }

    LTApp.iniciar = iniciar;
    LTApp.mostrarLogin = mostrarLogin;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})();
