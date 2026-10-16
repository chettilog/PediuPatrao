package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EventoAuditoria;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.EventoAuditoriaRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.seguranca.UsuarioLogado;
import com.umc.pediupatrao.util.Formatos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Regras do PedidoService sem banco: repositorios sao simulados em memoria,
 * e a auditoria e a real (so o repositorio dela e mock, para contar os registros gravados).
 */
class PedidoServiceTest {

    // 23/09/2026 14:35 em Sao Paulo
    private final Clock relogio = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));

    private PedidoRepository pedidoRepository;
    private EventoAuditoriaRepository auditoriaRepository;
    private UsuarioLogado usuarioLogado;
    private PedidoService service;

    private final Map<String, Pedido> banco = new HashMap<>();

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        ClienteRepository clienteRepository = mock(ClienteRepository.class);
        ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
        auditoriaRepository = mock(EventoAuditoriaRepository.class);
        usuarioLogado = mock(UsuarioLogado.class);
        Formatos formatos = new Formatos();

        when(usuarioLogado.username()).thenReturn("atendente01");
        when(usuarioLogado.perfil()).thenReturn("ATENDENTE");

        // "banco" em memoria para os pedidos
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("65f0c0ffee00000000" + String.format("%06d", banco.size() + 1));
            }
            banco.put(p.getId(), p);
            return p;
        });
        when(pedidoRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(banco.get(inv.getArgument(0))));

        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        cliente.setLogradouro("Rua das Flores");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));

        when(produtoRepository.findById("calabresa")).thenReturn(Optional.of(produto("calabresa", "Pizza Calabresa", 49.90)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante 2L", 14.00)));

        AuditoriaService auditoria = new AuditoriaService(auditoriaRepository, usuarioLogado, formatos, relogio);
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository,
                auditoria, usuarioLogado, formatos, relogio);
    }

    // ------------------------------------------------------------ RF02

    @Test
    void totalECalculadoNoServidorComPrecosDoCardapio() {
        Pedido p = service.registrar(form(TipoAtendimento.ENTREGA));

        assertEquals(new BigDecimal("113.80"), p.getSubtotal());   // 2 x 49,90 + 1 x 14,00
        assertEquals(new BigDecimal("0.00"), p.getValorDesconto());
        assertEquals(new BigDecimal("113.80"), p.getValorTotal());
        assertEquals(new BigDecimal("49.90"), p.getItens().get(0).getPrecoUnitario());
    }

    @Test
    void registroGuardaEntradaResponsavelEStatusRecebido() {
        Pedido p = service.registrar(form(TipoAtendimento.RETIRADA));

        assertEquals(StatusPedido.RECEBIDO, p.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 23, 14, 35), p.getEntradaEm());
        assertEquals("atendente01", p.getRegistradoPor());
        assertEquals(1, p.getHistorico().size());
    }

    @Test
    void pedidoSemItensERecusado() {
        PedidoForm vazio = new PedidoForm();
        vazio.setClienteId("c1");
        vazio.setTipoAtendimento(TipoAtendimento.RETIRADA);
        vazio.setItens(List.of(new PedidoForm.Linha("", 1)));

        assertThrows(RegraNegocioException.class, () -> service.registrar(vazio));
    }

    @Test
    void quantidadeForaDoLimiteERecusada() {
        PedidoForm f = form(TipoAtendimento.RETIRADA);
        f.getItens().get(0).setQuantidade(0);
        assertThrows(RegraNegocioException.class, () -> service.registrar(f));
    }

    // ------------------------------------------------------------ RF03

    @Test
    void naoPermitePularEtapa() {
        Pedido p = service.registrar(form(TipoAtendimento.ENTREGA));
        String id = p.getId();

        assertThrows(RegraNegocioException.class, () -> service.avancarStatus(id, StatusPedido.PRONTO));
        assertThrows(RegraNegocioException.class, () -> service.avancarStatus(id, StatusPedido.FINALIZADO));
    }

    @Test
    void naoPermiteFinalizarPedidoEmPreparacao() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        service.avancarStatus(id, StatusPedido.EM_PREPARACAO);

        assertThrows(RegraNegocioException.class, () -> service.avancarStatus(id, StatusPedido.FINALIZADO));
    }

    @Test
    void saidaRegistraHorarioEResponsavel() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        service.avancarStatus(id, StatusPedido.EM_PREPARACAO);
        service.avancarStatus(id, StatusPedido.PRONTO);

        when(usuarioLogado.username()).thenReturn("gerente01");
        Pedido p = service.avancarStatus(id, StatusPedido.SAIU_PARA_ENTREGA);

        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, p.getStatus());
        assertNotNull(p.getSaidaEm());
        assertEquals("gerente01", p.getSaidaPor());
        assertEquals(4, p.getHistorico().size());
    }

    @Test
    void pedidoDeBalcaoNaoSaiParaEntrega() {
        String id = service.registrar(form(TipoAtendimento.RETIRADA)).getId();
        service.avancarStatus(id, StatusPedido.EM_PREPARACAO);
        service.avancarStatus(id, StatusPedido.PRONTO);

        assertThrows(RegraNegocioException.class, () -> service.avancarStatus(id, StatusPedido.SAIU_PARA_ENTREGA));
        assertEquals(StatusPedido.RETIRADO, service.avancarStatus(id, StatusPedido.RETIRADO).getStatus());
    }

    @Test
    void statusCanceladoNaoPodeSerUsadoComoTransicaoComum() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        assertThrows(RegraNegocioException.class, () -> service.avancarStatus(id, StatusPedido.CANCELADO));
    }

    // ------------------------------------------------------------ RF04

    @Test
    void descontoRecalculaTotalEGeraTresRegistrosDeAuditoria() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        org.mockito.Mockito.clearInvocations(auditoriaRepository);

        Pedido p = service.aplicarDesconto(id, new BigDecimal("10"), "cliente fiel");

        assertEquals(new BigDecimal("11.38"), p.getValorDesconto());
        assertEquals(new BigDecimal("102.42"), p.getValorTotal());
        // percentualDesconto, valorDesconto e valorTotal
        verify(auditoriaRepository, times(3)).insert(any(EventoAuditoria.class));
    }

    @Test
    void descontoAcimaDe20PorCentoERecusado() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto(id, new BigDecimal("20.01"), null));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto(id, new BigDecimal("-1"), null));
    }

    @Test
    void descontoPodeSerReajustadoAntesDaSaida() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        service.aplicarDesconto(id, new BigDecimal("10"), null);
        Pedido p = service.aplicarDesconto(id, new BigDecimal("20"), null);

        assertEquals(new BigDecimal("22.76"), p.getValorDesconto());
        assertEquals(new BigDecimal("91.04"), p.getValorTotal());
    }

    @Test
    void descontoDepoisDaSaidaERecusado() {
        String id = service.registrar(form(TipoAtendimento.RETIRADA)).getId();
        service.avancarStatus(id, StatusPedido.EM_PREPARACAO);
        service.avancarStatus(id, StatusPedido.PRONTO);
        service.avancarStatus(id, StatusPedido.RETIRADO);

        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto(id, BigDecimal.TEN, null));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        assertThrows(RegraNegocioException.class, () -> service.cancelar(id, null));
        assertThrows(RegraNegocioException.class, () -> service.cancelar(id, "curta"));
    }

    @Test
    void pedidoCanceladoPreservaDadosEBloqueiaQualquerAlteracao() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        service.avancarStatus(id, StatusPedido.EM_PREPARACAO);

        Pedido p = service.cancelar(id, "Cliente desistiu por telefone");

        assertEquals(StatusPedido.CANCELADO, p.getStatus());
        assertEquals("Cliente desistiu por telefone", p.getMotivoCancelamento());
        assertEquals(new BigDecimal("113.80"), p.getValorTotal()); // historico mantido
        assertEquals(2, p.getItens().size());

        assertThrows(RegraNegocioException.class, () -> service.avancarStatus(id, StatusPedido.PRONTO));
        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto(id, BigDecimal.TEN, null));
        assertThrows(RegraNegocioException.class, () -> service.cancelar(id, "Cancelar de novo, por engano"));
        assertThrows(RegraNegocioException.class, () -> service.alterar(id, form(TipoAtendimento.ENTREGA)));
    }

    @Test
    void naoCancelaPedidoQueJaSaiu() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        service.avancarStatus(id, StatusPedido.EM_PREPARACAO);
        service.avancarStatus(id, StatusPedido.PRONTO);
        service.avancarStatus(id, StatusPedido.SAIU_PARA_ENTREGA);

        assertThrows(RegraNegocioException.class, () -> service.cancelar(id, "Motivo qualquer bem longo"));
    }

    @Test
    void cancelamentoVaiParaAuditoriaComJustificativa() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        org.mockito.Mockito.clearInvocations(auditoriaRepository);

        service.cancelar(id, "Pizzaria sem massa no momento");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaRepository, times(2)).insert(captor.capture()); // status e canceladoEm
        EventoAuditoria status = captor.getAllValues().get(0);
        assertEquals("status", status.getCampo());
        assertEquals("Recebido", status.getValorAnterior());
        assertEquals("Cancelado", status.getValorNovo());
        assertEquals("Pizzaria sem massa no momento", status.getJustificativa());
        assertEquals("atendente01", status.getUsuario());
        assertEquals(LocalDateTime.of(2026, 9, 23, 14, 35), status.getDataHora());
    }

    @Test
    void falhaDeRegraNaoGravaAuditoria() {
        String id = service.registrar(form(TipoAtendimento.ENTREGA)).getId();
        org.mockito.Mockito.clearInvocations(auditoriaRepository);

        assertThrows(RegraNegocioException.class, () -> service.aplicarDesconto(id, new BigDecimal("50"), null));
        verify(auditoriaRepository, never()).insert(any(EventoAuditoria.class));
        assertNull(banco.get(id).getCanceladoEm());
        assertTrue(banco.get(id).getPercentualDesconto().signum() == 0);
    }

    // ------------------------------------------------------------ apoio

    private static Produto produto(String id, String nome, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo("Pizza");
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    private static PedidoForm form(TipoAtendimento tipo) {
        PedidoForm f = new PedidoForm();
        f.setClienteId("c1");
        f.setTipoAtendimento(tipo);
        f.getItens().add(new PedidoForm.Linha("calabresa", 2));
        f.getItens().add(new PedidoForm.Linha("refri", 1));
        return f;
    }
}
