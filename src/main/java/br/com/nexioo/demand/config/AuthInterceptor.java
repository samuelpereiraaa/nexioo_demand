package br.com.nexioo.demand.config;

import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.service.SupabaseAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Interceptor para proteção de rotas com cookies HttpOnly e UserContext (@RequestScope).
 * Valida o JWT do Supabase, renova transparentemente se expirado via refresh token,
 * e inicializa o UserContext na requisição.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    public static final String CHAVE_USUARIO_LOGADO = "usuarioLogado";
    public static final String COOKIE_ACCESS_TOKEN = "sb-access-token";
    public static final String COOKIE_REFRESH_TOKEN = "sb-refresh-token";

    private final org.springframework.beans.factory.ObjectProvider<SupabaseAuthService> supabaseAuthServiceProvider;
    private final org.springframework.beans.factory.ObjectProvider<UserContext> userContextProvider;
    private final Environment environment;

    public AuthInterceptor(
            org.springframework.beans.factory.ObjectProvider<SupabaseAuthService> supabaseAuthServiceProvider,
            org.springframework.beans.factory.ObjectProvider<UserContext> userContextProvider,
            Environment environment) {
        this.supabaseAuthServiceProvider = supabaseAuthServiceProvider;
        this.userContextProvider = userContextProvider;
        this.environment = environment;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 1. Recursos estáticos
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.endsWith(".css") || uri.endsWith(".js") || uri.endsWith(".png") || uri.endsWith(".ico")) {
            return true;
        }

        // 2. Extração dos tokens dos cookies ou do cabeçalho Authorization
        String accessToken = extrairCookie(request, COOKIE_ACCESS_TOKEN);
        String refreshToken = extrairCookie(request, COOKIE_REFRESH_TOKEN);

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if ((accessToken == null || accessToken.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7).trim();
        }

        SupabaseUser user = null;
        SupabaseAuthService authService = (supabaseAuthServiceProvider != null) ? supabaseAuthServiceProvider.getIfAvailable() : null;

        // 3. Validação do Access Token
        if (accessToken != null && !accessToken.isBlank() && authService != null) {
            try {
                user = authService.validarToken(accessToken);
            } catch (Exception e) {
                log.debug("Access token inválido ou expirado. Tentando renovação via refresh token.");
            }
        }

        // 4. Renovação oficial via refresh token com rotação e suporte à janela de tolerância nativa do Supabase
        if (user == null && refreshToken != null && !refreshToken.isBlank() && authService != null) {
            try {
                user = authService.renovarToken(refreshToken);
                if (user != null && user.getAccessToken() != null) {
                    boolean isSecure = isSecureCookie(request);
                    long accessAge = (user.getExpiresIn() != null && user.getExpiresIn() > 0) ? user.getExpiresIn() : 3600;

                    ResponseCookie accessCookie = ResponseCookie.from(COOKIE_ACCESS_TOKEN, user.getAccessToken())
                            .path("/")
                            .httpOnly(true)
                            .secure(isSecure)
                            .sameSite("Lax")
                            .maxAge(accessAge)
                            .build();
                    response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

                    // Rotação do refresh token retornada pelo Supabase Auth
                    if (user.getRefreshToken() != null && !user.getRefreshToken().isBlank()) {
                        ResponseCookie refreshCookie = ResponseCookie.from(COOKIE_REFRESH_TOKEN, user.getRefreshToken())
                                .path("/")
                                .httpOnly(true)
                                .secure(isSecure)
                                .sameSite("Lax")
                                .maxAge(2592000) // 30 dias
                                .build();
                        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
                    }
                }
            } catch (Exception e) {
                log.debug("Falha na renovação via refresh token: {}", e.getMessage());
            }
        }

        // 5. Suporte a sessão de teste isolada para perfis de teste (bloqueado em prod e supabase)
        if (user == null && isTestProfile()) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(CHAVE_USUARIO_LOGADO) != null) {
                String email = (String) session.getAttribute(CHAVE_USUARIO_LOGADO);
                String nome = (String) session.getAttribute("usuarioNome");
                String idStr = (String) session.getAttribute("usuarioId");
                UUID uid = (idStr != null) ? br.com.nexioo.demand.util.IdUtils.parseUuid(idStr) : null;
                if (uid == null && session.getAttribute("testUserId") instanceof UUID) {
                    uid = (UUID) session.getAttribute("testUserId");
                }
                if (uid == null) {
                    uid = UUID.randomUUID();
                    session.setAttribute("usuarioId", uid.toString());
                }
                user = new SupabaseUser(uid.toString(), email, nome, "mock-test-token");
            }
        }

        // 6. Inicialização do UserContext (@RequestScope) na requisição
        boolean estaAutenticado = (user != null && user.getUsuarioIdUuid() != null);
        if (estaAutenticado) {
            try {
                UserContext ctx = (userContextProvider != null) ? userContextProvider.getIfAvailable() : null;
                if (ctx != null) {
                    ctx.inicializar(user.getUsuarioIdUuid(), user.getEmail(), user.getNome(), user.getAccessToken());
                }
            } catch (Exception e) {
                log.debug("Não foi possível inicializar UserContext nesta requisição: {}", e.getMessage());
            }
        }

        // 7. Tratamento de rotas públicas (/login, /signup, /logout)
        if (uri.equals("/login") || uri.equals("/signup") || uri.equals("/logout")) {
            if (uri.equals("/logout")) {
                return true;
            }
            if (estaAutenticado) {
                response.sendRedirect("/projetos");
                return false;
            }
            return true;
        }

        // 8. Se autenticado, libera o acesso à rota privada
        if (estaAutenticado) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            return true;
        }

        // 9. Se for AJAX não autenticado, retorna 401 Unauthorized
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":\"erro\",\"mensagem\":\"Sessão expirada. Por favor, faça login.\"}");
            return false;
        }

        // 10. Redirecionamento para /login
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.sendRedirect("/login");
        return false;
    }

    private boolean isSecureCookie(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        if (environment != null) {
            List<String> active = Arrays.asList(environment.getActiveProfiles());
            if (active.contains("prod")) {
                return true;
            }
        }
        return false;
    }

    private String extrairCookie(HttpServletRequest request, String nome) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> nome.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isTestProfile() {
        if (environment == null) return true;
        List<String> active = Arrays.asList(environment.getActiveProfiles());
        return !active.contains("prod") && !active.contains("supabase") && !active.contains("dev-supabase");
    }
}
