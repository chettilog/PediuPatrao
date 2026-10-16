package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Pedido da pizzaria.
 *
 * Os campos de valor (subtotal, desconto e total) so sao preenchidos pelo PedidoService.
 * As requisicoes HTTP usam o PedidoForm, que nao possui nenhum desses campos.
 */
@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;

    @Indexed
    private String clienteId;
    private String clienteNome;

    private TipoAtendimento tipoAtendimento;
    private String observacao;
    private List<ItemPedido> itens = new ArrayList<>();

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Indexed
    private StatusPedido status;

    // Entrada
    private LocalDateTime entradaEm;
    private String registradoPor;

    // Saida (entrega ou retirada)
    private LocalDateTime saidaEm;
    private String saidaPor;

    private LocalDateTime finalizadoEm;

    // Cancelamento
    private LocalDateTime canceladoEm;
    private String canceladoPor;
    private String motivoCancelamento;

    private List<MovimentacaoPedido> historico = new ArrayList<>();

    public void registrarMovimentacao(StatusPedido novoStatus, LocalDateTime quando, String quem) {
        this.status = novoStatus;
        this.historico.add(new MovimentacaoPedido(novoStatus, quando, quem));
    }

    /** Codigo curto exibido nas telas (os 6 ultimos caracteres do id). */
    public String getCodigo() {
        if (id == null) {
            return "";
        }
        return id.length() <= 6 ? id.toUpperCase() : id.substring(id.length() - 6).toUpperCase();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
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

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(BigDecimal percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public LocalDateTime getEntradaEm() {
        return entradaEm;
    }

    public void setEntradaEm(LocalDateTime entradaEm) {
        this.entradaEm = entradaEm;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(String registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getSaidaEm() {
        return saidaEm;
    }

    public void setSaidaEm(LocalDateTime saidaEm) {
        this.saidaEm = saidaEm;
    }

    public String getSaidaPor() {
        return saidaPor;
    }

    public void setSaidaPor(String saidaPor) {
        this.saidaPor = saidaPor;
    }

    public LocalDateTime getFinalizadoEm() {
        return finalizadoEm;
    }

    public void setFinalizadoEm(LocalDateTime finalizadoEm) {
        this.finalizadoEm = finalizadoEm;
    }

    public LocalDateTime getCanceladoEm() {
        return canceladoEm;
    }

    public void setCanceladoEm(LocalDateTime canceladoEm) {
        this.canceladoEm = canceladoEm;
    }

    public String getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(String canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public String getMotivoCancelamento() {
        return motivoCancelamento;
    }

    public void setMotivoCancelamento(String motivoCancelamento) {
        this.motivoCancelamento = motivoCancelamento;
    }

    public List<MovimentacaoPedido> getHistorico() {
        return historico;
    }
}
