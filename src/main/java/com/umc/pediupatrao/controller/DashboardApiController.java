package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDto.Agrupamento;
import com.umc.pediupatrao.dto.DashboardDto.CriterioRanking;
import com.umc.pediupatrao.dto.DashboardDto.Distribuicao;
import com.umc.pediupatrao.dto.DashboardDto.EmAndamento;
import com.umc.pediupatrao.dto.DashboardDto.OpcoesFiltro;
import com.umc.pediupatrao.dto.DashboardDto.PaginaPedidos;
import com.umc.pediupatrao.dto.DashboardDto.RankingProdutos;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.SerieVendas;
import com.umc.pediupatrao.dto.DashboardDto.SituacaoPedidos;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.excecao.ParametroInvalidoException;
import com.umc.pediupatrao.service.DashboardService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do Dashboard Administrativo (somente GET, perfis ADMIN e GERENTE).
 *
 * Parametros de filtro aceitos pela maioria das rotas (todos opcionais):
 *   inicio, fim (yyyy-MM-dd) | tipoAtendimento (ENTREGA, RETIRADA) | status (RECEBIDO ... CANCELADO)
 *
 * Documentacao completa: docs/DASHBOARD.md
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private static final int LIMITE_RANKING_MAXIMO = 50;
    private static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final DashboardService dashboardService;
    private final Clock relogio;

    public DashboardApiController(DashboardService dashboardService, Clock relogio) {
        this.dashboardService = dashboardService;
        this.relogio = relogio;
    }

    /** RF01 - indicadores gerais, com comparacao ao periodo anterior. */
    @GetMapping("/resumo")
    public Resumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoAtendimento tipoAtendimento,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.resumo(filtro(inicio, fim, tipoAtendimento, status, null));
    }

    /** RF02 - serie temporal de vendas (dia, semana ou mes). */
    @GetMapping("/vendas")
    public SerieVendas vendas(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoAtendimento tipoAtendimento,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        return dashboardService.vendas(filtro(inicio, fim, tipoAtendimento, status, null), agrupamento);
    }

    /** RF02 - vendas por hora do dia, dia da semana, tipo de atendimento e categoria. */
    @GetMapping("/vendas/distribuicao")
    public Distribuicao distribuicao(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoAtendimento tipoAtendimento,
            @RequestParam(required = false) StatusPedido status) {
        return dashboardService.distribuicao(filtro(inicio, fim, tipoAtendimento, status, null));
    }

    /** RF03 - quantidade e valor de pedidos por status no periodo. */
    @GetMapping("/pedidos/status")
    public SituacaoPedidos situacao(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoAtendimento tipoAtendimento) {
        return dashboardService.situacao(filtro(inicio, fim, tipoAtendimento, null, null));
    }

    /** RF03 - fila atual (pedidos nao finalizados), com alerta de atraso. Nao usa periodo. */
    @GetMapping("/pedidos/em-andamento")
    public EmAndamento emAndamento(@RequestParam(required = false) TipoAtendimento tipoAtendimento) {
        return dashboardService.emAndamento(tipoAtendimento);
    }

    /** RF03/RF05 - lista paginada de pedidos do periodo, mais recentes primeiro. */
    @GetMapping("/pedidos")
    public PaginaPedidos pedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoAtendimento tipoAtendimento,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanho) {
        if (pagina < 0) {
            throw new ParametroInvalidoException("O parâmetro 'pagina' começa em 0.");
        }
        if (tamanho < 1 || tamanho > TAMANHO_PAGINA_MAXIMO) {
            throw new ParametroInvalidoException("O parâmetro 'tamanho' deve ficar entre 1 e " + TAMANHO_PAGINA_MAXIMO + ".");
        }
        return dashboardService.pedidos(filtro(inicio, fim, tipoAtendimento, status, null), pagina, tamanho);
    }

    /** RF04 - ranking de produtos por quantidade ou faturamento. */
    @GetMapping("/produtos/mais-vendidos")
    public RankingProdutos maisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoAtendimento tipoAtendimento,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "QUANTIDADE") CriterioRanking criterio,
            @RequestParam(defaultValue = "10") int limite) {
        if (limite < 1 || limite > LIMITE_RANKING_MAXIMO) {
            throw new ParametroInvalidoException("O parâmetro 'limite' deve ficar entre 1 e " + LIMITE_RANKING_MAXIMO + ".");
        }
        return dashboardService.produtosMaisVendidos(filtro(inicio, fim, tipoAtendimento, null, categoria),
                criterio, limite);
    }

    /** RF05 - opcoes para montar os filtros da tela (status, tipos, categorias, periodo padrao). */
    @GetMapping("/filtros")
    public OpcoesFiltro filtros() {
        return dashboardService.opcoesFiltro();
    }

    private FiltroDashboard filtro(LocalDate inicio, LocalDate fim, TipoAtendimento tipo, StatusPedido status,
            String categoria) {
        return FiltroDashboard.de(inicio, fim, tipo, status, categoria, LocalDate.now(relogio));
    }
}
