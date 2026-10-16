package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Dados minimos para a demonstracao: um usuario de cada perfil e um cardapio basico.
 * So cria o que ainda nao existe (pode rodar varias vezes sem duplicar).
 * A senha inicial vem do application.properties e nunca e escrita no log.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository usuarios, ProdutoRepository produtos,
            PasswordEncoder encoder, @Value("${pediupatrao.demo.senha-inicial}") String senhaInicial) {
        return args -> {
            garantirUsuario(usuarios, encoder, "admin", Perfil.ADMIN, senhaInicial);
            garantirUsuario(usuarios, encoder, "gerente01", Perfil.GERENTE, senhaInicial);
            garantirUsuario(usuarios, encoder, "atendente01", Perfil.ATENDENTE, senhaInicial);

            if (produtos.count() == 0) {
                produtos.save(produto("Pizza Calabresa", "Pizza", 49.90));
                produtos.save(produto("Pizza Mussarela", "Pizza", 45.00));
                produtos.save(produto("Pizza Portuguesa", "Pizza", 54.90));
                produtos.save(produto("Esfiha de Carne", "Lanche", 6.50));
                produtos.save(produto("Refrigerante 2L", "Bebida", 14.00));
                log.info("Cardápio inicial cadastrado.");
            }
        };
    }

    private static void garantirUsuario(UsuarioRepository repo, PasswordEncoder encoder,
            String username, Perfil perfil, String senha) {
        repo.findByUsername(username).ifPresentOrElse(existente -> {
            // usuario antigo sem perfil (antes so existia a role USER)
            if (existente.getPerfil() == null) {
                existente.setPerfil(perfil);
                repo.save(existente);
                log.info("Perfil {} atribuído ao usuário existente {}.", perfil, username);
            }
        }, () -> {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setPerfil(perfil);
            u.setPassword(encoder.encode(senha));
            repo.save(u);
            log.info("Usuário de demonstração {} ({}) criado.", username, perfil);
        });
    }

    private static Produto produto(String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }
}
