package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDto.Agrupamento;
import com.umc.pediupatrao.dto.DashboardDto.CriterioRanking;
import com.umc.pediupatrao.dto.DashboardDto.Distribuicao;
import com.umc.pediupatrao.dto.DashboardDto.EmAndamento;
import com.umc.pediupatrao.dto.DashboardDto.Opcao;
import com.umc.pediupatrao.dto.DashboardDto.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDto.PaginaPedidos;
import com.umc.pediupatrao.dto.DashboardDto.RankingProdutos;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDto.SituacaoPedidos;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Rotulado;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Dados do dashboard administrativo (somente leitura).
 *
 * Divisao de trabalho:
 *  - MongoDB: recorte por periodo (consulta indexada em entradaEm) ou por status;
 *  - FiltroDashboard: filtros de tipo de atendimento e status;
 *  - CalculadoraDashboard: indicadores, series, distribuicoes e ranking.
 *
 * Acesso: ADMIN e GERENTE (responsaveis pelo estabelecimento). Protegido aqui e no SecurityConfig.
 */
@Service
public class DashboardService {

    private static final List<StatusPedido> STATUS_EM_ANDAMENTO = List.of(StatusPedido.RECEBIDO,
            StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final Clock relogio;
    private final int minutosAlerta;

    public DashboardService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository, Clock relogio,
            @Value("${pediupatrao.dashboard.minutos-alerta:40}") int minutosAlerta) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.relogio = relogio;
        this.minutosAlerta = minutosAlerta;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Resumo resumo(FiltroDashboard filtro) {
        FiltroDashboard anterior = filtro.periodoAnterior();
        return CalculadoraDashboard.resumo(buscar(filtro), filtro.periodo(), buscar(anterior), anterior.periodo(),
                LocalDateTime.now(relogio));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public SerieVendas vendas(FiltroDashboard filtro, Agrupamento agrupamento) {
        return CalculadoraDashboard.serie(buscar(filtro), filtro.periodo(), agrupamento);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Distribuicao distribuicao(FiltroDashboard filtro) {
        return CalculadoraDashboard.distribuicao(buscar(filtro), filtro.periodo(), categoriasPorProduto());
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public SituacaoPedidos situacao(FiltroDashboard filtro) {
        return CalculadoraDashboard.situacao(buscar(filtro), filtro.periodo());
    }

    /** Fila atual da pizzaria: nao depende do periodo, so do status. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public EmAndamento emAndamento(TipoAtendimento tipoAtendimento) {
        List<Pedido> ativos = pedidoRepository.findByStatusIn(STATUS_EM_ANDAMENTO).stream()
                .filter(p -> tipoAtendimento == null || tipoAtendimento == p.getTipoAtendimento())
                .toList();
        return CalculadoraDashboard.emAndamento(ativos, LocalDateTime.now(relogio), minutosAlerta);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public PaginaPedidos pedidos(FiltroDashboard filtro, int pagina, int tamanho) {
        return CalculadoraDashboard.paginar(buscar(filtro), pagina, tamanho);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public RankingProdutos produtosMaisVendidos(FiltroDashboard filtro, CriterioRanking criterio, int limite) {
        return CalculadoraDashboard.ranking(buscar(filtro), filtro.periodo(), categoriasPorProduto(),
                filtro.categoria(), criterio, limite);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public OpcoesFiltro opcoesFiltro() {
        FiltroDashboard padrao = FiltroDashboard.de(null, null, null, null, null, LocalDate.now(relogio));
        List<String> categorias = produtoRepository.findAll().stream()
                .map(Produto::getTipo)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new OpcoesFiltro(padrao.inicio(), padrao.fim(), FiltroDashboard.DIAS_MAXIMOS,
                opcoes(StatusPedido.values()), opcoes(TipoAtendimento.values()), categorias,
                opcoes(Agrupamento.values()), opcoes(CriterioRanking.values()));
    }

    // ------------------------------------------------------------------ apoio

    private List<Pedido> buscar(FiltroDashboard filtro) {
        return pedidoRepository.buscarPorPeriodo(filtro.inicioInclusivo(), filtro.fimExclusivo()).stream()
                .filter(filtro::aceita)
                .toList();
    }

    /** Categoria (campo "tipo" do produto) por id, para o ranking e a distribuicao por categoria. */
    private Map<String, String> categoriasPorProduto() {
        Map<String, String> mapa = new HashMap<>();
        for (Produto p : produtoRepository.findAll()) {
            if (p.getId() != null && p.getTipo() != null) {
                mapa.put(p.getId(), p.getTipo().trim());
            }
        }
        return mapa;
    }

    private static <E extends Enum<E> & Rotulado> List<Opcao> opcoes(E[] valores) {
        return Arrays.stream(valores).map(v -> new Opcao(v.name(), v.getRotulo())).toList();
    }
}
