/* Logitrack — W3 lista de carregamentos */
(function () {
    'use strict';

    let pagina = 0;
    let totalPaginas = 0;
    let totalRegistos = 0;

    function filtros() {
        return {
            q: document.getElementById('filtroQ').value.trim(),
            estado: document.getElementById('filtroEstado').value,
            fabricaId: document.getElementById('filtroFabrica').value,
            de: document.getElementById('filtroDe').value,
            ate: document.getElementById('filtroAte').value
        };
    }

    function construirQuery() {
        const f = filtros();
        const params = new URLSearchParams();
        Object.keys(f).forEach(function (chave) {
            if (f[chave]) {
                params.set(chave, f[chave]);
            }
        });
        params.set('page', String(pagina));
        params.set('size', '15');
        params.set('sort', 'dataHoraInicio');
        params.set('direction', 'desc');
        return params.toString();
    }

    function renderizar(dados) {
        const tbody = document.getElementById('tabelaCarregamentos');
        totalPaginas = dados.totalPages;
        totalRegistos = dados.total;
        if (!dados.content.length) {
            tbody.innerHTML = '<tr><td colspan="9" class="empty">Nenhum carregamento corresponde aos filtros.</td></tr>';
        } else {
            tbody.innerHTML = dados.content.map(function (c) {
                const url = LT.urlCarregamento(c.id);
                return '<tr style="cursor:pointer" onclick="window.location.href=\''
                    + LT.escape(url) + '\'">'
                    + '<td><span class="guia">' + LT.escape(c.guiaNumero) + '</span>'
                    + '<div class="sub">UUID ' + LT.escape((c.uuidLocal || '').substring(0, 8)) + '…</div></td>'
                    + '<td>' + LT.escape(c.fabricaNome || '—') + '</td>'
                    + '<td><span class="mono">' + LT.escape(c.veiculoMatricula || '—') + '</span></td>'
                    + '<td>' + LT.escape(c.motoristaNome || '—') + '</td>'
                    + '<td>' + LT.escape(c.tipoCarga) + ' · ' + LT.fmt.numero(c.quantidadeToneladas, 1) + ' t</td>'
                    + '<td>' + LT.chip(c.estado, c.estadoEtiqueta) + '</td>'
                    + '<td>' + LT.fmt.dataHora(c.dataHoraInicio) + '</td>'
                    + '<td>' + (c.atrasoMinutos > 0
                        ? '<span style="color:#c0392b;font-weight:700">+' + c.atrasoMinutos + ' min</span>'
                        : '<span class="muted">—</span>') + '</td>'
                    + '<td><a class="btn small ghost" href="' + LT.escape(url) + '">Abrir</a></td>'
                    + '</tr>';
            }).join('');
        }

        document.getElementById('infoPagina').textContent = totalRegistos === 0
            ? 'Sem registos'
            : 'Página ' + (dados.page + 1) + ' de ' + Math.max(1, totalPaginas)
            + ' · ' + totalRegistos + ' registo(s)';
        document.getElementById('resumoLista').textContent = totalRegistos + ' carregamento(s) encontrados';
        document.getElementById('btnAnterior').disabled = dados.first;
        document.getElementById('btnSeguinte').disabled = dados.last;
    }

    async function carregar() {
        try {
            const dados = await LT.api('/api/v1/carregamentos?' + construirQuery());
            renderizar(dados);
        } catch (e) {
            LT.erro(e);
        }
    }

    async function carregarFabricas() {
        try {
            const fabricas = await LT.carregarFabricas();
            LT.preencherSelect(document.getElementById('filtroFabrica'), fabricas, 'Todas',
                'id', 'nome');
        } catch (e) {
            LT.erroReferencia('as fábricas', e);
        }
    }

    document.getElementById('btnFiltrar').addEventListener('click', function () {
        pagina = 0;
        carregar();
    });
    document.getElementById('filtroQ').addEventListener('keydown', function (ev) {
        if (ev.key === 'Enter') {
            pagina = 0;
            carregar();
        }
    });
    document.getElementById('btnLimpar').addEventListener('click', function () {
        document.getElementById('filtroQ').value = '';
        document.getElementById('filtroEstado').value = '';
        document.getElementById('filtroFabrica').value = '';
        document.getElementById('filtroDe').value = '';
        document.getElementById('filtroAte').value = '';
        pagina = 0;
        carregar();
    });
    document.getElementById('btnAnterior').addEventListener('click', function () {
        if (pagina > 0) {
            pagina -= 1;
            carregar();
        }
    });
    document.getElementById('btnSeguinte').addEventListener('click', function () {
        if (pagina + 1 < totalPaginas) {
            pagina += 1;
            carregar();
        }
    });

    carregarFabricas();
    carregar();
})();
