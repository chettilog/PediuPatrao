package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.util.ArrayList;
import java.util.List;

/**
 * O que a tela/API pode enviar para registrar ou alterar um pedido.
 * Nao existe preco, desconto nem total aqui: esses valores sao calculados no PedidoService
 * a partir do cadastro de produtos. Se a requisicao mandar "valorTotal", o campo e ignorado.
 */
public class PedidoForm {

    private String clienteId;
    private TipoAtendimento tipoAtendimento;
    private String observacao;
    private List<Linha> itens = new ArrayList<>();

    public static PedidoForm de(Pedido pedido) {
        PedidoForm f = new PedidoForm();
        f.clienteId = pedido.getClienteId();
        f.tipoAtendimento = pedido.getTipoAtendimento();
        f.observacao = pedido.getObservacao();
        for (ItemPedido item : pedido.getItens()) {
            f.itens.add(new Linha(item.getProdutoId(), item.getQuantidade()));
        }
        return f;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public TipoAtendimento getTipoAtendimento() {
        return tipoAtendimento;
    }

    public void setTipoAtendimento(TipoAtendimento tipoAtendimento) {
        this.tipoAtendimento = tipoAtendimento;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<Linha> getItens() {
        return itens;
    }

    public void setItens(List<Linha> itens) {
        this.itens = itens;
    }

    /** Uma linha do formulario: qual produto e quantas unidades. */
    public static class Linha {

        private String produtoId;
        private Integer quantidade;

        public Linha() {
        }

        public Linha(String produtoId, Integer quantidade) {
            this.produtoId = produtoId;
            this.quantidade = quantidade;
        }

        public String getProdutoId() {
            return produtoId;
        }

        public void setProdutoId(String produtoId) {
            this.produtoId = produtoId;
        }

        public Integer getQuantidade() {
            return quantidade;
        }

        public void setQuantidade(Integer quantidade) {
            this.quantidade = quantidade;
        }
    }
}
