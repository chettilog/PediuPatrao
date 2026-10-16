package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.EventoAuditoria;
import com.umc.pediupatrao.entity.Rotulado;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.EventoAuditoriaRepository;
import com.umc.pediupatrao.seguranca.UsuarioLogado;
import com.umc.pediupatrao.util.Formatos;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Grava e consulta a trilha de auditoria.
 *
 * Uso nos outros services:
 *
 *   auditoria.abrir(TipoOperacao.ALTERACAO_CLIENTE, "Cliente", id)
 *            .campo("logradouro", antes.getLogradouro(), depois.getLogradouro())
 *            .gravar();
 *
 * Cada campo que realmente mudou vira um registro separado; campos iguais sao ignorados.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    public static final int ITENS_POR_PAGINA = 30;

    private final EventoAuditoriaRepository repositorio;
    private final UsuarioLogado usuarioLogado;
    private final Formatos formatos;
    private final Clock relogio;

    public AuditoriaService(EventoAuditoriaRepository repositorio, UsuarioLogado usuarioLogado,
            Formatos formatos, Clock relogio) {
        this.repositorio = repositorio;
        this.usuarioLogado = usuarioLogado;
        this.formatos = formatos;
        this.relogio = relogio;
    }

    public Lancamento abrir(TipoOperacao tipo, String entidade, String entidadeId) {
        return new Lancamento(tipo, entidade, entidadeId);
    }

    /** Chamado pelo AcessoNegadoHandler quando um usuario logado tenta algo que o perfil nao permite. */
    public void registrarAcessoNegado(String rota) {
        abrir(TipoOperacao.ACESSO_NEGADO, "Rota", rota)
                .campo("rota", null, rota)
                .gravar();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Page<EventoAuditoria> consultar(String usuario, TipoOperacao tipo, String entidade,
            String entidadeId, int pagina) {
        EventoAuditoria molde = EventoAuditoria.filtro(
                textoOuNulo(usuario), tipo, textoOuNulo(entidade), textoOuNulo(entidadeId));

        ExampleMatcher criterio = ExampleMatcher.matching()
                .withIgnoreNullValues()
                .withMatcher("usuario", m -> m.contains().ignoreCase());

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), ITENS_POR_PAGINA,
                Sort.by(Sort.Direction.DESC, "dataHora"));

        return repositorio.findAll(Example.of(molde, criterio), pageRequest);
    }

    private static String textoOuNulo(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Converte qualquer valor para o texto que fica gravado na auditoria. */
    private String texto(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Rotulado r) {
            return r.getRotulo();
        }
        if (valor instanceof LocalDateTime dt) {
            return formatos.dataHora(dt);
        }
        String s = valor.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Acumula os campos alterados de UMA operacao e grava todos juntos,
     * com o mesmo operacaoId e o mesmo horario.
     */
    public final class Lancamento {

        private final String operacaoId = UUID.randomUUID().toString();
        private final TipoOperacao tipo;
        private final String entidade;
        private final String entidadeId;
        private final List<String[]> campos = new ArrayList<>();
        private String justificativa;

        private Lancamento(TipoOperacao tipo, String entidade, String entidadeId) {
            this.tipo = tipo;
            this.entidade = entidade;
            this.entidadeId = entidadeId;
        }

        /** Registra o campo somente se o valor mudou. */
        public Lancamento campo(String nome, Object antes, Object depois) {
            String a = texto(antes);
            String d = texto(depois);
            if (!Objects.equals(a, d)) {
                campos.add(new String[] {nome, a, d});
            }
            return this;
        }

        /** Igual a campo(), mas grava o telefone mascarado (compara pelo valor real). */
        public Lancamento telefone(String nome, String antes, String depois) {
            String a = texto(antes);
            String d = texto(depois);
            if (!Objects.equals(a, d)) {
                campos.add(new String[] {nome, Formatos.mascararTelefone(a), Formatos.mascararTelefone(d)});
            }
            return this;
        }

        /** Para senha: registra so que houve alteracao, nunca o valor. */
        public Lancamento credencial(String nome, String descricao) {
            campos.add(new String[] {nome, null, descricao});
            return this;
        }

        public Lancamento justificativa(String texto) {
            this.justificativa = texto(texto);
            return this;
        }

        /** Grava um documento por campo alterado. Retorna quantos foram gravados. */
        public int gravar() {
            if (campos.isEmpty()) {
                return 0;
            }
            LocalDateTime agora = LocalDateTime.now(relogio);
            String quem = usuarioLogado.username();
            String perfil = usuarioLogado.perfil();

            for (String[] c : campos) {
                repositorio.insert(new EventoAuditoria(operacaoId, agora, quem, perfil,
                        tipo, entidade, entidadeId, c[0], c[1], c[2], justificativa));
            }
            log.info("Auditoria [{}] {} {} por {} ({} campo(s))",
                    tipo, entidade, entidadeId, quem, campos.size());
            return campos.size();
        }
    }
}
