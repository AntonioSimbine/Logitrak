/* Logitrack — utilitários partilhados do painel web */
(function () {
    'use strict';

    const LT = window.LT = window.LT || {};
    const CONFIG = window.LOGITRACK || {};
    const API_BASE = CONFIG.apiBase || '';
    const SESSAO = CONFIG.lerSessao ? CONFIG.lerSessao() : null;
    const TOKEN = SESSAO && SESSAO.token ? SESSAO.token : '';
    const UTILIZADOR = SESSAO && SESSAO.utilizador ? SESSAO.utilizador : {};

    LT.token = TOKEN;
    LT.utilizador = UTILIZADOR.nome || '';
    LT.perfil = UTILIZADOR.perfil || '';
    LT.sessao = SESSAO;
    LT.handlers = {alerta: [], carregamento: [], mapa: []};

    LT.apiUrl = function (caminho) {
        if (/^https?:\/\//i.test(caminho)) {
            return caminho;
        }
        return API_BASE + caminho;
    };

    LT.urlCarregamento = function (id) {
        const base = CONFIG.url ? CONFIG.url('app/carregamento-detalhe.html')
            : 'app/carregamento-detalhe.html';
        return base + '?id=' + encodeURIComponent(id);
    };

    LT.terminarSessao = function () {
        if (CONFIG.limparSessao) {
            CONFIG.limparSessao();
        }
        window.location.replace(CONFIG.url ? CONFIG.url('login.html?expirou=1')
            : 'login.html?expirou=1');
    };

    LT.on = function (tipo, fn) {
        if (!LT.handlers[tipo]) {
            LT.handlers[tipo] = [];
        }
        LT.handlers[tipo].push(fn);
    };

    LT.emit = function (tipo, payload) {
        (LT.handlers[tipo] || []).forEach(function (fn) {
            try {
                fn(payload);
            } catch (e) {
                console.error('Handler realtime falhou', e);
            }
        });
    };

    function mensagemErro(corpo, status) {
        if (!corpo) {
            return 'Ocorreu um erro (' + status + ').';
        }
        if (typeof corpo === 'string') {
            return corpo;
        }
        let texto = corpo.error || corpo.message || 'Ocorreu um erro (' + status + ').';
        if (corpo.detalhes) {
            const detalhes = Object.values(corpo.detalhes).filter(Boolean);
            if (detalhes.length) {
                texto += ' — ' + detalhes.join(' ');
            }
        }
        return texto;
    }

    LT.api = async function (caminho, opcoes) {
        const opcoesFinais = Object.assign({}, opcoes || {});
        opcoesFinais.headers = Object.assign({}, opcoesFinais.headers || {});
        if (TOKEN) {
            opcoesFinais.headers['Authorization'] = 'Bearer ' + TOKEN;
        }
        if (opcoesFinais.body && !(opcoesFinais.body instanceof FormData)
            && typeof opcoesFinais.body !== 'string') {
            opcoesFinais.headers['Content-Type'] = 'application/json';
            opcoesFinais.body = JSON.stringify(opcoesFinais.body);
        }
        if (!opcoesFinais.headers['Accept']) {
            opcoesFinais.headers['Accept'] = 'application/json';
        }

        const resposta = await fetch(LT.apiUrl(caminho), opcoesFinais);
        if (resposta.status === 401 && caminho.indexOf('/auth/login') === -1) {
            LT.terminarSessao();
        }
        if (!resposta.ok) {
            let corpo = null;
            const tipo = resposta.headers.get('content-type') || '';
            try {
                corpo = tipo.includes('json') ? await resposta.json() : await resposta.text();
            } catch (e) {
                corpo = null;
            }
            const erro = new Error(mensagemErro(corpo, resposta.status));
            erro.status = resposta.status;
            erro.corpo = corpo;
            throw erro;
        }
        if (resposta.status === 204) {
            return null;
        }
        const tipo = resposta.headers.get('content-type') || '';
        return tipo.includes('json') ? resposta.json() : resposta.text();
    };

    LT.download = async function (caminho, nomeSugerido) {
        const resposta = await fetch(LT.apiUrl(caminho), {
            headers: TOKEN ? {'Authorization': 'Bearer ' + TOKEN} : {}
        });
        if (!resposta.ok) {
            let corpo = null;
            try {
                corpo = await resposta.json();
            } catch (e) {
                corpo = null;
            }
            throw new Error(mensagemErro(corpo, resposta.status));
        }
        let nome = nomeSugerido;
        if (!nome) {
            const disposicao = resposta.headers.get('content-disposition') || '';
            const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposicao);
            nome = match ? decodeURIComponent(match[1]) : 'logitrack-export';
        }
        const blob = await resposta.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nome;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () {
            URL.revokeObjectURL(url);
        }, 3000);
    };

    /**
     * Descarrega um ficheiro protegido (por exemplo uma guia digitalizada)
     * usando o JWT e devolve um object URL utilizável em <img>/<a>.
     */
    LT.ficheiroProtegido = async function (caminho) {
        const resposta = await fetch(LT.apiUrl(caminho), {
            headers: TOKEN ? {'Authorization': 'Bearer ' + TOKEN} : {}
        });
        if (resposta.status === 401) {
            LT.terminarSessao();
        }
        if (!resposta.ok) {
            throw new Error('Não foi possível carregar o ficheiro (' + resposta.status + ').');
        }
        return URL.createObjectURL(await resposta.blob());
    };

    LT.libertarUrl = function (url) {
        if (url && String(url).indexOf('blob:') === 0) {
            URL.revokeObjectURL(url);
        }
    };

    LT.toast = function (titulo, mensagem, tipo) {
        const contentor = document.getElementById('toasts');
        if (!contentor) {
            console.log(titulo, mensagem);
            return;
        }
        const el = document.createElement('div');
        el.className = 'toast ' + (tipo || '');
        el.innerHTML = '<div style="flex:1"><strong></strong><p></p></div>'
            + '<button class="t-close" type="button" aria-label="Fechar">×</button>';
        el.querySelector('strong').textContent = titulo;
        el.querySelector('p').textContent = mensagem || '';
        el.querySelector('.t-close').addEventListener('click', function () {
            el.remove();
        });
        contentor.appendChild(el);
        setTimeout(function () {
            el.style.transition = 'opacity .3s, transform .3s';
            el.style.opacity = '0';
            el.style.transform = 'translateX(14px)';
            setTimeout(function () {
                el.remove();
            }, 320);
        }, tipo === 'error' ? 9000 : 5500);
    };

    LT.erro = function (e) {
        console.error(e);
        LT.toast('Não foi possível concluir', e && e.message ? e.message : String(e), 'error');
    };

    const dataFmt = new Intl.DateTimeFormat('pt-PT', {
        day: '2-digit', month: '2-digit', year: 'numeric'
    });
    const dataHoraFmt = new Intl.DateTimeFormat('pt-PT', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
    const horaFmt = new Intl.DateTimeFormat('pt-PT', {
        hour: '2-digit', minute: '2-digit'
    });

    function comoData(valor) {
        if (!valor) {
            return null;
        }
        if (valor instanceof Date) {
            return valor;
        }
        const d = new Date(valor);
        return isNaN(d.getTime()) ? null : d;
    }

    LT.fmt = {
        data: function (valor) {
            const d = comoData(valor);
            return d ? dataFmt.format(d) : '—';
        },
        dataHora: function (valor) {
            const d = comoData(valor);
            return d ? dataHoraFmt.format(d) : '—';
        },
        hora: function (valor) {
            const d = comoData(valor);
            return d ? horaFmt.format(d) : '—';
        },
        numero: function (valor, casas) {
            if (valor === null || valor === undefined || isNaN(Number(valor))) {
                return '—';
            }
            return new Intl.NumberFormat('pt-PT', {
                minimumFractionDigits: casas || 0,
                maximumFractionDigits: casas === undefined ? 1 : casas
            }).format(Number(valor));
        },
        duracao: function (minutos) {
            if (!minutos || minutos <= 0) {
                return '—';
            }
            const h = Math.floor(minutos / 60);
            const m = Math.round(minutos % 60);
            return h > 0 ? h + 'h ' + m + 'min' : m + ' min';
        },
        relativo: function (valor) {
            const d = comoData(valor);
            if (!d) {
                return '—';
            }
            const seg = Math.round((Date.now() - d.getTime()) / 1000);
            if (seg < 60) {
                return 'agora mesmo';
            }
            if (seg < 3600) {
                return 'há ' + Math.floor(seg / 60) + ' min';
            }
            if (seg < 86400) {
                return 'há ' + Math.floor(seg / 3600) + ' h';
            }
            return 'há ' + Math.floor(seg / 86400) + ' dias';
        }
    };

    LT.escape = function (valor) {
        return String(valor === null || valor === undefined ? '' : valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    LT.chip = function (estado, etiqueta) {
        const chave = String(estado || '').toLowerCase();
        return '<span class="chip ' + LT.escape(chave) + '">'
            + LT.escape(etiqueta || estado || '—') + '</span>';
    };

    LT.debounce = function (fn, ms) {
        let timer;
        return function () {
            const args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () {
                fn.apply(null, args);
            }, ms || 300);
        };
    };

    LT.query = function (nome) {
        return new URLSearchParams(window.location.search).get(nome);
    };

    LT.abrirModal = function (id) {
        const el = document.getElementById(id);
        if (el) {
            el.classList.add('open');
        }
    };

    LT.fecharModal = function (id) {
        const el = document.getElementById(id);
        if (el) {
            el.classList.remove('open');
        }
    };

    LT.limparFormulario = function (form) {
        if (!form) {
            return;
        }
        form.reset();
        form.querySelectorAll('input[type=hidden]').forEach(function (el) {
            el.value = '';
        });
    };

    LT.beep = function () {
        try {
            const Ctx = window.AudioContext || window.webkitAudioContext;
            if (!Ctx) {
                return;
            }
            const ctx = new Ctx();
            const osc = ctx.createOscillator();
            const ganho = ctx.createGain();
            osc.type = 'sine';
            osc.frequency.value = 880;
            ganho.gain.setValueAtTime(0.0001, ctx.currentTime);
            ganho.gain.exponentialRampToValueAtTime(0.12, ctx.currentTime + 0.02);
            ganho.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.32);
            osc.connect(ganho).connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.35);
            setTimeout(function () {
                ctx.close();
            }, 500);
        } catch (e) {
            /* sem áudio disponível */
        }
    };

    LT.atualizarBadge = function (total) {
        const badge = document.getElementById('navAlertBadge');
        if (!badge) {
            return;
        }
        if (total > 0) {
            badge.textContent = total > 99 ? '99+' : String(total);
            badge.classList.remove('hidden');
        } else {
            badge.classList.add('hidden');
        }
        document.title = document.title.replace(/^\(\d+\+?\)\s*/, '');
        if (total > 0) {
            document.title = '(' + (total > 99 ? '99+' : total) + ') ' + document.title;
        }
    };

    let stompClient = null;
    let tentativaRealtime = 0;
    let verificacaoServidor = null;

    function pintarIndicador(estado) {
        const indicador = document.getElementById('liveIndicator');
        if (!indicador) {
            return;
        }
        if (estado === 'offline') {
            indicador.innerHTML = '<i style="background:#c0392b"></i> Sem ligação à Internet';
            indicador.style.color = '#a33';
            indicador.style.background = '#fdecea';
            return;
        }
        if (estado === 'reconectar') {
            indicador.innerHTML = '<i style="background:#c0392b"></i> A reconectar…';
            indicador.style.color = '#c0392b';
            indicador.style.background = '#fdecea';
            return;
        }
        indicador.innerHTML = '<i></i> Tempo real';
        indicador.style.color = '';
        indicador.style.background = '';
    }

    // Confirma se a API continua acessível antes de assumir que a ligação caiu.
    // O canal WebSocket pode fechar (proxy, inatividade) sem que o servidor
    // esteja indisponível — nesse caso não faz sentido mostrar "A reconectar…".
    function servidorAcessivel() {
        if (!navigator.onLine) {
            return Promise.resolve(false);
        }
        if (verificacaoServidor) {
            return verificacaoServidor;
        }
        const controlador = typeof AbortController !== 'undefined'
            ? new AbortController() : null;
        const temporizador = controlador
            ? window.setTimeout(function () {
                controlador.abort();
            }, 4000)
            : null;
        verificacaoServidor = fetch(LT.apiUrl('/api/v1/health'), {
            method: 'GET',
            cache: 'no-store',
            signal: controlador ? controlador.signal : undefined
        }).then(function (resposta) {
            return resposta.ok;
        }).catch(function () {
            return false;
        }).finally(function () {
            if (temporizador) {
                window.clearTimeout(temporizador);
            }
            verificacaoServidor = null;
        });
        return verificacaoServidor;
    }

    function ligarRealtime() {
        if (!TOKEN || !window.SockJS || !window.StompJs) {
            return;
        }
        if (stompClient && stompClient.active) {
            return;
        }
        tentativaRealtime += 1;
        try {
            stompClient = new window.StompJs.Client({
                webSocketFactory: function () {
                    return new window.SockJS(LT.apiUrl('/ws'));
                },
                connectHeaders: {'Authorization': 'Bearer ' + TOKEN},
                reconnectDelay: 6000,
                heartbeatIncoming: 10000,
                heartbeatOutgoing: 10000,
                debug: function () {
                }
            });
            stompClient.onConnect = function () {
                pintarIndicador('ligado');
                stompClient.subscribe('/topic/alertas-atraso', function (msg) {
                    const alerta = JSON.parse(msg.body);
                    LT.emit('alerta', alerta);
                    LT.atualizarBadge((parseInt(document.getElementById('navAlertBadge')
                        ? document.getElementById('navAlertBadge').textContent : '0', 10) || 0) + 1);
                    LT.beep();
                    LT.toast('Alerta de atraso', alerta.mensagem, 'warn');
                });
                stompClient.subscribe('/topic/carregamentos', function (msg) {
                    LT.emit('carregamento', JSON.parse(msg.body));
                });
                stompClient.subscribe('/topic/mapa', function (msg) {
                    LT.emit('mapa', JSON.parse(msg.body));
                });
            };
            stompClient.onStompError = function (frame) {
                console.warn('Erro STOMP:', frame && frame.headers ? frame.headers.message : 'sem detalhe');
            };
            stompClient.onWebSocketError = function (evento) {
                console.warn('Erro WebSocket:', evento);
            };
            stompClient.onWebSocketClose = function () {
                if (!navigator.onLine) {
                    pintarIndicador('offline');
                    return;
                }
                servidorAcessivel().then(function (acessivel) {
                    pintarIndicador(acessivel ? 'ligado' : 'reconectar');
                });
            };
            stompClient.activate();
        } catch (e) {
            console.warn('WebSocket indisponível; nova tentativa será feita', e);
            window.setTimeout(ligarRealtime, Math.min(30000, 2000 * tentativaRealtime));
        }
    }

    // A ligação em tempo real volta a ser ativada automaticamente quando a rede regressa.
    window.addEventListener('online', function () {
        window.setTimeout(function () {
            if (stompClient && stompClient.active) {
                stompClient.deactivate().catch(function () {}).finally(function () {
                    ligarRealtime();
                });
            } else {
                ligarRealtime();
            }
            carregarBadge();
        }, 300);
    });

    window.addEventListener('offline', function () {
        pintarIndicador('offline');
    });

    function carregarBadge() {
        if (!TOKEN) {
            return;
        }
        if (LT.perfil !== 'ADMINISTRADOR' && LT.perfil !== 'OPERADOR_CENTRAL') {
            return;
        }
        LT.api('/api/v1/alertas/nao-lidos').then(function (alertas) {
            LT.atualizarBadge(alertas.length);
        }).catch(function () {
            /* silencioso */
        });
    }

    function initShell() {
        const botao = document.getElementById('menuToggle');
        const sidebar = document.getElementById('sidebar');
        if (botao && sidebar) {
            botao.addEventListener('click', function () {
                sidebar.classList.toggle('open');
            });
        }
        document.querySelectorAll('.modal-backdrop').forEach(function (backdrop) {
            backdrop.addEventListener('click', function (ev) {
                if (ev.target === backdrop) {
                    backdrop.classList.remove('open');
                }
            });
        });
        document.addEventListener('keydown', function (ev) {
            if (ev.key === 'Escape') {
                document.querySelectorAll('.modal-backdrop.open').forEach(function (m) {
                    m.classList.remove('open');
                });
                const lightbox = document.querySelector('.lightbox.open');
                if (lightbox) {
                    lightbox.classList.remove('open');
                }
            }
        });
        const lightbox = document.getElementById('lightbox');
        if (lightbox) {
            lightbox.addEventListener('click', function (ev) {
                if (ev.target === lightbox) {
                    lightbox.classList.remove('open');
                }
            });
        }
        carregarBadge();
        setTimeout(ligarRealtime, 400);
        setInterval(carregarBadge, 60000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initShell);
    } else {
        initShell();
    }

    LT.carregarFabricas = function () {
        return LT.api('/api/v1/fabricas');
    };

    LT.carregarVeiculos = function () {
        return LT.api('/api/v1/veiculos');
    };

    LT.carregarMotoristas = function () {
        return LT.api('/api/v1/utilizadores/motoristas');
    };

    LT.estadosCarregamento = [
        {valor: 'REGISTADO', etiqueta: 'Registado'},
        {valor: 'EM_TRANSITO', etiqueta: 'Em trânsito'},
        {valor: 'ATRASADO', etiqueta: 'Atrasado'},
        {valor: 'CONCLUIDO', etiqueta: 'Concluído'},
        {valor: 'REJEITADO', etiqueta: 'Rejeitado'}
    ];

    LT.preencherSelect = function (select, itens, opcaoVazia, valor, etiqueta) {
        if (!select) {
            return;
        }
        select.innerHTML = '';
        if (opcaoVazia) {
            select.appendChild(new Option(opcaoVazia, ''));
        }
        itens.forEach(function (item) {
            const value = typeof valor === 'function' ? valor(item) : item[valor];
            const label = typeof etiqueta === 'function' ? etiqueta(item) : item[etiqueta];
            select.appendChild(new Option(label, value));
        });
    };
})();
