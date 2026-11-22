/*
 * Logitrack — shell comum do painel web.
 *
 * Injeta os ícones SVG, a barra lateral e a topbar em todas as páginas.
 * Protege as páginas que exigem sessão e a área de administração.
 */
(function () {
    'use strict';

    const CONFIG = window.LOGITRACK || {};
    const sessao = CONFIG.lerSessao ? CONFIG.lerSessao() : null;

    // Raiz da aplicação (funciona na raiz do domínio e em subpastas).
    const scriptAtual = document.currentScript;
    const BASE = scriptAtual && scriptAtual.src
        ? scriptAtual.src.replace(/js\/layout\.js(?:\?.*)?$/, '')
        : './';
    const url = function (caminho) {
        return BASE + String(caminho || '').replace(/^\/+/, '');
    };

    if (!sessao || !sessao.token) {
        window.location.replace(url('login.html?redirect='
            + encodeURIComponent(window.location.pathname + window.location.search)));
        return;
    }

    const utilizador = sessao.utilizador || {};
    const perfil = utilizador.perfil || '';
    const nome = escapar(utilizador.nome || 'Utilizador');
    const etiquetaPerfil = escapar(utilizador.perfilEtiqueta || perfil);
    const inicial = escapar(String(utilizador.nome || '?').trim().charAt(0).toUpperCase() || '?');
    const admin = perfil === 'ADMINISTRADOR';
    document.body.dataset.perfilUi = admin ? 'ADMINISTRADOR' : 'OPERADOR';
    const pagina = document.body.dataset.pagina || '';
    const titulo = escapar(document.body.dataset.titulo || 'Logitrack');
    if (document.body.dataset.pagina === 'dashboard') {
        document.addEventListener('DOMContentLoaded', function () {
            const main = document.querySelector('.content');
            if (!main || main.querySelector('.role-banner')) return;
            const banner = document.createElement('div');
            banner.className = 'role-banner';
            banner.innerHTML = '<span class="role-dot"></span><strong>Perfil: '
              + (admin ? 'Administrador' : 'Operador da Central')
              + '</strong><small>'
              + (admin ? 'Configuração, acessos e acompanhamento da operação'
                       : 'Acompanha carregamentos, veículos e alertas em tempo real')
              + '</small>';
            main.prepend(banner);
        });
    }

    const subtitulo = escapar(document.body.dataset.subtitulo || '');

    function escapar(valor) {
        return String(valor === null || valor === undefined ? '' : valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    if (perfil === 'MOTORISTA') {
        if (window.location.pathname.indexOf('/app/') >= 0) {
            window.location.replace(url('m/index.html'));
            return;
        }
        if (window.location.pathname.indexOf('/m/') === -1) {
            window.location.replace(url('m/index.html'));
            return;
        }
    }
    if (document.body.dataset.requer === 'ADMIN' && !admin) {
        window.location.replace(url('app/dashboard.html'));
        return;
    }

    const ICONES = `
<svg xmlns="http://www.w3.org/2000/svg" style="display:none">
    <symbol id="i-dashboard" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/>
        <rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>
    </symbol>
    <symbol id="i-truck" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M1 4h13v12H1z"/><path d="M14 8h4l4 4v4h-8z"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="19" r="2"/>
    </symbol>
    <symbol id="i-bell" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 8h18s-3-1-3-8"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>
    </symbol>
    <symbol id="i-report" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/>
        <path d="M8 13h8M8 17h5"/>
    </symbol>
    <symbol id="i-chart" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 3v18h18"/><path d="M7 15l4-5 3 3 5-7"/>
    </symbol>
    <symbol id="i-factory" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M2 20h20"/><path d="M4 20V9l5 3V9l5 3V4h6v16"/>
        <path d="M8 20v-4M13 20v-4M18 20v-4"/>
    </symbol>
    <symbol id="i-users" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
        <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
    </symbol>
    <symbol id="i-car" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M5 17H3v-5l2-5h14l2 5v5h-2"/><circle cx="7" cy="17" r="2"/><circle cx="17" cy="17" r="2"/>
        <path d="M9 17h6M5 12h14"/>
    </symbol>
    <symbol id="i-settings" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="3"/>
        <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.55V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.11-1.55 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.55-1H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.6 8.9a1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.55V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.55 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 19.4 9a1.7 1.7 0 0 0 1.55 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1z"/>
    </symbol>
    <symbol id="i-logout" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>
    </symbol>
    <symbol id="i-menu" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round">
        <path d="M3 6h18M3 12h18M3 18h18"/>
    </symbol>
    <symbol id="i-refresh" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/>
    </symbol>
    <symbol id="i-plus" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round">
        <path d="M12 5v14M5 12h14"/>
    </symbol>
    <symbol id="i-search" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round">
        <circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>
    </symbol>
    <symbol id="i-check" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M20 6L9 17l-5-5"/>
    </symbol>
    <symbol id="i-x" viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round">
        <path d="M18 6L6 18M6 6l12 12"/>
    </symbol>
    <symbol id="i-cloud" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 10h-1.3A7 7 0 1 0 5 16h13a4 4 0 0 0 0-8z"/>
    </symbol>
    <symbol id="i-save" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
        <path d="M17 21v-8H7v8M7 3v5h8"/>
    </symbol>
    <symbol id="i-clock" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>
    </symbol>
    <symbol id="i-package" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 2l9 5v10l-9 5-9-5V7l9-5z"/><path d="M3 7l9 5 9-5M12 12v10"/>
    </symbol>
    <symbol id="i-pin" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 10c0 7-9 12-9 12S3 17 3 10a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/>
    </symbol>
    <symbol id="i-download" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/>
    </symbol>
    <symbol id="i-eye" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z"/><circle cx="12" cy="12" r="3"/>
    </symbol>
    <symbol id="i-rotate" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M23 4v6h-6"/><path d="M20.5 15a9 9 0 1 1-2.1-9.4L23 10"/>
    </symbol>
</svg>`;

    function item(ativo, href, icone, rotulo, extra) {
        return '<a class="nav-item' + (ativo === pagina ? ' active' : '') + '" href="' + href + '">'
            + '<svg><use href="#' + icone + '"/></svg> ' + rotulo + (extra || '') + '</a>';
    }

    const adminNav = admin
        ? `<div class="nav-section">Administração</div>
           ${item('fabricas', url('app/admin/fabricas.html'), 'i-factory', 'Fábricas')}
           ${item('veiculos', url('app/admin/veiculos.html'), 'i-car', 'Veículos')}
           ${item('utilizadores', url('app/admin/utilizadores.html'), 'i-users', 'Utilizadores')}
           ${item('parametros', url('app/admin/parametros.html'), 'i-settings', 'Regras da operação')}`
        : '';

    document.body.insertAdjacentHTML('afterbegin', ICONES);

    const sidebar = document.getElementById('sidebar');
    if (sidebar) {
        sidebar.innerHTML = `
            <a class="sidebar-brand" href="${url('app/dashboard.html')}">
                <div class="brand-mark">LT</div>
                <div>
                    <div class="brand-name">Logi<span>track</span></div>
                    <div class="brand-sub">Central de Operações</div>
                </div>
            </a>
            <nav>
                <div class="nav-section">Operação</div>
                ${item('dashboard', url('app/dashboard.html'), 'i-dashboard', 'Painel de Operações')}
                ${item('carregamentos', url('app/carregamentos.html'), 'i-truck', 'Carregamentos')}
                ${item('alertas', url('app/alertas.html'), 'i-bell', 'Alertas',
                    '<span class="nav-badge hidden" id="navAlertBadge">0</span>')}
                <div class="nav-section">Análise</div>
                ${item('relatorios', url('app/relatorios.html'), 'i-report', 'Relatórios')}
                ${item('kpis', url('app/kpis.html'), 'i-chart', 'Resultados da operação')}
                ${adminNav}
            </nav>
            <div class="sidebar-footer">
                <div class="user-mini">
                    <div class="avatar orange">${inicial}</div>
                    <div class="user-mini-info">
                        <strong>${nome}</strong>
                        <span>${etiquetaPerfil}</span>
                    </div>
                    <button class="logout-link" type="button" id="btnTerminarSessao"
                            title="Terminar sessão" aria-label="Terminar sessão">
                        <svg><use href="#i-logout"/></svg>
                    </button>
                </div>
            </div>`;
    }

    const topbar = document.getElementById('topbar');
    if (topbar) {
        topbar.innerHTML = `
            <button class="menu-toggle" type="button" id="menuToggle" aria-label="Abrir menu">
                <svg><use href="#i-menu"/></svg>
            </button>
            <div class="topbar-perfil">
                <span class="topbar-etiqueta">${etiquetaPerfil}</span>
                <span class="sub">${subtitulo || 'Painel da central Logitrack'}</span>
            </div>
            <div class="topbar-spacer"></div>
            <span class="live" id="liveIndicator"><i></i> Tempo real</span>
            <div class="avatar">${inicial}</div>`;
    }

    document.title = titulo + ' · Logitrack';

    const sair = document.getElementById('btnTerminarSessao');
    if (sair) {
        sair.addEventListener('click', function () {
            CONFIG.limparSessao();
            window.location.replace(url('login.html?logout=1'));
        });
    }

    if (!document.getElementById('toasts')) {
        document.body.insertAdjacentHTML('beforeend',
            '<div class="toasts" id="toasts"></div>');
    }
})();
