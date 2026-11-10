package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardDto.CriterioRanking;
import com.umc.pediupatrao.dto.DashboardDto.EmAndamento;
import com.umc.pediupatrao.dto.DashboardDto.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDto.RankingProdutos;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Integracao do service com os repositorios (mockados): consulta certa no banco e filtros aplicados. */
class DashboardServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 21, 0);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    private PedidoRepository pedidoRepository;
    private ProdutoRepository produtoRepository;
    private DashboardService service;

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        Clock relogio = Clock.fixed(AGORA.atZone(SP).toInstant(), SP);
        service = new DashboardService(pedidoRepository, produtoRepository, relogio, 40);

        Produto calabresa = new Produto();
        calabresa.setId("calabresa");
        calabresa.setNome("Pizza Calabresa");
        calabresa.setTipo("Pizza");
        Produto refri = new Produto();
        refri.setId("refri");
        refri.setNome("Refrigerante 2L");
        refri.setTipo("Bebida");
        when(produtoRepository.findAll()).thenReturn(List.of(calabresa, refri));
    }

    private static Pedido pedido(String id, LocalDateTime entrada, TipoAtendimento tipo, StatusPedido status, String total) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setTipoAtendimento(tipo);
        p.setEntradaEm(entrada);
        ItemPedido item = new ItemPedido("calabresa", "Pizza Calabresa", 1, new BigDecimal(total));
        item.setSubtotal(new BigDecimal(total));
        p.setItens(new ArrayList<>(List.of(item)));
        p.setSubtotal(new BigDecimal(total));
        p.setValorTotal(new BigDecimal(total));
        p.registrarMovimentacao(status, entrada, "teste");
        return p;
    }

    @Test
    void resumoConsultaPeriodoAtualEAnteriorNoBanco() {
        FiltroDashboard filtro = FiltroDashboard.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                null, null, null, HOJE);
        when(pedidoRepository.buscarPorPeriodo(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 11, 0, 0)))
                .thenReturn(List.of(pedido("a", LocalDateTime.of(2026, 11, 5, 20, 0), TipoAtendimento.ENTREGA,
                        StatusPedido.FINALIZADO, "60.00")));
        when(pedidoRepository.buscarPorPeriodo(LocalDateTime.of(2026, 10, 22, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0)))
                .thenReturn(List.of(pedido("b", LocalDateTime.of(2026, 10, 25, 20, 0), TipoAtendimento.ENTREGA,
                        StatusPedido.FINALIZADO, "40.00")));

        Resumo r = service.resumo(filtro);

        assertEquals(new BigDecimal("60.00"), r.faturamento());
        assertEquals(new BigDecimal("40.00"), r.comparacao().faturamento());
        assertEquals(new BigDecimal("50.00"), r.comparacao().variacaoFaturamento());
        assertEquals(AGORA, r.geradoEm());
    }

    @Test
    void filtroDeTipoEStatusEAplicadoSobreOResultadoDoBanco() {
        FiltroDashboard filtro = FiltroDashboard.de(null, null, TipoAtendimento.RETIRADA, null, null, HOJE);
        when(pedidoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                pedido("a", AGORA.minusDays(1), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO, "60.00"),
                pedido("b", AGORA.minusDays(2), TipoAtendimento.RETIRADA, StatusPedido.FINALIZADO, "45.00")));

        Resumo r = service.resumo(filtro);

        assertEquals(1, r.totalPedidos());
        assertEquals(new BigDecimal("45.00"), r.faturamento());
    }

    @Test
    void emAndamentoBuscaPorStatusNaoFinalizados() {
        when(pedidoRepository.findByStatusIn(any())).thenReturn(List.of(
                pedido("a", AGORA.minusMinutes(55), TipoAtendimento.ENTREGA, StatusPedido.EM_PREPARACAO, "50.00"),
                pedido("b", AGORA.minusMinutes(10), TipoAtendimento.RETIRADA, StatusPedido.RECEBIDO, "45.00")));

        EmAndamento e = service.emAndamento(null);

        assertEquals(2, e.total());
        assertEquals(1, e.atrasados());
        assertEquals(40, e.limiteMinutos());
        verify(pedidoRepository).findByStatusIn(List.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO,
                StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO));

        assertEquals(1, service.emAndamento(TipoAtendimento.RETIRADA).total());
    }

    @Test
    void rankingUsaCategoriaDoCadastroDeProdutos() {
        FiltroDashboard filtro = FiltroDashboard.de(null, null, null, null, "Pizza", HOJE);
        when(pedidoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                pedido("a", AGORA.minusDays(1), TipoAtendimento.ENTREGA, StatusPedido.FINALIZADO, "50.00")));

        RankingProdutos r = service.produtosMaisVendidos(filtro, CriterioRanking.QUANTIDADE, 5);

        assertEquals(1, r.produtos().size());
        assertEquals("Pizza", r.produtos().get(0).categoria());
    }

    @Test
    void opcoesDeFiltroTrazemCategoriasDoCardapioEPeriodoPadrao() {
        OpcoesFiltro o = service.opcoesFiltro();

        assertEquals(List.of("Bebida", "Pizza"), o.categorias());
        assertEquals(HOJE, o.fimPadrao());
        assertEquals(HOJE.minusDays(29), o.inicioPadrao());
        assertEquals(StatusPedido.values().length, o.status().size());
        assertTrue(o.tiposAtendimento().stream().anyMatch(op -> op.valor().equals("ENTREGA")));
    }
}
