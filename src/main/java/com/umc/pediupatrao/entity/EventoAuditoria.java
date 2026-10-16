package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Um registro da trilha de auditoria. Cada campo alterado gera um documento proprio;
 * os documentos de uma mesma operacao compartilham o mesmo operacaoId.
 *
 * Nao ha setters: depois de criado, o evento nao muda. O repositorio tambem nao
 * oferece save/delete, apenas insert e consultas.
 */
@Document(collection = "auditoria")
public class EventoAuditoria {

    @Id
    private String id;

    @Indexed
    private String operacaoId;

    @Indexed
    private LocalDateTime dataHora;

    private String usuario;
    private String perfil;

    private TipoOperacao tipoOperacao;
    private String entidade;

    @Indexed
    private String entidadeId;

    private String campo;
    private String valorAnterior;
    private String valorNovo;
    private String justificativa;

    // Usado pelo Spring Data ao ler do banco
    protected EventoAuditoria() {
    }

    public EventoAuditoria(String operacaoId, LocalDateTime dataHora, String usuario, String perfil,
            TipoOperacao tipoOperacao, String entidade, String entidadeId,
            String campo, String valorAnterior, String valorNovo, String justificativa) {
        this.operacaoId = operacaoId;
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.tipoOperacao = tipoOperacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNovo = valorNovo;
        this.justificativa = justificativa;
    }

    /** Objeto "molde" para a consulta por exemplo (campos nulos sao ignorados no filtro). */
    public static EventoAuditoria filtro(String usuario, TipoOperacao tipo, String entidade, String entidadeId) {
        EventoAuditoria e = new EventoAuditoria();
        e.usuario = usuario;
        e.tipoOperacao = tipo;
        e.entidade = entidade;
        e.entidadeId = entidadeId;
        return e;
    }

    public String getId() {
        return id;
    }

    public String getOperacaoId() {
        return operacaoId;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public TipoOperacao getTipoOperacao() {
        return tipoOperacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNovo() {
        return valorNovo;
    }

    public String getJustificativa() {
        return justificativa;
    }
}
