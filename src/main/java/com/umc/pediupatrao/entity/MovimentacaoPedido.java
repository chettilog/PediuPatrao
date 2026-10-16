package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/** Uma passagem de etapa do pedido: para qual status foi, quando e por quem. */
public class MovimentacaoPedido {

    private StatusPedido status;
    private LocalDateTime dataHora;
    private String responsavel;

    public MovimentacaoPedido() {
    }

    public MovimentacaoPedido(StatusPedido status, LocalDateTime dataHora, String responsavel) {
        this.status = status;
        this.dataHora = dataHora;
        this.responsavel = responsavel;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getResponsavel() {
        return responsavel;
    }
}
