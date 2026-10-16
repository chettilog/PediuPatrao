package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Antes todo usuario recebia a role USER. Agora a role vem do perfil gravado no banco.
     * Usuario sem perfil (dado antigo) fica desabilitado ate o ADMIN definir um perfil.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        boolean semPerfil = usuario.getPerfil() == null;

        return User.withUsername(usuario.getUsername())
                .password(usuario.getPassword())
                .roles(semPerfil ? new String[0] : new String[] {usuario.getPerfil().name()})
                .disabled(semPerfil)
                .build();
    }
}
