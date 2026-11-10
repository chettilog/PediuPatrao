package com.umc.pediupatrao.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.MovimentacaoPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Os dados de demonstracao precisam respeitar as mesmas regras dos pedidos reais. */
class GeradorPedidosDemoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 10, 20, 30);

    private static Produto produto(String id, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private static List<Pedido> gerar() {
        List<Produto> cardapio = List.of(produto("1", "Pizza Calabresa", "Pizza", 49.90),
                produto("2", "Pizza Mussarela", "Pizza", 45.00), produto("3", "Esfiha de Carne", "Lanche", 6.50),
                produto("4", "Refrigerante 2L", "Bebida", 14.00));
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Ana Souza");
        GeradorPedidosDemo gerador = new GeradorPedidosDemo(null, null, null, null, 30);
        return gerador.gerar(cardapio, List.of(c), AGORA, new Random(7));
    }

    @Test
    void geraPedidosComValoresCalculadosPelasRegrasDoSistema() {
        List<Pedido> pedidos = gerar();
        assertTrue(pedidos.size() > 150, "esperava um historico de 30 dias com volume, veio " + pedidos.size());

        for (Pedido p : pedidos) {
            BigDecimal soma = BigDecimal.ZERO;
            for (ItemPedido i : p.getItens()) {
                assertEquals(i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())).setScale(2, RoundingMode.HALF_UP),
                        i.getSubtotal());
                soma = soma.add(i.getSubtotal());
            }
            assertEquals(soma, p.getSubtotal());
            assertEquals(p.getSubtotal().subtract(p.getValorDesconto()), p.getValorTotal());
            assertTrue(p.getPercentualDesconto().compareTo(new BigDecimal("20")) <= 0, "desconto acima de 20%");
            assertEquals(GeradorPedidosDemo.RESPONSAVEL, p.getRegistradoPor());
        }
    }

    @Test
    void historicoSegueOFluxoDeStatusENaoTemDatasNoFuturo() {
        for (Pedido p : gerar()) {
            assertFalse(p.getEntradaEm().isAfter(AGORA), "entrada no futuro");
            List<MovimentacaoPedido> h = p.getHistorico();
            assertEquals(StatusPedido.RECEBIDO, h.get(0).getStatus());
            for (int i = 1; i < h.size(); i++) {
                StatusPedido anterior = h.get(i - 1).getStatus();
                StatusPedido atual = h.get(i).getStatus();
                assertTrue(atual == StatusPedido.CANCELADO || atual == anterior.seguinte(p.getTipoAtendimento()),
                        "transicao invalida " + anterior + " -> " + atual);
                assertFalse(h.get(i).getDataHora().isAfter(AGORA), "etapa no futuro");
            }
            assertEquals(h.get(h.size() - 1).getStatus(), p.getStatus());
            if (p.getStatus() == StatusPedido.FINALIZADO) {
                assertNotNull(p.getSaidaEm());
                assertNotNull(p.getFinalizadoEm());
            }
        }
    }

    @Test
    void mesmaSementeGeraOsMesmosDados() {
        assertEquals(gerar().size(), gerar().size());
        assertEquals(gerar().get(10).getValorTotal(), gerar().get(10).getValorTotal());
    }
}
