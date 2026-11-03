/* Logitrack — W8 gestão de utilizadores e perfis */
(function () {
    'use strict';

    let utilizadores = [];

    function iniciais(nome) {
        return String(nome || '?').trim().charAt(0).toUpperCase() || '?';
    }

    function renderizar() {
        const termo = (document.getElementById('pesquisaUtilizadores').value || '')
            .trim().toLowerCase();
        const lista = utilizadores.filter(function (u) {
            if (!termo) {
                return true;
            }
            return (u.nome + ' ' + u.email).toLowerCase().includes(termo);
        });
        const tbody = document.getElementById('tabelaUtilizadores');

        document.getElementById('kpiUtilizadores').textContent = utilizadores.length;
        document.getElementById('kpiAdmins').textContent = utilizadores.filter(function (u) {
            return u.perfil === 'ADMINISTRADOR';
        }).length;
        document.getElementById('kpiOperadores').textContent = utilizadores.filter(function (u) {
            return u.perfil === 'OPERADOR_CENTRAL';
        }).length;
        document.getElementById('kpiMotoristas').textContent = utilizadores.filter(function (u) {
            return u.perfil === 'MOTORISTA' && u.ativo;
        }).length;

        if (!lista.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="empty">'
                + (utilizadores.length ? 'Nenhum utilizador corresponde à pesquisa.'
                    : 'Ainda não existem utilizadores.')
                + '</td></tr>';
            return;
        }

        tbody.innerHTML = lista.map(function (u) {
            return '<tr>'
                + '<td><div style="display:flex;align-items:center;gap:10px">'
                + '<div class="avatar orange" style="width:34px;height:34px;font-size:13px">'
                + LT.escape(iniciais(u.nome)) + '</div>'
                + '<div><strong>' + LT.escape(u.nome) + '</strong>'
                + '<div class="sub">' + LT.escape(u.email) + '</div></div></div></td>'
                + '<td>' + LT.chip(String(u.perfil).toLowerCase(), u.perfilEtiqueta) + '</td>'
                + '<td>' + (u.ativo
                    ? '<span class="chip no">Ativa</span>'
                    : '<span class="chip sim">Inativa</span>') + '</td>'
                + '<td>' + LT.fmt.data(u.dataCriacao) + '</td>'
                + '<td><div class="table-actions">'
                + '<button class="btn small ghost" type="button" data-acao="editar" data-id="' + u.id + '">Editar</button>'
                + '<button class="btn small ghost" type="button" data-acao="senha" data-id="' + u.id + '">Senha</button>'
                + '<button class="btn small ghost" type="button" data-acao="estado" data-id="' + u.id + '">'
                + (u.ativo ? 'Desativar' : 'Ativar') + '</button>'
                + '<button class="btn small ghost" type="button" data-acao="eliminar" data-id="' + u.id + '">Eliminar</button>'
                + '</div></td>'
                + '</tr>';
        }).join('');
    }

    async function carregar() {
        const botao = document.getElementById('btnAtualizar');
        botao.disabled = true;
        try {
            utilizadores = await LT.api('/api/v1/utilizadores');
            renderizar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    function abrirNovo() {
        const form = document.getElementById('formUtilizador');
        form.reset();
        document.getElementById('utilizadorId').value = '';
        document.getElementById('tituloModalUtilizador').textContent = 'Novo utilizador';
        document.getElementById('senhaUtilizador').required = true;
        document.getElementById('hintSenha').textContent =
            'Obrigatória na criação (mínimo 6 caracteres).';
        document.getElementById('perfilUtilizador').value = 'OPERADOR_CENTRAL';
        document.getElementById('ativoUtilizador').value = 'true';
        LT.abrirModal('modalUtilizador');
    }

    function abrirEdicao(id) {
        const u = utilizadores.find(function (item) { return item.id === id; });
        if (!u) {
            return;
        }
        document.getElementById('utilizadorId').value = u.id;
        document.getElementById('tituloModalUtilizador').textContent = 'Editar ' + u.nome;
        document.getElementById('nomeUtilizador').value = u.nome || '';
        document.getElementById('emailUtilizador').value = u.email || '';
        document.getElementById('perfilUtilizador').value = u.perfil;
        document.getElementById('ativoUtilizador').value = u.ativo ? 'true' : 'false';
        document.getElementById('senhaUtilizador').value = '';
        document.getElementById('senhaUtilizador').required = false;
        document.getElementById('hintSenha').textContent =
            'Deixe em branco para manter a palavra-passe atual.';
        LT.abrirModal('modalUtilizador');
    }

    async function guardar(ev) {
        ev.preventDefault();
        const id = document.getElementById('utilizadorId').value;
        const senha = document.getElementById('senhaUtilizador').value;
        if (!id && senha.length < 6) {
            LT.toast('Palavra-passe inválida',
                'Defina uma palavra-passe com pelo menos 6 caracteres.', 'error');
            return;
        }
        const pedido = {
            nome: document.getElementById('nomeUtilizador').value.trim(),
            email: document.getElementById('emailUtilizador').value.trim(),
            perfil: document.getElementById('perfilUtilizador').value,
            ativo: document.getElementById('ativoUtilizador').value === 'true'
        };
        if (senha) {
            pedido.senha = senha;
        }

        const botao = document.getElementById('btnGuardarUtilizador');
        botao.disabled = true;
        try {
            if (id) {
                await LT.api('/api/v1/utilizadores/' + id, {method: 'PUT', body: pedido});
            } else {
                await LT.api('/api/v1/utilizadores', {method: 'POST', body: pedido});
            }
            LT.fecharModal('modalUtilizador');
            LT.toast('Utilizador guardado', pedido.nome + ' foi gravado com sucesso.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    async function alternarEstado(id) {
        const u = utilizadores.find(function (item) { return item.id === id; });
        if (!u) {
            return;
        }
        const novoEstado = !u.ativo;
        const accao = novoEstado ? 'ativar' : 'desativar';
        if (!window.confirm('Pretende ' + accao + ' a conta de ' + u.nome + '?')) {
            return;
        }
        try {
            await LT.api('/api/v1/utilizadores/' + id + '/ativo', {
                method: 'PATCH',
                body: {ativo: novoEstado}
            });
            LT.toast('Conta atualizada',
                u.nome + ' está agora ' + (novoEstado ? 'ativa' : 'inativa') + '.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    }

    async function eliminar(id) {
        const u = utilizadores.find(function (item) { return item.id === id; });
        if (!u || !window.confirm('Eliminar definitivamente a conta de ' + u.nome + '?')) {
            return;
        }
        try {
            await LT.api('/api/v1/utilizadores/' + id, {method: 'DELETE'});
            LT.toast('Conta eliminada', u.nome + ' foi removido do sistema.', 'success');
            await carregar();
        } catch (e) {
            LT.erro(e);
        }
    }

    function abrirSenha(id) {
        const u = utilizadores.find(function (item) { return item.id === id; });
        if (!u) {
            return;
        }
        document.getElementById('formSenha').reset();
        document.getElementById('senhaUtilizadorId').value = u.id;
        document.getElementById('subtituloSenha').textContent =
            'Definir uma nova palavra-passe para ' + u.nome + '.';
        LT.abrirModal('modalSenha');
    }

    async function guardarSenha(ev) {
        ev.preventDefault();
        const id = document.getElementById('senhaUtilizadorId').value;
        const nova = document.getElementById('novaSenha').value;
        const confirmar = document.getElementById('confirmarSenha').value;
        if (nova !== confirmar) {
            LT.toast('Palavras-passe diferentes',
                'A confirmação não corresponde à nova palavra-passe.', 'error');
            return;
        }
        try {
            await LT.api('/api/v1/utilizadores/' + id + '/senha', {
                method: 'PATCH',
                body: {novaSenha: nova}
            });
            LT.fecharModal('modalSenha');
            LT.toast('Palavra-passe redefinida',
                'O utilizador pode iniciar sessão com a nova palavra-passe.', 'success');
        } catch (e) {
            LT.erro(e);
        }
    }

    document.getElementById('btnNovoUtilizador').addEventListener('click', abrirNovo);
    document.getElementById('btnAtualizar').addEventListener('click', carregar);
    document.getElementById('formUtilizador').addEventListener('submit', guardar);
    document.getElementById('formSenha').addEventListener('submit', guardarSenha);
    document.getElementById('pesquisaUtilizadores').addEventListener('input', renderizar);
    document.querySelectorAll('[data-fechar]').forEach(function (botao) {
        botao.addEventListener('click', function () {
            LT.fecharModal(botao.dataset.fechar);
        });
    });

    document.getElementById('tabelaUtilizadores').addEventListener('click', function (ev) {
        const botao = ev.target.closest('button[data-acao]');
        if (!botao) {
            return;
        }
        const id = Number(botao.dataset.id);
        switch (botao.dataset.acao) {
            case 'editar':
                abrirEdicao(id);
                break;
            case 'senha':
                abrirSenha(id);
                break;
            case 'estado':
                alternarEstado(id);
                break;
            case 'eliminar':
                eliminar(id);
                break;
            default:
                break;
        }
    });

    carregar();
})();
