package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de pedidos (formularios Thymeleaf; cada acao recarrega a pagina). */
@Controller
@RequestMapping("/pedidos")
public class PedidoPaginaController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoPaginaController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) StatusPedido status, Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos(status));
        model.addAttribute("statusFiltro", status);
        model.addAttribute("todosStatus", StatusPedido.values());
        model.addAttribute("content", "pedidos/lista :: content");
        return "layout";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        return formulario(model, new PedidoForm(), null);
    }

    @PostMapping("/novo")
    public String registrar(@ModelAttribute("pedidoForm") PedidoForm form, Model model,
            RedirectAttributes redirect) {
        try {
            Pedido pedido = pedidoService.registrar(form);
            redirect.addFlashAttribute("sucesso", "Pedido #" + pedido.getCodigo() + " registrado.");
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, null);
        }
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable String id, Model model) {
        Pedido pedido = pedidoService.buscar(id);
        model.addAttribute("pedido", pedido);
        model.addAttribute("proximoStatus", pedido.getStatus().seguinte(pedido.getTipoAtendimento()));
        model.addAttribute("descontoMaximo", PedidoService.DESCONTO_MAXIMO);
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirect) {
        Pedido pedido = pedidoService.buscar(id);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            redirect.addFlashAttribute("erro", "Itens só podem ser alterados enquanto o pedido está RECEBIDO.");
            return "redirect:/pedidos/" + id;
        }
        return formulario(model, PedidoForm.de(pedido), pedido);
    }

    @PostMapping("/{id}/editar")
    public String salvarEdicao(@PathVariable String id, @ModelAttribute("pedidoForm") PedidoForm form,
            Model model, RedirectAttributes redirect) {
        try {
            pedidoService.alterar(id, form);
            redirect.addFlashAttribute("sucesso", "Pedido atualizado.");
            return "redirect:/pedidos/" + id;
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return formulario(model, form, pedidoService.buscar(id));
        }
    }

    @PostMapping("/{id}/status")
    public String avancar(@PathVariable String id, @RequestParam StatusPedido destino, RedirectAttributes redirect) {
        try {
            Pedido pedido = pedidoService.avancarStatus(id, destino);
            redirect.addFlashAttribute("sucesso", "Status alterado para " + pedido.getStatus().getRotulo() + ".");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/desconto")
    public String desconto(@PathVariable String id, @RequestParam String percentual,
            @RequestParam(required = false) String motivo, RedirectAttributes redirect) {
        try {
            BigDecimal valor = new BigDecimal(percentual.trim().replace(',', '.'));
            pedidoService.aplicarDesconto(id, valor, motivo);
            redirect.addFlashAttribute("sucesso", "Desconto aplicado.");
        } catch (NumberFormatException e) {
            redirect.addFlashAttribute("erro", "Percentual inválido. Use um número, por exemplo 10 ou 12,5.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
            RedirectAttributes redirect) {
        try {
            pedidoService.cancelar(id, justificativa);
            redirect.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    private String formulario(Model model, PedidoForm form, Pedido emEdicao) {
        if (form.getItens().isEmpty()) {
            form.getItens().add(new PedidoForm.Linha());
        }
        model.addAttribute("pedidoForm", form);
        model.addAttribute("pedidoEmEdicao", emEdicao);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtoService.listarAtivos());
        model.addAttribute("tiposAtendimento", TipoAtendimento.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }
}
