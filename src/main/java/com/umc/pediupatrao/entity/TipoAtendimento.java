package com.umc.pediupatrao.entity;

/** Definido quando o pedido e anotado; decide qual saida o pedido tera. */
public enum TipoAtendimento implements Rotulado {

    ENTREGA("Entrega"),
    RETIRADA("Retirada no balcão");

    private final String rotulo;

    TipoAtendimento(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
