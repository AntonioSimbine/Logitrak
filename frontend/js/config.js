/*
 * Logitrack — configuração do frontend
 *
 * O frontend é totalmente estático e consome a API Spring Boot.
 * Endereço predefinido em desenvolvimento: http://localhost:8080.
 *
 * Pode alterar aqui o endereço da API ou, sem editar ficheiros, abrir a
 * aplicação com ?api=https://a-tua-api.exemplo.com
 */
(function () {
    'use strict';

    // Raiz da aplicação, calculada a partir do próprio config.js.
    // Funciona na raiz do domínio e em subpastas (ex.: GitHub Pages /logitrack/).
    const scriptAtual = document.currentScript;
    const raiz = scriptAtual && scriptAtual.src
        ? scriptAtual.src.replace(/js\/config\.js(?:\?.*)?$/, '')
        : './';

    function url(caminho) {
        return raiz + String(caminho || '').replace(/^\/+/, '');
    }

    const CHAVE_SESSAO = 'logitrack.sessao';
    const CHAVE_API = 'logitrack.apiBase';
    const parametros = new URLSearchParams(window.location.search);
    const apiPedida = parametros.get('api');
    const apiGuardada = (function () {
        try {
            return window.localStorage.getItem(CHAVE_API);
        } catch (e) {
            return null;
        }
    })();
    const apiConfigurada = window.LOGITRACK_API_BASE;

    // API publicada no Railway. É usada sempre que o site não está a correr
    // no computador local (Railway, GitHub Pages, telemóvel, etc.).
    const API_PRODUCAO = 'https://api-production-938e6a7a.up.railway.app';
    const anfitrioesLocais = ['localhost', '127.0.0.1', '::1', ''];
    const emLocal = anfitrioesLocais.indexOf(window.location.hostname) !== -1;
    const apiPredefinida = emLocal ? 'http://localhost:8080' : API_PRODUCAO;

    const apiBase = String(apiPedida || apiConfigurada || apiGuardada || apiPredefinida)
        .replace(/\/+$/, '');

    if (apiPedida) {
        try {
            window.localStorage.setItem(CHAVE_API, apiBase);
        } catch (e) {
            /* armazenamento indisponível */
        }
    }

    const LT = window.LOGITRACK = window.LOGITRACK || {};
    LT.apiBase = apiBase;
    LT.root = raiz;
    LT.url = url;
    LT.storageKey = CHAVE_SESSAO;

    LT.lerSessao = function () {
        try {
            const bruto = window.localStorage.getItem(CHAVE_SESSAO);
            return bruto ? JSON.parse(bruto) : null;
        } catch (e) {
            return null;
        }
    };

    LT.guardarSessao = function (sessao) {
        window.localStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
    };

    LT.limparSessao = function () {
        window.localStorage.removeItem(CHAVE_SESSAO);
    };

    LT.rotaInicial = function (perfil) {
        return url(perfil === 'MOTORISTA' ? 'm/index.html' : 'app/dashboard.html');
    };
})();
