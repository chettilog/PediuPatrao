package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.EventoAuditoria;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.EventoAuditoriaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.seguranca.UsuarioLogado;
import com.umc.pediupatrao.util.Formatos;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UsuarioServiceTest {

    private UsuarioRepository usuarioRepository;
    private EventoAuditoriaRepository auditoriaRepository;
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        auditoriaRepository = mock(EventoAuditoriaRepository.class);
        UsuarioLogado logado = mock(UsuarioLogado.class);
        when(logado.username()).thenReturn("admin");
        when(logado.perfil()).thenReturn("ADMIN");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("novo");
            }
            return u;
        });
        when(usuarioRepository.findByUsername(any())).thenReturn(Optional.empty());

        AuditoriaService auditoria = new AuditoriaService(auditoriaRepository, logado, new Formatos(), Clock.systemUTC());
        service = new UsuarioService(usuarioRepository, new BCryptPasswordEncoder(), logado, auditoria);
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        Usuario admin = usuario("1", "admin", Perfil.ADMIN);
        when(usuarioRepository.findById("1")).thenReturn(Optional.of(admin));
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThrows(RegraNegocioException.class, () -> service.atualizar("1", form("admin", Perfil.ATENDENTE, null)));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void ultimoAdminNaoPodeSerRebaixado() {
        Usuario outroAdmin = usuario("2", "chefe", Perfil.ADMIN);
        when(usuarioRepository.findById("2")).thenReturn(Optional.of(outroAdmin));
        when(usuarioRepository.countByPerfil(Perfil.ADMIN)).thenReturn(1L);

        assertThrows(RegraNegocioException.class, () -> service.atualizar("2", form("chefe", Perfil.GERENTE, null)));
    }

    @Test
    void perfilEObrigatorio() {
        assertThrows(RegraNegocioException.class, () -> service.criar(form("novo.usuario", null, "senha123")));
    }

    @Test
    void senhaNuncaVaiParaAuditoria() {
        service.criar(form("caixa02", Perfil.ATENDENTE, "Segredo#123"));

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaRepository, org.mockito.Mockito.times(3)).insert(captor.capture());
        List<EventoAuditoria> eventos = captor.getAllValues();

        EventoAuditoria senha = eventos.get(2);
        assertEquals("senha", senha.getCampo());
        assertEquals("(definida)", senha.getValorNovo());
        eventos.forEach(e -> {
            assertEquals(false, String.valueOf(e.getValorNovo()).contains("Segredo#123"));
            assertEquals(false, String.valueOf(e.getValorAnterior()).contains("Segredo#123"));
        });
    }

    @Test
    void senhaEGravadaComHash() {
        Usuario criado = service.criar(form("caixa03", Perfil.ATENDENTE, "Segredo#123"));
        assertEquals(true, criado.getPassword().startsWith("$2"));
    }

    private static Usuario usuario(String id, String username, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setPerfil(perfil);
        u.setPassword("$2a$10$hash");
        return u;
    }

    private static UsuarioForm form(String username, Perfil perfil, String senha) {
        UsuarioForm f = new UsuarioForm();
        f.setUsername(username);
        f.setPerfil(perfil);
        f.setSenha(senha);
        return f;
    }
}
