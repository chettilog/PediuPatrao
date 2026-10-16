package com.umc.pediupatrao.entity;

public enum TipoOperacao implements Rotulado {

    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    MUDANCA_STATUS("Mudança de status"),
    DESCONTO_PEDIDO("Desconto no pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),

    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),

    INCLUSAO_PRODUTO("Inclusão de produto"),
    ALTERACAO_PRODUTO("Alteração de produto"),
    EXCLUSAO_PRODUTO("Exclusão de produto"),

    INCLUSAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário"),

    ACESSO_NEGADO("Tentativa de acesso negada");

    private final String rotulo;

    TipoOperacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
