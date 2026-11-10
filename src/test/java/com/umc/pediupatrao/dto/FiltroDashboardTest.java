package com.umc.pediupatrao.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.excecao.ParametroInvalidoException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FiltroDashboardTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    @Test
    void semParametrosUsaUltimos30Dias() {
        FiltroDashboard f = FiltroDashboard.de(null, null, null, null, null, HOJE);
        assertEquals(LocalDate.of(2026, 10, 12), f.inicio());
        assertEquals(HOJE, f.fim());
        assertEquals(30, f.dias());
    }

    @Test
    void periodoIncluiODiaFinalInteiro() {
        FiltroDashboard f = FiltroDashboard.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null, null, null, HOJE);
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), f.inicioInclusivo());
        assertEquals(LocalDateTime.of(2026, 11, 11, 0, 0), f.fimExclusivo());
    }

    @Test
    void inicioDepoisDoFimERecusado() {
        ParametroInvalidoException e = assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null, null, null, HOJE));
        assertEquals("A data inicial não pode ser posterior à data final.", e.getMessage());
    }

    @Test
    void periodoMaiorQueUmAnoERecusado() {
        assertThrows(ParametroInvalidoException.class,
                () -> FiltroDashboard.de(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 10), null, null, null, HOJE));
        // exatamente 366 dias passa
        FiltroDashboard f = FiltroDashboard.de(HOJE.minusDays(365), HOJE, null, null, null, HOJE);
        assertEquals(366, f.dias());
    }

    @Test
    void periodoAnteriorTemOMesmoTamanho() {
        FiltroDashboard f = FiltroDashboard.de(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null, null, null, HOJE);
        FiltroDashboard anterior = f.periodoAnterior();
        assertEquals(LocalDate.of(2026, 10, 22), anterior.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), anterior.fim());
        assertEquals(f.dias(), anterior.dias());
    }

    @Test
    void categoriaEmBrancoViraNula() {
        assertNull(FiltroDashboard.de(null, null, null, null, "   ", HOJE).categoria());
        assertEquals("Pizza", FiltroDashboard.de(null, null, null, null, " Pizza ", HOJE).categoria());
    }

    @Test
    void aceitaFiltraPorTipoEStatus() {
        Pedido p = new Pedido();
        p.setTipoAtendimento(TipoAtendimento.ENTREGA);
        p.registrarMovimentacao(StatusPedido.RECEBIDO, LocalDateTime.of(2026, 11, 5, 20, 0), "teste");

        assertTrue(FiltroDashboard.de(null, null, null, null, null, HOJE).aceita(p));
        assertTrue(FiltroDashboard.de(null, null, TipoAtendimento.ENTREGA, StatusPedido.RECEBIDO, null, HOJE).aceita(p));
        assertFalse(FiltroDashboard.de(null, null, TipoAtendimento.RETIRADA, null, null, HOJE).aceita(p));
        assertFalse(FiltroDashboard.de(null, null, null, StatusPedido.FINALIZADO, null, HOJE).aceita(p));
        assertFalse(FiltroDashboard.de(null, null, null, null, null, HOJE).aceita(null));
    }
}
