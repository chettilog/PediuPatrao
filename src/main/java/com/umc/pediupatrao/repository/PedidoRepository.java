package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByClienteId(String clienteId);

    List<Pedido> findByStatus(StatusPedido status, Sort sort);

    boolean existsByClienteId(String clienteId);

    // ------------------------------------------------------------------ dashboard

    /**
     * Pedidos com entrada no intervalo [inicio, fimExclusivo). O filtro de periodo roda no MongoDB
     * e usa o indice do campo entradaEm (ver Pedido).
     */
    @Query("{ 'entradaEm': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fimExclusivo);

    /** Pedidos em determinados status, sem limite de data (painel "em andamento"). */
    List<Pedido> findByStatusIn(Collection<StatusPedido> status);

    /** Usado pelo gerador de dados de demonstracao para nao gerar duas vezes. */
    boolean existsByRegistradoPor(String registradoPor);
}
