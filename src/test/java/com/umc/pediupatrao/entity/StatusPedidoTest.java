package com.umc.pediupatrao.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoDeEntregaPassaPorSaiuParaEntrega() {
        TipoAtendimento entrega = TipoAtendimento.ENTREGA;
        assertEquals(StatusPedido.EM_PREPARACAO, StatusPedido.RECEBIDO.seguinte(entrega));
        assertEquals(StatusPedido.PRONTO, StatusPedido.EM_PREPARACAO.seguinte(entrega));
        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.PRONTO.seguinte(entrega));
        assertEquals(StatusPedido.FINALIZADO, StatusPedido.SAIU_PARA_ENTREGA.seguinte(entrega));
    }

    @Test
    void fluxoDeBalcaoPassaPorRetirado() {
        assertEquals(StatusPedido.RETIRADO, StatusPedido.PRONTO.seguinte(TipoAtendimento.RETIRADA));
        assertEquals(StatusPedido.FINALIZADO, StatusPedido.RETIRADO.seguinte(TipoAtendimento.RETIRADA));
    }

    @Test
    void finalizadoECanceladoNaoTemProximaEtapa() {
        assertNull(StatusPedido.FINALIZADO.seguinte(TipoAtendimento.ENTREGA));
        assertNull(StatusPedido.CANCELADO.seguinte(TipoAtendimento.RETIRADA));
    }

    @Test
    void somenteEtapasInternasAceitamDescontoECancelamento() {
        assertTrue(StatusPedido.RECEBIDO.antesDaSaida());
        assertTrue(StatusPedido.PRONTO.antesDaSaida());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.antesDaSaida());
        assertFalse(StatusPedido.RETIRADO.antesDaSaida());
        assertFalse(StatusPedido.FINALIZADO.antesDaSaida());
        assertFalse(StatusPedido.CANCELADO.antesDaSaida());
    }
}
