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
        mostrarAlerta('ok', 'Sessão terminada com segurança.');
    } else if (params.get('expirou')) {
        mostrarAlerta('erro', 'A sua sessão expirou. Volte a autenticar-se.');
    } else if (params.get('redirect')) {
        mostrarAlerta('info', 'Autentique-se para continuar.');
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
                    throw new Error('E-mail ou palavra-passe incorretos. Verifique os dados e tente novamente.');
                }
                throw new Error('Não foi possível iniciar sessão neste momento. Tente novamente dentro de instantes.');
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
                            && (destino.pathname.indexOf('/app/') >= 0
                                || destino.pathname.indexOf('/m/') >= 0)) {
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
            mostrarAlerta('erro', semLigacao
                ? 'Não foi possível estabelecer ligação. Verifique a sua Internet e tente novamente.'
                : erro.message);
        } finally {
            botao.disabled = false;
            botao.textContent = textoOriginal;
        }
    });
})();
