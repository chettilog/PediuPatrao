package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoAtendimento;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Gera um historico de pedidos para demonstrar o dashboard (graficos vazios nao mostram nada).
 *
 * So roda com pediupatrao.demo.gerar-pedidos=true (desligado por padrao) e so uma vez:
 * os pedidos gerados ficam com registradoPor = "demo" e, se ja existir algum, nada e feito.
 * Para gerar de novo, apague no MongoDB os pedidos com registradoPor "demo".
 *
 * Os pedidos sao gravados direto no repositorio (sem passar pelo PedidoService), por isso
 * NAO geram registros de auditoria: sao dados de carga, nao operacoes de usuarios.
 * Os valores seguem a mesma regra do PedidoService (preco do cardapio x quantidade - desconto).
 */
@Component
@ConditionalOnProperty(name = "pediupatrao.demo.gerar-pedidos", havingValue = "true")
public class GeradorPedidosDemo implements CommandLineRunner {

    public static final String RESPONSAVEL = "demo";
    private static final Logger log = LoggerFactory.getLogger(GeradorPedidosDemo.class);
    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoRepository pedidos;
    private final ClienteRepository clientes;
    private final ProdutoRepository produtos;
    private final Clock relogio;
    private final int dias;

    public GeradorPedidosDemo(PedidoRepository pedidos, ClienteRepository clientes, ProdutoRepository produtos,
            Clock relogio, @Value("${pediupatrao.demo.dias:90}") int dias) {
        this.pedidos = pedidos;
        this.clientes = clientes;
        this.produtos = produtos;
        this.relogio = relogio;
        this.dias = dias;
    }

    @Override
    public void run(String... args) {
        if (pedidos.existsByRegistradoPor(RESPONSAVEL)) {
            log.info("Pedidos de demonstração já existem; nada a gerar.");
            return;
        }
        List<Produto> cardapio = garantirCardapio();
        List<Cliente> listaClientes = garantirClientes();
        List<Pedido> gerados = gerar(cardapio, listaClientes, LocalDateTime.now(relogio), new Random(2026));
        pedidos.saveAll(gerados);
        log.info("{} pedidos de demonstração gerados ({} dias).", gerados.size(), dias);
    }

    /** Monta os pedidos (separado de run() para poder ser testado sem banco). */
    List<Pedido> gerar(List<Produto> cardapio, List<Cliente> listaClientes, LocalDateTime agora, Random rnd) {
        List<Produto> pizzas = cardapio.stream().filter(p -> "Pizza".equalsIgnoreCase(p.getTipo())).toList();
        List<Produto> outros = cardapio.stream().filter(p -> !"Pizza".equalsIgnoreCase(p.getTipo())).toList();
        List<Produto> bebidas = cardapio.stream().filter(p -> "Bebida".equalsIgnoreCase(p.getTipo())).toList();
        List<Pedido> lista = new ArrayList<>();

        LocalDate hoje = agora.toLocalDate();
        for (int d = dias - 1; d >= 0; d--) {
            LocalDate dia = hoje.minusDays(d);
            int quantidade = pedidosNoDia(dia, d, rnd);
            for (int i = 0; i < quantidade; i++) {
                LocalDateTime entrada = dia.atTime(hora(rnd), rnd.nextInt(60), rnd.nextInt(60));
                if (entrada.isAfter(agora)) {
                    continue;
                }
                Cliente cliente = listaClientes.get(rnd.nextInt(listaClientes.size()));
                TipoAtendimento tipo = rnd.nextInt(100) < 62 ? TipoAtendimento.ENTREGA : TipoAtendimento.RETIRADA;
                Pedido p = new Pedido();
                p.setClienteId(cliente.getId());
                p.setClienteNome(cliente.getNome());
                p.setTipoAtendimento(tipo);
                p.setItens(itens(pizzas, outros, bebidas, rnd));
                p.setPercentualDesconto(rnd.nextInt(100) < 9 ? new BigDecimal(new int[] {5, 10, 15}[rnd.nextInt(3)]) : BigDecimal.ZERO);
                calcular(p);
                p.setEntradaEm(entrada);
                p.setRegistradoPor(RESPONSAVEL);
                p.registrarMovimentacao(StatusPedido.RECEBIDO, entrada, RESPONSAVEL);
                andamento(p, entrada, agora, rnd);
                lista.add(p);
            }
        }
        return lista;
    }

