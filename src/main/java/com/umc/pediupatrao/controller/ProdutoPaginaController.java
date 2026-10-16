package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.service.ProdutoService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Tela do cardapio. No projeto original o formulario enviava dados de formulario para a API JSON
 * e os botoes de editar/excluir apontavam para rotas inexistentes; aqui tudo passa por formularios.
 */
@Controller
public class ProdutoPaginaController {

    private static final List<String> TIPOS = List.of("Pizza", "Lanche", "Bebida", "Porção", "Adicional");

    private final ProdutoService produtoService;

    public ProdutoPaginaController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping("/produtos")
    public String produtos(@RequestParam(required = false) String editar, Model model) {
        Produto emEdicao = new Produto();
        emEdicao.setAtivo(true);
        if (editar != null) {
            emEdicao = produtoService.buscarPorId(editar)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
        }
        return pagina(model, emEdicao);
    }

    @PostMapping("/produtos/salvar")
    public String salvar(@ModelAttribute Produto produto, Model model, RedirectAttributes redirect) {
        try {
            produtoService.salvar(produto);
            redirect.addFlashAttribute("sucesso", "Produto salvo.");
            return "redirect:/produtos";
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return pagina(model, produto);
        }
    }

    @PostMapping("/produtos/{id}/excluir")
    public String excluir(@PathVariable String id, RedirectAttributes redirect) {
        produtoService.excluir(id);
        redirect.addFlashAttribute("sucesso", "Produto excluído.");
        return "redirect:/produtos";
    }

    private String pagina(Model model, Produto emEdicao) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("produto", emEdicao);
        model.addAttribute("tiposProduto", TIPOS);
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }
}
