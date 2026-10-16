package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoForm;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.seguranca.UsuarioLogado;
import com.umc.pediupatrao.util.Formatos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Regras de negocio dos pedidos:
 *  - valores calculados somente aqui (precos vem do cadastro de produtos);
 *  - fluxo de status controlado por StatusPedido.seguinte();
 *  - desconto e cancelamento exclusivos do GERENTE e apenas antes da saida;
 *  - pedido cancelado nao aceita mais nenhuma alteracao;
 *  - toda mudanca e registrada na auditoria.
 */
@Service
public class PedidoService {

    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int QUANTIDADE_MAXIMA = 50;
    private static final int JUSTIFICATIVA_MINIMA = 10;
    private static final String ENTIDADE = "Pedido";

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;
    private final UsuarioLogado usuarioLogado;
    private final Formatos formatos;
    private final Clock relogio;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoria, UsuarioLogado usuarioLogado,
            Formatos formatos, Clock relogio) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoria = auditoria;
        this.usuarioLogado = usuarioLogado;
        this.formatos = formatos;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ consultas

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos(StatusPedido filtro) {
        Sort maisRecentes = Sort.by(Sort.Direction.DESC, "entradaEm");
        return filtro == null
                ? pedidoRepository.findAll(maisRecentes)
                : pedidoRepository.findByStatus(filtro, maisRecentes);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    // ------------------------------------------------------------------ entrada (RF02 / RF03)

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido registrar(PedidoForm form) {
        LocalDateTime agora = LocalDateTime.now(relogio);
        String quem = usuarioLogado.username();

        Pedido pedido = new Pedido();
        aplicarFormulario(pedido, form);
        pedido.setEntradaEm(agora);
        pedido.setRegistradoPor(quem);
        pedido.registrarMovimentacao(StatusPedido.RECEBIDO, agora, quem);
        recalcular(pedido);
        pedido = pedidoRepository.save(pedido);

        auditoria.abrir(TipoOperacao.INCLUSAO_PEDIDO, ENTIDADE, pedido.getId())
                .campo("cliente", null, pedido.getClienteNome())
                .campo("tipoAtendimento", null, pedido.getTipoAtendimento())
                .campo("itens", null, descreverItens(pedido))
                .campo("observacao", null, pedido.getObservacao())
                .campo("valorTotal", null, formatos.moeda(pedido.getValorTotal()))
                .campo("status", null, pedido.getStatus())
                .campo("entradaEm", null, pedido.getEntradaEm())
                .gravar();
        return pedido;
    }

    /** Troca itens/cliente/tipo enquanto o pedido ainda nao foi para a cozinha. */
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterar(String id, PedidoForm form) {
        Pedido pedido = buscar(id);
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            throw new RegraNegocioException(
                    "Itens só podem ser alterados enquanto o pedido está RECEBIDO (antes de ir para a cozinha).");
        }

        String clienteAntes = pedido.getClienteNome();
        TipoAtendimento tipoAntes = pedido.getTipoAtendimento();
        String itensAntes = descreverItens(pedido);
        String observacaoAntes = pedido.getObservacao();
        BigDecimal subtotalAntes = pedido.getSubtotal();
        BigDecimal descontoAntes = pedido.getValorDesconto();
        BigDecimal totalAntes = pedido.getValorTotal();

        aplicarFormulario(pedido, form);
        recalcular(pedido);
        pedidoRepository.save(pedido);

        auditoria.abrir(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, id)
                .campo("cliente", clienteAntes, pedido.getClienteNome())
                .campo("tipoAtendimento", tipoAntes, pedido.getTipoAtendimento())
                .campo("itens", itensAntes, descreverItens(pedido))
                .campo("observacao", observacaoAntes, pedido.getObservacao())
                .campo("subtotal", formatos.moeda(subtotalAntes), formatos.moeda(pedido.getSubtotal()))
                .campo("valorDesconto", formatos.moeda(descontoAntes), formatos.moeda(pedido.getValorDesconto()))
                .campo("valorTotal", formatos.moeda(totalAntes), formatos.moeda(pedido.getValorTotal()))
                .gravar();
        return pedido;
    }

    // ------------------------------------------------------------------ transicoes e saida (RF03)

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido avancarStatus(String id, StatusPedido destino) {
        if (destino == null) {
            throw new RegraNegocioException("Informe o novo status.");
        }
        Pedido pedido = buscar(id);
        StatusPedido atual = pedido.getStatus();

        if (atual == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não pode ter o status alterado.");
        }
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException(
                    "Cancelamento só pela operação de cancelamento do gerente, com justificativa.");
        }
        StatusPedido permitido = atual.seguinte(pedido.getTipoAtendimento());
        if (permitido == null) {
            throw new RegraNegocioException("Pedido já finalizado; o status não pode mais mudar.");
        }
        if (destino != permitido) {
            throw new RegraNegocioException("Transição inválida: " + atual.getRotulo() + " → " + destino.getRotulo()
                    + ". A próxima etapa deste pedido é " + permitido.getRotulo() + ".");
        }

        LocalDateTime agora = LocalDateTime.now(relogio);
        String quem = usuarioLogado.username();
        pedido.registrarMovimentacao(destino, agora, quem);

        AuditoriaService.Lancamento lanc = auditoria.abrir(TipoOperacao.MUDANCA_STATUS, ENTIDADE, id)
                .campo("status", atual, destino);

        if (destino.marcaSaida()) {
            pedido.setSaidaEm(agora);
            pedido.setSaidaPor(quem);
            lanc.campo("saidaEm", null, agora).campo("saidaPor", null, quem);
        }
        if (destino == StatusPedido.FINALIZADO) {
            pedido.setFinalizadoEm(agora);
            lanc.campo("finalizadoEm", null, agora);
        }

        pedidoRepository.save(pedido);
        lanc.gravar();
        return pedido;
    }

    // ------------------------------------------------------------------ gerente (RF04)

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual, String motivo) {
        Pedido pedido = buscar(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Pedido cancelado não aceita desconto.");
        }
        if (!pedido.getStatus().antesDaSaida()) {
            throw new RegraNegocioException(
                    "Desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        BigDecimal novoPercentual = percentual.setScale(2, RoundingMode.HALF_UP);
        if (novoPercentual.signum() < 0 || novoPercentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new RegraNegocioException("O desconto deve ficar entre 0% e 20% do valor do pedido.");
        }
        BigDecimal percentualAntes = Objects.requireNonNullElse(pedido.getPercentualDesconto(), BigDecimal.ZERO);
        if (novoPercentual.compareTo(percentualAntes) == 0) {
            throw new RegraNegocioException("O pedido já está com " + formatos.percentual(percentualAntes) + " de desconto.");
        }

        BigDecimal valorDescontoAntes = pedido.getValorDesconto();
        BigDecimal totalAntes = pedido.getValorTotal();

        pedido.setPercentualDesconto(novoPercentual);
        recalcular(pedido);
        pedidoRepository.save(pedido);

        auditoria.abrir(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, id)
                .campo("percentualDesconto", formatos.percentual(percentualAntes), formatos.percentual(novoPercentual))
                .campo("valorDesconto", formatos.moeda(valorDescontoAntes), formatos.moeda(pedido.getValorDesconto()))
                .campo("valorTotal", formatos.moeda(totalAntes), formatos.moeda(pedido.getValorTotal()))
                .justificativa(motivo)
                .gravar();
        return pedido;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = buscar(id);
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Este pedido já está cancelado.");
        }
        if (!pedido.getStatus().antesDaSaida()) {
            throw new RegraNegocioException(
                    "Pedido que já saiu para entrega ou foi retirado não pode ser cancelado.");
        }
        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() < JUSTIFICATIVA_MINIMA) {
            throw new RegraNegocioException(
                    "Informe a justificativa do cancelamento (mínimo de " + JUSTIFICATIVA_MINIMA + " caracteres).");
        }

        StatusPedido anterior = pedido.getStatus();
        LocalDateTime agora = LocalDateTime.now(relogio);
        String quem = usuarioLogado.username();

        // Itens e valores sao mantidos: o pedido cancelado continua consultavel como historico
        pedido.registrarMovimentacao(StatusPedido.CANCELADO, agora, quem);
        pedido.setCanceladoEm(agora);
        pedido.setCanceladoPor(quem);
        pedido.setMotivoCancelamento(motivo);
        pedidoRepository.save(pedido);

        auditoria.abrir(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, id)
                .campo("status", anterior, StatusPedido.CANCELADO)
                .campo("canceladoEm", null, agora)
                .justificativa(motivo)
                .gravar();
        return pedido;
    }

    // ------------------------------------------------------------------ apoio

    private void aplicarFormulario(Pedido pedido, PedidoForm form) {
        if (form == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        if (form.getClienteId() == null || form.getClienteId().isBlank()) {
            throw new RegraNegocioException("Selecione o cliente.");
        }
        Cliente cliente = clienteRepository.findById(form.getClienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));

        if (form.getTipoAtendimento() == null) {
            throw new RegraNegocioException("Informe se o pedido é para entrega ou retirada no balcão.");
        }
        if (form.getTipoAtendimento() == TipoAtendimento.ENTREGA
                && (cliente.getLogradouro() == null || cliente.getLogradouro().isBlank())) {
            throw new RegraNegocioException("O cliente não tem endereço cadastrado; não é possível registrar entrega.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        List<PedidoForm.Linha> linhas = form.getItens() == null ? List.of() : form.getItens();
        for (PedidoForm.Linha linha : linhas) {
            if (linha == null || linha.getProdutoId() == null || linha.getProdutoId().isBlank()) {
                continue; // linha em branco do formulario
            }
            Produto produto = produtoRepository.findById(linha.getProdutoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado no cardápio."));
            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está inativo no cardápio.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("O produto " + produto.getNome() + " está sem preço válido.");
            }
            int quantidade = linha.getQuantidade() == null ? 0 : linha.getQuantidade();
            if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
                throw new RegraNegocioException("Quantidade de " + produto.getNome()
                        + " deve ser de 1 a " + QUANTIDADE_MAXIMA + ".");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, preco));
        }
        if (itens.isEmpty()) {
            throw new RegraNegocioException("Adicione pelo menos um item ao pedido.");
        }

        String observacao = form.getObservacao() == null ? null : form.getObservacao().trim();
        if (observacao != null && observacao.length() > 300) {
            throw new RegraNegocioException("A observação pode ter no máximo 300 caracteres.");
        }

        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoAtendimento(form.getTipoAtendimento());
        pedido.setObservacao(observacao == null || observacao.isEmpty() ? null : observacao);
        pedido.setItens(itens);
    }

    /** Subtotal, desconto e total sempre recalculados a partir dos itens e do percentual autorizado. */
    private void recalcular(Pedido pedido) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            BigDecimal valorItem = item.getPrecoUnitario()
                    .multiply(BigDecimal.valueOf(item.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setSubtotal(valorItem);
            subtotal = subtotal.add(valorItem);
        }
        BigDecimal percentual = Objects.requireNonNullElse(pedido.getPercentualDesconto(), BigDecimal.ZERO);
        BigDecimal desconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto));
    }

    private String descreverItens(Pedido pedido) {
        return pedido.getItens().stream()
                .map(i -> i.getQuantidade() + "x " + i.getDescricao() + " (" + formatos.moeda(i.getPrecoUnitario()) + ")")
                .collect(Collectors.joining("; "));
    }
}