    /** Fim de semana vende mais e ha um crescimento leve ao longo do periodo. */
    private int pedidosNoDia(LocalDate dia, int diasAtras, Random rnd) {
        DayOfWeek dow = dia.getDayOfWeek();
        int base = switch (dow) {
            case FRIDAY -> 11;
            case SATURDAY -> 14;
            case SUNDAY -> 12;
            case MONDAY -> 4;
            default -> 6;
        };
        double crescimento = 1.0 + (dias - diasAtras) / (double) dias * 0.35;
        return Math.max(1, (int) Math.round(base * crescimento + rnd.nextGaussian() * 1.8));
    }

    /** Almoco fraco, pico das 19h as 21h. */
    private int hora(Random rnd) {
        int[] horas = {11, 12, 13, 14, 18, 19, 20, 21, 22, 23};
        int[] pesos = {3, 6, 4, 2, 8, 18, 22, 17, 10, 4};
        int sorteio = rnd.nextInt(94);
        for (int i = 0; i < horas.length; i++) {
            sorteio -= pesos[i];
            if (sorteio < 0) {
                return horas[i];
            }
        }
        return 20;
    }

    private List<ItemPedido> itens(List<Produto> pizzas, List<Produto> outros, List<Produto> bebidas, Random rnd) {
        List<ItemPedido> itens = new ArrayList<>();
        int linhas = 1 + rnd.nextInt(3);
        for (int i = 0; i < linhas; i++) {
            List<Produto> grupo = i == 0 || outros.isEmpty() ? pizzas : (rnd.nextBoolean() ? pizzas : outros);
            if (grupo.isEmpty()) {
                grupo = outros;
            }
            // os primeiros produtos de cada grupo saem mais (ranking com lideres claros)
            Produto prod = grupo.get(Math.min(grupo.size() - 1, (int) Math.abs(rnd.nextGaussian() * grupo.size() / 2.2)));
            int qtd = "Lanche".equalsIgnoreCase(prod.getTipo()) ? 2 + rnd.nextInt(6) : 1 + (rnd.nextInt(100) < 20 ? 1 : 0);
            adicionar(itens, prod, qtd);
        }
        if (!bebidas.isEmpty() && rnd.nextInt(100) < 55) {
            adicionar(itens, bebidas.get(rnd.nextInt(bebidas.size())), 1 + rnd.nextInt(2));
        }
        return itens;
    }

    private static void adicionar(List<ItemPedido> itens, Produto prod, int qtd) {
        for (ItemPedido existente : itens) {
            if (existente.getProdutoId().equals(prod.getId())) {
                existente.setQuantidade(existente.getQuantidade() + qtd);
                return;
            }
        }
        BigDecimal preco = BigDecimal.valueOf(prod.getPreco()).setScale(2, RoundingMode.HALF_UP);
        itens.add(new ItemPedido(prod.getId(), prod.getNome(), qtd, preco));
    }

