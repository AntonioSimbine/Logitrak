/* Logitrack — W1 login (JWT guardado no localStorage) */
(function () {
    'use strict';

    const CONFIG = window.LOGITRACK;
    const params = new URLSearchParams(window.location.search);

    const sessao = CONFIG.lerSessao();
    if (sessao && sessao.token) {
        const destino = params.get('redirect');
        const inicial = CONFIG.rotaInicial(sessao.utilizador && sessao.utilizador.perfil);
        let alvo = inicial;
        if (destino) {
            try {
                alvo = new URL(destino, window.location.href).href;
            } catch (e) {
                alvo = inicial;
            }
        }
        window.location.replace(alvo);
        return;
    }

    const form = document.getElementById('formLogin');
    const email = document.getElementById('username');
    const senha = document.getElementById('password');
    const botao = document.getElementById('btnEntrar');
    const alerta = document.getElementById('loginAlert');

    if (params.get('logout')) {
        mostrarAlerta('ok', 'A sessão terminou. Para voltar, entre com a sua conta.');
    } else if (params.get('expirou')) {
        mostrarAlerta('erro', 'A sessão terminou. Entre novamente com ligação à Internet.');
    } else if (params.get('redirect')) {
        mostrarAlerta('info', 'Para continuar, entre com o seu e-mail e palavra-passe.');
    } else {
        // Frontend publicado mas API ainda em localhost: avisa em vez de falhar em silêncio.
        const apiLocal = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?/i.test(CONFIG.apiBase || '');
        const siteLocal = /^(localhost|127\.0\.0\.1|\[::1\])$/i.test(window.location.hostname);
        if (apiLocal && !siteLocal) {
            mostrarAlerta('info', 'O site está publicado, mas ainda não consegue falar com a central. '
                + 'Peça ao administrador o endereço do servidor e acrescente '
                + '?api=https://o-endereco-da-api ao endereço do site.');
        }
    }

    function mostrarAlerta(tipo, mensagem) {
        alerta.className = 'login-alert ' + (tipo === 'ok'
            ? 'ok' : tipo === 'info' ? 'info' : 'erro');
        alerta.innerHTML = '<span>' + (tipo === 'ok' ? '✓' : tipo === 'info' ? 'ℹ' : '⚠')
            + '</span><span></span>';
        alerta.querySelectorAll('span')[1].textContent = mensagem;
        alerta.classList.remove('hidden');
    }

    form.addEventListener('submit', async function (evento) {
        evento.preventDefault();
        alerta.classList.add('hidden');

        const credenciais = {
            email: email.value.trim(),
            senha: senha.value
        };
        if (!credenciais.email || !credenciais.senha) {
            mostrarAlerta('erro', 'Preencha o e-mail e a palavra-passe.');
            return;
        }

        botao.disabled = true;
        const textoOriginal = botao.textContent;
        botao.textContent = 'A ENTRAR…';

        try {
            const resposta = await fetch(CONFIG.apiBase + '/api/v1/auth/login', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                body: JSON.stringify(credenciais)
            });

            let corpo = null;
            const tipo = resposta.headers.get('content-type') || '';
            try {
                corpo = tipo.indexOf('json') >= 0 ? await resposta.json() : await resposta.text();
            } catch (e) {
                corpo = null;
            }

            if (!resposta.ok) {
                if (resposta.status === 401 || resposta.status === 403) {
                    throw new Error('Não foi possível entrar. Verifique o e-mail e a palavra-passe.');
                }
                throw new Error('A central não está a responder. '
                    + 'Os registos ficam guardados no telemóvel. Tente novamente dentro de instantes.');
            }

            CONFIG.guardarSessao({
                token: corpo.token,
                utilizador: corpo.utilizador,
                guardadoEm: Date.now()
            });

            const redirecionar = params.get('redirect');
            const perfil = corpo.utilizador && corpo.utilizador.perfil;
            if (perfil === 'MOTORISTA') {
                window.location.replace(CONFIG.rotaInicial(perfil));
                return;
            }
            if (redirecionar) {
                try {
                    const destino = new URL(redirecionar, window.location.href);
                    if (destino.origin === window.location.origin
                            && destino.pathname.indexOf('/app/') >= 0) {
                        window.location.replace(destino.href);
                        return;
                    }
                } catch (e) {
                    /* redirect inválido: segue para a rota inicial */
                }
            }
            window.location.replace(CONFIG.rotaInicial(perfil));
        } catch (erro) {
            console.error(erro);
            const semLigacao = !navigator.onLine || erro instanceof TypeError
                || erro.message === 'Failed to fetch';
            if (semLigacao) {
                console.error('[LogiTrack] Não foi possível chegar à central:', CONFIG.apiBase, erro);
                mostrarAlerta('erro', 'Sem ligação. Não foi possível chegar à central. '
                    + 'Verifique a ligação à Internet e tente novamente. Se continuar, '
                    + 'peça ao administrador para confirmar o endereço do servidor.');
            } else {
                mostrarAlerta('erro', erro.message);
            }
        } finally {
            botao.disabled = false;
            botao.textContent = textoOriginal;
        }
    });
})();
