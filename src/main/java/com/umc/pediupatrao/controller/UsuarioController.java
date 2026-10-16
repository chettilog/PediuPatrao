package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioForm;
import com.umc.pediupatrao.dto.UsuarioResumo;
import com.umc.pediupatrao.excecao.RecursoNaoEncontradoException;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de usuarios. Antes era publica (permitAll) e devolvia o hash da senha;
 * agora exige ADMIN e responde apenas id, username e perfil.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResumo> listarTodos() {
        return usuarioService.listarTodos().stream().map(UsuarioResumo::de).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResumo buscarPorId(@PathVariable String id) {
        return usuarioService.buscarPorId(id).map(UsuarioResumo::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    @PostMapping
    public ResponseEntity<UsuarioResumo> criarUsuario(@RequestBody UsuarioForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResumo.de(usuarioService.criar(form)));
    }

    @PutMapping("/{id}")
    public UsuarioResumo atualizarUsuario(@PathVariable String id, @RequestBody UsuarioForm form) {
        return UsuarioResumo.de(usuarioService.atualizar(id, form));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        usuarioService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
