package br.com.nexioo.demand.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Serviço para geração e validação de tokens CSRF vinculados à sessão e cookies.
 * Valida a integridade de Origin e Referer como URIs formais (RFC 3986) e
 * realiza comparação de tokens em tempo constante contra timing attacks.
 */
@Service
public class CsrfTokenService {

    private static final Logger log = LoggerFactory.getLogger(CsrfTokenService.class);

    public static final String CSRF_ATTR_NAME = "_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final SecureRandom secureRandom = new SecureRandom();

    private final Environment environment;

    public CsrfTokenService() {
        this.environment = null;
    }

    public CsrfTokenService(Environment environment) {
        this.environment = environment;
    }

    public String obterOuGerarToken(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(CSRF_ATTR_NAME);
        if (token == null || token.isBlank()) {
            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            session.setAttribute(CSRF_ATTR_NAME, token);
        }

        request.setAttribute(CSRF_ATTR_NAME, token);

        if (response != null) {
            boolean isHttps = request.isSecure() || isProdProfile();
            Cookie cookie = new Cookie(CSRF_COOKIE_NAME, token);
            cookie.setPath("/");
            cookie.setHttpOnly(false); // Legível via JavaScript para envio em headers AJAX
            cookie.setSecure(isHttps);
            response.addCookie(cookie);
        }

        return token;
    }

    public boolean validarOrigem(HttpServletRequest request) {
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank()) {
            source = request.getHeader("Referer");
        }
        if (source == null || source.isBlank()) {
            // Em clientes directos ou chamadas de mesma origem sem cabeçalho Origin/Referer explícito
            return true;
        }

        try {
            URI sourceUri = new URI(source.trim());
            String sourceHost = sourceUri.getHost();
            if (sourceHost == null || sourceHost.isBlank()) {
                log.warn("Origem com formato de URI inválido ou sem host: {}", source);
                return false;
            }

            String serverName = request.getServerName();
            if (sourceHost.equalsIgnoreCase(serverName)) {
                return true;
            }

            // Validação contra Host HTTP configurado
            String hostHeader = request.getHeader("Host");
            if (hostHeader != null && !hostHeader.isBlank()) {
                String hostHeaderName = hostHeader.split(":")[0].trim();
                if (sourceHost.equalsIgnoreCase(hostHeaderName)) {
                    return true;
                }
            }

            log.warn("Origem rejeitada: sourceHost='{}' não corresponde a serverName='{}'", sourceHost, serverName);
            return false;
        } catch (Exception e) {
            log.warn("Falha ao analisar URI de origem: {}", e.getMessage());
            return false;
        }
    }

    public boolean validarToken(HttpServletRequest request) {
        // 1. Validação obrigatória de Origin / Referer como URI
        if (!validarOrigem(request)) {
            return false;
        }

        HttpSession session = request.getSession(false);
        String sessionToken = (session != null) ? (String) session.getAttribute(CSRF_ATTR_NAME) : null;
        if (sessionToken == null || sessionToken.isBlank()) {
            return false;
        }

        String requestToken = request.getHeader(CSRF_HEADER_NAME);
        if (requestToken == null || requestToken.isBlank()) {
            requestToken = request.getHeader("X-XSRF-TOKEN");
        }
        if (requestToken == null || requestToken.isBlank()) {
            requestToken = request.getParameter("_csrf");
        }

        if (requestToken == null || requestToken.isBlank()) {
            return false;
        }

        // Comparação de tokens em tempo constante
        return MessageDigest.isEqual(
                sessionToken.trim().getBytes(StandardCharsets.UTF_8),
                requestToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isProdProfile() {
        if (environment == null) return false;
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        return active.contains("prod");
    }
}
