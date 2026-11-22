/* ============================================================
   Logitrack — M3 novo carregamento + M4 câmara
   ============================================================ */
(function () {
    'use strict';

    const LTM = window.LTM;
    const state = LTM.state;
    const $ = LTM.$;
    const esc = LTM.esc;
    const fmt = LTM.fmt;
    const LTApp = window.LTApp = window.LTApp || {};
    const LOJA_FILA = LTM.constantes.LOJA_FILA;
    const TAMANHO_FOTO_MAX = LTM.constantes.TAMANHO_FOTO_MAX;
    const MIN_LARGURA = LTM.constantes.MIN_LARGURA;
    const MIN_ALTURA = LTM.constantes.MIN_ALTURA;

    let ligado = false;

    /* ---------- Formulário ---------- */
    function limparSelect(select) {
        if (!select) {
            return;
        }
        select.innerHTML = '';
    }

    function preencherSelect(select, itens, textoVazio, valor, etiqueta) {
        if (!select) {
            return;
        }
        limparSelect(select);
        select.appendChild(new Option(textoVazio, ''));
        itens.forEach(function (item) {
            select.appendChild(new Option(etiqueta(item), String(valor(item))));
        });
    }

    function reporFormulario() {
        const form = $('#formNovo');
        if (form) {
            form.reset();
        }
        state.gps = null;
        state.foto = null;
        state.fabricaId = null;
        state.fabricaNome = '';
        state.foraGeofence = false;

        const previewWrap = $('#photoPreviewWrap');
        const preview = $('#photoPreview');
        if (previewWrap) {
            previewWrap.classList.add('hidden');
        }
        if (preview) {
            preview.removeAttribute('src');
        }
        const statusFoto = $('#photoStatus');
        if (statusFoto) {
            statusFoto.textContent = 'Fotografia obrigatória';
            statusFoto.classList.remove('ok');
        }
        const textoBotao = $('#btnFotografarTexto');
        if (textoBotao) {
            textoBotao.textContent = 'FOTOGRAFAR GUIA';
        }

        const precisao = $('#gpsPrecisao');
        if (precisao) {
            precisao.textContent = 'A obter sinal…';
            precisao.classList.remove('ok', 'warn');
        }
        const lat = $('#gpsLat');
        const lng = $('#gpsLng');
        if (lat) {
            lat.textContent = '—';
        }
        if (lng) {
            lng.textContent = '—';
        }

        const fabricaNome = $('#fabricaNome');
        if (fabricaNome) {
            fabricaNome.textContent = 'A detetar por GPS…';
        }
        const fabricaInfo = $('#fabricaInfo');
        if (fabricaInfo) {
            fabricaInfo.textContent = 'Ative a localização para detetar a fábrica.';
        }
        const lock = $('#fabricaLock');
        if (lock) {
            lock.className = 'lock-pill';
            lock.innerHTML = '<svg><use href="#i-lock"/></svg> GPS';
        }
        const aviso = $('#fabricaAviso');
        if (aviso) {
            aviso.classList.add('hidden');
        }
        const manual = $('#fabricaManualWrap');
        if (manual) {
            manual.classList.add('hidden');
        }
        const selectFabrica = $('#selectFabrica');
        if (selectFabrica) {
            selectFabrica.disabled = false;
            limparSelect(selectFabrica);
        }
        const confirmarWrap = $('#confirmarWrap');
        if (confirmarWrap) {
            confirmarWrap.classList.add('hidden');
        }
        const confirmar = $('#confirmarFora');
        if (confirmar) {
            confirmar.checked = false;
        }
        const guia = $('#guiaNumero');
        if (guia) {
            guia.value = '';
        }
    }

    function prepararReferencias() {
        const refs = state.refs;
        if (!refs) {
            return false;
        }
        const veiculos = refs.veiculos || [];
        const tipos = refs.tiposCarga || [];
        const fabricas = refs.fabricas || [];
        preencherSelect($('#selectVeiculo'), veiculos, 'Selecione o veículo…',
            function (v) { return v.id; },
            function (v) {
                return v.matricula + ' · ' + v.modelo
                    + (v.capacidadeCargaToneladas ? ' (' + v.capacidadeCargaToneladas + ' t)' : '');
            });
        const seletorVeiculo = $('#selectVeiculo');
        if (seletorVeiculo && !seletorVeiculo.dataset.capacidadeLigada) {
            seletorVeiculo.dataset.capacidadeLigada = '1';
            seletorVeiculo.addEventListener('change', function () {
                const v = veiculos.find(function (item) {
                    return Number(item.id) === Number(seletorVeiculo.value);
                });
                const capacidade = v ? Number(v.capacidadeCargaToneladas) : NaN;
                const quantidade = $('#quantidade');
                const aviso = $('#capacidadeVeiculo');
                if (quantidade) {
                    if (isFinite(capacidade) && capacidade > 0) {
                        quantidade.max = String(capacidade);
                    } else {
                        quantidade.removeAttribute('max');
                    }
                }
                if (aviso) {
                    aviso.textContent = isFinite(capacidade) && capacidade > 0
                        ? 'Capacidade do veículo: ' + capacidade + ' t. Não é possível exceder.'
                        : 'Capacidade do veículo indisponível.';
                }
            });
        }
        preencherSelect($('#selectTipo'), tipos.map(function (t) {
            return {nome: t};
        }), 'Selecione o tipo…',
            function (t) { return t.nome; },
            function (t) { return t.nome; });
        preencherSelect($('#selectFabrica'), fabricas, 'Selecione a fábrica…',
            function (f) { return f.id; },
            function (f) { return f.nome; });
        return veiculos.length > 0;
    }

    async function abrirNovo() {
        if (!state.utilizador) {
            return;
        }
        LTApp.mostrarEcra('novo');
        reporFormulario();
        if (!state.refs) {
            await LTApp.carregarReferencias();
        }
        if (!state.refs) {
            LTM.toast('Referências indisponíveis',
                'Ligue-se à Internet pelo menos uma vez para descarregar fábricas e veículos.',
                'error');
            LTApp.mostrarEcra('dashboard');
            return;
        }
        if (!prepararReferencias()) {
            LTM.toast('Sem veículos disponíveis',
                'Não existem veículos aptos. Contacte o operador da central.', 'error');
            LTApp.mostrarEcra('dashboard');
            return;
        }
        iniciarGps();
    }
    LTApp.abrirNovo = abrirNovo;

    /* ---------- GPS e geofence ---------- */
    function textoPrecisao(valor) {
        return Number.isInteger(valor) ? String(valor) : Number(valor).toFixed(1);
    }

    function iniciarGps() {
        const precisao = $('#gpsPrecisao');
        if (!navigator.geolocation) {
            if (precisao) {
                precisao.textContent = 'GPS indisponível neste dispositivo';
                precisao.classList.add('warn');
            }
            LTM.toast('GPS indisponível',
                'Este dispositivo não permite obter a localização. '
                + 'Não é possível validar o carregamento.', 'error');
            return;
        }
        if (precisao) {
            precisao.textContent = 'A obter sinal…';
            precisao.classList.remove('ok', 'warn');
        }
        navigator.geolocation.getCurrentPosition(function (posicao) {
            const coords = posicao.coords;
            state.gps = {
                lat: coords.latitude,
                lng: coords.longitude,
                precisao: Number(coords.accuracy) || 9999,
                timestamp: Date.now()
            };
            const lat = $('#gpsLat');
            const lng = $('#gpsLng');
            if (lat) {
                lat.textContent = Number(coords.latitude).toFixed(6);
            }
            if (lng) {
                lng.textContent = Number(coords.longitude).toFixed(6);
            }
            const maximo = LTM.precisaMaxima();
            if (precisao) {
                if (state.gps.precisao > maximo) {
                    precisao.textContent = 'Precisão ' + textoPrecisao(state.gps.precisao)
                        + ' m · máximo ' + textoPrecisao(maximo) + ' m';
                    precisao.classList.add('warn');
                    precisao.classList.remove('ok');
                } else {
                    precisao.textContent = '± ' + textoPrecisao(state.gps.precisao)
                        + ' m · sinal adequado';
                    precisao.classList.add('ok');
                    precisao.classList.remove('warn');
                }
            }
            avaliarGeofence();
        }, function (erro) {
            let mensagem = 'Não foi possível obter a localização.';
            if (erro && erro.code === 1) {
                mensagem = 'Permissão de localização negada. Ative o GPS nas definições do dispositivo.';
            } else if (erro && erro.code === 2) {
                mensagem = 'Sinal GPS indisponível. Saia para o exterior e tente novamente.';
            } else if (erro && erro.code === 3) {
                mensagem = 'Tempo esgotado a obter o GPS. Toque em atualizar para tentar novamente.';
            }
            if (precisao) {
                precisao.textContent = 'GPS indisponível — toque em atualizar';
                precisao.classList.add('warn');
                precisao.classList.remove('ok');
            }
            LTM.toast('GPS indisponível', mensagem, 'error');
        }, {
            enableHighAccuracy: true,
            timeout: 20000,
            maximumAge: 5000
        });
    }

    function mostrarAvisoFabrica(texto) {
        const aviso = $('#fabricaAviso');
        const corpo = $('#fabricaAvisoTexto');
        if (aviso && corpo) {
            corpo.textContent = texto;
            aviso.classList.remove('hidden');
        }
    }

    function esconderAvisoFabrica() {
        const aviso = $('#fabricaAviso');
        if (aviso) {
            aviso.classList.add('hidden');
        }
    }

    function aplicarSelecaoManual() {
        const select = $('#selectFabrica');
        const fabricaNome = $('#fabricaNome');
        const fabricaInfo = $('#fabricaInfo');
        const confirmarWrap = $('#confirmarWrap');
        const confirmar = $('#confirmarFora');
        if (!select || !state.refs) {
            return;
        }
        const id = Number(select.value);
        const fabrica = (state.refs.fabricas || []).find(function (f) {
            return Number(f.id) === id;
        });
        if (!fabrica) {
            state.fabricaId = null;
            state.fabricaNome = '';
            state.foraGeofence = false;
            if (fabricaNome) {
                fabricaNome.textContent = 'Selecione a fábrica';
            }
            if (fabricaInfo) {
                fabricaInfo.textContent = 'A origem é necessária para validar o carregamento.';
            }
            if (confirmarWrap) {
                confirmarWrap.classList.add('hidden');
            }
            if (confirmar) {
                confirmar.checked = false;
            }
            return;
        }
        state.fabricaId = fabrica.id;
        state.fabricaNome = fabrica.nome;
        if (fabricaNome) {
            fabricaNome.textContent = fabrica.nome;
        }
        const raio = Number(fabrica.raioGeofenceMetros) || 500;
        if (state.gps) {
            const distancia = LTM.haversine(state.gps.lat, state.gps.lng,
                Number(fabrica.latitude), Number(fabrica.longitude));
            const fora = distancia > raio;
            state.foraGeofence = fora;
            if (fabricaInfo) {
                fabricaInfo.textContent = (fora ? 'Fora do raio' : 'Dentro do raio')
                    + ' · ' + Math.round(distancia) + ' m do centro (raio '
                    + Math.round(raio) + ' m)';
            }
            if (confirmarWrap) {
                confirmarWrap.classList.toggle('hidden', !fora);
            }
            if (!fora && confirmar) {
                confirmar.checked = false;
            }
        } else {
            state.foraGeofence = false;
            if (fabricaInfo) {
                fabricaInfo.textContent = 'Localização ainda não disponível.';
            }
            if (confirmarWrap) {
                confirmarWrap.classList.add('hidden');
            }
        }
    }

    function avaliarGeofence() {
        if (!state.gps || !state.refs) {
            return;
        }
        const fabricas = state.refs.fabricas || [];
        if (!fabricas.length) {
            mostrarAvisoFabrica('Não existem fábricas ativas configuradas.');
            return;
        }
        const mais = LTM.fabricaMaisProxima(state.gps.lat, state.gps.lng);
        if (!mais) {
            mostrarAvisoFabrica('Não foi possível comparar a sua posição com as fábricas.');
            return;
        }
        const select = $('#selectFabrica');
        const manual = $('#fabricaManualWrap');
        const lock = $('#fabricaLock');
        const fabricaNome = $('#fabricaNome');
        const fabricaInfo = $('#fabricaInfo');
        const confirmarWrap = $('#confirmarWrap');
        const confirmar = $('#confirmarFora');

        if (mais.dentro) {
            state.fabricaId = mais.fabrica.id;
            state.fabricaNome = mais.fabrica.nome;
            state.foraGeofence = false;
            if (select) {
                select.value = String(mais.fabrica.id);
                select.disabled = true;
            }
            if (fabricaNome) {
                fabricaNome.textContent = mais.fabrica.nome;
            }
            if (fabricaInfo) {
                fabricaInfo.textContent = 'Deteção GPS · ' + Math.round(mais.distancia)
                    + ' m do centro (raio ' + Math.round(mais.raio) + ' m)';
            }
            if (lock) {
                lock.className = 'lock-pill';
                lock.innerHTML = '<svg><use href="#i-lock"/></svg> AUTO';
            }
            esconderAvisoFabrica();
            if (manual) {
                manual.classList.add('hidden');
            }
            if (confirmarWrap) {
                confirmarWrap.classList.add('hidden');
            }
            if (confirmar) {
                confirmar.checked = false;
            }
            return;
        }

        if (select) {
            select.disabled = false;
            if (!select.value) {
                select.value = String(mais.fabrica.id);
            }
        }
        if (manual) {
            manual.classList.remove('hidden');
        }
        if (lock) {
            lock.className = 'lock-pill manual';
            lock.innerHTML = '<svg><use href="#i-pin"/></svg> GPS';
        }
        mostrarAvisoFabrica('Está fora do raio de geofence. A fábrica mais próxima é '
            + mais.fabrica.nome + ', a ' + Math.round(mais.distancia)
            + ' m (raio ' + Math.round(mais.raio) + ' m). Selecione a origem e confirme o registo.');
        aplicarSelecaoManual();
    }

    /* ---------- Câmara e fotografia ---------- */
    function limparObjectosCamera() {
        const video = $('#cameraVideo');
        if (video) {
            video.style.display = '';
            video.srcObject = null;
        }
    }

    function pararCamera() {
        if (state.cameraStream) {
            state.cameraStream.getTracks().forEach(function (track) {
                try {
                    track.stop();
                } catch (e) {
                    /* ignorado */
                }
            });
            state.cameraStream = null;
        }
        limparObjectosCamera();
    }
    LTApp.pararCamera = pararCamera;

    function mostrarMensagemCamera(titulo, texto) {
        const mensagem = $('#cameraMensagem');
        const tituloEl = $('#cameraMsgTitulo');
        const textoEl = $('#cameraMsgTexto');
        const frame = $('#cameraFrame');
        const tip = $('#cameraTip');
        const shutter = $('#btnDisparar');
        const repetir = $('#btnRepetir');
        const usar = $('#btnUsarFoto');
        if (tituloEl) {
            tituloEl.textContent = titulo;
        }
        if (textoEl) {
            textoEl.textContent = texto;
        }
        if (mensagem) {
            mensagem.classList.remove('hidden');
        }
        if (frame) {
            frame.classList.add('hidden');
        }
        if (tip) {
            tip.classList.add('hidden');
        }
        if (shutter) {
            shutter.classList.add('hidden');
        }
        if (repetir) {
            repetir.classList.add('hidden');
        }
        if (usar) {
            usar.classList.add('hidden');
        }
    }

    function reiniciarUiCamera() {
        const mensagem = $('#cameraMensagem');
        const frame = $('#cameraFrame');
        const tip = $('#cameraTip');
        const shutter = $('#btnDisparar');
        const repetir = $('#btnRepetir');
        const usar = $('#btnUsarFoto');
        const preview = $('#cameraPreview');
        const video = $('#cameraVideo');
        if (mensagem) {
            mensagem.classList.add('hidden');
        }
        if (frame) {
            frame.classList.remove('hidden');
        }
        if (tip) {
            tip.classList.remove('hidden');
        }
        if (shutter) {
            shutter.classList.remove('hidden');
            shutter.disabled = false;
        }
        if (repetir) {
            repetir.classList.add('hidden');
        }
        if (usar) {
            usar.classList.add('hidden');
        }
        if (preview) {
            preview.classList.add('hidden');
            preview.removeAttribute('src');
        }
        if (video) {
            video.style.display = '';
        }
    }

    async function iniciarCamera() {
        reiniciarUiCamera();
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            mostrarMensagemCamera('Câmara indisponível',
                'Este dispositivo não permite acesso à câmara. Escolha uma fotografia da galeria.');
            return;
        }
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: false,
                video: {
                    facingMode: {ideal: 'environment'},
                    width: {ideal: 1920},
                    height: {ideal: 1080}
                }
            });
            state.cameraStream = stream;
            const video = $('#cameraVideo');
            if (video) {
                video.srcObject = stream;
                try {
                    await video.play();
                } catch (e) {
                    /* autoplay pode ser bloqueado, a pré-visualização continua */
                }
            }
        } catch (e) {
            console.warn('Câmara indisponível', e);
            const negada = e && (e.name === 'NotAllowedError' || e.name === 'SecurityError');
            mostrarMensagemCamera('Câmara indisponível',
                negada
                    ? 'Autorize o acesso à câmara nas definições do navegador ou escolha uma fotografia da galeria.'
                    : 'Não foi possível abrir a câmara. Escolha uma fotografia da galeria.');
        }
    }

    function abrirCamera() {
        LTApp.mostrarEcra('camera');
        reiniciarUiCamera();
        pararCamera();
        iniciarCamera();
    }

    function fecharCamera() {
        pararCamera();
        LTApp.mostrarEcra('novo');
    }
    LTApp.fecharCamera = fecharCamera;

    function calcularDimensoes(largura, altura, limite) {
        const maior = Math.max(largura, altura);
        if (maior <= limite) {
            return {largura: Math.round(largura), altura: Math.round(altura)};
        }
        const escala = limite / maior;
        return {
            largura: Math.max(1, Math.round(largura * escala)),
            altura: Math.max(1, Math.round(altura * escala))
        };
    }

    function desenhar(fonte, largura, altura, qualidade) {
        const canvas = document.createElement('canvas');
        canvas.width = largura;
        canvas.height = altura;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
            throw new Error('Não foi possível processar a imagem.');
        }
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, largura, altura);
        ctx.drawImage(fonte, 0, 0, largura, altura);
        return canvas.toDataURL('image/jpeg', qualidade);
    }

    function comprimirImagem(fonte, largura, altura) {
        let dims = calcularDimensoes(largura, altura, 1600);
        let dataUrl = desenhar(fonte, dims.largura, dims.altura, 0.82);
        const qualidades = [0.72, 0.6, 0.48, 0.38, 0.3];
        for (let i = 0; i < qualidades.length && dataUrl.length > TAMANHO_FOTO_MAX; i++) {
            dataUrl = desenhar(fonte, dims.largura, dims.altura, qualidades[i]);
        }
        if (dataUrl.length > TAMANHO_FOTO_MAX) {
            dims = calcularDimensoes(largura, altura, 1200);
            dataUrl = desenhar(fonte, dims.largura, dims.altura, 0.62);
            for (let i = 0; i < qualidades.length && dataUrl.length > TAMANHO_FOTO_MAX; i++) {
                dataUrl = desenhar(fonte, dims.largura, dims.altura, qualidades[i]);
            }
        }
        return {
            dataUrl: dataUrl,
            largura: dims.largura,
            altura: dims.altura
        };
    }

    function atualizarStatusFoto() {
        const status = $('#photoStatus');
        const previewWrap = $('#photoPreviewWrap');
        const preview = $('#photoPreview');
        const textoBotao = $('#btnFotografarTexto');
        if (state.foto) {
            if (preview) {
                preview.src = state.foto.dataUrl;
            }
            if (previewWrap) {
                previewWrap.classList.remove('hidden');
            }
            if (status) {
                status.textContent = 'Fotografia pronta · '
                    + state.foto.largura + '×' + state.foto.altura;
                status.classList.add('ok');
            }
            if (textoBotao) {
                textoBotao.textContent = 'SUBSTITUIR FOTOGRAFIA';
            }
        } else {
            if (previewWrap) {
                previewWrap.classList.add('hidden');
            }
            if (status) {
                status.textContent = 'Fotografia obrigatória';
                status.classList.remove('ok');
            }
            if (textoBotao) {
                textoBotao.textContent = 'FOTOGRAFAR GUIA';
            }
        }
    }

    function mostrarFotoCapturada(resultado) {
        if (!resultado) {
            return;
        }
        const preview = $('#cameraPreview');
        const video = $('#cameraVideo');
        const frame = $('#cameraFrame');
        const tip = $('#cameraTip');
        const shutter = $('#btnDisparar');
        const repetir = $('#btnRepetir');
        const usar = $('#btnUsarFoto');
        const mensagem = $('#cameraMensagem');
        if (preview) {
            preview.src = resultado.dataUrl;
            preview.classList.remove('hidden');
        }
        if (video) {
            video.style.display = 'none';
        }
        if (frame) {
            frame.classList.add('hidden');
        }
        if (tip) {
            tip.classList.add('hidden');
        }
        if (shutter) {
            shutter.classList.add('hidden');
        }
        if (mensagem) {
            mensagem.classList.add('hidden');
        }
        if (repetir) {
            repetir.classList.remove('hidden');
        }
        if (usar) {
            usar.classList.remove('hidden');
        }
        state.foto = resultado;
    }

    function capturarVideo() {
        const video = $('#cameraVideo');
        if (!video || !video.videoWidth || !video.videoHeight) {
            LTM.toast('Câmara ainda a iniciar', 'Aguarde um instante e tente novamente.', 'warn');
            return;
        }
        const botao = $('#btnDisparar');
        if (botao) {
            botao.disabled = true;
        }
        try {
            const largura = video.videoWidth;
            const altura = video.videoHeight;
            if (Math.max(largura, altura) < MIN_LARGURA || Math.min(largura, altura) < MIN_ALTURA) {
                LTM.toast('Resolução insuficiente',
                    'A guia deve ter pelo menos 640×360 pixéis.', 'error');
                return;
            }
            const resultado = comprimirImagem(video, largura, altura);
            resultado.bytes = Math.round(resultado.dataUrl.length * 0.75);
            mostrarFotoCapturada(resultado);
            pararCamera();
        } catch (e) {
            LTM.toast('Falha ao capturar', e.message || 'Tente novamente.', 'error');
        } finally {
            if (botao) {
                botao.disabled = false;
            }
        }
    }

    function carregarImagem(ficheiro) {
        return new Promise(function (resolve, reject) {
            const url = URL.createObjectURL(ficheiro);
            const imagem = new Image();
            imagem.onload = function () {
                URL.revokeObjectURL(url);
                resolve(imagem);
            };
            imagem.onerror = function () {
                URL.revokeObjectURL(url);
                reject(new Error('Não foi possível ler a imagem selecionada.'));
            };
            imagem.src = url;
        });
    }

    async function processarFicheiro(ficheiro) {
        if (!ficheiro) {
            return;
        }
        if (ficheiro.type && ficheiro.type.indexOf('image/') !== 0) {
            LTM.toast('Ficheiro inválido', 'Escolha uma imagem JPEG ou PNG.', 'error');
            return;
        }
        if (ficheiro.size > 12 * 1024 * 1024) {
            LTM.toast('Fotografia demasiado grande',
                'Escolha uma imagem com menos de 12 MB.', 'error');
            return;
        }
        try {
            const imagem = await carregarImagem(ficheiro);
            const largura = imagem.naturalWidth || imagem.width;
            const altura = imagem.naturalHeight || imagem.height;
            if (Math.max(largura, altura) < MIN_LARGURA || Math.min(largura, altura) < MIN_ALTURA) {
                LTM.toast('Resolução insuficiente',
                    'A guia deve ter pelo menos 640×360 pixéis (recebido '
                    + largura + '×' + altura + ').', 'error');
                return;
            }
            const resultado = comprimirImagem(imagem, largura, altura);
            resultado.bytes = Math.round(resultado.dataUrl.length * 0.75);
            mostrarFotoCapturada(resultado);
            pararCamera();
        } catch (e) {
            LTM.toast('Fotografia inválida', e.message || 'Tente outra imagem.', 'error');
        } finally {
            const input = $('#inputFicheiro');
            if (input) {
                input.value = '';
            }
        }
    }

    function repetirFoto() {
        state.foto = null;
        if (!state.cameraStream) {
            iniciarCamera();
        } else {
            reiniciarUiCamera();
        }
    }

    function usarFoto() {
        if (!state.foto) {
            LTM.toast('Sem fotografia', 'Capture a guia antes de continuar.', 'warn');
            return;
        }
        atualizarStatusFoto();
        pararCamera();
        LTApp.mostrarEcra('novo');
        LTM.toast('Guia fotografada', 'A fotografia foi comprimida e está pronta a guardar.', 'success');
    }

    /* ---------- Guardar carregamento ---------- */
    function valor(id) {
        const el = document.getElementById(id);
        return el ? el.value.trim() : '';
    }

    function construirItem() {
        const guia = valor('guiaNumero');
        const veiculoId = Number(valor('selectVeiculo'));
        const tipo = valor('selectTipo');
        const quantidade = Number(valor('quantidade').replace(',', '.'));

        if (!guia) {
            LTM.toast('Número da guia em falta', 'Introduza o número da guia de transporte.', 'error');
            $('#guiaNumero').focus();
            return null;
        }
        if (!veiculoId) {
            LTM.toast('Veículo em falta', 'Selecione o veículo que está a utilizar.', 'error');
            return null;
        }
        if (!tipo) {
            LTM.toast('Tipo de carga em falta', 'Selecione o tipo de carga.', 'error');
            return null;
        }
        if (!isFinite(quantidade) || quantidade <= 0) {
            LTM.toast('Quantidade inválida', 'Indique a quantidade em toneladas.', 'error');
            return null;
        }
        const veiculoSelecionado = (state.refs && state.refs.veiculos || []).find(function (v) {
            return Number(v.id) === veiculoId;
        });
        const capacidade = veiculoSelecionado
            ? Number(veiculoSelecionado.capacidadeCargaToneladas) : NaN;
        if (!isFinite(capacidade) || capacidade <= 0) {
            LTM.toast('Capacidade indisponível',
                'O veículo selecionado não tem uma capacidade válida.', 'error');
            return null;
        }
        if (quantidade > capacidade) {
            LTM.toast('Capacidade excedida',
                'A quantidade (' + quantidade + ' t) excede a capacidade do veículo '
                + veiculoSelecionado.matricula + ' (' + capacidade + ' t).', 'error');
            return null;
        }
        if (!state.foto) {
            LTM.toast('Fotografia em falta', 'Fotografe a guia de transporte.', 'error');
            return null;
        }
        if (!state.gps) {
            LTM.toast('GPS em falta',
                'Aguarde pela localização ou toque em atualizar. A validação exige GPS.', 'error');
            return null;
        }
        const maximo = LTM.precisaMaxima();
        if (!isFinite(state.gps.precisao) || state.gps.precisao < 0
                || state.gps.precisao > maximo) {
            LTM.toast('GPS insuficiente', 'Precisão de GPS insuficiente para validação (Máx: '
                + textoPrecisao(maximo) + 'm). Active o GPS e aguarde uma fix melhor.', 'error');
            return null;
        }
        if (!state.fabricaId) {
            LTM.toast('Fábrica em falta',
                'Selecione a fábrica de origem para validar o carregamento.', 'error');
            return null;
        }
        const confirmar = $('#confirmarFora');
        if (state.foraGeofence && (!confirmar || !confirmar.checked)) {
            LTM.toast('Confirmação necessária',
                'Está fora do raio de geofence. Confirme o registo para continuar.', 'error');
            return null;
        }

        const veiculo = (state.refs.veiculos || []).find(function (v) {
            return Number(v.id) === veiculoId;
        });
        const agora = new Date();
        return {
            uuidLocal: LTM.novoUuid(),
            guiaNumero: guia,
            tipoCarga: tipo,
            quantidadeToneladas: quantidade,
            fabricaId: state.fabricaId,
            fabricaNome: state.fabricaNome,
            veiculoId: veiculoId,
            veiculoMatricula: veiculo ? veiculo.matricula : '',
            motoristaId: state.utilizador.id,
            motoristaNome: state.utilizador.nome,
            latitude: Number(state.gps.lat),
            longitude: Number(state.gps.lng),
            precisaoMetros: Number(state.gps.precisao),
            dataHoraInicio: LTM.dataLocalISO(agora),
            criadoEm: agora.getTime(),
            fotoBase64: state.foto.dataUrl,
            fotoLargura: state.foto.largura,
            fotoAltura: state.foto.altura,
            confirmarForaGeofence: !!(state.foraGeofence && confirmar && confirmar.checked),
            estado: 'PENDENTE',
            tentativas: 0,
            erro: null
        };
    }

    async function guardarCarregamento(evento) {
        evento.preventDefault();
        if (!state.utilizador) {
            return;
        }
        const item = construirItem();
        if (!item) {
            return;
        }
        const botao = $('#btnGuardar');
        botao.disabled = true;
        try {
            await LTM.dbPut(LOJA_FILA, item);
            state.foto = null;
            state.gps = null;
            state.fabricaId = null;
            state.fabricaNome = '';
            LTM.toast('Carregamento guardado',
                'A guia ' + item.guiaNumero + ' ficou na fila do dispositivo. '
                + 'A sincronização é automática quando houver rede.', 'success');
            LTApp.mostrarEcra('dashboard');
            await LTApp.atualizarResumoFila();
            if (LTM.ligado()) {
                setTimeout(function () {
                    LTApp.sincronizarFila(true);
                }, 500);
            }
        } catch (e) {
            LTM.toast('Não foi possível guardar', e.message, 'error');
        } finally {
            botao.disabled = false;
        }
    }

    /* ---------- Eventos ---------- */
    function iniciarRegisto() {
        if (ligado) {
            return;
        }
        ligado = true;
        const fotografar = $('#btnFotografar');
        if (fotografar) {
            fotografar.addEventListener('click', abrirCamera);
        }
        const atualizarGps = $('#btnAtualizarGps');
        if (atualizarGps) {
            atualizarGps.addEventListener('click', iniciarGps);
        }
        const selectFabrica = $('#selectFabrica');
        if (selectFabrica) {
            selectFabrica.addEventListener('change', aplicarSelecaoManual);
        }
        const form = $('#formNovo');
        if (form) {
            form.addEventListener('submit', guardarCarregamento);
        }
        const fecharCamera = $('#btnFecharCamera');
        if (fecharCamera) {
            fecharCamera.addEventListener('click', fecharCamera);
        }
        const disparar = $('#btnDisparar');
        if (disparar) {
            disparar.addEventListener('click', capturarVideo);
        }
        const repetir = $('#btnRepetir');
        if (repetir) {
            repetir.addEventListener('click', repetirFoto);
        }
        const usar = $('#btnUsarFoto');
        if (usar) {
            usar.addEventListener('click', usarFoto);
        }
        const escolher = $('#btnEscolherFicheiro');
        const input = $('#inputFicheiro');
        if (escolher && input) {
            escolher.addEventListener('click', function () {
                input.click();
            });
            input.addEventListener('change', function () {
                if (input.files && input.files[0]) {
                    processarFicheiro(input.files[0]);
                }
            });
        }
    }

    LTApp.iniciarRegisto = iniciarRegisto;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciarRegisto);
    } else {
        iniciarRegisto();
    }
})();
