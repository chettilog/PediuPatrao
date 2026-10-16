package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;

/** O que a API devolve sobre um usuario (sem senha). */
public record UsuarioResumo(String id, String username, Perfil perfil) {

    public static UsuarioResumo de(Usuario u) {
        return new UsuarioResumo(u.getId(), u.getUsername(), u.getPerfil());
    }
}
