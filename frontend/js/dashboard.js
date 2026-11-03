/* Logitrack — W2 Painel de Operações */
(function () {
    'use strict';

    let mapa = null;
    let camadaFabrica = null;
    let camadaVeiculos = null;
    let recentes = [];
    let alertas = [];
    let mapaDisponivel = false;

    function iniciais(nome) {
        if (!nome) {
            return '—';
        }
        return nome.split(' ').filter(Boolean).slice(0, 2)
            .map(function (p) { return p[0].toUpperCase(); }).join('');
    }

    function criacaoMapa() {
        if (mapa || !window.L) {
            return;
        }
        mapaDisponivel = true;
        mapa = L.map('map', {
            zoomControl: true,
            attributionControl: false,
            scrollWheelZoom: false
        }).setView([-19.2, 34.8], 5);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 18,
            attribution: '© OpenStreetMap'
        }).addTo(mapa);
        const fallback = document.getElementById('mapFallback');
        if (fallback) {
            fallback.style.display = 'none';
        }
        setTimeout(function () { mapa.invalidateSize(); }, 250);
    }

    function iconeFabrica() {
        return L.divIcon({
            className: '',
            html: '<div style="width:26px;height:26px;border-radius:8px;background:#f26b2e;'
                + 'display:grid;place-items:center;color:#fff;font-size:12px;font-weight:800;'
                + 'box-shadow:0 4px 12px rgba(242,107,46,.45);border:2px solid #fff">F</div>',
            iconSize: [26, 26],
            iconAnchor: [13, 13]
        });
    }

    function iconeVeiculo(atrasado) {
        const cor = atrasado ? '#c0392b' : '#2563eb';
        return L.divIcon({
            className: '',
            html: '<div style="width:28px;height:28px;border-radius:50%;background:' + cor + ';'
                + 'display:grid;place-items:center;color:#fff;font-size:12px;font-weight:800;'
                + 'box-shadow:0 4px 14px rgba(11,31,58,.35);border:2px solid #fff">'
                + '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="white"'
                + ' stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'
                + '<path d="M1 4h13v12H1z"/><path d="M14 8h4l4 4v4h-8z"/>'
                + '<circle cx="6" cy="19" r="2"/><circle cx="18" cy="19" r="2"/></svg></div>',
            iconSize: [28, 28],
            iconAnchor: [14, 14]
        });
    }

    function desenharMapa(dados) {
        criacaoMapa();
        if (!mapa) {
            return;
        }
        if (camadaFabrica) {
            camadaFabrica.remove();
        }
        if (camadaVeiculos) {
            camadaVeiculos.remove();
        }
        camadaFabrica = L.layerGroup();
        camadaVeiculos = L.layerGroup();

        (dados.fabricas || []).forEach(function (f) {
            if (!f.ativa) {
                return;
            }
            L.circle([f.latitude, f.longitude], {
                radius: f.raioGeofenceMetros,
                color: '#f26b2e',
                weight: 1.5,
                fillColor: '#f26b2e',
                fillOpacity: 0.08,
                dashArray: '5 5'
            }).addTo(camadaFabrica);
            L.marker([f.latitude, f.longitude], {icon: iconeFabrica(), title: f.nome})
                .bindPopup('<strong>' + LT.escape(f.nome) + '</strong><br>'
                    + LT.escape(f.endereco) + '<br><span style="color:#6b7a90">Geofence: '
                    + f.raioGeofenceMetros + ' m</span>')
                .addTo(camadaFabrica);
        });

        (dados.veiculos || []).forEach(function (v) {
            const atrasado = String(v.estado).toLowerCase().indexOf('atras') >= 0;
            L.marker([v.latitude, v.longitude], {icon: iconeVeiculo(atrasado), title: v.matricula})
                .bindPopup('<strong>' + LT.escape(v.guiaNumero) + '</strong><br>'
                    + LT.escape(v.matricula) + ' · ' + LT.escape(v.motorista || '—') + '<br>'
                    + '<span style="color:#6b7a90">' + LT.escape(v.fabrica || '') + '</span><br>'
                    + 'Precisão: ' + LT.fmt.numero(v.precisaoMetros, 1) + ' m<br>'
                    + '<a href="' + LT.urlCarregamento(v.carregamentoId) + '">Abrir carregamento</a>')
                .addTo(camadaVeiculos);
        });

        camadaFabrica.addTo(mapa);
        camadaVeiculos.addTo(mapa);

        const todos = (dados.fabricas || []).map(function (f) {
            return [f.latitude, f.longitude];
        }).concat((dados.veiculos || []).map(function (v) {
            return [v.latitude, v.longitude];
        }));
        if (todos.length) {
            mapa.fitBounds(todos, {padding: [42, 42], maxZoom: 8});
        }
        const chip = document.getElementById('chipVeiculos');
        if (chip) {
            chip.textContent = (dados.veiculos || []).length + ' em rota';
        }
    }

    function renderizarKpis(kpis) {
        document.getElementById('kpiTransito').textContent = kpis.emTransito;
        document.getElementById('kpiAtrasados').textContent = kpis.atrasados;
        document.getElementById('kpiConcluidos').textContent = kpis.concluidosHoje;
        document.getElementById('kpiTempo').innerHTML = LT.fmt.numero(kpis.tempoMedioEsperaHoras, 1)
            + '<small>h</small>';
    }

    function renderizarAlertas(lista) {
        const contentor = document.getElementById('listaAlertas');
        const contador = document.getElementById('alertasCount');
        if (contador) {
            contador.textContent = lista.filter(function (a) { return !a.lido; }).length;
        }
        if (!lista.length) {
            contentor.innerHTML = '<div class="empty"><svg><use href="#i-bell"/></svg><br>'
                + 'Sem alertas de atraso. A operação está dentro do SLA.</div>';
            return;
        }
        contentor.innerHTML = lista.slice(0, 14).map(function (a) {
            return '<article class="alert-item ' + (a.lido ? 'read' : '')
                + '" data-id="' + a.id + '" data-carregamento="' + a.carregamentoId + '">'
                + '<div class="a-top"><span class="a-guia">' + LT.escape(a.guiaNumero || '—')
                + '</span><span class="a-time">+' + (a.minutosExcedidos || 0) + ' min</span></div>'
                + '<p>' + LT.escape(a.mensagem || '') + '</p>'
                + '<div class="a-meta">' + LT.escape(a.fabrica || '—') + ' · '
                + LT.escape(a.motorista || '—') + ' · ' + LT.fmt.relativo(a.dataHoraEmissao)
                + '</div></article>';
        }).join('');
        contentor.querySelectorAll('.alert-item').forEach(function (el) {
            el.addEventListener('click', function () {
                const id = el.dataset.carregamento;
                if (id) {
                    window.location.href = LT.urlCarregamento(id);
                }
            });
        });
    }

    function renderizarRecentes() {
        const tbody = document.getElementById('tabelaRecentes');
        const termo = (document.getElementById('pesquisaRecentes').value || '').toLowerCase().trim();
        const dados = recentes.filter(function (c) {
            if (!termo) {
                return true;
            }
            return [c.guiaNumero, c.fabricaNome, c.veiculoMatricula, c.motoristaNome, c.estadoEtiqueta]
                .join(' ').toLowerCase().indexOf(termo) >= 0;
        });
        if (!dados.length) {
            tbody.innerHTML = '<tr><td colspan="8" class="empty">Nenhum carregamento encontrado.</td></tr>';
            return;
        }
        tbody.innerHTML = dados.map(function (c) {
            return '<tr>'
                + '<td><a class="guia" href="' + LT.urlCarregamento(c.id) + '">'
                + LT.escape(c.guiaNumero) + '</a>'
                + '<div class="sub">' + LT.escape(c.tipoCarga) + ' · '
                + LT.fmt.numero(c.quantidadeToneladas, 1) + ' t</div></td>'
                + '<td>' + LT.escape(c.fabricaNome || '—') + '</td>'
                + '<td><span class="mono">' + LT.escape(c.veiculoMatricula || '—') + '</span></td>'
                + '<td>' + LT.escape(c.motoristaNome || '—') + '</td>'
                + '<td>' + LT.chip(c.estado, c.estadoEtiqueta) + '</td>'
                + '<td>' + LT.fmt.dataHora(c.dataHoraInicio) + '</td>'
                + '<td>' + (c.atrasoMinutos > 0
                    ? '<span style="color:#c0392b;font-weight:700">+' + c.atrasoMinutos + ' min</span>'
                    : '<span class="muted">—</span>') + '</td>'
                + '<td><a class="btn small ghost" href="' + LT.urlCarregamento(c.id) + '">'
                + 'Abrir</a></td>'
                + '</tr>';
        }).join('');
    }

    async function carregar() {
        const botao = document.getElementById('btnAtualizar');
        if (botao) {
            botao.disabled = true;
        }
        try {
            const dados = await LT.api('/api/v1/dashboard');
            recentes = dados.recentes || [];
            alertas = dados.alertas || [];
            renderizarKpis(dados.kpis);
            renderizarAlertas(alertas);
            renderizarRecentes();
            desenharMapa(dados);
            const atualizado = document.getElementById('atualizadoEm');
            if (atualizado) {
                atualizado.textContent = 'Atualizado ' + LT.fmt.relativo(dados.atualizadoEm) + '.';
            }
        } catch (e) {
            LT.erro(e);
        } finally {
            if (botao) {
                botao.disabled = false;
            }
        }
    }

    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('pesquisaRecentes').addEventListener('input',
        LT.debounce(renderizarRecentes, 180));

    LT.on('alerta', function (alerta) {
        alertas.unshift(alerta);
        renderizarAlertas(alertas);
        const kpi = document.getElementById('kpiAtrasados');
        if (kpi) {
            kpi.textContent = String((parseInt(kpi.textContent, 10) || 0) + 1);
        }
    });

    LT.on('carregamento', function () {
        setTimeout(carregar, 800);
    });

    carregar();
    setInterval(carregar, 60000);
})();
