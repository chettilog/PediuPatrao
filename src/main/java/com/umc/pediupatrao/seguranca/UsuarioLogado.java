package com.umc.pediupatrao.seguranca;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Le do Spring Security quem esta autenticado na requisicao atual. */
@Component
public class UsuarioLogado {

    public static final String SISTEMA = "sistema";

    public boolean autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    public String username() {
        if (!autenticado()) {
            return SISTEMA;
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** Perfil sem o prefixo ROLE_ (ex.: "GERENTE"). */
    public String perfil() {
        if (!autenticado()) {
            return "-";
        }
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("-");
    }
}
