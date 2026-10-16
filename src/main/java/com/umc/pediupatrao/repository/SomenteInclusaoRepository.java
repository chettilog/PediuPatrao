package com.umc.pediupatrao.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.QueryByExampleExecutor;

/**
 * Base para repositorios que so podem incluir e consultar.
 * De proposito NAO estende CrudRepository/MongoRepository: nao existem save(), delete() nem deleteAll().
 * insert() falha se o id ja existir, entao nao sobrescreve um documento antigo.
 */
@NoRepositoryBean
public interface SomenteInclusaoRepository<T, ID> extends Repository<T, ID>, QueryByExampleExecutor<T> {

    <S extends T> S insert(S entidade);
}
