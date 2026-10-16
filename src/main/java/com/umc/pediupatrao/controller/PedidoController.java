package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.OperacaoGerente;
import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * API REST de pedidos. As permissoes ficam no SecurityConfig (URL) e no PedidoService (@PreAuthorize);
 * este controller so traduz HTTP para chamadas do service.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<Pedido> listarPedidos(@RequestParam(required = false) StatusPedido status) {
        return pedidoService.listarPedidos(status);
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido criarPedido(@RequestBody PedidoForm form) {
        return pedidoService.registrar(form);
    }

    @PutMapping("/{id}")
    public Pedido alterar(@PathVariable String id, @RequestBody PedidoForm form) {
        return pedidoService.alterar(id, form);
    }

    // Mantem o endpoint original (PUT /api/pedidos/{id}/status?status=...), agora com transicoes validadas
    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam StatusPedido status) {
        return pedidoService.avancarStatus(id, status);
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody OperacaoGerente.Desconto corpo) {
        return pedidoService.aplicarDesconto(id, corpo.percentual(), corpo.motivo());
    }

    @PostMapping("/{id}/cancelamento")
    public Pedido cancelar(@PathVariable String id, @RequestBody OperacaoGerente.Cancelamento corpo) {
        return pedidoService.cancelar(id, corpo.justificativa());
    }
}
