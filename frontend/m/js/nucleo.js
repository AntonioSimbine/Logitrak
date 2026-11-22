/* ============================================================
   Logitrack — núcleo da aplicação do motorista
   Utilitários, sessão, IndexedDB, API e estado de ligação.
   ============================================================ */
(function () {
    'use strict';

    const API = ((window.LOGITRACK && window.LOGITRACK.apiBase) || '') + '/api/v1';
    const LS = {
        sessao: 'logitrack.mobile.sessao',
        credencial: 'logitrack.mobile.credencial',
        tokenOffline: 'logitrack.mobile.tokenOffline',
        email: 'logitrack.mobile.email'
    };
    const DB_NOME = 'logitrack-mobile';
    const DB_VERSAO = 1;
    const LOJA_FILA = 'fila';
    const LOJA_CACHE = 'cache';
    const TEXTO_ONLINE = 'Ligação ativa';
    const TEXTO_OFFLINE = 'Sem ligação — a aplicação funciona em modo offline';
    const PRECISAO_PADRAO = 15;
    const LIMITE_GPS_METROS = 15; // Teto fixo do RNF005
    const TAMANHO_FOTO_MAX = 1500000; // ~1,1 MB em Base64
    const MIN_LARGURA = 640;
    const MIN_ALTURA = 360;

    const LTM = window.LTM = window.LTM || {};

    const state = LTM.state = {
        token: '',
        tokenExpiraEm: 0,
        utilizador: null,
        refs: null,
        ecra: 'login',
        ecraAnterior: 'dashboard',
        online: navigator.onLine,
        servidorOk: navigator.onLine ? null : false,
        gps: null,
        foto: null,
        cameraStream: null,
        filaFiltro: 'PENDENTE',
        detalheId: null,
        sincronizando: false,
        ultimaSync: 0,
        ultimaTentativaSync: 0,
        objectUrlFoto: null
    };

    let bd = null;
    let usarFallback = false;
    let timerRede = null;

    /* ---------- DOM e texto ---------- */
    LTM.$ = function (seletor) {
        return document.querySelector(seletor);
    };

    LTM.$$ = function (seletor) {
        return Array.prototype.slice.call(document.querySelectorAll(seletor));
    };

    LTM.esc = function (valor) {
        return String(valor === null || valor === undefined ? '' : valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    LTM.iniciais = function (nome) {
        const partes = String(nome || '').trim().split(/\s+/).filter(Boolean);
        if (!partes.length) {
            return 'M';
        }
        if (partes.length === 1) {
            return partes[0].slice(0, 1).toUpperCase();
        }
        return (partes[0].slice(0, 1) + partes[partes.length - 1].slice(0, 1)).toUpperCase();
    };

    LTM.toast = function (titulo, mensagem, tipo) {
        const contentor = LTM.$('#toasts');
        if (!contentor) {
            console.log(titulo, mensagem);
            return;
        }
        const el = document.createElement('div');
        el.className = 'toast ' + (tipo || '');
        const corpo = document.createElement('div');
        corpo.style.flex = '1';
        const forte = document.createElement('strong');
        forte.textContent = titulo;
        corpo.appendChild(forte);
        if (mensagem) {
            const p = document.createElement('p');
            p.textContent = mensagem;
            corpo.appendChild(p);
        }
        const fechar = document.createElement('button');
        fechar.className = 't-close';
        fechar.type = 'button';
        fechar.setAttribute('aria-label', 'Fechar');
        fechar.textContent = '×';
        fechar.addEventListener('click', function () {
            el.remove();
        });
        el.appendChild(corpo);
        el.appendChild(fechar);
        contentor.appendChild(el);
        setTimeout(function () {
            el.style.transition = 'opacity .25s, transform .25s';
            el.style.opacity = '0';
            el.style.transform = 'translateY(-8px)';
            setTimeout(function () {
                el.remove();
            }, 260);
        }, tipo === 'error' ? 9000 : 5000);
    };

    /* ---------- Formatação ---------- */
    const dataHoraFmt = new Intl.DateTimeFormat('pt-PT', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
    const horaFmt = new Intl.DateTimeFormat('pt-PT', {
        hour: '2-digit', minute: '2-digit'
    });
    const dataFmt = new Intl.DateTimeFormat('pt-PT', {
        day: '2-digit', month: '2-digit', year: 'numeric'
    });

    function comoData(valor) {
        if (!valor) {
            return null;
        }
        if (valor instanceof Date) {
            return isNaN(valor.getTime()) ? null : valor;
        }
        const d = new Date(valor);
        return isNaN(d.getTime()) ? null : d;
    }

    LTM.fmt = {
        dataHora: function (valor) {
            const d = comoData(valor);
            return d ? dataHoraFmt.format(d) : '—';
        },
        data: function (valor) {
            const d = comoData(valor);
            return d ? dataFmt.format(d) : '—';
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
        metros: function (valor) {
            if (valor === null || valor === undefined || isNaN(Number(valor))) {
                return '—';
            }
            return Math.round(Number(valor)) + ' m';
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
            if (seg < 604800) {
                return 'há ' + Math.floor(seg / 86400) + ' dias';
            }
            return dataFmt.format(d);
        }
    };

    /* ---------- Armazenamento local ---------- */
    LTM.lerJSON = function (chave, omissao) {
        try {
            const bruto = localStorage.getItem(chave);
            return bruto ? JSON.parse(bruto) : omissao;
        } catch (e) {
            return omissao;
        }
    };

    LTM.guardarJSON = function (chave, valor) {
        try {
            localStorage.setItem(chave, JSON.stringify(valor));
            return true;
        } catch (e) {
            console.warn('Armazenamento local indisponível', e);
            return false;
        }
    };

    LTM.removerLocal = function (chave) {
        try {
            localStorage.removeItem(chave);
        } catch (e) {
            /* ignorado */
        }
    };

    /* ---------- Sessão / token ---------- */
    function descodificarToken(token) {
        try {
            const partes = String(token).split('.');
            if (partes.length < 2) {
                return null;
            }
            const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
            const preenchido = base64 + '==='.slice((base64.length + 3) % 4);
            const texto = decodeURIComponent(atob(preenchido).split('').map(function (c) {
                return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
            }).join(''));
            return JSON.parse(texto);
        } catch (e) {
            return null;
        }
    }

    LTM.sessaoValida = function (sessao) {
        return !!(sessao && sessao.token
            && (!sessao.expiraEm || sessao.expiraEm > Date.now() + 30000));
    };

    LTM.guardarSessao = function (auth) {
        const payload = descodificarToken(auth.token) || {};
        const expiraEm = payload.exp
            ? payload.exp * 1000
            : Date.now() + (Number(auth.expiresInSegundos) || 0) * 1000;
        const sessao = {
            token: auth.token,
            expiraEm: expiraEm,
            utilizador: auth.utilizador,
            criadoEm: Date.now()
        };
        LTM.guardarJSON(LS.sessao, sessao);
        LTM.guardarJSON(LS.tokenOffline, sessao);
        state.token = sessao.token;
        state.tokenExpiraEm = sessao.expiraEm;
        state.utilizador = sessao.utilizador;
        return sessao;
    };

    LTM.restaurarSessao = function (sessao) {
        LTM.guardarJSON(LS.sessao, sessao);
        state.token = sessao.token;
        state.tokenExpiraEm = sessao.expiraEm || 0;
        state.utilizador = sessao.utilizador;
    };

    LTM.lerSessao = function () {
        return LTM.lerJSON(LS.sessao, null);
    };

    LTM.lerTokenOffline = function () {
        return LTM.lerJSON(LS.tokenOffline, null);
    };

    LTM.limparSessao = function () {
        LTM.removerLocal(LS.sessao);
        state.token = '';
        state.tokenExpiraEm = 0;
        state.utilizador = null;
    };

    LTM.registarEmail = function (email) {
        try {
            localStorage.setItem(LS.email, email);
        } catch (e) {
            /* ignorado */
        }
    };

    LTM.emailRecordado = function () {
        return localStorage.getItem(LS.email) || '';
    };

    /* ---------- Credencial offline (PBKDF2 / fallback) ---------- */
    function bytesParaHex(bytes) {
        return Array.prototype.map.call(bytes, function (b) {
            return ('0' + b.toString(16)).slice(-2);
        }).join('');
    }

    function hexParaBytes(hex) {
        const limpo = String(hex || '').replace(/[^0-9a-f]/gi, '');
        const bytes = new Uint8Array(Math.floor(limpo.length / 2));
        for (let i = 0; i < bytes.length; i++) {
            bytes[i] = parseInt(limpo.substr(i * 2, 2), 16);
        }
        return bytes;
    }

    function hashFallback(texto) {
        let h1 = 0xdeadbeef ^ texto.length;
        let h2 = 0x41c6ce57 ^ texto.length;
        const dados = texto + '::logitrack::' + texto;
        for (let volta = 0; volta < 6000; volta++) {
            for (let i = 0; i < dados.length; i++) {
                const c = dados.charCodeAt(i);
                h1 = Math.imul(h1 ^ c, 2654435761);
                h2 = Math.imul(h2 ^ c, 1597334677);
            }
            h1 = (h1 ^ (h1 >>> 16)) >>> 0;
            h2 = (h2 ^ (h2 >>> 13)) >>> 0;
        }
        h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
        h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
        const partes = [];
        [h1, h2].forEach(function (n) {
            const s = (n >>> 0).toString(16);
            partes.push(('00000000' + s).slice(-8));
        });
        return (partes.join('') + partes.join('')).slice(0, 64);
    }

    LTM.derivarSenha = async function (senha, salt) {
        const seguro = window.crypto && window.crypto.subtle && window.isSecureContext;
        if (seguro) {
            try {
                const encoder = new TextEncoder();
                const chave = await window.crypto.subtle.importKey(
                    'raw', encoder.encode(senha), 'PBKDF2', false, ['deriveBits']);
                const bits = await window.crypto.subtle.deriveBits({
                    name: 'PBKDF2',
                    salt: hexParaBytes(salt),
                    iterations: 150000,
                    hash: 'SHA-256'
                }, chave, 256);
                return bytesParaHex(new Uint8Array(bits));
            } catch (e) {
                console.warn('PBKDF2 indisponível, a usar fallback local', e);
            }
        }
        return hashFallback(salt + ':' + senha);
    };

    LTM.guardarCredencial = async function (email, senha) {
        let salt = '';
        if (window.crypto && window.crypto.getRandomValues) {
            const bytes = new Uint8Array(16);
            window.crypto.getRandomValues(bytes);
            salt = bytesParaHex(bytes);
        } else {
            salt = String(Date.now()) + Math.random().toString(36).slice(2);
        }
        const hash = await LTM.derivarSenha(senha, salt);
        const registo = {
            email: String(email).trim().toLowerCase(),
            salt: salt,
            hash: hash,
            criadoEm: Date.now()
        };
        LTM.guardarJSON(LS.credencial, registo);
        LTM.registarEmail(email);
        return registo;
    };

    LTM.lerCredencial = function () {
        const cred = LTM.lerJSON(LS.credencial, null);
        if (!cred || !cred.email || !cred.salt || !cred.hash) {
            return null;
        }
        return cred;
    };

    LTM.verificarCredencialOffline = async function (email, senha) {
        const cred = LTM.lerCredencial();
        if (!cred) {
            throw new Error('O primeiro acesso requer ligação à Internet. '
                + 'Ligue-se uma vez para ativar o modo offline neste dispositivo.');
        }
        if (cred.email !== String(email).trim().toLowerCase()) {
            throw new Error('Credenciais inválidas');
        }
        const hash = await LTM.derivarSenha(senha, cred.salt);
        if (hash !== cred.hash) {
            throw new Error('Credenciais inválidas');
        }
        return true;
    };

    /* ---------- IndexedDB (com fallback para localStorage) ---------- */
    function abrirBD() {
        return new Promise(function (resolve) {
            if (!('indexedDB' in window)) {
                usarFallback = true;
                resolve();
                return;
            }
            let pedido;
            try {
                pedido = window.indexedDB.open(DB_NOME, DB_VERSAO);
            } catch (e) {
                usarFallback = true;
                resolve();
                return;
            }
            pedido.onupgradeneeded = function (evento) {
                const base = evento.target.result;
                if (!base.objectStoreNames.contains(LOJA_FILA)) {
                    const loja = base.createObjectStore(LOJA_FILA, {keyPath: 'uuidLocal'});
                    loja.createIndex('estado', 'estado', {unique: false});
                    loja.createIndex('criadoEm', 'criadoEm', {unique: false});
                }
                if (!base.objectStoreNames.contains(LOJA_CACHE)) {
                    base.createObjectStore(LOJA_CACHE, {keyPath: 'chave'});
                }
            };
            pedido.onsuccess = function () {
                bd = pedido.result;
                resolve();
            };
            pedido.onerror = function () {
                usarFallback = true;
                resolve();
            };
            pedido.onblocked = function () {
                usarFallback = true;
                resolve();
            };
        });
    }

    LTM.abrirBD = abrirBD;

    function chaveLoja(loja) {
        return 'logitrack.mobile.fb.' + loja;
    }

    function nomeChave(loja) {
        return loja === LOJA_FILA ? 'uuidLocal' : 'chave';
    }

    function lerFallback(loja) {
        const dados = LTM.lerJSON(chaveLoja(loja), []);
        return Array.isArray(dados) ? dados : [];
    }

    function gravarFallback(loja, registos) {
        try {
            localStorage.setItem(chaveLoja(loja), JSON.stringify(registos));
        } catch (e) {
            throw new Error('Armazenamento do dispositivo cheio. '
                + 'Sincronize ou remova registos pendentes para continuar.');
        }
    }

    LTM.dbPut = function (loja, valor) {
        if (usarFallback || !bd) {
            const registos = lerFallback(loja);
            const chave = nomeChave(loja);
            const indice = registos.findIndex(function (r) {
                return String(r[chave]) === String(valor[chave]);
            });
            if (indice >= 0) {
                registos[indice] = valor;
            } else {
                registos.push(valor);
            }
            gravarFallback(loja, registos);
            return Promise.resolve();
        }
        return new Promise(function (resolve, reject) {
            try {
                const tx = bd.transaction(loja, 'readwrite');
                tx.objectStore(loja).put(valor);
                tx.oncomplete = function () {
                    resolve();
                };
                tx.onerror = function () {
                    reject(tx.error || new Error('Falha ao gravar no dispositivo'));
                };
                tx.onabort = function () {
                    reject(tx.error || new Error('Gravação cancelada'));
                };
            } catch (e) {
                reject(e);
            }
        });
    };

    LTM.dbGet = function (loja, chave) {
        if (usarFallback || !bd) {
            const campo = nomeChave(loja);
            const encontrado = lerFallback(loja).find(function (r) {
                return String(r[campo]) === String(chave);
            });
            return Promise.resolve(encontrado || null);
        }
        return new Promise(function (resolve, reject) {
            try {
                const tx = bd.transaction(loja, 'readonly');
                const pedido = tx.objectStore(loja).get(chave);
                pedido.onsuccess = function () {
                    resolve(pedido.result || null);
                };
                pedido.onerror = function () {
                    reject(pedido.error);
                };
            } catch (e) {
                reject(e);
            }
        });
    };

    LTM.dbGetAll = function (loja) {
        if (usarFallback || !bd) {
            return Promise.resolve(lerFallback(loja).slice());
        }
        return new Promise(function (resolve, reject) {
            try {
                const tx = bd.transaction(loja, 'readonly');
                const pedido = tx.objectStore(loja).getAll();
                pedido.onsuccess = function () {
                    resolve(pedido.result || []);
                };
                pedido.onerror = function () {
                    reject(pedido.error);
                };
            } catch (e) {
                reject(e);
            }
        });
    };

    LTM.dbDelete = function (loja, chave) {
        if (usarFallback || !bd) {
            const campo = nomeChave(loja);
            const registos = lerFallback(loja).filter(function (r) {
                return String(r[campo]) !== String(chave);
            });
            gravarFallback(loja, registos);
            return Promise.resolve();
        }
        return new Promise(function (resolve, reject) {
            try {
                const tx = bd.transaction(loja, 'readwrite');
                tx.objectStore(loja).delete(chave);
                tx.oncomplete = function () {
                    resolve();
                };
                tx.onerror = function () {
                    reject(tx.error);
                };
            } catch (e) {
                reject(e);
            }
        });
    };

    LTM.cacheGet = async function (chave) {
        const registo = await LTM.dbGet(LOJA_CACHE, chave);
        return registo ? registo.valor : null;
    };

    LTM.cacheSet = function (chave, valor) {
        return LTM.dbPut(LOJA_CACHE, {
            chave: chave,
            valor: valor,
            atualizadoEm: Date.now()
        });
    };

    LTM.cacheRemove = function (chave) {
        return LTM.dbDelete(LOJA_CACHE, chave);
    };

    /* ---------- Estado da ligação ---------- */
    LTM.ligado = function () {
        return navigator.onLine && state.servidorOk !== false;
    };

    LTM.atualizarBarra = function () {
        const barra = LTM.$('#connectivityBar');
        const texto = LTM.$('#connectivityText');
        if (!barra || !texto) {
            return;
        }
        const ligado = LTM.ligado();
        state.online = ligado;
        barra.classList.toggle('online', ligado);
        barra.classList.toggle('offline', !ligado);
        texto.textContent = ligado ? TEXTO_ONLINE : TEXTO_OFFLINE;
    };

    LTM.marcarRedeOk = function () {
        const antes = state.servidorOk;
        state.servidorOk = true;
        state.online = true;
        if (antes === false) {
            LTM.atualizarBarra();
            if (typeof LTM.aoReconectar === 'function') {
                try {
                    LTM.aoReconectar();
                } catch (e) {
                    console.warn(e);
                }
            }
        } else {
            LTM.atualizarBarra();
        }
    };

    LTM.marcarRedeFalhou = function () {
        const antes = state.servidorOk;
        state.servidorOk = false;
        state.online = false;
        LTM.atualizarBarra();
        if (antes !== false && typeof LTM.aoMudarLigacao === 'function') {
            try {
                LTM.aoMudarLigacao(false);
            } catch (e) {
                console.warn(e);
            }
        }
    };

    LTM.iniciarMonitorLigacao = function () {
        window.addEventListener('online', function () {
            state.servidorOk = null;
            LTM.atualizarBarra();
            if (typeof LTM.aoMudarLigacao === 'function') {
                LTM.aoMudarLigacao(true);
            }
        });
        window.addEventListener('offline', function () {
            state.servidorOk = false;
            LTM.atualizarBarra();
            if (typeof LTM.aoMudarLigacao === 'function') {
                LTM.aoMudarLigacao(false);
            }
        });
        if (timerRede) {
            clearInterval(timerRede);
        }
        timerRede = setInterval(async function () {
            if (navigator.onLine && state.servidorOk === false && state.token) {
                try {
                    await LTM.api('/auth/me');
                } catch (e) {
                    /* continua offline */
                }
            }
        }, 45000);
        LTM.atualizarBarra();
    };

    /* ---------- API ---------- */
    function mensagemErroCorpo(corpo, status) {
        if (!corpo) {
            return status === 0
                ? 'Sem ligação à Internet.'
                : 'Ocorreu um erro (' + status + ').';
        }
        if (typeof corpo === 'string') {
            return corpo;
        }
        let texto = corpo.error || corpo.message || ('Ocorreu um erro (' + status + ').');
        if (corpo.detalhes) {
            const detalhes = Object.keys(corpo.detalhes).map(function (k) {
                return corpo.detalhes[k];
            }).filter(Boolean);
            if (detalhes.length) {
                texto += ' — ' + detalhes.join(' ');
            }
        }
        return texto;
    }

    LTM.ErroApi = function (mensagem, status, corpo) {
        this.name = 'ErroApi';
        this.message = mensagem;
        this.status = status || 0;
        this.corpo = corpo || null;
    };
    LTM.ErroApi.prototype = Object.create(Error.prototype);
    LTM.ErroApi.prototype.constructor = LTM.ErroApi;

    LTM.api = async function (caminho, opcoes) {
        const opts = opcoes || {};
        const cabecalhos = Object.assign({'Accept': 'application/json'}, opts.headers || {});
        if (state.token) {
            cabecalhos.Authorization = 'Bearer ' + state.token;
        }
        let corpo = opts.body;
        if (corpo && !(corpo instanceof FormData) && typeof corpo !== 'string') {
            cabecalhos['Content-Type'] = 'application/json';
            corpo = JSON.stringify(corpo);
        }
        let resposta;
        try {
            resposta = await fetch(API + caminho, {
                method: opts.method || 'GET',
                headers: cabecalhos,
                body: corpo,
                cache: 'no-store'
            });
        } catch (e) {
            LTM.marcarRedeFalhou();
            throw new LTM.ErroApi(
                'Sem ligação à Internet. O registo fica guardado neste dispositivo.',
                0, null);
        }

        LTM.marcarRedeOk();

        if (resposta.status === 401 && opts.naoDesautenticar !== true) {
            LTM.sessaoExpirada();
        }

        if (!resposta.ok) {
            let conteudo = null;
            const tipo = resposta.headers.get('content-type') || '';
            try {
                conteudo = tipo.indexOf('json') >= 0
                    ? await resposta.json()
                    : await resposta.text();
            } catch (e) {
                conteudo = null;
            }
            throw new LTM.ErroApi(
                mensagemErroCorpo(conteudo, resposta.status),
                resposta.status, conteudo);
        }

        if (resposta.status === 204) {
            return null;
        }
        const tipo = resposta.headers.get('content-type') || '';
        return tipo.indexOf('json') >= 0 ? resposta.json() : resposta.text();
    };

    LTM.sessaoExpirada = function () {
        if (!state.token && !state.utilizador) {
            return;
        }
        LTM.limparSessao();
        if (typeof LTM.aoExpirarSessao === 'function') {
            try {
                LTM.aoExpirarSessao();
            } catch (e) {
                console.warn(e);
            }
        }
    };

    /* ---------- Geografia ---------- */
    LTM.haversine = function (lat1, lon1, lat2, lon2) {
        const raioTerra = 6371000;
        const rad = Math.PI / 180;
        const dLat = (lat2 - lat1) * rad;
        const dLon = (lon2 - lon1) * rad;
        const a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1 * rad) * Math.cos(lat2 * rad)
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * raioTerra * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    };

    LTM.precisaMaxima = function () {
        const params = state.refs && state.refs.parametros ? state.refs.parametros : {};
        const valor = Number(String(params.PRECISAO_MAXIMA_GPS_METROS || '').replace(',', '.'));
        if (!isFinite(valor) || valor <= 0) return PRECISAO_PADRAO;
        // Configurações antigas superiores a 15 m não podem alargar o limite.
        return Math.min(valor, LIMITE_GPS_METROS);
    };

    LTM.intervaloSyncMinutos = function () {
        const params = state.refs && state.refs.parametros ? state.refs.parametros : {};
        const valor = parseInt(params.INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS, 10);
        return isNaN(valor) || valor < 1 ? 5 : valor;
    };

    LTM.fabricaMaisProxima = function (lat, lng) {
        const fabricas = state.refs && state.refs.fabricas ? state.refs.fabricas : [];
        let melhor = null;
        fabricas.forEach(function (f) {
            if (f.latitude === null || f.longitude === null) {
                return;
            }
            const distancia = LTM.haversine(lat, lng, Number(f.latitude), Number(f.longitude));
            if (!melhor || distancia < melhor.distancia) {
                melhor = {
                    fabrica: f,
                    distancia: distancia,
                    raio: Number(f.raioGeofenceMetros) || 500,
                    dentro: distancia <= (Number(f.raioGeofenceMetros) || 500)
                };
            }
        });
        return melhor;
    };

    LTM.novoUuid = function () {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return window.crypto.randomUUID();
        }
        const bytes = new Uint8Array(16);
        if (window.crypto && window.crypto.getRandomValues) {
            window.crypto.getRandomValues(bytes);
        } else {
            for (let i = 0; i < 16; i++) {
                bytes[i] = Math.floor(Math.random() * 256);
            }
        }
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = bytesParaHex(bytes);
        return hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16)
            + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
    };

    LTM.dataLocalISO = function (data) {
        const d = data || new Date();
        const pad = function (n) {
            return String(n).padStart(2, '0');
        };
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
            + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    };

    /* ---------- Service Worker ---------- */
    LTM.registarServiceWorker = function () {
        if (!('serviceWorker' in navigator)) {
            return;
        }
        const seguro = location.protocol === 'https:'
            || location.hostname === 'localhost'
            || location.hostname === '127.0.0.1';
        if (!seguro) {
            console.info('Service Worker requer HTTPS ou localhost.');
            return;
        }
        navigator.serviceWorker.register('sw.js', {updateViaCache: 'none'})
            .then(function (registo) {
                // O nginx serve sw.js sem cache; verificar actualizações periodicamente.
                setInterval(function () {
                    registo.update().catch(function () {});
                }, 60 * 60 * 1000);
                registo.addEventListener('updatefound', function () {
                    const novo = registo.installing;
                    if (!novo) return;
                    novo.addEventListener('statechange', function () {
                        if (novo.state === 'installed' && navigator.serviceWorker.controller) {
                            LTM.toast('Actualização disponível',
                                ' A nova versão será usada no próximo arranque da aplicação.',
                                'info');
                        }
                    });
                });
            })
            .catch(function (e) {
                console.warn('Service Worker não registado', e);
            });
    };

    LTM.constantes = {
        LOJA_FILA: LOJA_FILA,
        LOJA_CACHE: LOJA_CACHE,
        LIMITE_GPS_METROS: LIMITE_GPS_METROS,
        TAMANHO_FOTO_MAX: TAMANHO_FOTO_MAX,
        MIN_LARGURA: MIN_LARGURA,
        MIN_ALTURA: MIN_ALTURA,
        TEXTO_ONLINE: TEXTO_ONLINE,
        TEXTO_OFFLINE: TEXTO_OFFLINE
    };
})();
