package com.umc.pediupatrao.entity;

/**
 * Perfis de acesso do sistema. O nome da constante vira a role do Spring Security
 * (ex.: GERENTE -> ROLE_GERENTE), por isso nao deve ser renomeado.
 */
public enum Perfil implements Rotulado {

    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String rotulo;

    Perfil(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
