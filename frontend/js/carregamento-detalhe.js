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
            return '—';
        }
        const minutos = [fases.esperaMinutos, fases.carregamentoMinutos,
            fases.transporteMinutos][fase];
        return minutos === null || minutos === undefined
            ? '—' : LT.fmt.duracao(minutos);
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
            LT.toast('Sem alterações', 'Preencha pelo menos um horário.', 'info');
            return;
        }
        botao.disabled = true;
        try {
            await LT.api('/api/v1/carregamentos/' + id + '/fases', {
                method: 'PATCH',
                body: corpo
            });
            LT.toast('Horários guardados', 'Os horários foram atualizados.', 'success');
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
            + ' · última alteração ' + LT.fmt.relativo(detalhe.dataHoraFim || detalhe.dataHoraInicio);

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
            ? '+' + detalhe.atrasoMinutos + ' min além do prazo'
            : 'Dentro do prazo';

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
                + 'Este carregamento ainda não tem fotografia da guia.</div>';
            tools.innerHTML = '';
            hash.textContent = '';
            document.getElementById('guideIntegridade').textContent = 'Sem fotografia';
            document.getElementById('guideSub').textContent = 'Ainda não foi tirada nenhuma fotografia';
            return;
        }
        const doc = detalhe.documento;
        let url;
        try {
            url = await carregarGuia();
        } catch (e) {
            viewer.innerHTML = '<div class="empty"><svg><use href="#i-report"/></svg><br>'
                + 'Não foi possível abrir a fotografia da guia. Verifique a ligação e tente novamente.</div>';
            tools.innerHTML = '';
            hash.textContent = '';
            document.getElementById('guideIntegridade').textContent = 'Fotografia indisponível';
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
        document.getElementById('guideSub').textContent = 'Fotografada em '
            + LT.fmt.dataHora(doc.dataHoraCaptura) + ' · '
            + (doc.tamanhoBytes ? (doc.tamanhoBytes / 1024).toFixed(0) + ' KB' : '—');
        document.getElementById('guideIntegridade').textContent =
            doc.mimeType === 'image/svg+xml' ? 'Guia de exemplo' : 'Fotografia verificada';
        hash.innerHTML = '';

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
                    : (etapa.concluida ? 'Concluída' : 'Por fazer')) + '</span></div></div>';
        }).join('');
    }

    function renderizarInfo() {
        const itens = [
            ['Número da guia', detalhe.guiaNumero],
            ['Tipo de carga', detalhe.tipoCarga],
            ['Quantidade', LT.fmt.numero(detalhe.quantidadeToneladas, 2) + ' t'],
            ['Início', LT.fmt.dataHora(detalhe.dataHoraInicio)],
            ['Fim', LT.fmt.dataHora(detalhe.dataHoraFim)],
            ['Espera na fábrica', MinutosFase(0)],
            ['Carregamento', MinutosFase(1)],
            ['Transporte', MinutosFase(2)],
            ['Horário de recepção', detalhe.fabrica ? detalhe.fabrica.horarioAcostagem : '—'],
            ['Prazo da viagem', detalhe.fabrica
                ? detalhe.fabrica.slaLimiteHoras + ' horas' : '—'],
            ['Alertas por tratar', String(detalhe.alertasAbertos || 0)]
        ];
        if (detalhe.motivoRejeicao) {
            itens.push(['Motivo de rejeição', detalhe.motivoRejeicao]);
        }
        const tecnicos = [
            ['Número do registo no telemóvel', detalhe.uuidLocal || '—'],
            ['Área da fábrica', detalhe.fabrica ? detalhe.fabrica.raioGeofenceMetros + ' m' : '—'],
            ['Código de verificação da fotografia', detalhe.documento ? (detalhe.documento.hashSha256 || '—') : '—']
        ];
        document.getElementById('infoDetalhada').innerHTML = itens.map(function (item) {
            return '<div class="info-item"><div class="lbl">' + LT.escape(item[0])
                + '</div><div class="val">' + LT.escape(item[1]) + '</div></div>';
        }).join('') + '<details class="detalhes-tecnicos full"><summary>Detalhes técnicos</summary><div class="info-grid mt-12">'
            + tecnicos.map(function (item) {
                return '<div class="info-item"><div class="lbl">' + LT.escape(item[0])
                    + '</div><div class="val">' + LT.escape(item[1]) + '</div></div>';
            }).join('') + '</div></details>';
    }

    function mostrarAvisoMapa(mensagem) {
        const contentor = document.getElementById('mapDetalhe');
        if (!contentor) return;
        let aviso = contentor.querySelector('.map-fallback');
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.className = 'map-fallback';
            aviso.innerHTML = '<div></div>';
            contentor.appendChild(aviso);
        }
        aviso.querySelector('div').textContent = mensagem;
        aviso.style.display = 'grid';
    }

    function esconderAvisoMapa() {
        const contentor = document.getElementById('mapDetalhe');
        const aviso = contentor && contentor.querySelector('.map-fallback');
        if (aviso) aviso.style.display = 'none';
    }

    function garantirTamanhoMapa() {
        if (!mapa) return;
        window.requestAnimationFrame(function () {
            mapa.invalidateSize();
            setTimeout(function () { if (mapa) mapa.invalidateSize(); }, 250);
        });
    }

    function renderizarMapa() {
        const contentor = document.getElementById('mapDetalhe');
        const resumo = document.getElementById('resumoGps');
        if (!window.L) {
            contentor.innerHTML = '<div class="map-fallback" style="display:grid"><div>Mapa indisponível. Verifique a ligação à Internet. A informação da localização continua disponível abaixo.</div></div>';
        } else {
            if (!mapa) {
                mapa = L.map('mapDetalhe', {
                    zoomControl: true, attributionControl: false, scrollWheelZoom: false
                }).setView([-19.2, 34.8], 5);
                const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 18});
                let falhaTile = false;
                tiles.on('tileerror', function () {
                    if (falhaTile) return;
                    falhaTile = true;
                    mostrarAvisoMapa('Não foi possível carregar o mapa. Verifique a ligação à Internet. A informação da localização continua disponível abaixo.');
                });
                tiles.on('load', function () {
                    falhaTile = false;
                    esconderAvisoMapa();
                });
                tiles.addTo(mapa);
                window.addEventListener('resize', garantirTamanhoMapa);
                if (window.ResizeObserver && contentor) {
                    new ResizeObserver(garantirTamanhoMapa).observe(contentor);
                }
            }
            if (camada) {
                camada.remove();
            }
            camada = L.layerGroup();
            const pontos = [];
            if (detalhe.fabrica && detalhe.fabrica.latitude && detalhe.fabrica.longitude) {
                const f = detalhe.fabrica;
                L.circle([f.latitude, f.longitude], {
                    radius: f.raioGeofenceMetros || 200, color: '#f26b2e', weight: 1.5,
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
                    }).bindPopup('Ponto ' + (indice + 1) + ' do percurso<br>' + LT.fmt.dataHora(p.timestamp)
                        + '<br>Precisão da localização: ' + LT.fmt.numero(p.precisaoMetros, 1) + ' m')
                        .addTo(camada);
                    pontos.push([p.latitude, p.longitude]);
                });
            }
            camada.addTo(mapa);
            if (pontos.length) {
                mapa.fitBounds(pontos, {padding: [28, 28], maxZoom: 13});
            }
            garantirTamanhoMapa();
        }

        const ultima = detalhe.ultimaPosicao;
        resumo.innerHTML = ultima
            ? '<span class="chip em_transito">Última: ' + LT.fmt.dataHora(ultima.timestamp) + '</span>'
            + '<span class="chip">Latitude ' + LT.fmt.numero(ultima.latitude, 5) + '</span>'
            + '<span class="chip">Longitude ' + LT.fmt.numero(ultima.longitude, 5) + '</span>'
            + '<span class="chip ' + (ultima.precisaoMetros <= 15 ? 'concluido' : 'atrasado') + '">'
            + 'Precisão ' + LT.fmt.numero(ultima.precisaoMetros, 1) + ' m</span>'
            : '<span class="muted small">Sem pontos de localização registados.</span>';
    }

    async function concluir() {
        if (!window.confirm('Confirmar que o carregamento da guia ' + detalhe.guiaNumero + ' chegou ao destino?')) {
            return;
        }
        try {
            await LT.api('/api/v1/carregamentos/' + id + '/estado', {
                method: 'PATCH',
                body: {estado: 'CONCLUIDO'}
            });
            LT.toast('Carregamento concluído', 'O carregamento da guia foi marcado como concluído.', 'success');
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
            LT.toast('Carregamento não aceite', 'Motivo registado: ' + motivo + '. O motorista vai vê-lo e pode corrigir a guia.', 'success');
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
        LT.toast('Carregamento inválido', 'Falta o número do carregamento. Volte à lista e escolha um carregamento.', 'error');
    } else {
        carregar();
    }
})();
