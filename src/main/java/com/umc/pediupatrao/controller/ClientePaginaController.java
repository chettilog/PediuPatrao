package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de clientes (as rotas sao as mesmas do projeto original). */
@Controller
public class ClientePaginaController {

    private final ClienteService clienteService;

    public ClientePaginaController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping("/clientes")
    public String clientes(Model model, HttpServletRequest request) {
        // so decide se os botoes aparecem; a API faz a verificacao de verdade
        model.addAttribute("podeEditar", request.isUserInRole("ATENDENTE"));
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        return formulario(model, new Cliente());
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, Model model, RedirectAttributes redirect) {
        try {
            clienteService.cadastrar(cliente);
            redirect.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
            return "redirect:/clientes";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            cliente.setId(null);
            return formulario(model, cliente);
        }
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model, RedirectAttributes redirect) {
        return clienteService.buscarPorId(id)
                .map(c -> formulario(model, c))
                .orElseGet(() -> {
                    redirect.addFlashAttribute("erro", "Cliente não encontrado.");
                    return "redirect:/clientes";
                });
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente, Model model,
            RedirectAttributes redirect) {
        try {
            clienteService.atualizar(id, cliente);
            redirect.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
            return "redirect:/clientes";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            cliente.setId(id);
            return formulario(model, cliente);
        }
    }

    private String formulario(Model model, Cliente cliente) {
        model.addAttribute("cliente", cliente);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }
}
