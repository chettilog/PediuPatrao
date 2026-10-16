package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.EventoAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da auditoria via API. So existe GET: nao ha endpoint para alterar ou apagar registros
 * (e o SecurityConfig ainda nega qualquer outro metodo HTTP nessa rota).
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaApiController {

    private final AuditoriaService auditoriaService;

    public AuditoriaApiController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<EventoAuditoria> consultar(@RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao tipo,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(defaultValue = "0") int pagina) {
        Page<EventoAuditoria> resultado = auditoriaService.consultar(usuario, tipo, entidade, entidadeId, pagina);
        return resultado.getContent();
    }
}
