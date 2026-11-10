package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDto.Agrupamento;
import com.umc.pediupatrao.dto.DashboardDto.Comparacao;
import com.umc.pediupatrao.dto.DashboardDto.CriterioRanking;
import com.umc.pediupatrao.dto.DashboardDto.Distribuicao;
import com.umc.pediupatrao.dto.DashboardDto.EmAndamento;
import com.umc.pediupatrao.dto.DashboardDto.Fatia;
import com.umc.pediupatrao.dto.DashboardDto.PaginaPedidos;
import com.umc.pediupatrao.dto.DashboardDto.PedidoEmAndamento;
import com.umc.pediupatrao.dto.DashboardDto.PedidoResumo;
import com.umc.pediupatrao.dto.DashboardDto.Periodo;
import com.umc.pediupatrao.dto.DashboardDto.PontoSerie;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDto.RankingProdutos;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDto.SituacaoPedidos;
import com.umc.pediupatrao.dto.DashboardDto.SituacaoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Calculos do dashboard a partir de uma lista de pedidos ja filtrada.
 *
 * Classe sem Spring e sem banco: recebe dados, devolve numeros. Isso deixa as regras
 * faceis de testar (CalculadoraDashboardTest) e separa "buscar" (DashboardService) de "calcular".
 *
 * Regras de negocio dos indicadores:
 *  - venda = pedido NAO cancelado (inclui os que ainda estao em andamento);
 *  - faturamento = soma do valorTotal (ja com desconto) das vendas;
 *  - ticket medio = faturamento / quantidade de vendas;
 *  - taxa de cancelamento = cancelados / total de pedidos do periodo;
 *  - ranking de produtos usa o subtotal dos itens (antes do desconto, que e do pedido inteiro).
 *
 * Tratamento de dados (RF07): pedidos antigos do projeto original podem nao ter itens, valores,
 * tipo de atendimento ou datas. Valores nulos contam como zero e itens nulos sao ignorados,
 * para um registro incompleto nao derrubar o painel inteiro.
 */
public final class CalculadoraDashboard {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("MMM/yyyy", PT_BR);
    private static final String[] DIAS_SEMANA = {"Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"};
    public static final String SEM_CATEGORIA = "Sem categoria";
    public static final String NAO_INFORMADO = "Não informado";

    private CalculadoraDashboard() {
    }

    // ================================================================== RF01

    public static Resumo resumo(List<Pedido> atuais, Periodo periodo, List<Pedido> anteriores,
            Periodo periodoAnterior, LocalDateTime agora) {
        Totais t = Totais.de(atuais);
        Totais a = Totais.de(anteriores);

        Comparacao comparacao = new Comparacao(periodoAnterior,
                t.dinheiro(a.faturamento), variacao(t.faturamento, a.faturamento),
                a.validos, variacao(BigDecimal.valueOf(t.validos), BigDecimal.valueOf(a.validos)),
                a.ticketMedio(), variacao(t.ticketMedio(), a.ticketMedio()),
                a.taxaCancelamento());

        return new Resumo(periodo, t.total, t.validos, t.finalizados, t.emAndamento, t.cancelados,
                t.taxaCancelamento(), dinheiro(t.bruto), dinheiro(t.descontos), dinheiro(t.faturamento),
                t.ticketMedio(), dinheiro(t.valorCancelado), t.itens, t.tempoMedioSaida(), comparacao, agora);
    }

    /** Acumuladores do RF01 (um passe pela lista). */
    private static final class Totais {
        long total;
        long validos;
        long finalizados;
        long emAndamento;
        long cancelados;
        long itens;
        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal valorCancelado = BigDecimal.ZERO;
        long minutosSaida;
        long pedidosComSaida;

