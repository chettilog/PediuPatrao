// Dashboard administrativo - consome a API /api/dashboard/** (RF06) e desenha o painel.
// Sem frameworks: fetch + Chart.js. Os filtros ficam na URL, entao recarregar a pagina mantem a consulta.
(function () {
    'use strict';

    const API = '/api/dashboard';
    const INTERVALO_AUTO_MS = 60000;
    const COR = {
        serie1: '#2a78d6', serie1Area: 'rgba(42,120,214,0.10)', serie2: '#eb6834',
        grade: '#e1e0d9', eixo: '#c3c2b7', textoMudo: '#898781', texto2: '#52514e', superficie: '#fcfcfb'
    };

    const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
    const moedaCurta = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
    const inteiro = new Intl.NumberFormat('pt-BR');
    const compacto = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 });
    const decimal1 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

    const $ = (id) => document.getElementById(id);
    const estado = {
        inicio: '', fim: '', tipoAtendimento: '', status: '',
        agrupamento: 'DIA', medida: 'faturamento', criterio: 'QUANTIDADE', categoria: '', pagina: 0
    };
    const graficos = {};
    let controlador = null;      // AbortController da carga em andamento
    let timerAuto = null;
    let ultimaSerie = null;

    // ------------------------------------------------------------------ utilidades

    function esc(texto) {
        return String(texto == null ? '' : texto)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function dataBr(iso) {
        if (!iso) return '';
        const [a, m, d] = iso.substring(0, 10).split('-');
        return d + '/' + m + '/' + a;
    }

    function dataHoraBr(iso) {
        if (!iso) return '—';
        return dataBr(iso) + ' ' + iso.substring(11, 16);
    }

    function hojeIso() {
        const d = new Date();
        return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 10);
    }

    function somarDias(iso, dias) {
        const d = new Date(iso + 'T12:00:00');
        d.setDate(d.getDate() + dias);
        return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().substring(0, 10);
    }

    function minutosTexto(min) {
        if (min < 60) return min + ' min';
        const h = Math.floor(min / 60), m = min % 60;
        if (h >= 48) return Math.floor(h / 24) + ' dias';
        return h + 'h' + (m ? String(m).padStart(2, '0') : '');
    }

    function plural(n, singular, pluralTexto) {
        return inteiro.format(n) + ' ' + (n === 1 ? singular : pluralTexto);
    }

    function selo(status, rotulo) {
        return '<span class="badge selo-status st-' + esc(status) + '">' + esc(rotulo) + '</span>';
    }

    function params(extra) {
        const p = new URLSearchParams();
        ['inicio', 'fim', 'tipoAtendimento', 'status'].forEach((k) => { if (estado[k]) p.set(k, estado[k]); });
        Object.entries(extra || {}).forEach(([k, v]) => { if (v !== '' && v != null) p.set(k, v); });
        const s = p.toString();
        return s ? '?' + s : '';
    }

    /** Erro com status HTTP e a mensagem que a API devolveu no campo "erro". */
    class ErroApi extends Error {
        constructor(status, mensagem) { super(mensagem); this.status = status; }
    }

    async function api(caminho, sinal) {
        let resp;
        try {
            resp = await fetch(API + caminho, { headers: { Accept: 'application/json' }, signal: sinal, credentials: 'same-origin' });
        } catch (e) {
            if (e.name === 'AbortError') throw e;
            throw new ErroApi(0, 'Sem conexão com o servidor. Verifique se o sistema está no ar.');
        }
        if (resp.status === 401 || resp.redirected && resp.url.includes('/login')) {
            window.location.href = '/login';
            throw new ErroApi(401, 'Sessão expirada.');
        }
        let corpo = null;
        try { corpo = await resp.json(); } catch (e) { /* corpo vazio ou nao-JSON */ }
        if (!resp.ok) {
            const msg = corpo && corpo.erro ? corpo.erro
                : resp.status === 403 ? 'Seu perfil não tem acesso ao dashboard.'
                : 'Erro ' + resp.status + ' ao consultar ' + caminho + '.';
            throw new ErroApi(resp.status, msg);
        }
        return corpo;
    }

    // ------------------------------------------------------------------ estado <-> URL/formulario

    function lerUrl() {
        const q = new URLSearchParams(window.location.search);
        Object.keys(estado).forEach((k) => {
            if (q.has(k)) estado[k] = k === 'pagina' ? Math.max(0, parseInt(q.get(k), 10) || 0) : q.get(k);
        });
    }

    function gravarUrl() {
        const q = new URLSearchParams();
        Object.entries(estado).forEach(([k, v]) => { if (v !== '' && v !== 0 && v != null) q.set(k, v); });
        ['agrupamento', 'medida', 'criterio'].forEach((k) => {
            if (q.get(k) === { agrupamento: 'DIA', medida: 'faturamento', criterio: 'QUANTIDADE' }[k]) q.delete(k);
        });
        const s = q.toString();
        history.replaceState(null, '', window.location.pathname + (s ? '?' + s : ''));
    }

    function preencherFormulario() {
        $('db-inicio').value = estado.inicio;
        $('db-fim').value = estado.fim;
        $('db-tipo').value = estado.tipoAtendimento;
        $('db-status').value = estado.status;
        $('db-agrupamento').value = estado.agrupamento;
        $('db-categoria').value = estado.categoria;
        document.querySelectorAll('input[name="db-medida"]').forEach((r) => { r.checked = r.value === estado.medida; });
        document.querySelectorAll('input[name="db-criterio"]').forEach((r) => { r.checked = r.value === estado.criterio; });
        marcarPreset();
    }

    function marcarPreset() {
        const hoje = hojeIso();
        document.querySelectorAll('#db-presets [data-preset]').forEach((b) => {
            const [i, f] = intervaloPreset(b.dataset.preset, hoje);
            b.classList.toggle('active', estado.inicio === i && estado.fim === f);
        });
    }

    function intervaloPreset(preset, hoje) {
        if (preset === 'hoje') return [hoje, hoje];
        if (preset === 'mes') return [hoje.substring(0, 8) + '01', hoje];
        return [somarDias(hoje, -(parseInt(preset, 10) - 1)), hoje];
    }

    /** Validacao no navegador (a API valida de novo e devolve 400 se algo passar). */
    function validarFiltros() {
        const erro = $('db-erro-filtro');
        let msg = '';
        if (!estado.inicio || !estado.fim) msg = 'Informe as duas datas do período.';
        else if (estado.inicio > estado.fim) msg = 'A data inicial não pode ser posterior à data final.';
        erro.textContent = msg;
        erro.hidden = !msg;
        return !msg;
    }

    // ------------------------------------------------------------------ carga (RF06 / RF07)

    async function carregarOpcoes() {
        const op = await api('/filtros');
        const preencher = (sel, lista, valor, rotulo) => {
            lista.forEach((o) => {
                const opt = document.createElement('option');
                opt.value = valor(o);
                opt.textContent = rotulo(o);
                sel.appendChild(opt);
            });
        };
        preencher($('db-tipo'), op.tiposAtendimento, (o) => o.valor, (o) => o.rotulo);
        preencher($('db-status'), op.status, (o) => o.valor, (o) => o.rotulo);
        preencher($('db-categoria'), op.categorias, (c) => c, (c) => c);
        if (!estado.inicio) estado.inicio = op.inicioPadrao;
        if (!estado.fim) estado.fim = op.fimPadrao;
    }

    async function carregarTudo(opcoes) {
        const silencioso = opcoes && opcoes.silencioso;
        if (!validarFiltros()) return;
        gravarUrl();
        marcarPreset();
        if (controlador) controlador.abort();
        controlador = new AbortController();
        const sinal = controlador.signal;

        if (!silencioso) document.querySelectorAll('.db-bloco').forEach((b) => b.classList.add('carregando'));
        $('db-periodo-texto').textContent = 'Período: ' + dataBr(estado.inicio) + ' a ' + dataBr(estado.fim);

        const tarefas = [
            api('/resumo' + params(), sinal).then(desenharResumo),
            api('/vendas' + params({ agrupamento: estado.agrupamento }), sinal).then(desenharVendas),
            api('/vendas/distribuicao' + params(), sinal).then(desenharDistribuicao),
            api('/pedidos/status' + params(), sinal).then(desenharStatus),
            api('/pedidos/em-andamento' + (estado.tipoAtendimento ? '?tipoAtendimento=' + estado.tipoAtendimento : ''), sinal).then(desenharFila),
            carregarRanking(sinal),
            carregarPedidos(sinal)
        ];
        const resultados = await Promise.allSettled(tarefas);
        if (sinal.aborted) return;

        document.querySelectorAll('.db-bloco').forEach((b) => b.classList.remove('carregando'));
        const falhas = resultados.filter((r) => r.status === 'rejected' && r.reason && r.reason.name !== 'AbortError');
        if (falhas.length) {
            const e = falhas[0].reason;
            mostrarErro(e instanceof ErroApi ? e.message : 'Erro inesperado ao montar o painel.');
            if (!(e instanceof ErroApi)) console.error(e);
        } else {
            $('db-erro').hidden = true;
            $('db-atualizado').textContent = 'Atualizado às ' + new Date().toLocaleTimeString('pt-BR');
        }
    }

    function carregarRanking(sinal) {
        return api('/produtos/mais-vendidos' + params({ criterio: estado.criterio, categoria: estado.categoria, limite: 10 }), sinal)
            .then(desenharRanking);
    }

    function carregarPedidos(sinal) {
        return api('/pedidos' + params({ pagina: estado.pagina, tamanho: 10 }), sinal).then(desenharPedidos);
    }

    function mostrarErro(texto) {
        $('db-erro-texto').textContent = texto;
        $('db-erro').hidden = false;
    }

    function vazio(bloco, estaVazio) {
        const el = $(bloco).querySelector('.db-vazio');
        if (el) el.hidden = !estaVazio;
        const canvas = $(bloco).querySelector('.db-grafico');
        if (canvas) canvas.hidden = estaVazio;
    }

    // ------------------------------------------------------------------ RF01 indicadores

    function desenharResumo(r) {
        const c = r.comparacao;
        const anterior = 'vs ' + dataBr(c.periodoAnterior.inicio).substring(0, 5) + '–' + dataBr(c.periodoAnterior.fim).substring(0, 5);
        const tiles = [
            { rotulo: 'Faturamento', valor: moeda.format(r.faturamento), delta: c.variacaoFaturamento, subirEBom: true,
              extra: 'Bruto ' + moeda.format(r.faturamentoBruto) },
            { rotulo: 'Pedidos', valor: inteiro.format(r.pedidosValidos), delta: c.variacaoPedidos, subirEBom: true,
              extra: inteiro.format(r.pedidosFinalizados) + ' finalizados · ' + inteiro.format(r.pedidosEmAndamento) + ' em andamento' },
            { rotulo: 'Ticket médio', valor: moeda.format(r.ticketMedio), delta: c.variacaoTicketMedio, subirEBom: true,
              extra: plural(r.itensVendidos, 'item vendido', 'itens vendidos') },
            { rotulo: 'Cancelamentos', valor: decimal1.format(r.taxaCancelamento) + '%',
              deltaPp: r.taxaCancelamento - c.taxaCancelamento, subirEBom: false, temBase: c.pedidosValidos > 0,
              extra: plural(r.pedidosCancelados, 'pedido', 'pedidos') + ' · ' + moeda.format(r.valorCancelado) },
            { rotulo: 'Descontos concedidos', valor: moeda.format(r.totalDescontos), neutro: true,
              extra: (r.faturamentoBruto > 0 ? decimal1.format(r.totalDescontos * 100 / r.faturamentoBruto) : '0') + '% do faturamento bruto' },
            { rotulo: 'Tempo até a saída', valor: r.tempoMedioSaidaMinutos == null ? '—' : decimal1.format(r.tempoMedioSaidaMinutos) + ' min',
              neutro: true, extra: 'média da entrada à saída' }
        ];
        $('db-kpis').innerHTML = tiles.map((t) => {
            let delta = '';
            if (t.neutro) {
                delta = '&nbsp;';
            } else if (t.deltaPp !== undefined) {
                if (!t.temBase) delta = '— sem base ' + anterior;
                else {
                    const pp = Math.round(t.deltaPp * 10) / 10;
                    const classe = pp === 0 ? '' : (pp > 0) === t.subirEBom ? 'bom' : 'ruim';
                    delta = '<span class="' + classe + '">' + (pp > 0 ? '▲ ' : pp < 0 ? '▼ ' : '') + decimal1.format(Math.abs(pp)) + ' p.p.</span> ' + anterior;
                }
            } else if (t.delta == null) {
                delta = '— sem base ' + anterior;
            } else {
                const classe = t.delta === 0 ? '' : (t.delta > 0) === t.subirEBom ? 'bom' : 'ruim';
                delta = '<span class="' + classe + '">' + (t.delta > 0 ? '▲ ' : t.delta < 0 ? '▼ ' : '') + decimal1.format(Math.abs(t.delta)) + '%</span> ' + anterior;
            }
            return '<div class="col-6 col-lg-4 col-xxl-2"><div class="card db-kpi h-100"><div class="card-body">'
                + '<div class="db-kpi-rotulo">' + esc(t.rotulo) + '</div>'
                + '<div class="db-kpi-valor">' + esc(t.valor) + '</div>'
                + '<div class="db-kpi-delta">' + delta + '</div>'
                + '<div class="db-kpi-extra">' + esc(t.extra) + '</div>'
                + '</div></div></div>';
        }).join('');
    }

    // ------------------------------------------------------------------ RF02 graficos

    // linha vertical acompanhando o tooltip (crosshair) no grafico de linha
    const crosshair = {
        id: 'crosshair',
        afterDatasetsDraw(chart) {
            const ativo = chart.tooltip && chart.tooltip.getActiveElements();
            if (!ativo || !ativo.length) return;
            const x = ativo[0].element.x, area = chart.chartArea, ctx = chart.ctx;
            ctx.save();
            ctx.strokeStyle = COR.eixo;
            ctx.lineWidth = 1;
            ctx.beginPath();
            ctx.moveTo(x, area.top);
            ctx.lineTo(x, area.bottom);
            ctx.stroke();
            ctx.restore();
        }
    };

    function eixos(formatoY) {
        return {
            x: { grid: { display: false }, border: { color: COR.eixo },
                 ticks: { color: COR.textoMudo, maxRotation: 0, autoSkipPadding: 12, font: { size: 11 } } },
            y: { beginAtZero: true, grid: { color: COR.grade }, border: { display: false },
                 ticks: { color: COR.textoMudo, font: { size: 11 }, maxTicksLimit: 5, callback: formatoY } }
        };
    }

    function baseOpcoes(formatoY, tooltipLabel) {
        return {
            responsive: true, maintainAspectRatio: false, animation: { duration: 250 },
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: { backgroundColor: '#0b0b0b', padding: 8, displayColors: false,
                           callbacks: { label: tooltipLabel } }
            },
            scales: eixos(formatoY)
        };
    }

    const formatoMoedaEixo = (v) => 'R$ ' + compacto.format(v);
    const formatoInteiroEixo = (v) => (Number.isInteger(v) ? compacto.format(v) : '');

    function colunas(id, rotulos, valores, formatoY, tooltipLabel) {
        const dados = { labels: rotulos, datasets: [{ data: valores, backgroundColor: COR.serie1, hoverBackgroundColor: '#256abf',
            borderRadius: { topLeft: 4, topRight: 4 }, borderSkipped: 'start', maxBarThickness: 24 }] };
        if (graficos[id]) {
            graficos[id].data = dados;
            graficos[id].options = baseOpcoes(formatoY, tooltipLabel);
            graficos[id].update();
        } else {
            graficos[id] = new Chart($(id), { type: 'bar', data: dados, options: baseOpcoes(formatoY, tooltipLabel) });
        }
    }

    function desenharVendas(s) {
        ultimaSerie = s;
        const porFaturamento = estado.medida === 'faturamento';
        const temVenda = s.totalPedidos > 0;
        vazio('bloco-vendas', !temVenda);
        $('db-vendas-total').textContent = temVenda
            ? moeda.format(s.totalFaturamento) + ' em ' + plural(s.totalPedidos, 'pedido', 'pedidos') + ' (cancelados não entram)'
            : '';

        const rotulos = s.pontos.map((p) => p.rotulo);
        const valores = s.pontos.map((p) => (porFaturamento ? Number(p.faturamento) : p.pedidos));
        const tooltip = (ctx) => {
            const p = s.pontos[ctx.dataIndex];
            return [moeda.format(p.faturamento), plural(p.pedidos, 'pedido', 'pedidos'), 'Ticket médio ' + moeda.format(p.ticketMedio)];
        };
        const poucosPontos = s.pontos.length <= 2;
        const dados = { labels: rotulos, datasets: [{
            data: valores, borderColor: COR.serie1, backgroundColor: COR.serie1Area, fill: true, tension: 0.25,
            borderWidth: 2, borderCapStyle: 'round', borderJoinStyle: 'round',
            pointRadius: poucosPontos ? 4 : 0, pointHoverRadius: 5, pointBackgroundColor: COR.serie1,
            pointBorderColor: COR.superficie, pointBorderWidth: 2, pointHitRadius: 12
        }] };
        const opcoes = baseOpcoes(porFaturamento ? formatoMoedaEixo : formatoInteiroEixo, tooltip);
        $('titulo-vendas').textContent = porFaturamento ? 'Faturamento no período' : 'Pedidos no período';

        if (graficos.vendas) {
            graficos.vendas.data = dados;
            graficos.vendas.options = opcoes;
            graficos.vendas.update();
        } else {
            graficos.vendas = new Chart($('graf-vendas'), { type: 'line', data: dados, options: opcoes, plugins: [crosshair] });
        }
        desenharTabelaVendas();
    }

    function desenharTabelaVendas() {
        const caixa = document.querySelector('.db-tabela-vendas');
        const ligada = $('db-vendas-tabela').getAttribute('aria-pressed') === 'true';
        caixa.hidden = !ligada || !ultimaSerie || ultimaSerie.totalPedidos === 0;
        if (caixa.hidden) return;
        caixa.innerHTML = '<table class="table table-sm mb-0"><thead><tr><th>Período</th><th class="num">Pedidos</th>'
            + '<th class="num">Faturamento</th><th class="num">Ticket médio</th></tr></thead><tbody>'
            + ultimaSerie.pontos.map((p) => '<tr><td>' + esc(p.rotulo) + '</td><td class="num">' + inteiro.format(p.pedidos)
                + '</td><td class="num">' + moeda.format(p.faturamento) + '</td><td class="num">' + moeda.format(p.ticketMedio) + '</td></tr>').join('')
            + '</tbody></table>';
    }

    function desenharDistribuicao(d) {
        // horario: corta as horas vazias das pontas para o grafico nao ficar espremido
        const comDados = d.porHora.filter((f) => f.pedidos > 0);
        vazio('bloco-hora', comDados.length === 0);
        if (comDados.length) {
            const primeira = d.porHora.indexOf(comDados[0]);
            const ultima = d.porHora.indexOf(comDados[comDados.length - 1]);
            const horas = d.porHora.slice(primeira, ultima + 1);
            const pico = comDados.reduce((a, b) => (b.pedidos > a.pedidos ? b : a));
            $('db-pico').textContent = 'Pico às ' + pico.rotulo + ' (' + plural(pico.pedidos, 'pedido', 'pedidos') + ', '
                + decimal1.format(pico.percentual) + '% do faturamento)';
            colunas('graf-hora', horas.map((f) => f.rotulo), horas.map((f) => f.pedidos), formatoInteiroEixo,
                (ctx) => [plural(horas[ctx.dataIndex].pedidos, 'pedido', 'pedidos'), moeda.format(horas[ctx.dataIndex].faturamento)]);
        } else {
            $('db-pico').textContent = '';
        }

        const semanaTem = d.porDiaSemana.some((f) => f.pedidos > 0);
        vazio('bloco-semana', !semanaTem);
        if (semanaTem) {
            const melhor = d.porDiaSemana.reduce((a, b) => (Number(b.faturamento) > Number(a.faturamento) ? b : a));
            $('db-melhor-dia').textContent = 'Melhor dia: ' + melhor.rotulo + ' (' + decimal1.format(melhor.percentual) + '%)';
            colunas('graf-semana', d.porDiaSemana.map((f) => f.rotulo), d.porDiaSemana.map((f) => Number(f.faturamento)),
                formatoMoedaEixo, (ctx) => {
                    const f = d.porDiaSemana[ctx.dataIndex];
                    return [moeda.format(f.faturamento), plural(f.pedidos, 'pedido', 'pedidos')];
                });
        } else {
            $('db-melhor-dia').textContent = '';
        }

        // entrega x retirada: barra segmentada com legenda (identidade nunca so pela cor)
        const tipos = d.porTipoAtendimento.filter((f) => f.pedidos > 0);
        const cores = [COR.serie1, COR.serie2, COR.textoMudo];
        $('db-tipo-barra').innerHTML = tipos.map((f, i) =>
            '<span style="flex:' + Math.max(Number(f.percentual), 0.5) + ';background:' + cores[i % cores.length] + '"></span>').join('');
        $('db-tipo-legenda').innerHTML = tipos.map((f, i) =>
            '<li class="list-inline-item"><span class="chave" style="background:' + cores[i % cores.length] + '"></span>'
            + esc(f.rotulo) + ' <strong>' + decimal1.format(f.percentual) + '%</strong> · ' + plural(f.pedidos, 'pedido', 'pedidos') + '</li>').join('');

        barras($('db-categorias'), d.porCategoria.map((f) => ({
            nome: f.rotulo, valor: Number(f.faturamento), texto: moedaCurta.format(f.faturamento), sufixo: decimal1.format(f.percentual) + '%'
        })));
        vazio('bloco-mix', tipos.length === 0);
        $('db-cat-titulo').hidden = d.porCategoria.length === 0;
    }

    /** Lista de barras horizontais em HTML (rotulo, barra, valor). Largura relativa ao maior valor. */
    function barras(ul, itens, comPosicao) {
        const max = Math.max(0, ...itens.map((i) => i.valor));
        ul.innerHTML = itens.map((i, idx) => {
            const largura = max > 0 ? Math.max(i.valor / max * 100, i.valor > 0 ? 1 : 0) : 0;
            return '<li class="' + (i.valor === 0 ? 'zerado' : '') + '">'
                + (comPosicao ? '<span class="pos">' + (idx + 1) + '</span>' : '')
                + '<span class="nome" title="' + esc(i.nome) + '">' + (i.html || esc(i.nome)) + (i.detalhe ? '<span class="cat">' + esc(i.detalhe) + '</span>' : '') + '</span>'
                + '<span class="trilha">' + (largura > 0 ? '<span class="barra d-block" style="width:' + largura.toFixed(1) + '%"></span>' : '') + '</span>'
                + '<span class="valor">' + esc(i.texto) + (i.sufixo ? '<small>' + esc(i.sufixo) + '</small>' : '') + '</span>'
                + '</li>';
        }).join('');
    }

    // ------------------------------------------------------------------ RF03 situacao

    function desenharStatus(s) {
        vazio('bloco-status', s.total === 0);
        $('db-status-total').textContent = s.total ? plural(s.total, 'pedido', 'pedidos') + ' no período' : '';
        barras($('db-status-lista'), s.total === 0 ? [] : s.porStatus.map((st) => ({
            nome: st.rotulo, html: selo(st.status, st.rotulo), valor: st.quantidade,
            texto: inteiro.format(st.quantidade), sufixo: decimal1.format(st.percentual) + '%'
        })));
    }

    function desenharFila(f) {
        vazio('bloco-fila', f.total === 0);
        $('db-fila-resumo').innerHTML = f.total === 0 ? ''
            : inteiro.format(f.total) + ' em andamento'
              + (f.atrasados ? ' · <span class="db-atraso"><i class="bi bi-exclamation-triangle-fill"></i> '
                  + inteiro.format(f.atrasados) + ' acima de ' + f.limiteMinutos + ' min</span>' : '');
        $('db-fila').innerHTML = f.pedidos.map((p) => '<tr>'
            + '<td class="ps-3"><a href="/pedidos/' + encodeURIComponent(p.id) + '">#' + esc(p.codigo) + '</a></td>'
            + '<td>' + esc(p.clienteNome) + '</td>'
            + '<td>' + selo(p.status, p.statusRotulo) + '</td>'
            + '<td class="num">' + (p.atrasado
                ? '<span class="db-atraso" title="Acima de ' + f.limiteMinutos + ' min ainda na pizzaria"><i class="bi bi-exclamation-triangle-fill"></i> Atrasado · ' + minutosTexto(p.minutosDesdeEntrada) + '</span>'
                : minutosTexto(p.minutosDesdeEntrada)) + '</td>'
            + '<td class="num pe-3">' + moeda.format(p.valorTotal) + '</td>'
            + '</tr>').join('');
    }

    function desenharPedidos(pg) {
        vazio('bloco-pedidos', pg.totalElementos === 0);
        $('db-pedidos-info').textContent = pg.totalElementos === 0 ? ''
            : 'Página ' + (pg.pagina + 1) + ' de ' + pg.totalPaginas + ' · ' + plural(pg.totalElementos, 'pedido', 'pedidos');
        $('db-pedidos').innerHTML = pg.conteudo.map((p) => '<tr>'
            + '<td class="ps-3"><a href="/pedidos/' + encodeURIComponent(p.id) + '">#' + esc(p.codigo) + '</a></td>'
            + '<td>' + dataHoraBr(p.entradaEm) + '</td>'
            + '<td>' + esc(p.clienteNome) + '</td>'
            + '<td>' + esc(p.tipoAtendimentoRotulo) + '</td>'
            + '<td>' + (p.status ? selo(p.status, p.statusRotulo) : esc(p.statusRotulo)) + '</td>'
            + '<td class="num">' + inteiro.format(p.quantidadeItens) + '</td>'
            + '<td class="num">' + (Number(p.valorDesconto) > 0 ? moeda.format(p.valorDesconto) : '—') + '</td>'
            + '<td class="num pe-3">' + moeda.format(p.valorTotal) + '</td>'
            + '</tr>').join('');
        $('db-pag-ant').disabled = pg.pagina <= 0;
        $('db-pag-prox').disabled = pg.pagina + 1 >= pg.totalPaginas;
        estado.pagina = pg.pagina;
    }

    // ------------------------------------------------------------------ RF04 ranking

    function desenharRanking(r) {
        vazio('bloco-produtos', r.produtos.length === 0);
        const porFat = r.criterio === 'FATURAMENTO';
        barras($('db-ranking'), r.produtos.map((p) => ({
            nome: p.nome, detalhe: p.categoria,
            valor: porFat ? Number(p.faturamento) : p.quantidade,
            texto: porFat ? moedaCurta.format(p.faturamento) : inteiro.format(p.quantidade) + ' un.',
            sufixo: porFat ? decimal1.format(p.participacao) + '%' : moedaCurta.format(p.faturamento)
        })), true);
    }

    // ------------------------------------------------------------------ eventos

    function aplicarFormulario() {
        estado.inicio = $('db-inicio').value;
        estado.fim = $('db-fim').value;
        estado.tipoAtendimento = $('db-tipo').value;
        estado.status = $('db-status').value;
        estado.pagina = 0;
    }

    function ligarEventos() {
        $('db-form').addEventListener('submit', (e) => { e.preventDefault(); aplicarFormulario(); carregarTudo(); });
        ['db-tipo', 'db-status'].forEach((id) => $(id).addEventListener('change', () => { aplicarFormulario(); carregarTudo(); }));
        document.querySelectorAll('#db-presets [data-preset]').forEach((b) => b.addEventListener('click', () => {
            const [i, f] = intervaloPreset(b.dataset.preset, hojeIso());
            $('db-inicio').value = i;
            $('db-fim').value = f;
            aplicarFormulario();
            carregarTudo();
        }));
        $('db-limpar').addEventListener('click', () => {
            const [i, f] = intervaloPreset('30', hojeIso());
            Object.assign(estado, { inicio: i, fim: f, tipoAtendimento: '', status: '', categoria: '', pagina: 0 });
            preencherFormulario();
            carregarTudo();
        });
        $('db-agrupamento').addEventListener('change', (e) => {
            estado.agrupamento = e.target.value;
            gravarUrl();
            api('/vendas' + params({ agrupamento: estado.agrupamento })).then(desenharVendas).catch((er) => mostrarErro(er.message));
        });
        document.querySelectorAll('input[name="db-medida"]').forEach((r) => r.addEventListener('change', () => {
            estado.medida = r.value;
            gravarUrl();
            if (ultimaSerie) desenharVendas(ultimaSerie);
        }));
        $('db-vendas-tabela').addEventListener('click', (e) => {
            const btn = e.currentTarget;
            btn.setAttribute('aria-pressed', btn.getAttribute('aria-pressed') === 'true' ? 'false' : 'true');
            btn.classList.toggle('active');
            desenharTabelaVendas();
        });
        document.querySelectorAll('input[name="db-criterio"]').forEach((r) => r.addEventListener('change', () => {
            estado.criterio = r.value;
            gravarUrl();
            carregarRanking().catch((er) => mostrarErro(er.message));
        }));
        $('db-categoria').addEventListener('change', (e) => {
            estado.categoria = e.target.value;
            gravarUrl();
            carregarRanking().catch((er) => mostrarErro(er.message));
        });
        $('db-pag-ant').addEventListener('click', () => { estado.pagina = Math.max(0, estado.pagina - 1); gravarUrl(); carregarPedidos().catch((er) => mostrarErro(er.message)); });
        $('db-pag-prox').addEventListener('click', () => { estado.pagina += 1; gravarUrl(); carregarPedidos().catch((er) => mostrarErro(er.message)); });
        $('db-atualizar').addEventListener('click', () => carregarTudo());
        $('db-tentar').addEventListener('click', () => carregarTudo());
        $('db-auto').addEventListener('change', programarAuto);
        document.addEventListener('visibilitychange', () => {
            // aba escondida nao consulta a API; ao voltar, atualiza na hora
            if (!document.hidden && $('db-auto').checked) carregarTudo({ silencioso: true });
            programarAuto();
        });
    }

    /** RF07 - atualizacao automatica a cada 60 s, so com a aba visivel. */
    function programarAuto() {
        clearInterval(timerAuto);
        timerAuto = null;
        if ($('db-auto').checked && !document.hidden) {
            timerAuto = setInterval(() => carregarTudo({ silencioso: true }), INTERVALO_AUTO_MS);
        }
    }

    async function iniciar() {
        if (typeof Chart === 'undefined') {
            mostrarErro('A biblioteca de gráficos não carregou. Recarregue a página.');
            return;
        }
        Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
        Chart.defaults.color = COR.texto2;
        lerUrl();
        ligarEventos();
        try {
            await carregarOpcoes();
        } catch (e) {
            mostrarErro(e.message || 'Não foi possível carregar os filtros.');
            return;
        }
        preencherFormulario();
        await carregarTudo();
        programarAuto();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})();
