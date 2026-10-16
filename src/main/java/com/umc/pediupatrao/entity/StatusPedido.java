package com.umc.pediupatrao.entity;

/**
 * Etapas do pedido. O fluxo normal e linear:
 *
 *   RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA (entrega) -> FINALIZADO
 *                                       -> RETIRADO (balcao)           -> FINALIZADO
 *
 * CANCELADO fica fora do fluxo: so e alcancado pela operacao de cancelamento do gerente.
 */
public enum StatusPedido implements Rotulado {

    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String rotulo;

    StatusPedido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /**
     * Unica etapa para a qual o pedido pode avancar a partir desta.
     * Retorna null quando nao existe proxima etapa (finalizado ou cancelado).
     */
    public StatusPedido seguinte(TipoAtendimento tipo) {
        return switch (this) {
            case RECEBIDO -> EM_PREPARACAO;
            case EM_PREPARACAO -> PRONTO;
            case PRONTO -> tipo == TipoAtendimento.ENTREGA ? SAIU_PARA_ENTREGA : RETIRADO;
            case SAIU_PARA_ENTREGA, RETIRADO -> FINALIZADO;
            case FINALIZADO, CANCELADO -> null;
        };
    }

    /** Etapas em que o pedido ainda esta dentro da pizzaria (aceita desconto e cancelamento). */
    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    /** Etapas que marcam a saida do pedido e exigem registro de horario e responsavel. */
    public boolean marcaSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }
}