        static Totais de(List<Pedido> pedidos) {
            Totais t = new Totais();
            for (Pedido p : naoNulos(pedidos)) {
                t.total++;
                if (cancelado(p)) {
                    t.cancelados++;
                    t.valorCancelado = t.valorCancelado.add(valor(p.getValorTotal()));
                    continue;
                }
                t.validos++;
                if (p.getStatus() == StatusPedido.FINALIZADO) {
                    t.finalizados++;
                } else if (p.getStatus() != null) {
                    t.emAndamento++; // sem status (registro antigo) conta como venda, mas nao como "em andamento"
                }
                t.bruto = t.bruto.add(valor(p.getSubtotal()));
                t.descontos = t.descontos.add(valor(p.getValorDesconto()));
                t.faturamento = t.faturamento.add(valor(p.getValorTotal()));
                for (ItemPedido item : itens(p)) {
                    t.itens += Math.max(0, item.getQuantidade());
                }
                if (p.getEntradaEm() != null && p.getSaidaEm() != null && !p.getSaidaEm().isBefore(p.getEntradaEm())) {
                    t.minutosSaida += Duration.between(p.getEntradaEm(), p.getSaidaEm()).toMinutes();
                    t.pedidosComSaida++;
                }
            }
            return t;
        }

        BigDecimal dinheiro(BigDecimal v) {
            return CalculadoraDashboard.dinheiro(v);
        }

        BigDecimal ticketMedio() {
            return validos == 0 ? dinheiro(BigDecimal.ZERO)
                    : faturamento.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP);
        }

        BigDecimal taxaCancelamento() {
            return percentual(BigDecimal.valueOf(cancelados), BigDecimal.valueOf(total));
        }

