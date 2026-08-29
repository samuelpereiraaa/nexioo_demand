package br.com.nexioo.demand.exception;

/**
 * Lançada quando uma demanda não é encontrada pelo ID informado.
 * Tratada pelo {@code GlobalExceptionHandler} com resposta HTTP 404.
 */
public class DemandaNaoEncontradaException extends RuntimeException {

    public DemandaNaoEncontradaException(Long id) {
        super("Demanda com ID " + id + " não encontrada.");
    }
}
