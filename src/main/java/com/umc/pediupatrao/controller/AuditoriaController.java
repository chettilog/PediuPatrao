package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.EventoAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Tela de consulta da auditoria (somente leitura, ADMIN e GERENTE). */
@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

    static final List<String> ENTIDADES = List.of("Pedido", "Cliente", "Produto", "Usuario", "Rota");

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String consultar(@RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao tipo,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {

        Page<EventoAuditoria> eventos = auditoriaService.consultar(usuario, tipo, entidade, entidadeId, pagina);

        model.addAttribute("eventos", eventos);
        model.addAttribute("tipos", TipoOperacao.values());
        model.addAttribute("entidades", ENTIDADES);
        model.addAttribute("fUsuario", usuario);
        model.addAttribute("fTipo", tipo);
        model.addAttribute("fEntidade", entidade);
        model.addAttribute("fEntidadeId", entidadeId);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
