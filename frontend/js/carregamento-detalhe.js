/* Logitrack — W3 detalhe do carregamento e guia */
(function () {
    'use strict';

    const id = LT.query('id');
    let detalhe = null;
    let mapa = null;
    let camada = null;
    let rotacao = 0;
    let guiaUrl = null;

    async function carregarGuia() {
        if (guiaUrl) {
            LT.libertarUrl(guiaUrl);
            guiaUrl = null;
        }
        if (!detalhe || !detalhe.documento) {
            return null;
        }
        guiaUrl = await LT.ficheiroProtegido(
            '/api/v1/documentos/' + detalhe.documento.id + '/ficheiro');
        return guiaUrl;
    }

    function MinutosFase(fase) {
        const fases = detalhe && detalhe.fases;
        if (!fases) {
            return 'sem dados';
        }
        const minutos = [fases.esperaMinutos, fases.carregamentoMinutos,
            fases.transporteMinutos][fase];
        return minutos === null || minutos === undefined
            ? 'sem dados' : minutos + ' min';
    }

    function valorInputLocal(valor) {
        return valor ? String(valor).slice(0, 16) : '';
    }

    function renderizarFases() {
        const cartao = document.getElementById('cardFases');
        if (!cartao) {
            return;
        }
        const podeEditar = LT.perfil === 'ADMINISTRADOR'
            || LT.perfil === 'OPERADOR_CENTRAL';
        cartao.classList.toggle('hidden', !podeEditar);
        if (!podeEditar) {
            return;
        }
        const fases = detalhe.fases || {};
        document.getElementById('faseInicioCarregamento').value =
            valorInputLocal(fases.inicioCarregamento);
        document.getElementById('faseFimCarregamento').value =
            valorInputLocal(fases.fimCarregamento);
        document.getElementById('faseInicioTransporte').value =
            valorInputLocal(fases.inicioTransporte);
        document.getElementById('faseFimTransporte').value =
            valorInputLocal(fases.fimTransporte);
    }

    async function guardarFases() {
        const botao = document.getElementById('btnGuardarFases');
        const corpo = {};
        [
            ['faseInicioCarregamento', 'dataHoraInicioCarregamento'],
            ['faseFimCarregamento', 'dataHoraFimCarregamento'],
            ['faseInicioTransporte', 'dataHoraInicioTransporte'],
            ['faseFimTransporte', 'dataHoraFimTransporte']
        ].forEach(function (par) {
            const valor = document.getElementById(par[0]).value;
            if (valor) {
                corpo[par[1]] = valor;
            }
        });
        if (!Object.keys(corpo).length) {
            LT.toast('Sem alterações', 'Indique pelo menos um instante operacional.', 'info');
            return;
        }
        botao.disabled = true;
        try {
            await LT.api('/api/v1/carregamentos/' + id + '/fases', {
                method: 'PATCH',
                body: corpo
            });
            LT.toast('Fases guardadas', 'Os instantes operacionais foram atualizados.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    function renderizarCabecalho() {
        document.getElementById('tituloGuia').textContent = 'Guia ' + detalhe.guiaNumero;
        document.getElementById('chipEstado').innerHTML = LT.chip(detalhe.estado, detalhe.estadoEtiqueta);
        document.getElementById('subtituloDetalhe').textContent =
            'Registado em ' + LT.fmt.dataHora(detalhe.dataHoraInicio)
            + ' · última atualização ' + LT.fmt.relativo(detalhe.dataHoraFim || detalhe.dataHoraInicio);

        document.getElementById('infoFabrica').textContent = detalhe.fabrica
            ? detalhe.fabrica.nome : '—';
        document.getElementById('infoFabricaEndereco').textContent = detalhe.fabrica
            ? detalhe.fabrica.endereco : 'Sem fábrica associada';
        document.getElementById('infoVeiculo').textContent = detalhe.veiculo
            ? detalhe.veiculo.matricula : '—';
        document.getElementById('infoMotorista').textContent = detalhe.motorista
            ? detalhe.motorista.nome : '—';
        document.getElementById('infoCarga').textContent = detalhe.tipoCarga;
        document.getElementById('infoToneladas').textContent =
            LT.fmt.numero(detalhe.quantidadeToneladas, 2) + ' toneladas';
        document.getElementById('infoLimite').textContent = detalhe.horaLimite
            ? LT.fmt.dataHora(detalhe.horaLimite) : '—';
        document.getElementById('infoAtraso').textContent = detalhe.atrasoMinutos > 0
            ? '+' + detalhe.atrasoMinutos + ' min além do SLA'
            : 'Dentro do prazo de SLA';

        const acoes = document.getElementById('acoesEstado');
        if (detalhe.estado === 'EM_TRANSITO' || detalhe.estado === 'ATRASADO') {
            acoes.innerHTML = '<button class="btn danger" id="btnRejeitar">Rejeitar</button>'
                + '<button class="btn green" id="btnConcluir">'
                + '<svg><use href="#i-check"/></svg> Concluir carregamento</button>';
            document.getElementById('btnConcluir').addEventListener('click', concluir);
            document.getElementById('btnRejeitar').addEventListener('click', function () {
                LT.abrirModal('modalRejeitar');
            });
        } else if (detalhe.estado === 'REGISTADO') {
            acoes.innerHTML = '<button class="btn danger" id="btnRejeitar">Rejeitar</button>';
            document.getElementById('btnRejeitar').addEventListener('click', function () {
                LT.abrirModal('modalRejeitar');
            });
        } else {
            acoes.innerHTML = '';
        }
    }

    async function renderizarGuia() {
        const viewer = document.getElementById('guideViewer');
        const tools = document.getElementById('guideTools');
        const hash = document.getElementById('guideHash');
        if (!detalhe.documento) {
            viewer.innerHTML = '<div class="empty"><svg><use href="#i-report"/></svg><br>'
                + 'Este carregamento ainda não tem guia digitalizada.</div>';
            tools.innerHTML = '';
            hash.textContent = '';
            document.getElementById('guideIntegridade').textContent = 'Sem documento';
            document.getElementById('guideSub').textContent = 'Nenhuma captura associada';
            return;
        }
        const doc = detalhe.documento;
        let url;
        try {
            url = await carregarGuia();
        } catch (e) {
            viewer.innerHTML = '<div class="empty"><svg><use href="#i-report"/></svg><br>'
                + 'Não foi possível carregar a guia digitalizada.</div>';
            tools.innerHTML = '';
            hash.textContent = '';
            document.getElementById('guideIntegridade').textContent = 'Documento indisponível';
            document.getElementById('guideSub').textContent = e.message;
            return;
        }
        viewer.innerHTML = '<img id="imgGuia" src="' + url + '" alt="Guia de transporte '
            + LT.escape(detalhe.guiaNumero) + '">';
        tools.innerHTML = '<button class="btn small" id="btnAmpliar">'
            + '<svg><use href="#i-eye"/></svg> Ampliar</button>'
            + '<button class="btn small" id="btnGirar">'
            + '<svg><use href="#i-rotate"/></svg> Girar</button>'
            + '<a class="btn small" id="btnDescarregar" href="' + url + '" download>'
            + '<svg><use href="#i-download"/></svg> Descarregar</a>';
        document.getElementById('guideSub').textContent = 'Capturada em '
            + LT.fmt.dataHora(doc.dataHoraCaptura) + ' · '
            + (doc.tamanhoBytes ? (doc.tamanhoBytes / 1024).toFixed(0) + ' KB' : '—');
        document.getElementById('guideIntegridade').textContent =
            doc.mimeType === 'image/svg+xml' ? 'Guia de demonstração' : 'SHA-256 verificado';
        hash.innerHTML = '<strong>Hash SHA-256:</strong> ' + LT.escape(doc.hashSha256 || '—');

        document.getElementById('btnAmpliar').addEventListener('click', function () {
            const img = document.getElementById('lightboxImg');
            img.src = url;
            img.style.transform = 'rotate(' + rotacao + 'deg)';
            document.getElementById('lightbox').classList.add('open');
        });
        document.getElementById('btnGirar').addEventListener('click', function () {
            rotacao = (rotacao + 90) % 360;
            document.getElementById('imgGuia').style.transform = 'rotate(' + rotacao + 'deg)';
        });
    }

    function renderizarTimeline() {
        const contentor = document.getElementById('timeline');
        contentor.innerHTML = (detalhe.timeline || []).map(function (etapa) {
            return '<div class="tl-item ' + (etapa.concluida ? 'done' : '') + '">'
                + '<div class="tl-icon"><svg><use href="#i-'
                + (etapa.icone === 'save' ? 'save'
                    : etapa.icone === 'cloud' ? 'cloud'
                        : etapa.icone === 'clock' ? 'clock'
                            : etapa.icone === 'package' ? 'package'
                                : etapa.icone === 'truck' ? 'truck'
                                    : etapa.icone === 'check' ? 'check' : 'x')
                + '"/></svg></div>'
                + '<div class="tl-body"><strong>' + LT.escape(etapa.titulo) + '</strong>'
                + '<span>' + (etapa.dataHora ? LT.fmt.dataHora(etapa.dataHora)
                    : (etapa.concluida ? 'Concluída' : 'A aguardar')) + '</span></div></div>';
        }).join('');
    }

    function renderizarInfo() {
        const itens = [
            ['Número da guia', detalhe.guiaNumero],
            ['UUID local', detalhe.uuidLocal || '—'],
            ['Tipo de carga', detalhe.tipoCarga],
            ['Quantidade', LT.fmt.numero(detalhe.quantidadeToneladas, 2) + ' t'],
            ['Início', LT.fmt.dataHora(detalhe.dataHoraInicio)],
            ['Fim', LT.fmt.dataHora(detalhe.dataHoraFim)],
            ['Espera na fábrica', MinutosFase(0)],
            ['Carregamento', MinutosFase(1)],
            ['Transporte', MinutosFase(2)],
            ['Horário de acostagem', detalhe.fabrica ? detalhe.fabrica.horarioAcostagem : '—'],
            ['Raio de geofence', detalhe.fabrica
                ? detalhe.fabrica.raioGeofenceMetros + ' m' : '—'],
            ['SLA da fábrica', detalhe.fabrica
                ? detalhe.fabrica.slaLimiteHoras + ' horas' : '—'],
            ['Alertas abertos', String(detalhe.alertasAbertos || 0)]
        ];
        if (detalhe.motivoRejeicao) {
            itens.push(['Motivo de rejeição', detalhe.motivoRejeicao]);
        }
        document.getElementById('infoDetalhada').innerHTML = itens.map(function (item) {
            return '<div class="info-item"><div class="lbl">' + LT.escape(item[0])
                + '</div><div class="val">' + LT.escape(item[1]) + '</div></div>';
        }).join('');
    }

    function renderizarMapa() {
        const contentor = document.getElementById('mapDetalhe');
        const resumo = document.getElementById('resumoGps');
        if (!window.L) {
            contentor.innerHTML = '<div class="map-fallback"><div>Mapa indisponível sem ligação.</div></div>';
        } else {
            if (!mapa) {
                mapa = L.map('mapDetalhe', {
                    zoomControl: true, attributionControl: false, scrollWheelZoom: false
                }).setView([-19.2, 34.8], 5);
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 18})
                    .addTo(mapa);
            }
            if (camada) {
                camada.remove();
            }
            camada = L.layerGroup();
            const pontos = [];
            if (detalhe.fabrica) {
                const f = detalhe.fabrica;
                L.circle([f.latitude, f.longitude], {
                    radius: f.raioGeofenceMetros, color: '#f26b2e', weight: 1.5,
                    fillColor: '#f26b2e', fillOpacity: .08, dashArray: '5 5'
                }).addTo(camada);
                L.marker([f.latitude, f.longitude])
                    .bindPopup('<strong>' + LT.escape(f.nome) + '</strong><br>Fábrica de origem')
                    .addTo(camada);
                pontos.push([f.latitude, f.longitude]);
            }
            const posicoes = detalhe.posicoes || [];
            if (posicoes.length) {
                const linha = posicoes.map(function (p) { return [p.latitude, p.longitude]; });
                L.polyline(linha, {color: '#2563eb', weight: 3, opacity: .75}).addTo(camada);
                posicoes.forEach(function (p, indice) {
                    const ultima = indice === posicoes.length - 1;
                    L.circleMarker([p.latitude, p.longitude], {
                        radius: ultima ? 7 : 4,
                        color: ultima ? '#c0392b' : '#2563eb',
                        fillColor: ultima ? '#c0392b' : '#2563eb',
                        fillOpacity: .9, weight: 2
                    }).bindPopup('Ponto ' + (indice + 1) + '<br>' + LT.fmt.dataHora(p.timestamp)
                        + '<br>Precisão: ' + LT.fmt.numero(p.precisaoMetros, 1) + ' m')
                        .addTo(camada);
                    pontos.push([p.latitude, p.longitude]);
                });
            }
            camada.addTo(mapa);
            if (pontos.length) {
                mapa.fitBounds(pontos, {padding: [28, 28], maxZoom: 13});
            }
            setTimeout(function () { mapa.invalidateSize(); }, 220);
        }

        const ultima = detalhe.ultimaPosicao;
        resumo.innerHTML = ultima
            ? '<span class="chip em_transito">Última: ' + LT.fmt.dataHora(ultima.timestamp) + '</span>'
            + '<span class="chip">Lat ' + LT.fmt.numero(ultima.latitude, 5) + '</span>'
            + '<span class="chip">Lon ' + LT.fmt.numero(ultima.longitude, 5) + '</span>'
            + '<span class="chip ' + (ultima.precisaoMetros <= 15 ? 'concluido' : 'atrasado') + '">'
            + 'Precisão ' + LT.fmt.numero(ultima.precisaoMetros, 1) + ' m</span>'
            : '<span class="muted small">Sem pontos GPS registados.</span>';
    }

    async function concluir() {
        if (!window.confirm('Confirmar a conclusão do carregamento ' + detalhe.guiaNumero + '?')) {
            return;
        }
        try {
            await LT.api('/api/v1/carregamentos/' + id + '/estado', {
                method: 'PATCH',
                body: {estado: 'CONCLUIDO'}
            });
            LT.toast('Carregamento concluído', 'O estado foi atualizado para Concluído.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    }

    document.getElementById('btnGuardarFases').addEventListener('click', guardarFases);

    document.getElementById('formRejeitar').addEventListener('submit', async function (ev) {
        ev.preventDefault();
        const motivo = document.getElementById('motivoRejeicao').value.trim();
        if (!motivo) {
            return;
        }
        try {
            await LT.api('/api/v1/carregamentos/' + id + '/estado', {
                method: 'PATCH',
                body: {estado: 'REJEITADO', motivoRejeicao: motivo}
            });
            LT.fecharModal('modalRejeitar');
            LT.toast('Carregamento rejeitado', 'O motivo foi registado.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    });

    async function carregar() {
        try {
            detalhe = await LT.api('/api/v1/carregamentos/' + id);
            rotacao = 0;
            renderizarCabecalho();
            await renderizarGuia();
            renderizarTimeline();
            renderizarInfo();
            renderizarFases();
            renderizarMapa();
        } catch (e) {
            LT.erro(e);
            document.getElementById('subtituloDetalhe').textContent = e.message;
        }
    }

    if (!id) {
        LT.toast('Carregamento inválido', 'Não foi indicado um identificador.', 'error');
    } else {
        carregar();
    }
})();
