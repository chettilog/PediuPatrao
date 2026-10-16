package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.EventoAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.EventoAuditoriaRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.seguranca.UsuarioLogado;
import com.umc.pediupatrao.util.Formatos;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClienteServiceTest {

    private ClienteRepository clienteRepository;
    private PedidoRepository pedidoRepository;
    private EventoAuditoriaRepository auditoriaRepository;
    private ClienteService service;

    @BeforeEach
    void preparar() {
        clienteRepository = mock(ClienteRepository.class);
        pedidoRepository = mock(PedidoRepository.class);
        auditoriaRepository = mock(EventoAuditoriaRepository.class);
        UsuarioLogado usuarioLogado = mock(UsuarioLogado.class);
        when(usuarioLogado.username()).thenReturn("atendente01");
        when(usuarioLogado.perfil()).thenReturn("ATENDENTE");
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        Clock relogio = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));
        AuditoriaService auditoria = new AuditoriaService(auditoriaRepository, usuarioLogado, new Formatos(), relogio);
        service = new ClienteService(clienteRepository, pedidoRepository, auditoria);
    }

    @Test
    void alteracaoDeEnderecoETelefoneGeraUmRegistroPorCampo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(clienteMaria()));

        Cliente novosDados = clienteMaria();
        novosDados.setLogradouro("Av. Brasil");
        novosDados.setTelefone("(11) 98888-7777");
        service.atualizar("c1", novosDados);

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaRepository, times(2)).insert(captor.capture());
        List<EventoAuditoria> eventos = captor.getAllValues();

        EventoAuditoria telefone = eventos.get(0);
        assertEquals("telefone", telefone.getCampo());
        assertEquals("(11) *****-4321", telefone.getValorAnterior()); // numero completo nao fica exposto
        assertEquals("(11) *****-7777", telefone.getValorNovo());

        EventoAuditoria logradouro = eventos.get(1);
        assertEquals(TipoOperacao.ALTERACAO_CLIENTE, logradouro.getTipoOperacao());
        assertEquals("c1", logradouro.getEntidadeId());
        assertEquals("logradouro", logradouro.getCampo());
        assertEquals("Rua das Flores", logradouro.getValorAnterior());
        assertEquals("Av. Brasil", logradouro.getValorNovo());
        assertEquals("atendente01", logradouro.getUsuario());
        assertEquals("ATENDENTE", logradouro.getPerfil());
    }

    @Test
    void salvarSemMudarNadaNaoGeraAuditoria() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(clienteMaria()));
        service.atualizar("c1", clienteMaria());
        verify(auditoriaRepository, never()).insert(any(EventoAuditoria.class));
    }

    @Test
    void idEnviadoNaRequisicaoNaoTrocaOClienteAlterado() {
        Cliente existente = clienteMaria();
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(existente));

        Cliente dados = clienteMaria();
        dados.setId("outro-id");
        Cliente salvo = service.atualizar("c1", dados);

        assertEquals("c1", salvo.getId());
    }

    @Test
    void naoExcluiClienteComPedidos() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(clienteMaria()));
        when(pedidoRepository.existsByClienteId("c1")).thenReturn(true);

        assertThrows(RegraNegocioException.class, () -> service.excluir("c1"));
        verify(clienteRepository, never()).deleteById(any());
    }

    @Test
    void telefoneInvalidoERecusado() {
        Cliente c = clienteMaria();
        c.setId(null);
        c.setTelefone("1234");
        assertThrows(RegraNegocioException.class, () -> service.cadastrar(c));
    }

    private static Cliente clienteMaria() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria Souza");
        c.setTelefone("(11) 91234-4321");
        c.setLogradouro("Rua das Flores");
        c.setNumero("100");
        c.setBairro("Centro");
        c.setCidade("Mogi das Cruzes");
        c.setEstado("SP");
        return c;
    }
}
