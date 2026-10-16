package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.EventoAuditoria;

/**
 * Repositorio da auditoria: apenas insert() e consultas por exemplo (somente leitura).
 * Nenhuma classe do sistema consegue alterar ou apagar um evento por aqui.
 */
public interface EventoAuditoriaRepository extends SomenteInclusaoRepository<EventoAuditoria, String> {
}
