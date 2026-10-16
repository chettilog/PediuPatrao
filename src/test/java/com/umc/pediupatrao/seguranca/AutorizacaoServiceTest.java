package com.umc.pediupatrao.seguranca;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.util.Formatos;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Segunda camada de protecao: mesmo chamando o PedidoService diretamente (sem passar pela URL),
 * o @PreAuthorize impede que outro perfil cancele ou aplique desconto.
 */
@SpringJUnitConfig(AutorizacaoServiceTest.Config.class)
class AutorizacaoServiceTest {

    @Configuration
    @EnableMethodSecurity
    @Import(PedidoService.class)
    static class Config {
    }

    @MockitoBean
    private PedidoRepository pedidoRepository;
    @MockitoBean
    private ClienteRepository clienteRepository;
    @MockitoBean
    private ProdutoRepository produtoRepository;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioLogado usuarioLogado;
    @MockitoBean
    private Formatos formatos;
    @MockitoBean
    private Clock relogio;

    @Autowired
    private PedidoService pedidoService;

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoCancelaNemComChamadaDireta() {
        assertThrows(AccessDeniedException.class,
                () -> pedidoService.cancelar("x", "Justificativa suficientemente longa"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoDaDescontoNemComChamadaDireta() {
        assertThrows(AccessDeniedException.class,
                () -> pedidoService.aplicarDesconto("x", BigDecimal.TEN, null));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoRegistraPedido() {
        assertThrows(AccessDeniedException.class, () -> pedidoService.registrar(null));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerentePassaPelaSegurancaEChegaNaRegraDeNegocio() {
        when(pedidoRepository.findById(any())).thenReturn(Optional.empty());
        // passou pelo @PreAuthorize; falha depois apenas porque o pedido nao existe
        assertThrows(RecursoNaoEncontradoException.class,
                () -> pedidoService.cancelar("x", "Justificativa suficientemente longa"));
    }
}
