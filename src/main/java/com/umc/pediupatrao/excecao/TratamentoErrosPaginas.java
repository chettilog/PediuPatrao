package com.umc.pediupatrao.excecao;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Erros das telas: mostra a pagina error.html com o codigo certo e uma mensagem amigavel. */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class TratamentoErrosPaginas {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public String naoEncontrado(RecursoNaoEncontradoException e, Model model, HttpServletResponse response) {
        return erro(model, response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public String regraNegocio(RegraNegocioException e, Model model, HttpServletResponse response) {
        return erro(model, response, 422, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String parametroInvalido(Exception e, Model model, HttpServletResponse response) {
        return erro(model, response, HttpServletResponse.SC_BAD_REQUEST, "Parâmetro inválido na requisição.");
    }

    private static String erro(Model model, HttpServletResponse response, int status, String mensagem) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("message", mensagem);
        return "error";
    }
}
