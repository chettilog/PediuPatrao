package com.umc.pediupatrao.seguranca;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.RelogioConfig;
import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardApiController;
import com.umc.pediupatrao.dto.DashboardDto.CriterioRanking;
import com.umc.pediupatrao.dto.DashboardDto.EmAndamento;
import com.umc.pediupatrao.dto.FiltroDashboard;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API do dashboard: quem pode ver (ADMIN e GERENTE), que nada e alteravel por ela
 * e que parametros invalidos voltam 400 com mensagem, antes de chegar ao service.
 */
@WebMvcTest(controllers = DashboardApiController.class)
@Import({SecurityConfig.class, AcessoNegadoHandler.class, UsuarioLogado.class, RelogioConfig.class})
class DashboardApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    // ---------------------------------------------------------------- acesso

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
    }

    @Test
    void atendenteNaoVeODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/dashboard").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteEAdminVeemODashboard() throws Exception {
        when(dashboardService.emAndamento(any())).thenReturn(new EmAndamento(40, 0, 0, List.of()));

        mvc.perform(get("/api/dashboard/pedidos/em-andamento").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limiteMinutos").value(40));
        mvc.perform(get("/api/dashboard/pedidos/em-andamento").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/resumo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void dashboardESomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- validacao de parametros

    @Test
    void dataInicialDepoisDaFinalResponde400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-10").param("fim", "2026-11-01")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A data inicial não pode ser posterior à data final."));
        verify(dashboardService, never()).resumo(any());
    }

    @Test
    void dataEmFormatoErradoResponde400() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").param("inicio", "10/11/2026")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void valorDeEnumInvalidoResponde400() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").param("agrupamento", "ANO")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/resumo").param("status", "PERDIDO")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void limitesDePaginacaoERanking() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("limite", "0")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/pedidos").param("tamanho", "500")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard/pedidos").param("pagina", "-1")
                        .with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parametrosValidosChegamAoService() throws Exception {
        mvc.perform(get("/api/dashboard/produtos/mais-vendidos").param("criterio", "FATURAMENTO").param("limite", "5")
                        .param("categoria", "Pizza").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk());
        verify(dashboardService).produtosMaisVendidos(any(FiltroDashboard.class), eq(CriterioRanking.FATURAMENTO), eq(5));

        mvc.perform(get("/api/dashboard/pedidos").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk());
        verify(dashboardService).pedidos(any(FiltroDashboard.class), eq(0), anyInt());
    }
}
