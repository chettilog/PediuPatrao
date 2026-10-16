package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.excecao.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.util.Formatos;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Cardapio. Qualquer usuario logado consulta; somente o GERENTE altera (e fica auditado). */
@Service
public class ProdutoService {

    private static final String ENTIDADE = "Produto";

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoria;
    private final Formatos formatos;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoria, Formatos formatos) {
        this.produtoRepository = produtoRepository;
        this.auditoria = auditoria;
        this.formatos = formatos;
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll(Sort.by("tipo", "nome"));
    }

    public List<Produto> listarAtivos() {
        return listarProdutos().stream().filter(p -> Boolean.TRUE.equals(p.getAtivo())).toList();
    }

    public Optional<Produto> buscarPorId(String id) {
        return produtoRepository.findById(id);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Produto salvar(Produto dados) {
        validar(dados);
        boolean novo = dados.getId() == null || dados.getId().isBlank();

        Produto produto;
        Produto antes;
        if (novo) {
            produto = new Produto();
            antes = new Produto();
        } else {
            produto = produtoRepository.findById(dados.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado para atualização."));
            antes = copia(produto);
        }
        produto.setNome(dados.getNome().trim());
        produto.setTipo(dados.getTipo().trim());
        produto.setPreco(dados.getPreco());
        produto.setAtivo(Boolean.TRUE.equals(dados.getAtivo()));
        produto = produtoRepository.save(produto);

        TipoOperacao tipo = novo ? TipoOperacao.INCLUSAO_PRODUTO : TipoOperacao.ALTERACAO_PRODUTO;
        comparar(antes, produto, auditoria.abrir(tipo, ENTIDADE, produto.getId())).gravar();
        return produto;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
        produtoRepository.deleteById(id);
        comparar(produto, new Produto(), auditoria.abrir(TipoOperacao.EXCLUSAO_PRODUTO, ENTIDADE, id)).gravar();
    }

    private AuditoriaService.Lancamento comparar(Produto a, Produto d, AuditoriaService.Lancamento lanc) {
        return lanc
                .campo("nome", a.getNome(), d.getNome())
                .campo("tipo", a.getTipo(), d.getTipo())
                .campo("preco", formatos.moeda(a.getPreco()), formatos.moeda(d.getPreco()))
                .campo("ativo", simNao(a.getAtivo()), simNao(d.getAtivo()));
    }

    private static String simNao(Boolean b) {
        return b == null ? null : (b ? "Sim" : "Não");
    }

    private static Produto copia(Produto p) {
        Produto c = new Produto();
        c.setId(p.getId());
        c.setNome(p.getNome());
        c.setTipo(p.getTipo());
        c.setPreco(p.getPreco());
        c.setAtivo(p.getAtivo());
        return c;
    }

    private static void validar(Produto p) {
        if (p.getNome() == null || p.getNome().isBlank()) {
            throw new RegraNegocioException("Informe o nome do produto.");
        }
        if (p.getTipo() == null || p.getTipo().isBlank()) {
            throw new RegraNegocioException("Informe o tipo do produto.");
        }
        if (p.getPreco() == null || p.getPreco() <= 0) {
            throw new RegraNegocioException("O preço deve ser maior que zero.");
        }
    }
}
