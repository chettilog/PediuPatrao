package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.seguranca.UsuarioLogado;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Gestao de usuarios e perfis. Tudo aqui e exclusivo do ADMIN (tambem bloqueado por URL
 * no SecurityConfig). Regras contra atribuicao indevida de privilegios:
 *  - o perfil so pode ser um dos tres do enum Perfil;
 *  - o ADMIN nao altera o proprio perfil nem exclui a si mesmo;
 *  - o ultimo ADMIN do sistema nao pode ser rebaixado nem excluido.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private static final int SENHA_MINIMA = 6;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;
    private final AuditoriaService auditoria;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            UsuarioLogado usuarioLogado, AuditoriaService auditoria) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioLogado = usuarioLogado;
        this.auditoria = auditoria;
    }

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll(Sort.by("username"));
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Usuario criar(UsuarioForm form) {
        String username = validarUsername(form.getUsername(), null);
        exigirPerfil(form.getPerfil());
        if (form.getSenha() == null || form.getSenha().length() < SENHA_MINIMA) {
            throw new RegraNegocioException("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }

        Usuario novo = new Usuario();
        novo.setUsername(username);
        novo.setPerfil(form.getPerfil());
        novo.setPassword(passwordEncoder.encode(form.getSenha()));
        novo = usuarioRepository.save(novo);

        auditoria.abrir(TipoOperacao.INCLUSAO_USUARIO, "Usuario", novo.getId())
                .campo("username", null, novo.getUsername())
                .campo("perfil", null, novo.getPerfil())
                .credencial("senha", "(definida)")
                .gravar();
        return novo;
    }

    public Usuario atualizar(String id, UsuarioForm form) {
        Usuario usuario = buscarObrigatorio(id);
        String usernameAntes = usuario.getUsername();
        Perfil perfilAntes = usuario.getPerfil();

        String username = validarUsername(form.getUsername(), id);
        exigirPerfil(form.getPerfil());

        boolean ehOProprio = usernameAntes.equals(usuarioLogado.username());
        if (ehOProprio && form.getPerfil() != perfilAntes) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (perfilAntes == Perfil.ADMIN && form.getPerfil() != Perfil.ADMIN && ultimoAdmin()) {
            throw new RegraNegocioException("Este é o último ADMIN do sistema; o perfil dele não pode ser rebaixado.");
        }

        usuario.setUsername(username);
        usuario.setPerfil(form.getPerfil());

        boolean trocouSenha = form.getSenha() != null && !form.getSenha().isBlank();
        if (trocouSenha) {
            if (form.getSenha().length() < SENHA_MINIMA) {
                throw new RegraNegocioException("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
            }
            usuario.setPassword(passwordEncoder.encode(form.getSenha()));
        }
        usuarioRepository.save(usuario);

        AuditoriaService.Lancamento lanc = auditoria.abrir(TipoOperacao.ALTERACAO_USUARIO, "Usuario", id)
                .campo("username", usernameAntes, usuario.getUsername())
                .campo("perfil", perfilAntes, usuario.getPerfil());
        if (trocouSenha) {
            lanc.credencial("senha", "(redefinida)");
        }
        lanc.gravar();
        return usuario;
    }

    public void excluir(String id) {
        Usuario usuario = buscarObrigatorio(id);
        if (usuario.getUsername().equals(usuarioLogado.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (usuario.getPerfil() == Perfil.ADMIN && ultimoAdmin()) {
            throw new RegraNegocioException("Não é possível excluir o último ADMIN do sistema.");
        }
        usuarioRepository.deleteById(id);

        auditoria.abrir(TipoOperacao.EXCLUSAO_USUARIO, "Usuario", id)
                .campo("username", usuario.getUsername(), null)
                .campo("perfil", usuario.getPerfil(), null)
                .gravar();
    }

    private Usuario buscarObrigatorio(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private boolean ultimoAdmin() {
        return usuarioRepository.countByPerfil(Perfil.ADMIN) <= 1;
    }

    private void exigirPerfil(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException("Escolha um perfil: ADMIN, GERENTE ou ATENDENTE.");
        }
    }

    private String validarUsername(String username, String idAtual) {
        String limpo = username == null ? "" : username.trim();
        if (!limpo.matches("[A-Za-z0-9._-]{3,30}")) {
            throw new RegraNegocioException(
                    "Username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        usuarioRepository.findByUsername(limpo)
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw new RegraNegocioException("Já existe um usuário com o username " + limpo + ".");
                });
        return limpo;
    }
}
