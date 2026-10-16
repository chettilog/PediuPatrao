package com.umc.pediupatrao.excecao;

/** Operacao recusada por uma regra do negocio (mensagem pode ser mostrada ao usuario). */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
