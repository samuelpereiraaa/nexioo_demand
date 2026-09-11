package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.exception.DemandaNaoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tratamento centralizado de exceções.
 * Suporta respostas amigáveis para navegação tradicional (HTML)
 * e códigos de status HTTP adequados com mensagens claras para requisições AJAX.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DemandaNaoEncontradaException.class)
    public Object demandaNaoEncontrada(DemandaNaoEncontradaException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.warn("Demanda não encontrada: {}", ex.getMessage());
        if (isAjax(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("titulo", "Demanda não encontrada");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro/nao-encontrado";
    }

    @ExceptionHandler(SecurityException.class)
    public Object erroSeguranca(SecurityException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.warn("Acesso negado/segurança: {}", ex.getMessage());
        if (isAjax(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"erro\",\"mensagem\":\"" + ex.getMessage() + "\"}");
        }
        return "redirect:/login";
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public Object metodoNaoSuportado(org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        if (isAjax(request)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ex.getMessage());
        }
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        model.addAttribute("titulo", "Método HTTP não permitido");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro/nao-encontrado";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object argumentoInvalido(IllegalArgumentException ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        if (isAjax(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("titulo", "Requisição inválida");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro/nao-encontrado";
    }

    @ExceptionHandler(Exception.class)
    public Object erroGenerico(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.error("Exceção não tratada em {}: ", request.getRequestURI(), ex);
        if (isAjax(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ocorreu um erro ao processar a solicitação.");
        }

        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        model.addAttribute("titulo", "Erro inesperado");
        model.addAttribute("mensagem",
                "Ocorreu um problema ao processar sua solicitação. Tente novamente.");
        return "erro/nao-encontrado";
    }

    private boolean isAjax(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && (accept.contains("application/json") || accept.contains("text/plain")));
    }
}