        BigDecimal tempoMedioSaida() {
            return pedidosComSaida == 0 ? null
                    : BigDecimal.valueOf(minutosSaida).divide(BigDecimal.valueOf(pedidosComSaida), 1, RoundingMode.HALF_UP);
        }
    }

    // ================================================================== RF02

    /** Serie temporal de vendas. Todos os periodos aparecem, inclusive os sem venda (valor zero). */
    public static SerieVendas serie(List<Pedido> pedidos, Periodo periodo, Agrupamento agrupamento) {
        Agrupamento ag = agrupamento == null ? Agrupamento.DIA : agrupamento;
        Map<String, Acumulador> baldes = new LinkedHashMap<>();
        Map<String, String> rotulos = new LinkedHashMap<>();

        LocalDate cursor = inicioDoBalde(periodo.inicio(), ag);
        while (!cursor.isAfter(periodo.fim())) {
            String chave = chave(cursor, ag);
            baldes.put(chave, new Acumulador());
            rotulos.put(chave, rotulo(cursor, ag));
            cursor = proximo(cursor, ag);
        }

        long totalPedidos = 0;
        BigDecimal totalFaturamento = BigDecimal.ZERO;
        for (Pedido p : naoNulos(pedidos)) {
            if (cancelado(p) || p.getEntradaEm() == null) {
                continue;
            }
            Acumulador acc = baldes.get(chave(inicioDoBalde(p.getEntradaEm().toLocalDate(), ag), ag));
            if (acc == null) {
                continue; // fora do periodo (nao deveria acontecer, a consulta ja filtra)
            }
            acc.somar(p);
            totalPedidos++;
            totalFaturamento = totalFaturamento.add(valor(p.getValorTotal()));
        }

        List<PontoSerie> pontos = new ArrayList<>();
        baldes.forEach((chave, acc) -> pontos.add(new PontoSerie(chave, rotulos.get(chave), acc.pedidos,
                dinheiro(acc.faturamento), acc.ticketMedio())));
        return new SerieVendas(periodo, ag, totalPedidos, dinheiro(totalFaturamento), pontos);
    }

    /** Horario de pico, dia da semana, tipo de atendimento e categoria (somente vendas). */
    public static Distribuicao distribuicao(List<Pedido> pedidos, Periodo periodo, Map<String, String> categoriaPorProduto) {
        Map<String, Acumulador> hora = new LinkedHashMap<>();
        Map<String, String> rotuloHora = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            String chave = String.format("%02d", h);
            hora.put(chave, new Acumulador());
            rotuloHora.put(chave, chave + "h");
        }
        Map<String, Acumulador> semana = new LinkedHashMap<>();
        Map<String, String> rotuloSemana = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            semana.put(d.name(), new Acumulador());
            rotuloSemana.put(d.name(), DIAS_SEMANA[d.getValue() - 1]);
        }
        Map<String, Acumulador> tipo = new LinkedHashMap<>();
        Map<String, String> rotuloTipo = new LinkedHashMap<>();
        for (TipoAtendimento t : TipoAtendimento.values()) {
            tipo.put(t.name(), new Acumulador());
            rotuloTipo.put(t.name(), t.getRotulo());
        }
        Map<String, Acumulador> categoria = new LinkedHashMap<>();

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalItens = BigDecimal.ZERO;
        for (Pedido p : naoNulos(pedidos)) {
            if (cancelado(p)) {
                continue;
            }
            total = total.add(valor(p.getValorTotal()));
            if (p.getEntradaEm() != null) {
                hora.get(String.format("%02d", p.getEntradaEm().getHour())).somar(p);
                semana.get(p.getEntradaEm().getDayOfWeek().name()).somar(p);
            }
            String chaveTipo = p.getTipoAtendimento() == null ? "NAO_INFORMADO" : p.getTipoAtendimento().name();
            if (!tipo.containsKey(chaveTipo)) {
                tipo.put(chaveTipo, new Acumulador());
                rotuloTipo.put(chaveTipo, NAO_INFORMADO);
            }
            tipo.get(chaveTipo).somar(p);

            // categoria: soma por item; um pedido conta uma vez em cada categoria que aparece nele
            Map<String, BigDecimal> porCategoriaNoPedido = new LinkedHashMap<>();
            for (ItemPedido item : itens(p)) {
                String cat = categoriaDo(item, categoriaPorProduto);
                porCategoriaNoPedido.merge(cat, valorItem(item), BigDecimal::add);
            }
            for (Map.Entry<String, BigDecimal> e : porCategoriaNoPedido.entrySet()) {
                Acumulador acc = categoria.computeIfAbsent(e.getKey(), k -> new Acumulador());
                acc.pedidos++;
                acc.faturamento = acc.faturamento.add(e.getValue());
                totalItens = totalItens.add(e.getValue());
            }
        }

        List<Fatia> categorias = fatias(categoria, null, totalItens);
        categorias.sort(Comparator.comparing(Fatia::faturamento).reversed());
        return new Distribuicao(periodo, fatias(hora, rotuloHora, total), fatias(semana, rotuloSemana, total),
                fatias(tipo, rotuloTipo, total), categorias);
    }

    // ================================================================== RF03

    /** Quantidade e valor por status, na ordem do fluxo, incluindo os status sem pedidos. */
    public static SituacaoPedidos situacao(List<Pedido> pedidos, Periodo periodo) {
        Map<StatusPedido, Acumulador> mapa = new LinkedHashMap<>();
        for (StatusPedido s : StatusPedido.values()) {
            mapa.put(s, new Acumulador());
        }
        long total = 0;
        for (Pedido p : naoNulos(pedidos)) {
            total++;
            if (p.getStatus() != null) {
                mapa.get(p.getStatus()).somar(p);
            }
        }
        List<SituacaoStatus> lista = new ArrayList<>();
        for (Map.Entry<StatusPedido, Acumulador> e : mapa.entrySet()) {
            Acumulador acc = e.getValue();
            lista.add(new SituacaoStatus(e.getKey(), e.getKey().getRotulo(), acc.pedidos, dinheiro(acc.faturamento),
                    percentual(BigDecimal.valueOf(acc.pedidos), BigDecimal.valueOf(total))));
        }
        return new SituacaoPedidos(periodo, total, lista);
    }

    /** Pedidos ainda nao finalizados, do mais antigo para o mais novo, com alerta de atraso. */
    public static EmAndamento emAndamento(List<Pedido> pedidos, LocalDateTime agora, int limiteMinutos) {
        List<PedidoEmAndamento> lista = new ArrayList<>();
        long atrasados = 0;
        List<Pedido> ordenados = new ArrayList<>(naoNulos(pedidos));
        ordenados.sort(Comparator.comparing(Pedido::getEntradaEm, Comparator.nullsLast(Comparator.naturalOrder())));
        for (Pedido p : ordenados) {
            if (p.getStatus() == null || p.getStatus() == StatusPedido.FINALIZADO || p.getStatus() == StatusPedido.CANCELADO) {
                continue;
            }
            long minutos = p.getEntradaEm() == null ? 0 : Math.max(0, Duration.between(p.getEntradaEm(), agora).toMinutes());
            // so conta atraso enquanto o pedido esta dentro da pizzaria
            boolean atrasado = p.getStatus().antesDaSaida() && minutos > limiteMinutos;
            if (atrasado) {
                atrasados++;
            }
            lista.add(new PedidoEmAndamento(p.getId(), p.getCodigo(), p.getClienteNome(), p.getStatus(),
                    p.getStatus().getRotulo(), p.getTipoAtendimento(), p.getEntradaEm(), minutos, atrasado,
                    dinheiro(valor(p.getValorTotal()))));
        }
        return new EmAndamento(limiteMinutos, lista.size(), atrasados, lista);
    }

    /** Lista paginada para a tabela do painel (mais recentes primeiro). */
    public static PaginaPedidos paginar(List<Pedido> pedidos, int pagina, int tamanho) {
        List<Pedido> ordenados = new ArrayList<>(naoNulos(pedidos));
        ordenados.sort(Comparator.comparing(Pedido::getEntradaEm, Comparator.nullsLast(Comparator.reverseOrder())));
        int total = ordenados.size();
        int totalPaginas = total == 0 ? 0 : (total + tamanho - 1) / tamanho;
        int de = Math.min(pagina * tamanho, total);
        int ate = Math.min(de + tamanho, total);

        List<PedidoResumo> conteudo = new ArrayList<>();
        for (Pedido p : ordenados.subList(de, ate)) {
            conteudo.add(new PedidoResumo(p.getId(), p.getCodigo(), p.getEntradaEm(), p.getClienteNome(),
                    p.getTipoAtendimento(), p.getTipoAtendimento() == null ? NAO_INFORMADO : p.getTipoAtendimento().getRotulo(),
                    p.getStatus(), p.getStatus() == null ? NAO_INFORMADO : p.getStatus().getRotulo(),
                    itens(p).stream().mapToInt(i -> Math.max(0, i.getQuantidade())).sum(),
                    dinheiro(valor(p.getValorDesconto())), dinheiro(valor(p.getValorTotal())), p.getRegistradoPor()));
        }
        return new PaginaPedidos(pagina, tamanho, total, totalPaginas, conteudo);
    }

    // ================================================================== RF04

    public static RankingProdutos ranking(List<Pedido> pedidos, Periodo periodo, Map<String, String> categoriaPorProduto,
            String categoriaFiltro, CriterioRanking criterio, int limite) {
        CriterioRanking crit = criterio == null ? CriterioRanking.QUANTIDADE : criterio;

        Map<String, AcumuladorProduto> mapa = new LinkedHashMap<>();
        BigDecimal totalItens = BigDecimal.ZERO;
        for (Pedido p : naoNulos(pedidos)) {
            if (cancelado(p)) {
                continue;
            }
            Map<String, Boolean> jaContadoNestePedido = new LinkedHashMap<>();
            for (ItemPedido item : itens(p)) {
                BigDecimal v = valorItem(item);
                totalItens = totalItens.add(v);
                String categoria = categoriaDo(item, categoriaPorProduto);
                if (categoriaFiltro != null && !categoriaFiltro.equalsIgnoreCase(categoria)) {
                    continue;
                }
                String chave = item.getProdutoId() != null ? item.getProdutoId()
                        : "sem-id:" + Objects.toString(item.getDescricao(), "?");
                AcumuladorProduto acc = mapa.computeIfAbsent(chave, k -> new AcumuladorProduto());
                acc.produtoId = item.getProdutoId();
                acc.nome = item.getDescricao() != null ? item.getDescricao() : Objects.toString(acc.nome, "Produto sem nome");
                acc.categoria = categoria;
                acc.quantidade += Math.max(0, item.getQuantidade());
                acc.faturamento = acc.faturamento.add(v);
                if (jaContadoNestePedido.putIfAbsent(chave, Boolean.TRUE) == null) {
                    acc.pedidos++;
                }
            }
        }

        Comparator<AcumuladorProduto> ordem = crit == CriterioRanking.FATURAMENTO
                ? Comparator.comparing((AcumuladorProduto a) -> a.faturamento).reversed()
                        .thenComparing(a -> a.quantidade, Comparator.reverseOrder())
                : Comparator.comparing((AcumuladorProduto a) -> a.quantidade).reversed()
                        .thenComparing(a -> a.faturamento, Comparator.reverseOrder());
        List<AcumuladorProduto> ordenados = new ArrayList<>(mapa.values());
        ordenados.sort(ordem.thenComparing(a -> a.nome));

        List<ProdutoVendido> produtos = new ArrayList<>();
        int posicao = 1;
        for (AcumuladorProduto a : ordenados.subList(0, Math.min(limite, ordenados.size()))) {
            produtos.add(new ProdutoVendido(posicao++, a.produtoId, a.nome, a.categoria, a.quantidade, a.pedidos,
                    dinheiro(a.faturamento), percentual(a.faturamento, totalItens)));
        }
        return new RankingProdutos(periodo, crit, categoriaFiltro, dinheiro(totalItens), produtos);
    }

    // ================================================================== apoio

    private static final class Acumulador {
        long pedidos;
        BigDecimal faturamento = BigDecimal.ZERO;

        void somar(Pedido p) {
            pedidos++;
            faturamento = faturamento.add(valor(p.getValorTotal()));
        }

        BigDecimal ticketMedio() {
            return pedidos == 0 ? dinheiro(BigDecimal.ZERO)
                    : faturamento.divide(BigDecimal.valueOf(pedidos), 2, RoundingMode.HALF_UP);
        }
    }

    private static final class AcumuladorProduto {
        String produtoId;
        String nome;
        String categoria;
        long quantidade;
        long pedidos;
        BigDecimal faturamento = BigDecimal.ZERO;
    }

    private static List<Fatia> fatias(Map<String, Acumulador> mapa, Map<String, String> rotulos, BigDecimal total) {
        List<Fatia> lista = new ArrayList<>();
        mapa.forEach((chave, acc) -> lista.add(new Fatia(chave, rotulos == null ? chave : rotulos.get(chave),
                acc.pedidos, dinheiro(acc.faturamento), percentual(acc.faturamento, total))));
        return lista;
    }

    private static String categoriaDo(ItemPedido item, Map<String, String> categoriaPorProduto) {
        String cat = item.getProdutoId() == null || categoriaPorProduto == null ? null
                : categoriaPorProduto.get(item.getProdutoId());
        return cat == null || cat.isBlank() ? SEM_CATEGORIA : cat;
    }

    private static BigDecimal valorItem(ItemPedido item) {
        if (item.getSubtotal() != null) {
            return item.getSubtotal();
        }
        // pedido antigo sem subtotal no item: recalcula pelo preco unitario
        return valor(item.getPrecoUnitario()).multiply(BigDecimal.valueOf(Math.max(0, item.getQuantidade())));
    }

    private static List<ItemPedido> itens(Pedido p) {
        return p.getItens() == null ? List.of() : p.getItens().stream().filter(Objects::nonNull).toList();
    }

    private static List<Pedido> naoNulos(List<Pedido> pedidos) {
        return pedidos == null ? List.of() : pedidos.stream().filter(Objects::nonNull).toList();
    }

    private static boolean cancelado(Pedido p) {
        return p.getStatus() == StatusPedido.CANCELADO;
    }

    static BigDecimal valor(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static BigDecimal dinheiro(BigDecimal v) {
        return valor(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** parte / todo em %, 2 casas. Todo zero -> 0. */
    static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        if (todo == null || todo.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return valor(parte).multiply(CEM).divide(todo, 2, RoundingMode.HALF_UP);
    }

    /** Variacao % de anterior para atual. Sem base (anterior zero) -> null, o painel mostra "—". */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) {
            return null;
        }
        return valor(atual).subtract(anterior).multiply(CEM).divide(anterior, 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- baldes de tempo

    private static LocalDate inicioDoBalde(LocalDate data, Agrupamento ag) {
        return switch (ag) {
            case DIA -> data;
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate proximo(LocalDate data, Agrupamento ag) {
        return switch (ag) {
            case DIA -> data.plusDays(1);
            case SEMANA -> data.plusWeeks(1);
            case MES -> data.plusMonths(1);
        };
    }

    private static String chave(LocalDate inicioBalde, Agrupamento ag) {
        return ag == Agrupamento.MES ? YearMonth.from(inicioBalde).toString() : inicioBalde.toString();
    }

    private static String rotulo(LocalDate inicioBalde, Agrupamento ag) {
        return switch (ag) {
            case DIA -> inicioBalde.format(DIA_MES);
            case SEMANA -> "Sem. " + inicioBalde.format(DIA_MES);
            case MES -> inicioBalde.format(MES_ANO).replace(".", "");
        };
    }
}
