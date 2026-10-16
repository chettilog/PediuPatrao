package com.umc.pediupatrao.seguranca;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaApiController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Prova que as permissoes valem no backend: as chamadas vao direto na API,
 * sem passar por tela nenhuma (como faria alguem usando Postman ou o console do navegador).
 */
@WebMvcTest(controllers = {PedidoController.class, AuditoriaApiController.class,
        UsuarioController.class, ClienteController.class})
@Import({SecurityConfig.class, AcessoNegadoHandler.class, UsuarioLogado.class})
class AutorizacaoApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    private static MockHttpServletRequestBuilder cancelar() {
        return post("/api/pedidos/abc123/cancelamento")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"justificativa\":\"Cliente desistiu do pedido\"}");
    }

    private static MockHttpServletRequestBuilder descontar() {
        return post("/api/pedidos/abc123/desconto")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"percentual\":10}");
    }

    // ---------------------------------------------------------------- sem login

    @Test
    void apiSemLoginResponde401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- cancelamento e desconto

    @Test
    void atendenteNaoCancelaPedido() throws Exception {
        mvc.perform(cancelar().with(user("atendente01").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(auditoriaService, atLeastOnce()).registrarAcessoNegado(anyString());
    }

    @Test
    void adminTambemNaoCancelaPedido() throws Exception {
        mvc.perform(cancelar().with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteCancelaPedido() throws Exception {
        mvc.perform(cancelar().with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void atendenteEAdminNaoDaoDesconto() throws Exception {
        mvc.perform(descontar().with(user("atendente01").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(descontar().with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteDaDesconto() throws Exception {
        mvc.perform(descontar().with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void requisicaoSemTokenCsrfERecusada() throws Exception {
        mvc.perform(cancelar().with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- auditoria

    @Test
    void atendenteNaoVeAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GERENTE") // contexto so para conseguir configurar o mock abaixo
    void gerenteEAdminVeemAuditoria() throws Exception {
        when(auditoriaService.consultar(any(), any(), any(), any(), anyInt())).thenReturn(Page.empty());

        mvc.perform(get("/api/auditoria").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void ninguemApagaAuditoria() throws Exception {
        mvc.perform(delete("/api/auditoria/qualquer").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auditoria").with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- usuarios e clientes

    @Test
    void somenteAdminGerenciaUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacker\",\"senha\":\"123456\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void cadastroPublicoDeUsuarioFoiFechado() throws Exception {
        // no projeto original POST /api/usuarios era permitAll
        mvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruso\",\"senha\":\"123456\",\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gerenteSoConsultaClientes() throws Exception {
        mvc.perform(get("/api/clientes").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(user("gerente01").roles("GERENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteCadastraCliente() throws Exception {
        mvc.perform(post("/api/clientes").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Teste\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminNaoAcessaClientes() throws Exception {
        mvc.perform(get("/api/clientes").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