    /** Mesmo calculo do PedidoService.recalcular(). */
    private static void calcular(Pedido p) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedido item : p.getItens()) {
            BigDecimal v = item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setSubtotal(v);
            subtotal = subtotal.add(v);
        }
        BigDecimal desconto = subtotal.multiply(p.getPercentualDesconto()).divide(CEM, 2, RoundingMode.HALF_UP);
        p.setSubtotal(subtotal);
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal.subtract(desconto));
    }

    /**
     * Leva o pedido pelas etapas do fluxo ate onde o horario permite.
     * Pedidos antigos terminam FINALIZADO (ou CANCELADO, ~6%); os de agora ficam em andamento.
     */
    private static void andamento(Pedido p, LocalDateTime entrada, LocalDateTime agora, Random rnd) {
        boolean cancelar = rnd.nextInt(100) < 6;
        LocalDateTime t = entrada.plusMinutes(3 + rnd.nextInt(8));
        if (cancelar) {
            if (t.isBefore(agora)) {
                p.registrarMovimentacao(StatusPedido.CANCELADO, t, "gerente01");
                p.setCanceladoEm(t);
                p.setCanceladoPor("gerente01");
                p.setMotivoCancelamento("Cliente desistiu do pedido (dado de demonstração).");
            }
            return;
        }
        StatusPedido atual = StatusPedido.RECEBIDO;
        int[] minutos = {4 + rnd.nextInt(8), 18 + rnd.nextInt(17), 2 + rnd.nextInt(10), 12 + rnd.nextInt(25)};
        int etapa = 0;
        t = entrada;
        while (atual.seguinte(p.getTipoAtendimento()) != null) {
            StatusPedido proximo = atual.seguinte(p.getTipoAtendimento());
            int espera = proximo == StatusPedido.FINALIZADO && p.getTipoAtendimento() == TipoAtendimento.RETIRADA
                    ? 1 : minutos[Math.min(etapa, minutos.length - 1)];
            t = t.plusMinutes(espera);
            if (t.isAfter(agora)) {
                return;
            }
            p.registrarMovimentacao(proximo, t, RESPONSAVEL);
            if (proximo.marcaSaida()) {
                p.setSaidaEm(t);
                p.setSaidaPor(RESPONSAVEL);
            }
            if (proximo == StatusPedido.FINALIZADO) {
                p.setFinalizadoEm(t);
            }
            atual = proximo;
            etapa++;
        }
    }

    // ------------------------------------------------------------------ cardapio e clientes

    private List<Produto> garantirCardapio() {
        Object[][] demo = {
                {"Pizza Calabresa", "Pizza", 49.90}, {"Pizza Mussarela", "Pizza", 45.00},
                {"Pizza Portuguesa", "Pizza", 54.90}, {"Pizza Frango com Catupiry", "Pizza", 56.90},
                {"Pizza Margherita", "Pizza", 52.00}, {"Pizza Quatro Queijos", "Pizza", 58.90},
                {"Pizza de Chocolate", "Sobremesa", 47.00},
                {"Esfiha de Carne", "Lanche", 6.50}, {"Esfiha de Queijo", "Lanche", 6.50},
                {"Refrigerante 2L", "Bebida", 14.00}, {"Guaraná Lata", "Bebida", 6.00},
                {"Suco Natural 500ml", "Bebida", 9.00},
        };
        List<Produto> existentes = produtos.findAll();
        for (Object[] linha : demo) {
            boolean existe = existentes.stream().anyMatch(p -> linha[0].equals(p.getNome()));
            if (!existe) {
                Produto p = new Produto();
                p.setNome((String) linha[0]);
                p.setTipo((String) linha[1]);
                p.setPreco((Double) linha[2]);
                p.setAtivo(true);
                produtos.save(p);
            }
        }
        return produtos.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
    }

    private List<Cliente> garantirClientes() {
        String[][] demo = {
                {"Ana Souza", "11990000001", "Rua Cel. Souza Franco"}, {"Bruno Lima", "11990000002", "Av. Voluntário Fernando Pinheiro Franco"},
                {"Carla Mendes", "11990000003", "Rua Ipiranga"}, {"Diego Rocha", "11990000004", "Rua Dr. Deodato Wertheimer"},
                {"Elaine Castro", "11990000005", "Av. Cândido Xavier de Almeida e Souza"}, {"Felipe Nunes", "11990000006", "Rua Navajas"},
                {"Gabriela Alves", "11990000007", "Rua Barão de Jaceguai"}, {"Henrique Prado", "11990000008", "Av. Francisco Rodrigues Filho"},
                {"Isabela Freitas", "11990000009", "Rua Professor Flaviano de Melo"}, {"João Pedro Reis", "11990000010", "Rua Casarejos"},
        };
        List<Cliente> lista = new ArrayList<>();
        int numero = 100;
        for (String[] linha : demo) {
            List<Cliente> achados = clientes.findByTelefone(linha[1]);
            if (!achados.isEmpty()) {
                lista.add(achados.get(0));
                continue;
            }
            Cliente c = new Cliente();
            c.setNome(linha[0]);
            c.setTelefone(linha[1]);
            c.setLogradouro(linha[2]);
            c.setNumero(String.valueOf(numero += 37));
            c.setBairro("Centro");
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            c.setCep("08780-000");
            lista.add(clientes.save(c));
        }
        return lista;
    }
}
