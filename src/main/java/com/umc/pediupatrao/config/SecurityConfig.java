package com.umc.pediupatrao.config;

import com.umc.pediupatrao.seguranca.AcessoNegadoHandler;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Autorizacao em duas camadas:
 *  1) regras por URL e metodo HTTP (abaixo);
 *  2) @PreAuthorize nos services (ativado por @EnableMethodSecurity), que protege a regra
 *     mesmo que alguem crie outra rota chamando o mesmo metodo.
 *
 * Matriz de permissoes:
 *  - ADMIN     : usuarios/perfis, consulta de pedidos, auditoria
 *  - GERENTE   : pedidos (inclusive desconto e cancelamento), consulta de clientes, produtos, auditoria
 *  - ATENDENTE : cadastro/alteracao de clientes, registro e andamento de pedidos
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final RequestMatcher ROTAS_API = request -> request.getRequestURI().startsWith("/api/");

    private final UsuarioDetailsService usuarioDetailsService;
    private final AcessoNegadoHandler acessoNegadoHandler;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService, AcessoNegadoHandler acessoNegadoHandler) {
        this.usuarioDetailsService = usuarioDetailsService;
        this.acessoNegadoHandler = acessoNegadoHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF ligado (antes estava desativado). Formularios Thymeleaf recebem o token
                // automaticamente; chamadas fetch() enviam o token pelo cabecalho (ver js/csrf.js).
                .csrf(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**",
                                "/dist/**", "/plugins/**").permitAll()

                        // Usuarios e perfis: somente ADMIN (a rota /api/usuarios era publica)
                        .requestMatchers("/usuarios", "/usuarios/**", "/api/usuarios", "/api/usuarios/**", "/admin", "/configuracoes")
                            .hasRole("ADMIN")

                        // Auditoria: apenas leitura, apenas ADMIN e GERENTE
                        .requestMatchers(HttpMethod.GET, "/auditoria", "/auditoria/**", "/api/auditoria", "/api/auditoria/**")
                            .hasAnyRole("ADMIN", "GERENTE")
                        .requestMatchers("/auditoria", "/auditoria/**", "/api/auditoria", "/api/auditoria/**").denyAll()

                        // Exclusivo do GERENTE (nem o ADMIN pode)
                        .requestMatchers("/pedidos/*/desconto", "/pedidos/*/cancelar",
                                "/api/pedidos/*/desconto", "/api/pedidos/*/cancelamento")
                            .hasRole("GERENTE")

                        // Pedidos
                        .requestMatchers(HttpMethod.GET, "/pedidos/novo", "/pedidos/*/editar")
                            .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/**", "/api/pedidos", "/api/pedidos/**")
                            .hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")
                        .requestMatchers("/pedidos", "/pedidos/**", "/api/pedidos", "/api/pedidos/**")
                            .hasAnyRole("GERENTE", "ATENDENTE")

                        // Clientes: ATENDENTE cadastra/altera, GERENTE so consulta
                        .requestMatchers(HttpMethod.GET, "/clientes/novo", "/clientes/editar/**")
                            .hasRole("ATENDENTE")
                        .requestMatchers(HttpMethod.GET, "/clientes", "/clientes/**", "/api/clientes", "/api/clientes/**")
                            .hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers("/clientes", "/clientes/**", "/api/clientes", "/api/clientes/**")
                            .hasRole("ATENDENTE")

                        // Cardapio: todos consultam, GERENTE mantem
                        .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/**", "/api/produtos", "/api/produtos/**").authenticated()
                        .requestMatchers("/produtos", "/produtos/**", "/api/produtos", "/api/produtos/**").hasRole("GERENTE")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(acessoNegadoHandler)
                        // API sem login responde 401 em vez de redirecionar para a tela de login
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), ROTAS_API)
                );

        return http.build();
    }
}
