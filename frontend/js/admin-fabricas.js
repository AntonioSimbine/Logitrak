/* Logitrack — W7 gestão de fábricas e geofences */
(function () {
    'use strict';

    let fabricas = [];
    let mapa = null;
    let marcador = null;
    let circulo = null;

    function iniciais(nome) {
        return String(nome || '?').trim().charAt(0).toUpperCase() || '?';
    }

    function garantirMapa() {
        if (mapa || !window.L) {
            return mapa;
        }
        mapa = window.L.map('mapaFabrica', {
            zoomControl: true,
            attributionControl: true
        }).setView([-19.84, 34.84], 5);

        window.L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; OpenStreetMap'
        }).addTo(mapa);

        mapa.on('click', function (ev) {
            definirCoordenadas(ev.latlng.lat, ev.latlng.lng);
        });
        return mapa;
    }

    function definirCoordenadas(lat, lng) {
        document.getElementById('latitudeFabrica').value = Number(lat).toFixed(6);
        document.getElementById('longitudeFabrica').value = Number(lng).toFixed(6);
        if (!mapa) {
            return;
        }
        if (!marcador) {
            marcador = window.L.marker([lat, lng], {draggable: true}).addTo(mapa);
            marcador.on('dragend', function () {
                const posicao = marcador.getLatLng();
                definirCoordenadas(posicao.lat, posicao.lng);
            });
        } else {
            marcador.setLatLng([lat, lng]);
        }
        const raio = Number(document.getElementById('raioFabrica').value) || 500;
        if (!circulo) {
            circulo = window.L.circle([lat, lng], {
                radius: raio,
                color: '#f26b2e',
                fillColor: '#f26b2e',
                fillOpacity: .12,
                weight: 2
            }).addTo(mapa);
        } else {
            circulo.setLatLng([lat, lng]);
            circulo.setRadius(raio);
        }
    }

    function ajustarMapa(lat, lng, zoom) {
        const m = garantirMapa();
        if (!m) {
            return;
        }
        setTimeout(function () {
            m.invalidateSize();
            if (lat !== null && lng !== null && !isNaN(lat) && !isNaN(lng)) {
                m.setView([lat, lng], zoom || 14);
                definirCoordenadas(lat, lng);
            } else {
                m.setView([-19.84, 34.84], 5);
                if (marcador) {
                    m.removeLayer(marcador);
                    marcador = null;
                }
                if (circulo) {
                    m.removeLayer(circulo);
                    circulo = null;
                }
            }
        }, 140);
    }

    function limparMapa() {
        if (marcador) {
            mapa.removeLayer(marcador);
            marcador = null;
        }
        if (circulo) {
            mapa.removeLayer(circulo);
            circulo = null;
        }
    }

    function renderizar() {
        const termo = (document.getElementById('pesquisaFabricas').value || '')
            .trim().toLowerCase();
        const lista = fabricas.filter(function (f) {
            if (!termo) {
                return true;
            }
            return (f.nome + ' ' + f.endereco).toLowerCase().includes(termo);
        });
        const tbody = document.getElementById('tabelaFabricas');
        document.getElementById('totalFabricas').textContent = fabricas.length;

        if (!lista.length) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty">'
                + (fabricas.length ? 'Nenhuma fábrica corresponde à pesquisa.' : 'Ainda não existem fábricas registadas.')
                + '</td></tr>';
            return;
        }

        tbody.innerHTML = lista.map(function (f) {
            const ativa = f.ativa !== false;
            return '<tr>'
                + '<td><div style="display:flex;align-items:center;gap:10px">'
                + '<div class="avatar" style="width:34px;height:34px;font-size:13px">'
                + LT.escape(iniciais(f.nome)) + '</div>'
                + '<div><strong>' + LT.escape(f.nome) + '</strong>'
                + '<div class="sub">' + LT.escape(f.endereco) + '</div></div></div></td>'
                + '<td><span class="mono small">' + LT.fmt.numero(f.latitude, 5) + ', '
                + LT.fmt.numero(f.longitude, 5) + '</span></td>'
                + '<td>' + LT.fmt.numero(f.raioGeofenceMetros, 0) + ' m</td>'
                + '<td><strong>' + LT.fmt.numero(f.slaLimiteHoras, 0) + ' h</strong></td>'
                + '<td>' + LT.escape(f.horarioAcostagem || '—') + '</td>'
                + '<td>' + (ativa
                    ? '<span class="chip no">Ativa</span>'
                    : '<span class="chip sim">Inativa</span>') + '</td>'
                + '<td><div class="table-actions">'
                + '<button class="btn small ghost" type="button" data-acao="editar" data-id="' + f.id + '">Editar</button>'
                + (ativa
                    ? '<button class="btn small ghost" type="button" data-acao="eliminar" data-id="' + f.id + '">Eliminar</button>'
                    : '<button class="btn small ghost" type="button" data-acao="reativar" data-id="' + f.id + '">Reativar</button>')
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    async function carregar() {
        const botao = document.getElementById('btnAtualizar');
        botao.disabled = true;
        try {
            fabricas = await LT.api('/api/v1/fabricas');
            renderizar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    function abrirNova() {
        const form = document.getElementById('formFabrica');
        form.reset();
        document.getElementById('fabricaId').value = '';
        document.getElementById('tituloModalFabrica').textContent = 'Nova fábrica';
        document.getElementById('latitudeFabrica').value = '';
        document.getElementById('longitudeFabrica').value = '';
        document.getElementById('raioFabrica').value = '100';
        document.getElementById('slaFabrica').value = '24';
        document.getElementById('ativaFabrica').value = 'true';
        LT.limparFormulario(form);
        LT.abrirModal('modalFabrica');
        ajustarMapa(null, null);
    }

    function abrirEdicao(id) {
        const f = fabricas.find(function (item) { return item.id === id; });
        if (!f) {
            return;
        }
        document.getElementById('fabricaId').value = f.id;
        document.getElementById('tituloModalFabrica').textContent = 'Editar ' + f.nome;
        document.getElementById('nomeFabrica').value = f.nome || '';
        document.getElementById('enderecoFabrica').value = f.endereco || '';
        document.getElementById('latitudeFabrica').value = f.latitude;
        document.getElementById('longitudeFabrica').value = f.longitude;
        document.getElementById('raioFabrica').value = f.raioGeofenceMetros;
        document.getElementById('slaFabrica').value = f.slaLimiteHoras;
        document.getElementById('horarioFabrica').value = f.horarioAcostagem || '';
        document.getElementById('ativaFabrica').value = f.ativa === false ? 'false' : 'true';
        LT.abrirModal('modalFabrica');
        ajustarMapa(f.latitude, f.longitude, 14);
    }

    async function guardar(ev) {
        ev.preventDefault();
        const id = document.getElementById('fabricaId').value;
        const pedido = {
            nome: document.getElementById('nomeFabrica').value.trim(),
            endereco: document.getElementById('enderecoFabrica').value.trim(),
            latitude: Number(document.getElementById('latitudeFabrica').value),
            longitude: Number(document.getElementById('longitudeFabrica').value),
            raioGeofenceMetros: Number(document.getElementById('raioFabrica').value),
            slaLimiteHoras: Number(document.getElementById('slaFabrica').value),
            horarioAcostagem: document.getElementById('horarioFabrica').value.trim(),
            ativa: document.getElementById('ativaFabrica').value === 'true'
        };
        const botao = document.getElementById('btnGuardarFabrica');
        botao.disabled = true;
        try {
            if (id) {
                await LT.api('/api/v1/fabricas/' + id, {method: 'PUT', body: pedido});
            } else {
                await LT.api('/api/v1/fabricas', {method: 'POST', body: pedido});
            }
            LT.fecharModal('modalFabrica');
            LT.toast('Fábrica guardada',
                pedido.nome + ' foi gravada com sucesso.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    async function eliminar(id) {
        const f = fabricas.find(function (item) { return item.id === id; });
        if (!f || !window.confirm('Retirar "' + f.nome
            + '" das unidades disponíveis na aplicação móvel? O histórico será preservado.')) {
            return;
        }
        try {
            await LT.api('/api/v1/fabricas/' + id, {method: 'DELETE'});
            LT.toast('Unidade retirada', f.nome + ' já não estará disponível na aplicação móvel.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    }

    async function reativar(id) {
        const f = fabricas.find(function (item) { return item.id === id; });
        if (!f) return;
        const pedido = {
            nome: f.nome, endereco: f.endereco, latitude: f.latitude,
            longitude: f.longitude, raioGeofenceMetros: f.raioGeofenceMetros,
            slaLimiteHoras: f.slaLimiteHoras, horarioAcostagem: f.horarioAcostagem,
            ativa: true
        };
        try {
            await LT.api('/api/v1/fabricas/' + id, {method: 'PUT', body: pedido});
            LT.toast('Unidade reativada', f.nome + ' voltou a estar disponível.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    }

    document.getElementById('btnNovaFabrica').addEventListener('click', abrirNova);
    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('formFabrica').addEventListener('submit', guardar);
    document.getElementById('pesquisaFabricas').addEventListener('input', renderizar);
    document.getElementById('raioFabrica').addEventListener('input', function () {
        if (circulo) {
            circulo.setRadius(Number(this.value) || 10);
        }
    });
    document.getElementById('latitudeFabrica').addEventListener('change', sincronizarMapa);
    document.getElementById('longitudeFabrica').addEventListener('change', sincronizarMapa);

    function sincronizarMapa() {
        const lat = Number(document.getElementById('latitudeFabrica').value);
        const lng = Number(document.getElementById('longitudeFabrica').value);
        if (!isNaN(lat) && !isNaN(lng) && document.getElementById('latitudeFabrica').value
            && document.getElementById('longitudeFabrica').value) {
            garantirMapa();
            definirCoordenadas(lat, lng);
        }
    }

    document.querySelectorAll('[data-fechar]').forEach(function (botao) {
        botao.addEventListener('click', function () {
            LT.fecharModal(botao.dataset.fechar);
            limparMapa();
        });
    });

    document.getElementById('tabelaFabricas').addEventListener('click', function (ev) {
        const botao = ev.target.closest('button[data-acao]');
        if (!botao) {
            return;
        }
        const id = Number(botao.dataset.id);
        if (botao.dataset.acao === 'editar') {
            abrirEdicao(id);
        } else if (botao.dataset.acao === 'eliminar') {
            eliminar(id);
        } else if (botao.dataset.acao === 'reativar') {
            reativar(id);
        }
    });

    carregar();
})();
