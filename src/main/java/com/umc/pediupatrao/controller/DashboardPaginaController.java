package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela do dashboard. A pagina so traz a estrutura; os numeros chegam pelo JavaScript
 * (static/js/dashboard.js), que consome a API /api/dashboard/**.
 */
@Controller
public class DashboardPaginaController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
