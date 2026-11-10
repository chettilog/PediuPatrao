package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Rotulado;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Corpos JSON devolvidos pela API do dashboard (/api/dashboard/**).
 *
 * Convencoes:
 *  - valores em reais: numero com 2 casas (ex.: 1234.50);
 *  - percentuais: numero de 0 a 100 com 2 casas (ex.: 12.35 = 12,35%);
 *  - variacao: null quando o periodo anterior nao tem base de comparacao (valor zero);
 *  - datas: ISO-8601 (2026-11-10 / 2026-11-10T19:32:00), fuso de Sao Paulo.
 */
public final class DashboardDto {

    private DashboardDto() {
    }

    // ------------------------------------------------------------------ enums dos parametros

    /** Granularidade da serie de vendas (RF02). */
    public enum Agrupamento implements Rotulado {
        DIA("Por dia"),
        SEMANA("Por semana"),
        MES("Por mês");

        private final String rotulo;

        Agrupamento(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    /** Ordenacao do ranking de produtos (RF04). */
    public enum CriterioRanking implements Rotulado {
        QUANTIDADE("Quantidade vendida"),
        FATURAMENTO("Faturamento");

        private final String rotulo;

        CriterioRanking(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    // ------------------------------------------------------------------ comum

    public record Periodo(LocalDate inicio, LocalDate fim, long dias) {
    }

    public record Opcao(String valor, String rotulo) {
    }

    // ------------------------------------------------------------------ RF01 - indicadores gerais

    public record Resumo(
            Periodo periodo,
            long totalPedidos,
            long pedidosValidos,
            long pedidosFinalizados,
            long pedidosEmAndamento,
            long pedidosCancelados,
            BigDecimal taxaCancelamento,
            BigDecimal faturamentoBruto,
            BigDecimal totalDescontos,
            BigDecimal faturamento,
            BigDecimal ticketMedio,
            BigDecimal valorCancelado,
            long itensVendidos,
            BigDecimal tempoMedioSaidaMinutos,
            Comparacao comparacao,
            LocalDateTime geradoEm) {
    }

    /** Mesmos indicadores no periodo imediatamente anterior, com o mesmo numero de dias. */
    public record Comparacao(
            Periodo periodoAnterior,
            BigDecimal faturamento,
            BigDecimal variacaoFaturamento,
            long pedidosValidos,
            BigDecimal variacaoPedidos,
            BigDecimal ticketMedio,
            BigDecimal variacaoTicketMedio,
            BigDecimal taxaCancelamento) {
    }

    // ------------------------------------------------------------------ RF02 - analise de vendas

    public record PontoSerie(String chave, String rotulo, long pedidos, BigDecimal faturamento, BigDecimal ticketMedio) {
    }

    public record SerieVendas(Periodo periodo, Agrupamento agrupamento, long totalPedidos,
            BigDecimal totalFaturamento, List<PontoSerie> pontos) {
    }

    /** Uma fatia de uma distribuicao (hora do dia, dia da semana, tipo de atendimento, categoria). */
    public record Fatia(String chave, String rotulo, long pedidos, BigDecimal faturamento, BigDecimal percentual) {
    }

    public record Distribuicao(Periodo periodo, List<Fatia> porHora, List<Fatia> porDiaSemana,
            List<Fatia> porTipoAtendimento, List<Fatia> porCategoria) {
    }

    // ------------------------------------------------------------------ RF03 - situacao dos pedidos

    public record SituacaoStatus(StatusPedido status, String rotulo, long quantidade, BigDecimal valor,
            BigDecimal percentual) {
    }

    public record SituacaoPedidos(Periodo periodo, long total, List<SituacaoStatus> porStatus) {
    }

    public record PedidoEmAndamento(String id, String codigo, String clienteNome, StatusPedido status,
            String statusRotulo, TipoAtendimento tipoAtendimento, LocalDateTime entradaEm,
            long minutosDesdeEntrada, boolean atrasado, BigDecimal valorTotal) {
    }

    public record EmAndamento(int limiteMinutos, long total, long atrasados, List<PedidoEmAndamento> pedidos) {
    }

    public record PedidoResumo(String id, String codigo, LocalDateTime entradaEm, String clienteNome,
            TipoAtendimento tipoAtendimento, String tipoAtendimentoRotulo, StatusPedido status, String statusRotulo,
            int quantidadeItens, BigDecimal valorDesconto, BigDecimal valorTotal, String registradoPor) {
    }

    public record PaginaPedidos(int pagina, int tamanho, long totalElementos, int totalPaginas,
            List<PedidoResumo> conteudo) {
    }

    // ------------------------------------------------------------------ RF04 - produtos mais vendidos

    public record ProdutoVendido(int posicao, String produtoId, String nome, String categoria, long quantidade,
            long pedidos, BigDecimal faturamento, BigDecimal participacao) {
    }

    public record RankingProdutos(Periodo periodo, CriterioRanking criterio, String categoria,
            BigDecimal totalItens, List<ProdutoVendido> produtos) {
    }

    // ------------------------------------------------------------------ RF05 - opcoes de filtro

    public record OpcoesFiltro(LocalDate inicioPadrao, LocalDate fimPadrao, int diasMaximos,
            List<Opcao> status, List<Opcao> tiposAtendimento, List<String> categorias,
            List<Opcao> agrupamentos, List<Opcao> criteriosRanking) {
    }
}
