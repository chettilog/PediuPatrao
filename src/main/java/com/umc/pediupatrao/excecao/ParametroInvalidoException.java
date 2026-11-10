package com.umc.pediupatrao.excecao;

/** Parametro de consulta fora do permitido (ex.: data inicial depois da final). Vira HTTP 400. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
