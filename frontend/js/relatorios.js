/* Logitrack — W5 relatórios operacionais */
(function () {
    'use strict';

    const TAMANHO_PAGINA = 15;

    let dados = null;
    let ordenacao = {campo: 'dataHoraInicio', asc: false};
    let pagina = 0;

    const colunas = document.querySelectorAll('#tabelaRelatorio thead th[data-campo]');

    function valorFiltro(id) {
        const el = document.getElementById(id);
        return el ? el.value : '';
    }

    function filtros() {
        return {
            de: valorFiltro('filtroDe'),
            ate: valorFiltro('filtroAte'),
            fabricaId: valorFiltro('filtroFabrica'),
            veiculoId: valorFiltro('filtroVeiculo'),
            motoristaId: valorFiltro('filtroMotorista'),
            estado: valorFiltro('filtroEstado')
        };
    }

    function queryString(incluirFormato) {
        const params = new URLSearchParams();
        const f = filtros();
        Object.keys(f).forEach(function (chave) {
            if (f[chave]) {
                params.set(chave, f[chave]);
            }
        });
        if (incluirFormato) {
            params.set('formato', incluirFormato);
        }
        return params.toString();
    }

    function valorCampo(objeto, campo) {
        if (campo.indexOf('fases.') === 0) {
            return objeto.fases ? objeto.fases[campo.slice(6)] : null;
        }
        return objeto[campo];
    }

    function minutosFase(linha, chave) {
        const valor = linha.fases ? linha.fases[chave] : null;
        return valor === null || valor === undefined ? '—' : LT.fmt.duracao(valor);
    }

    function comparar(a, b, campo, tipo) {
        let va = valorCampo(a, campo);
        let vb = valorCampo(b, campo);
        if (tipo === 'numero') {
            return (Number(va) || 0) - (Number(vb) || 0);
        }
        if (tipo === 'data') {
            if (!va && !vb) return 0;
            if (!va) return 1;
            if (!vb) return -1;
            return new Date(va).getTime() - new Date(vb).getTime();
        }
        va = String(va === null || va === undefined ? '' : va).toLowerCase();
        vb = String(vb === null || vb === undefined ? '' : vb).toLowerCase();
        return va.localeCompare(vb, 'pt');
    }

    function linhasOrdenadas() {
        if (!dados || !dados.linhas) {
            return [];
        }
        const th = document.querySelector('#tabelaRelatorio thead th[data-campo="'
            + ordenacao.campo + '"]');
        const tipo = th ? th.dataset.tipo : 'texto';
        const copia = dados.linhas.slice();
        copia.sort(function (a, b) {
            const resultado = comparar(a, b, ordenacao.campo, tipo);
            return ordenacao.asc ? resultado : -resultado;
        });
        return copia;
    }

    function atualizarCabecalhos() {
        colunas.forEach(function (th) {
            const base = th.textContent.replace(/[ ▲▼]+$/, '');
            th.textContent = base + (th.dataset.campo === ordenacao.campo
                ? (ordenacao.asc ? ' ▲' : ' ▼') : '');
        });
    }

    function renderizarResumo(resumo) {
        const valorHoras = function (horas) {
            return horas === null || horas === undefined
                ? 'sem horário registado' : LT.fmt.numero(horas, 1) + ' h';
        };
        const valores = [
            LT.fmt.numero(resumo.total, 0),
            LT.fmt.numero(resumo.toneladas, 1) + ' t',
            LT.fmt.numero(resumo.concluidos, 0),
            LT.fmt.numero(resumo.atrasados, 0),
            valorHoras(resumo.tempoMedioHoras),
            valorHoras(resumo.esperaMediaHoras),
            valorHoras(resumo.carregamentoMedioHoras),
            valorHoras(resumo.transporteMedioHoras)
        ];
        const itens = document.querySelectorAll('#resumoRelatorio .rs-item .val');
        itens.forEach(function (el, indice) {
            if (valores[indice] !== undefined) {
                el.textContent = valores[indice];
            }
        });
    }

    function colunasExtrasVisiveis() {
        const tabela = document.getElementById('tabelaRelatorio');
        return tabela && !tabela.classList.contains('relatorio-compacto');
    }

    function atualizarBotaoColunas() {
        const botao = document.getElementById('btnColunas');
        if (!botao) return;
        const visiveis = colunasExtrasVisiveis();
        botao.textContent = visiveis ? 'Mostrar menos colunas' : 'Mostrar mais colunas';
        botao.setAttribute('aria-expanded', visiveis ? 'true' : 'false');
    }

    function renderizarTabela() {
        const tbody = document.getElementById('tabelaRelatorioCorpo');
        const linhas = linhasOrdenadas();
        const totalPaginas = Math.max(1, Math.ceil(linhas.length / TAMANHO_PAGINA));
        if (pagina >= totalPaginas) {
            pagina = totalPaginas - 1;
        }
        const inicio = pagina * TAMANHO_PAGINA;
        const visiveis = linhas.slice(inicio, inicio + TAMANHO_PAGINA);

        if (!visiveis.length) {
            tbody.innerHTML = '<tr><td colspan="14" class="empty">'
                + 'Nenhum carregamento corresponde aos filtros selecionados.</td></tr>';
        } else {
            tbody.innerHTML = visiveis.map(function (l) {
                return '<tr>'
                    + '<td><span class="guia">' + LT.escape(l.guiaNumero) + '</span></td>'
                    + '<td>' + LT.escape(l.fabrica || '—') + '</td>'
                    + '<td><span class="mono">' + LT.escape(l.veiculo || '—') + '</span></td>'
                    + '<td>' + LT.escape(l.motorista || '—') + '</td>'
                    + '<td class="col-extra">' + LT.escape(l.tipoCarga || '—') + '</td>'
                    + '<td class="col-extra">' + LT.fmt.numero(l.toneladas, 1) + '</td>'
                    + '<td>' + LT.chip(estadoChave(l.estado), l.estado) + '</td>'
                    + '<td>' + LT.fmt.dataHora(l.dataHoraInicio) + '</td>'
                    + '<td class="col-extra">' + LT.fmt.dataHora(l.dataHoraFim) + '</td>'
                    + '<td>' + (l.atrasoMinutos > 0
                        ? '<span style="color:#c0392b;font-weight:700">+' + LT.fmt.duracao(l.atrasoMinutos) + '</span>'
                        : '<span class="muted">—</span>') + '</td>'
                    + '<td class="col-extra">' + minutosFase(l, 'esperaMinutos') + '</td>'
                    + '<td class="col-extra">' + minutosFase(l, 'carregamentoMinutos') + '</td>'
                    + '<td class="col-extra">' + minutosFase(l, 'transporteMinutos') + '</td>'
                    + '<td><a class="btn small ghost" href="' + LT.urlCarregamento(l.id) + '">'
                    + 'Abrir</a></td>'
                    + '</tr>';
            }).join('');
        }

        document.getElementById('infoPaginaRelatorio').textContent = linhas.length
            ? 'Página ' + (pagina + 1) + ' de ' + totalPaginas + ' · ' + linhas.length + ' carregamentos'
            : 'Sem registos';
        document.getElementById('btnAnterior').disabled = pagina === 0;
        document.getElementById('btnSeguinte').disabled = pagina >= totalPaginas - 1;
        atualizarBotaoColunas();
    }

    function estadoChave(etiqueta) {
        const mapa = {
            'Registado': 'registado',
            'Em trânsito': 'em_transito',
            'Atrasado': 'atrasado',
            'Concluído': 'concluido',
            'Rejeitado': 'rejeitado'
        };
        return mapa[etiqueta] || 'registado';
    }

    function renderizar(dadosResposta) {
        dados = dadosResposta;
        pagina = 0;
        renderizarResumo(dados.resumo);
        atualizarCabecalhos();
        renderizarTabela();
        document.getElementById('chipTotal').textContent = dados.resumo.total + ' carregamentos';
        document.getElementById('infoGeracao').textContent = 'Gerado às ' + LT.fmt.hora(new Date());
    }

    async function gerar() {
        const botao = document.getElementById('btnGerar');
        botao.disabled = true;
        try {
            const resposta = await LT.api('/api/v1/relatorios?' + queryString());
            renderizar(resposta);
            LT.toast('Relatório pronto',
                resposta.resumo.total + ' carregamentos no período escolhido.', 'success');
        } catch (e) {
            LT.erro(e);
        } finally {
            botao.disabled = false;
        }
    }

    async function exportar(formato) {
        const caminho = '/api/v1/relatorios/exportar?' + queryString(formato);
        try {
            await LT.download(caminho);
            LT.toast('Download iniciado',
                'O ficheiro ' + formato.toUpperCase() + ' está a ser guardado.', 'success');
        } catch (e) {
            LT.erro(e);
        }
    }

    async function carregarReferencias() {
        const tarefas = [
            LT.carregarFabricas().then(function (fabricas) {
                LT.preencherSelect(document.getElementById('filtroFabrica'), fabricas,
                    'Todas', 'id', 'nome');
            }).catch(function (e) {
                LT.erroReferencia('as fábricas', e);
            }),
            LT.carregarVeiculos().then(function (veiculos) {
                LT.preencherSelect(document.getElementById('filtroVeiculo'), veiculos,
                    'Todos', 'id', function (v) {
                        return v.matricula + ' · ' + v.modelo;
                    });
            }).catch(function (e) {
                LT.erroReferencia('os veículos', e);
            }),
            LT.carregarMotoristas().then(function (motoristas) {
                LT.preencherSelect(document.getElementById('filtroMotorista'), motoristas,
                    'Todos', 'id', 'nome');
            }).catch(function (e) {
                LT.erroReferencia('os motoristas', e);
            })
        ];
        await Promise.all(tarefas);
    }

    document.getElementById('btnGerar').addEventListener('click', gerar);
    document.getElementById('btnLimpar').addEventListener('click', function () {
        ['filtroDe', 'filtroAte', 'filtroFabrica', 'filtroVeiculo',
            'filtroMotorista', 'filtroEstado'].forEach(function (id) {
            document.getElementById(id).value = '';
        });
        gerar();
    });
    document.getElementById('btnExportarPdf').addEventListener('click', function () {
        exportar('pdf');
    });
    document.getElementById('btnExportarXlsx').addEventListener('click', function () {
        exportar('xlsx');
    });
    document.getElementById('btnAnterior').addEventListener('click', function () {
        if (pagina > 0) {
            pagina -= 1;
            renderizarTabela();
        }
    });
    document.getElementById('btnSeguinte').addEventListener('click', function () {
        const totalPaginas = Math.max(1, Math.ceil(linhasOrdenadas().length / TAMANHO_PAGINA));
        if (pagina < totalPaginas - 1) {
            pagina += 1;
            renderizarTabela();
        }
    });
    const btnColunas = document.getElementById('btnColunas');
    const tabelaRelatorio = document.getElementById('tabelaRelatorio');
    if (tabelaRelatorio) {
        tabelaRelatorio.classList.add('relatorio-compacto');
    }
    if (btnColunas && tabelaRelatorio) {
        btnColunas.addEventListener('click', function () {
            tabelaRelatorio.classList.toggle('relatorio-compacto');
            atualizarBotaoColunas();
        });
        atualizarBotaoColunas();
    }
    colunas.forEach(function (th) {
        th.addEventListener('click', function () {
            const campo = th.dataset.campo;
            if (ordenacao.campo === campo) {
                ordenacao.asc = !ordenacao.asc;
            } else {
                ordenacao = {campo: campo, asc: campo !== 'dataHoraInicio'};
            }
            pagina = 0;
            atualizarCabecalhos();
            renderizarTabela();
        });
    });

    carregarReferencias().then(gerar);
})();
