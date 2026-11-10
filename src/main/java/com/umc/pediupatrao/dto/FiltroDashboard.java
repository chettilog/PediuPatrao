package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.excecao.ParametroInvalidoException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Filtros comuns do dashboard (RF05), ja validados.
 *
 * O periodo e fechado nas duas pontas: inicio=2026-11-01 e fim=2026-11-10 incluem o dia 10 inteiro.
 * Para o banco, isso vira [inicio 00:00, fim+1 00:00).
 */
public record FiltroDashboard(LocalDate inicio, LocalDate fim, TipoAtendimento tipoAtendimento,
        StatusPedido status, String categoria) {

    public static final int DIAS_PADRAO = 30;
    public static final int DIAS_MAXIMOS = 366;

    /**
     * Monta o filtro a partir dos parametros da requisicao (todos opcionais).
     * Sem datas: ultimos 30 dias ate hoje. Sem fim: ate hoje.
     */
    public static FiltroDashboard de(LocalDate inicio, LocalDate fim, TipoAtendimento tipoAtendimento,
            StatusPedido status, String categoria, LocalDate hoje) {
        LocalDate dataFim = fim != null ? fim : hoje;
        LocalDate dataInicio = inicio != null ? inicio : dataFim.minusDays(DIAS_PADRAO - 1L);

        if (dataInicio.isAfter(dataFim)) {
            throw new ParametroInvalidoException("A data inicial não pode ser posterior à data final.");
        }
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        if (dias > DIAS_MAXIMOS) {
            throw new ParametroInvalidoException("O período consultado pode ter no máximo " + DIAS_MAXIMOS + " dias.");
        }
        String cat = categoria == null || categoria.isBlank() ? null : categoria.trim();
        return new FiltroDashboard(dataInicio, dataFim, tipoAtendimento, status, cat);
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    public LocalDateTime inicioInclusivo() {
        return inicio.atStartOfDay();
    }

    public LocalDateTime fimExclusivo() {
        return fim.plusDays(1).atStartOfDay();
    }

    /** Mesmo numero de dias, terminando na vespera do inicio (usado na comparacao dos indicadores). */
    public FiltroDashboard periodoAnterior() {
        LocalDate fimAnterior = inicio.minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(dias() - 1);
        return new FiltroDashboard(inicioAnterior, fimAnterior, tipoAtendimento, status, categoria);
    }

    public DashboardDto.Periodo periodo() {
        return new DashboardDto.Periodo(inicio, fim, dias());
    }

    /** Filtros que nao vao para a consulta do banco (tipo de atendimento e status). */
    public boolean aceita(Pedido pedido) {
        if (pedido == null) {
            return false;
        }
        if (tipoAtendimento != null && tipoAtendimento != pedido.getTipoAtendimento()) {
            return false;
        }
        return status == null || status == pedido.getStatus();
    }
}
