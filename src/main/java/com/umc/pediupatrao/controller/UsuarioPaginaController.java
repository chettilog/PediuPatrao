package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de usuarios (somente ADMIN). Rotas mantidas do projeto original. */
@Controller
public class UsuarioPaginaController {

    private final UsuarioService usuarioService;

    public UsuarioPaginaController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        return formulario(model, new UsuarioForm(), null);
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute("usuarioForm") UsuarioForm form, Model model,
            RedirectAttributes redirect) {
        try {
            usuarioService.criar(form);
            redirect.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
            return "redirect:/usuarios";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            form.setSenha(null);
            return formulario(model, form, null);
        }
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model) {
        return usuarioService.buscarPorId(id)
                .map(u -> formulario(model, UsuarioForm.de(u), id))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute("usuarioForm") UsuarioForm form,
            Model model, RedirectAttributes redirect) {
        try {
            usuarioService.atualizar(id, form);
            redirect.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
            return "redirect:/usuarios";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            form.setSenha(null);
            return formulario(model, form, id);
        }
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirect) {
        try {
            usuarioService.excluir(id);
            redirect.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private String formulario(Model model, UsuarioForm form, String idEmEdicao) {
        model.addAttribute("usuarioForm", form);
        model.addAttribute("idEmEdicao", idEmEdicao);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }
}
