package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

/** Corpos JSON das operacoes exclusivas do gerente na API de pedidos. */
public final class OperacaoGerente {

    private OperacaoGerente() {
    }

    public record Desconto(BigDecimal percentual, String motivo) {
    }

    public record Cancelamento(String justificativa) {
    }
}
