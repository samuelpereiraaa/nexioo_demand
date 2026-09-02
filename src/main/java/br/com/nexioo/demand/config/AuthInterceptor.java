package br.com.nexioo.demand.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Interceptor para proteção de rotas.
 * Garante que apenas /login e /signup estejam acessíveis para usuários não autenticados.
 * Qualquer tentativa de acessar /quadro, /projetos ou endpoints internos sem sessão ativa
 * é redirecionada para /login.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String CHAVE_USUARIO_LOGADO = "usuarioLogado";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 1. Permitir recursos estáticos (.css, .js, .png, etc.)
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.endsWith(".css") || uri.endsWith(".js") || uri.endsWith(".png") || uri.endsWith(".ico")) {
            return true;
        }

        // 2. Permitir rotas públicas de autenticação (/login e /signup)
        if (uri.equals("/login") || uri.equals("/signup")) {
            // Se o usuário já estiver logado e tentar acessar /login ou /signup, redireciona para /projetos
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(CHAVE_USUARIO_LOGADO) != null) {
                response.sendRedirect("/projetos");
                return false;
            }

            return true;
        }

        // 3. Verificar se existe usuário logado na sessão
        HttpSession session = request.getSession(false);
        boolean estaAutenticado = session != null && session.getAttribute(CHAVE_USUARIO_LOGADO) != null;

        if (estaAutenticado) {
            return true;
        }

        // 4. Se for requisição AJAX não autenticada, retorna 401 Unauthorized
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Sessão expirada. Por favor, faça login.");
            return false;
        }

        // 5. Redirecionar para /login se não estiver autenticado
        response.sendRedirect("/login");
        return false;
    }
}
