package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.dto.DashboardDto.Agrupamento;
import com.umc.pediupatrao.dto.DashboardDto.CriterioRanking;
import com.umc.pediupatrao.dto.DashboardDto.Distribuicao;
import com.umc.pediupatrao.dto.DashboardDto.EmAndamento;
import com.umc.pediupatrao.dto.DashboardDto.Fatia;
import com.umc.pediupatrao.dto.DashboardDto.PaginaPedidos;
import com.umc.pediupatrao.dto.DashboardDto.Periodo;
import com.umc.pediupatrao.dto.DashboardDto.RankingProdutos;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDto.SituacaoPedidos;
import com.umc.pediupatrao.dto.DashboardDto.SituacaoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Regras dos indicadores do dashboard, sem banco e sem Spring.
 * Os valores esperados foram calculados a mao a partir dos pedidos montados em cada teste.
 */
class CalculadoraDashboardTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 2);   // segunda-feira
    private static final Periodo PERIODO = new Periodo(D1, D1.plusDays(6), 7);
    private static final Periodo ANTERIOR = new Periodo(D1.minusDays(7), D1.minusDays(1), 7);
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 8, 21, 0);
    private static final Map<String, String> CATEGORIAS = Map.of("calabresa", "Pizza", "mussarela", "Pizza",
            "refri", "Bebida");

    // ------------------------------------------------------------------ montagem dos pedidos

    private static ItemPedido item(String produtoId, String nome, int qtd, String preco) {
        ItemPedido i = new ItemPedido(produtoId, nome, qtd, new BigDecimal(preco));
        i.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(qtd)).setScale(2, RoundingMode.HALF_UP));
        return i;
    }

    /** Pedido com valores ja calculados e levado ate o status pedido (passando pelas etapas do fluxo). */
    private static Pedido pedido(String id, LocalDateTime entrada, TipoAtendimento tipo, StatusPedido status,
            String percentualDesconto, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteNome("Cliente " + id);
        p.setTipoAtendimento(tipo);
        p.setItens(new ArrayList<>(Arrays.asList(itens)));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido i : itens) {
            subtotal = subtotal.add(i.getSubtotal());
        }
        BigDecimal pct = new BigDecimal(percentualDesconto);
        BigDecimal desconto = subtotal.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        p.setSubtotal(subtotal);
        p.setPercentualDesconto(pct);
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal.subtract(desconto));
        p.setEntradaEm(entrada);
        p.registrarMovimentacao(StatusPedido.RECEBIDO, entrada, "teste");
        if (status == StatusPedido.CANCELADO) {
            p.registrarMovimentacao(StatusPedido.CANCELADO, entrada.plusMinutes(5), "gerente01");
            return p;
        }
        StatusPedido atual = StatusPedido.RECEBIDO;
        LocalDateTime t = entrada;
        while (atual != status) {
            atual = atual.seguinte(tipo);
            t = t.plusMinutes(10);
            p.registrarMovimentacao(atual, t, "teste");
            if (atual.marcaSaida()) {
                p.setSaidaEm(t);
            }
        }
        return p;
    }

    /**
     * Cenario base (periodo atual):
     *  A: seg 19h, entrega, finalizado, 2x calabresa 50,00 + 1 refri 14,00 = 114,00, sem desconto
     *  B: seg 20h, retirada, finalizado, 1x mussarela 45,00, 10% -> 40,50
     *  C: qua 20h, entrega, cancelado, 1x calabresa 50,00
     *  D: sab 21h, entrega, em preparacao, 1x calabresa 50,00 + 2 refri 28,00 = 78,00
     */
    private static List<Pedido> cenario() {
        return List.of(
                pedido("a", D1.atTime(19, 10), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO, "0",
                        item("calabresa", "Pizza Calabresa", 2, "50.00"), item("refri", "Refrigerante 2L", 1, "14.00")),
                pedido("b", D1.atTime(20, 5), TipoAtendimento.RETIRADA, StatusPedido.FINALIZADO, "10",
                        item("mussarela", "Pizza Mussarela", 1, "45.00")),
                pedido("c", D1.plusDays(2).atTime(20, 30), TipoAtendimento.ENTREGA, StatusPedido.CANCELADO, "0",
                        item("calabresa", "Pizza Calabresa", 1, "50.00")),
                pedido("d", D1.plusDays(5).atTime(21, 0), TipoAtendimento.ENTREGA, StatusPedido.EM_PREPARACAO, "0",
                        item("calabresa", "Pizza Calabresa", 1, "50.00"), item("refri", "Refrigerante 2L", 2, "14.00")));
    }

    // ------------------------------------------------------------------ RF01

    @Test
    void resumoSomaSoPedidosNaoCancelados() {
        Resumo r = CalculadoraDashboard.resumo(cenario(), PERIODO, List.of(), ANTERIOR, AGORA);

        assertEquals(4, r.totalPedidos());
        assertEquals(3, r.pedidosValidos());
        assertEquals(2, r.pedidosFinalizados());
        assertEquals(1, r.pedidosEmAndamento());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(new BigDecimal("237.00"), r.faturamentoBruto());    // 114 + 45 + 78
        assertEquals(new BigDecimal("4.50"), r.totalDescontos());
        assertEquals(new BigDecimal("232.50"), r.faturamento());         // 114 + 40,50 + 78
        assertEquals(new BigDecimal("77.50"), r.ticketMedio());          // 232,50 / 3
        assertEquals(new BigDecimal("50.00"), r.valorCancelado());
        assertEquals(new BigDecimal("25.00"), r.taxaCancelamento());     // 1 de 4
        assertEquals(7, r.itensVendidos());                              // 3 + 1 + 3
    }

    @Test
    void tempoMedioAteSaidaUsaSoPedidosQueSairam() {
        Resumo r = CalculadoraDashboard.resumo(cenario(), PERIODO, List.of(), ANTERIOR, AGORA);
        // A e B sairam 30 min depois da entrada (3 etapas de 10 min); D ainda nao saiu
        assertEquals(new BigDecimal("30.0"), r.tempoMedioSaidaMinutos());
    }

    @Test
    void comparacaoComPeriodoAnterior() {
        List<Pedido> anteriores = List.of(
                pedido("x", D1.minusDays(3).atTime(20, 0), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO, "0",
                        item("calabresa", "Pizza Calabresa", 3, "50.00")),
                pedido("y", D1.minusDays(2).atTime(20, 0), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO, "0",
                        item("mussarela", "Pizza Mussarela", 1, "45.00")));
        Resumo r = CalculadoraDashboard.resumo(cenario(), PERIODO, anteriores, ANTERIOR, AGORA);

        assertEquals(new BigDecimal("195.00"), r.comparacao().faturamento());
        assertEquals(new BigDecimal("19.23"), r.comparacao().variacaoFaturamento());   // (232,50 - 195) / 195
        assertEquals(2, r.comparacao().pedidosValidos());
        assertEquals(new BigDecimal("50.00"), r.comparacao().variacaoPedidos());       // 3 contra 2
        assertEquals(new BigDecimal("97.50"), r.comparacao().ticketMedio());
    }

    @Test
    void semPeriodoAnteriorAVariacaoFicaNula() {
        Resumo r = CalculadoraDashboard.resumo(cenario(), PERIODO, List.of(), ANTERIOR, AGORA);
        assertNull(r.comparacao().variacaoFaturamento());
        assertNull(r.comparacao().variacaoPedidos());
    }

    @Test
    void periodoVazioNaoDivideAZero() {
        Resumo r = CalculadoraDashboard.resumo(List.of(), PERIODO, List.of(), ANTERIOR, AGORA);
        assertEquals(0, r.totalPedidos());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(new BigDecimal("0.00"), r.taxaCancelamento());
        assertNull(r.tempoMedioSaidaMinutos());
    }

    @Test
    void pedidoAntigoComCamposNulosNaoQuebraOsCalculos() {
        Pedido legado = new Pedido();   // como os pedidos do projeto original: sem itens, valores nem datas
        legado.setItens(null);
        legado.setSubtotal(null);
        legado.setValorDesconto(null);
        legado.setValorTotal(null);
        List<Pedido> lista = new ArrayList<>(cenario());
        lista.add(legado);
        lista.add(null);

        Resumo r = CalculadoraDashboard.resumo(lista, PERIODO, null, ANTERIOR, AGORA);
        assertEquals(5, r.totalPedidos());
        assertEquals(new BigDecimal("232.50"), r.faturamento());
        assertEquals(1, r.pedidosEmAndamento());   // so o D; o legado sem status nao entra na fila
        SerieVendas s = CalculadoraDashboard.serie(lista, PERIODO, Agrupamento.DIA);
        assertEquals(3, s.totalPedidos());   // o legado nao tem data de entrada: fica fora da serie
    }

    // ------------------------------------------------------------------ RF02

    @Test
    void seriePorDiaPreencheDiasSemVenda() {
        SerieVendas s = CalculadoraDashboard.serie(cenario(), PERIODO, Agrupamento.DIA);

        assertEquals(7, s.pontos().size());
        assertEquals("2026-11-02", s.pontos().get(0).chave());
        assertEquals("02/11", s.pontos().get(0).rotulo());
        assertEquals(2, s.pontos().get(0).pedidos());
        assertEquals(new BigDecimal("154.50"), s.pontos().get(0).faturamento());
        assertEquals(new BigDecimal("77.25"), s.pontos().get(0).ticketMedio());
        assertEquals(0, s.pontos().get(2).pedidos());                        // quarta: so o cancelado
        assertEquals(new BigDecimal("0.00"), s.pontos().get(1).faturamento());
        assertEquals(new BigDecimal("232.50"), s.totalFaturamento());
        assertEquals(3, s.totalPedidos());
    }

    @Test
    void seriePorSemanaEMes() {
        Periodo mes = new Periodo(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 8), 20);
        SerieVendas semanas = CalculadoraDashboard.serie(cenario(), mes, Agrupamento.SEMANA);
        // semanas comecando na segunda: 19/10, 26/10, 02/11
        assertEquals(3, semanas.pontos().size());
        assertEquals("2026-11-02", semanas.pontos().get(2).chave());
        assertEquals(3, semanas.pontos().get(2).pedidos());

        SerieVendas meses = CalculadoraDashboard.serie(cenario(), mes, Agrupamento.MES);
        assertEquals(2, meses.pontos().size());
        assertEquals("2026-10", meses.pontos().get(0).chave());
        assertEquals("2026-11", meses.pontos().get(1).chave());
        assertEquals("nov/2026", meses.pontos().get(1).rotulo());
    }

    @Test
    void distribuicaoPorHoraDiaTipoECategoria() {
        Distribuicao d = CalculadoraDashboard.distribuicao(cenario(), PERIODO, CATEGORIAS);

        assertEquals(24, d.porHora().size());
        assertEquals(1, d.porHora().get(19).pedidos());
        assertEquals(1, d.porHora().get(20).pedidos());   // o cancelado das 20h30 nao conta
        assertEquals("20h", d.porHora().get(20).rotulo());

        assertEquals(7, d.porDiaSemana().size());
        assertEquals("Seg", d.porDiaSemana().get(0).rotulo());
        assertEquals(2, d.porDiaSemana().get(0).pedidos());
        assertEquals(1, d.porDiaSemana().get(5).pedidos()); // sabado

        Fatia entrega = d.porTipoAtendimento().get(0);
        assertEquals("ENTREGA", entrega.chave());
        assertEquals(2, entrega.pedidos());
        assertEquals(new BigDecimal("192.00"), entrega.faturamento());
        assertEquals(new BigDecimal("82.58"), entrega.percentual());   // 192 / 232,50

        // categorias pelo subtotal dos itens: pizza 100 + 45 + 50 = 195; bebida 14 + 28 = 42
        assertEquals("Pizza", d.porCategoria().get(0).chave());
        assertEquals(new BigDecimal("195.00"), d.porCategoria().get(0).faturamento());
        assertEquals(3, d.porCategoria().get(0).pedidos());
        assertEquals("Bebida", d.porCategoria().get(1).chave());
        assertEquals(new BigDecimal("42.00"), d.porCategoria().get(1).faturamento());
    }

    // ------------------------------------------------------------------ RF03

    @Test
    void situacaoListaTodosOsStatusNaOrdemDoFluxo() {
        SituacaoPedidos s = CalculadoraDashboard.situacao(cenario(), PERIODO);

        assertEquals(4, s.total());
        assertEquals(StatusPedido.values().length, s.porStatus().size());
        assertEquals(StatusPedido.RECEBIDO, s.porStatus().get(0).status());
        SituacaoStatus finalizado = s.porStatus().stream()
                .filter(x -> x.status() == StatusPedido.FINALIZADO).findFirst().orElseThrow();
        assertEquals(2, finalizado.quantidade());
        assertEquals(new BigDecimal("50.00"), finalizado.percentual());
        assertEquals(new BigDecimal("154.50"), finalizado.valor());
        assertEquals(0, s.porStatus().get(0).quantidade());
    }

    @Test
    void emAndamentoMarcaAtrasoSoAntesDaSaida() {
        LocalDateTime agora = D1.atTime(21, 0);
        List<Pedido> ativos = List.of(
                pedido("p1", agora.minusMinutes(50), TipoAtendimento.ENTREGA, StatusPedido.EM_PREPARACAO, "0",
                        item("calabresa", "Pizza Calabresa", 1, "50.00")),
                pedido("p2", agora.minusMinutes(15), TipoAtendimento.RETIRADA, StatusPedido.RECEBIDO, "0",
                        item("mussarela", "Pizza Mussarela", 1, "45.00")),
                pedido("p3", agora.minusMinutes(70), TipoAtendimento.ENTREGA, StatusPedido.SAIU_PARA_ENTREGA, "0",
                        item("calabresa", "Pizza Calabresa", 1, "50.00")),
                pedido("p4", agora.minusMinutes(90), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO, "0",
                        item("calabresa", "Pizza Calabresa", 1, "50.00")));

        EmAndamento e = CalculadoraDashboard.emAndamento(ativos, agora, 40);

        assertEquals(3, e.total());                        // o finalizado sai da fila
        assertEquals(1, e.atrasados());
        assertEquals("p3", e.pedidos().get(0).id());       // mais antigo primeiro
        assertFalse(e.pedidos().get(0).atrasado());        // ja saiu para entrega: nao conta atraso
        assertTrue(e.pedidos().get(1).atrasado());         // 50 min ainda em preparo
        assertEquals(50, e.pedidos().get(1).minutosDesdeEntrada());
        assertFalse(e.pedidos().get(2).atrasado());
    }

    @Test
    void paginacaoMaisRecentesPrimeiro() {
        PaginaPedidos p0 = CalculadoraDashboard.paginar(cenario(), 0, 3);
        assertEquals(4, p0.totalElementos());
        assertEquals(2, p0.totalPaginas());
        assertEquals("d", p0.conteudo().get(0).id());
        assertEquals(3, p0.conteudo().get(0).quantidadeItens());

        PaginaPedidos p1 = CalculadoraDashboard.paginar(cenario(), 1, 3);
        assertEquals(1, p1.conteudo().size());
        assertEquals("a", p1.conteudo().get(0).id());

        assertTrue(CalculadoraDashboard.paginar(cenario(), 9, 3).conteudo().isEmpty());
    }

    // ------------------------------------------------------------------ RF04

    @Test
    void rankingPorQuantidadeIgnoraCancelados() {
        RankingProdutos r = CalculadoraDashboard.ranking(cenario(), PERIODO, CATEGORIAS, null,
                CriterioRanking.QUANTIDADE, 10);

        // calabresa: 2 (A) + 1 (D) = 3; refri: 1 + 2 = 3; mussarela: 1. Empate desfeito pelo faturamento.
        assertEquals(3, r.produtos().size());
        assertEquals("Pizza Calabresa", r.produtos().get(0).nome());
        assertEquals(1, r.produtos().get(0).posicao());
        assertEquals(3, r.produtos().get(0).quantidade());
        assertEquals(2, r.produtos().get(0).pedidos());
        assertEquals(new BigDecimal("150.00"), r.produtos().get(0).faturamento());
        assertEquals("Pizza", r.produtos().get(0).categoria());
        assertEquals("Refrigerante 2L", r.produtos().get(1).nome());
        assertEquals(new BigDecimal("237.00"), r.totalItens());
        assertEquals(new BigDecimal("63.29"), r.produtos().get(0).participacao());   // 150 / 237
    }

    @Test
    void rankingPorFaturamentoComFiltroDeCategoriaELimite() {
        RankingProdutos r = CalculadoraDashboard.ranking(cenario(), PERIODO, CATEGORIAS, "pizza",
                CriterioRanking.FATURAMENTO, 1);

        assertEquals(1, r.produtos().size());
        assertEquals("Pizza Calabresa", r.produtos().get(0).nome());
        assertEquals(CriterioRanking.FATURAMENTO, r.criterio());
    }

    @Test
    void produtoSemCategoriaCadastradaVaiParaSemCategoria() {
        RankingProdutos r = CalculadoraDashboard.ranking(cenario(), PERIODO, Map.of(), null,
                CriterioRanking.QUANTIDADE, 10);
        assertEquals(CalculadoraDashboard.SEM_CATEGORIA, r.produtos().get(0).categoria());
    }
}
