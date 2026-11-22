/* Complementos visuais não invasivos. Não substitui os controladores existentes. */
(function () {
    'use strict';

    const rotulos = {
        dashboard: 'Centro de controlo',
        carregamentos: 'Gestão de carregamentos',
        'carregamento-detalhe': 'Detalhes do carregamento',
        alertas: 'Central de alertas',
        kpis: 'Indicadores de desempenho',
        relatorios: 'Relatórios operacionais',
        utilizadores: 'Gestão de utilizadores',
        veiculos: 'Gestão da frota',
        fabricas: 'Gestão de fábricas',
        parametros: 'Parâmetros do sistema'
    };

    function render() {
        const main = document.querySelector('.content');
        if (!main || main.querySelector('.lt-breadcrumb')) return;

        const chave = document.body.dataset.pagina
            || location.pathname.split('/').pop().replace('.html', '');
        const rotulo = rotulos[chave];
        if (!rotulo) return;

        const perfil = document.body.dataset.perfilUi === 'ADMINISTRADOR'
            ? 'Administrador' : 'Operador';

        // Um único título principal por página: o .page-head do HTML.
        // Aqui apenas se mantém o contexto de navegação e o rodapé.
        const navegacao = document.createElement('nav');
        navegacao.className = 'lt-breadcrumb';
        navegacao.setAttribute('aria-label', 'Caminho de navegação');
        const ligacao = document.createElement('a');
        ligacao.href = window.LOGITRACK && window.LOGITRACK.url
            ? window.LOGITRACK.url('app/dashboard.html') : 'app/dashboard.html';
        ligacao.textContent = 'Logitrack';
        const separador = document.createElement('span');
        separador.setAttribute('aria-hidden', 'true');
        separador.textContent = ' / ';
        const atual = document.createElement('span');
        atual.textContent = rotulo;
        navegacao.append(ligacao, separador, atual);

        const banner = main.querySelector('.role-banner');
        if (banner) banner.after(navegacao);
        else main.prepend(navegacao);

        const rodape = document.createElement('footer');
        rodape.className = 'lt-footer';
        rodape.innerHTML = '<span>Logitrack · Gestão e Rastreabilidade de Carregamentos</span>'
            + '<span>Ambiente web · ' + perfil + '</span>';
        main.append(rodape);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', render);
    } else {
        render();
    }
})();
