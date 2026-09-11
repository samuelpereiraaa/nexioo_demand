package br.com.nexioo.demand.util;

import br.com.nexioo.demand.config.CsrfTokenService;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Utilitário para testes MockMvc fornecerem tokens CSRF válidos em requisições mutativas.
 */
public final class CsrfTestUtils {

    private CsrfTestUtils() {}

    public static RequestPostProcessor withCsrf() {
        return request -> {
            String token = "test-csrf-valid-token";
            request.getSession().setAttribute(CsrfTokenService.CSRF_ATTR_NAME, token);
            request.addHeader(CsrfTokenService.CSRF_HEADER_NAME, token);
            return request;
        };
    }
}
