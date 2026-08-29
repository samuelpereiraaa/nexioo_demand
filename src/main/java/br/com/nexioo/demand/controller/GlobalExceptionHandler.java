package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.exception.DemandaNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Tratamento centralizado de exceções.
 * Converte exceções de domínio em páginas de erro amigáveis.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DemandaNaoEncontradaException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String demandaNaoEncontrada(DemandaNaoEncontradaException ex, Model model) {
        model.addAttribute("titulo", "Demanda não encontrada");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro/nao-encontrado";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String erroGenerico(Exception ex, Model model) {
        model.addAttribute("titulo", "Erro inesperado");
        model.addAttribute("mensagem",
                "Ocorreu um problema ao processar sua solicitação. Tente novamente.");
        return "erro/nao-encontrado";
    }
}
