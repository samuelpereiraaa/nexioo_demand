package br.com.nexioo.demand.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Interceptor para validação automática de CSRF em requisições mutativas.
 * Protege contra manipulações cross-origin e exige validação de token CSRF.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private final CsrfTokenService csrfTokenService;

    public CsrfInterceptor(org.springframework.beans.factory.ObjectProvider<CsrfTokenService> csrfTokenServiceProvider) {
        CsrfTokenService service = csrfTokenServiceProvider.getIfAvailable();
        this.csrfTokenService = service != null ? service : new CsrfTokenService();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();

        // 1. Gera ou recupera o token CSRF para requisições seguras e renderização de formulários
        csrfTokenService.obterOuGerarToken(request, response);

        // 2. Métodos de leitura são seguros contra alteração de estado (RFC 7231)
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) 
                || "OPTIONS".equalsIgnoreCase(method) || "TRACE".equalsIgnoreCase(method)) {
            return true;
        }

        // 3. Validação mandatória de Origin / Referer contra falsificação entre origens
        if (!csrfTokenService.validarOrigem(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":\"erro\",\"mensagem\":\"Requisição rejeitada: origem não autorizada.\"}");
            return false;
        }

        String uri = request.getRequestURI();

        // 4. Rotas públicas de onboarding (/login e /signup)
        if (uri.equals("/login") || uri.equals("/signup")) {
            boolean hasCsrfHeader = request.getHeader(CsrfTokenService.CSRF_HEADER_NAME) != null 
                    || request.getHeader("X-XSRF-TOKEN") != null 
                    || request.getParameter("_csrf") != null;

            if (hasCsrfHeader) {
                if (!csrfTokenService.validarToken(request)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"status\":\"erro\",\"mensagem\":\"Token CSRF inválido.\"}");
                    return false;
                }
            }
            return true;
        }

        // 5. Validação estrita de CSRF para /logout e operações privadas mutativas
        if (!csrfTokenService.validarToken(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":\"erro\",\"mensagem\":\"Requisição rejeitada: token CSRF ausente ou inválido.\"}");
            return false;
        }

        return true;
    }
}
