package com.umc.pediupatrao.seguranca;

import com.umc.pediupatrao.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Executado pelo Spring Security sempre que um usuario autenticado tenta algo que o perfil
 * nao permite (regra de URL ou @PreAuthorize) ou envia requisicao sem token CSRF valido.
 * Registra a tentativa no log e na auditoria e responde 403.
 */
@Component
public class AcessoNegadoHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AcessoNegadoHandler.class);

    private final AuditoriaService auditoria;
    private final UsuarioLogado usuarioLogado;

    public AcessoNegadoHandler(AuditoriaService auditoria, UsuarioLogado usuarioLogado) {
        this.auditoria = auditoria;
        this.usuarioLogado = usuarioLogado;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException excecao) throws IOException {

        String rota = request.getMethod() + " " + request.getRequestURI();
        boolean falhaCsrf = excecao instanceof CsrfException;
        String motivo = falhaCsrf
                ? "Requisição recusada: token CSRF ausente ou inválido."
                : "Seu perfil não tem permissão para esta operação.";

        log.warn("Acesso negado: usuario={} perfil={} rota={} csrf={}",
                usuarioLogado.username(), usuarioLogado.perfil(), rota, falhaCsrf);

        if (usuarioLogado.autenticado()) {
            try {
                auditoria.registrarAcessoNegado(rota);
            } catch (RuntimeException e) {
                // a resposta 403 nao pode depender da auditoria estar disponivel
                log.error("Falha ao auditar acesso negado em {}", rota, e);
            }
        }

        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":403,\"erro\":\"" + motivo + "\"}");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, motivo);
        }
    }
}
