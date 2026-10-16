package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Clientes: ATENDENTE cadastra, altera e exclui; GERENTE apenas consulta.
 * Toda inclusao, alteracao e exclusao vai para a auditoria, campo a campo.
 */
@Service
public class ClienteService {

    private static final String ENTIDADE = "Cliente";

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            AuditoriaService auditoria) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoria = auditoria;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll(Sort.by("nome"));
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente cadastrar(Cliente dados) {
        Cliente novo = new Cliente();
        copiarCamposEditaveis(dados, novo);
        validar(novo);
        novo = clienteRepository.save(novo);

        Cliente vazio = new Cliente();
        compararCampos(vazio, novo, auditoria.abrir(TipoOperacao.INCLUSAO_CLIENTE, ENTIDADE, novo.getId()))
                .gravar();
        return novo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        Cliente atual = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        Cliente antes = atual.copia();

        // Copia apenas os campos de cadastro: id e qualquer outro dado vindo da requisicao sao ignorados
        copiarCamposEditaveis(dados, atual);
        validar(atual);
        clienteRepository.save(atual);

        compararCampos(antes, atual, auditoria.abrir(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, id))
                .gravar();
        return atual;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public void excluir(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        if (pedidoRepository.existsByClienteId(id)) {
            throw new RegraNegocioException(
                    "Este cliente possui pedidos registrados e não pode ser excluído (o histórico seria perdido).");
        }
        clienteRepository.deleteById(id);

        compararCampos(cliente, new Cliente(), auditoria.abrir(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id))
                .gravar();
    }

    /** Lista, um a um, os campos auditados do cliente. Telefone sai mascarado. */
    private AuditoriaService.Lancamento compararCampos(Cliente a, Cliente d, AuditoriaService.Lancamento lanc) {
        return lanc
                .campo("nome", a.getNome(), d.getNome())
                .telefone("telefone", a.getTelefone(), d.getTelefone())
                .campo("cep", a.getCep(), d.getCep())
                .campo("logradouro", a.getLogradouro(), d.getLogradouro())
                .campo("numero", a.getNumero(), d.getNumero())
                .campo("complemento", a.getComplemento(), d.getComplemento())
                .campo("bairro", a.getBairro(), d.getBairro())
                .campo("cidade", a.getCidade(), d.getCidade())
                .campo("estado", a.getEstado(), d.getEstado());
    }

    private static void copiarCamposEditaveis(Cliente origem, Cliente destino) {
        destino.setNome(limpar(origem.getNome()));
        destino.setTelefone(limpar(origem.getTelefone()));
        destino.setCep(limpar(origem.getCep()));
        destino.setLogradouro(limpar(origem.getLogradouro()));
        destino.setNumero(limpar(origem.getNumero()));
        destino.setComplemento(limpar(origem.getComplemento()));
        destino.setBairro(limpar(origem.getBairro()));
        destino.setCidade(limpar(origem.getCidade()));
        String uf = limpar(origem.getEstado());
        destino.setEstado(uf == null ? null : uf.toUpperCase());
    }

    private static void validar(Cliente c) {
        if (c.getNome() == null || c.getNome().length() < 2) {
            throw new RegraNegocioException("Informe o nome do cliente.");
        }
        String digitosTelefone = c.getTelefone() == null ? "" : c.getTelefone().replaceAll("\\D", "");
        if (digitosTelefone.length() < 10 || digitosTelefone.length() > 11) {
            throw new RegraNegocioException("Telefone inválido: use DDD + número (10 ou 11 dígitos).");
        }
        if (c.getCep() != null && c.getCep().replaceAll("\\D", "").length() != 8) {
            throw new RegraNegocioException("CEP inválido: são 8 dígitos.");
        }
        if (c.getEstado() != null && !c.getEstado().matches("[A-Z]{2}")) {
            throw new RegraNegocioException("Estado deve ser a sigla com 2 letras (ex.: SP).");
        }
    }

    private static String limpar(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
