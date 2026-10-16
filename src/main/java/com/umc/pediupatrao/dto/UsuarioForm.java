package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;

/**
 * Dados que o formulario/API de usuarios pode enviar. Usar este objeto em vez da entidade
 * evita que a requisicao preencha campos que nao deveria (id, hash da senha etc.).
 */
public class UsuarioForm {

    private String username;
    private String senha;
    private Perfil perfil;

    public static UsuarioForm de(Usuario u) {
        UsuarioForm f = new UsuarioForm();
        f.username = u.getUsername();
        f.perfil = u.getPerfil();
        // senha fica vazia de proposito: o hash nunca volta para a tela
        return f;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public void setPerfil(Perfil perfil) {
        this.perfil = perfil;
    }
}
