package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.service.SupabaseAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.List;

/**
 * Controller responsável pelas rotas de autenticação (/login, /signup, /logout),
 * integrando de forma segura e stateless com o Supabase Auth.
 */
@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SupabaseAuthService supabaseAuthService;
    private final Environment environment;

    public AuthController(SupabaseAuthService supabaseAuthService, Environment environment) {
        this.supabaseAuthService = supabaseAuthService;
        this.environment = environment;
    }

    @GetMapping("/login")
    public String loginPage(HttpServletRequest request, Model model) {
        model.addAttribute("titulo", "Login");
        return "auth/login";
    }

    @PostMapping("/login")
    public Object realizarLogin(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String requestedWith = request.getHeader("X-Requested-With");
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);

        try {
            SupabaseUser user;
            if (token != null && !token.isBlank()) {
                user = supabaseAuthService.validarToken(token.trim());
            } else {
                if (email == null || email.isBlank() || password == null || password.isBlank()) {
                    throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
                }
                user = supabaseAuthService.autenticar(email.trim(), password);
            }

            adicionarCookiesAutenticacao(request, response, user);

            // Rotaciona ID da sessão contra session fixation e login CSRF
            try {
                request.changeSessionId();
                HttpSession activeSession = request.getSession(true);
                activeSession.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, user.getEmail());
                activeSession.setAttribute("usuarioNome", user.getNome());
                activeSession.setAttribute("usuarioId", user.getId());
                if (user.getAccessToken() != null) {
                    activeSession.setAttribute("supabaseAccessToken", user.getAccessToken());
                }
            } catch (Exception e) {
                log.debug("Aviso ao rotacionar sessionId no login: {}", e.getMessage());
            }

            log.info("Login bem-sucedido para usuário autenticado.");

            if (isAjax) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ok\",\"user\":\"" + user.getEmail() + "\",\"redirect\":\"/projetos\"}");
            }
            return "redirect:/projetos";

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "E-mail ou senha inválidos.";
            HttpStatus status = msg.contains("obrigatórios") ? HttpStatus.BAD_REQUEST : HttpStatus.UNAUTHORIZED;

            if (isAjax) {
                return ResponseEntity.status(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"erro\",\"mensagem\":\"" + msg + "\"}");
            }
            redirectAttributes.addFlashAttribute("erro", msg);
            return "redirect:/login";
        }
    }

    @GetMapping("/signup")
    public String signupPage(HttpServletRequest request, Model model) {
        model.addAttribute("titulo", "Sign Up");
        return "auth/signup";
    }

    @PostMapping("/signup")
    public Object realizarCadastro(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String requestedWith = request.getHeader("X-Requested-With");
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(requestedWith);

        try {
            SupabaseUser user;
            if (token != null && !token.isBlank()) {
                user = supabaseAuthService.validarToken(token.trim());
            } else {
                if (email == null || email.isBlank() || password == null || password.isBlank()) {
                    throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
                }
                user = supabaseAuthService.cadastrar(email.trim(), password, nome);
            }

            adicionarCookiesAutenticacao(request, response, user);

            try {
                request.changeSessionId();
                HttpSession activeSession = request.getSession(true);
                activeSession.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, user.getEmail());
                activeSession.setAttribute("usuarioNome", user.getNome());
                activeSession.setAttribute("usuarioId", user.getId());
                if (user.getAccessToken() != null) {
                    activeSession.setAttribute("supabaseAccessToken", user.getAccessToken());
                }
            } catch (Exception e) {
                log.debug("Aviso ao rotacionar sessionId no cadastro: {}", e.getMessage());
            }

            log.info("Cadastro realizado com sucesso.");

            if (isAjax) {
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ok\",\"user\":\"" + user.getEmail() + "\",\"redirect\":\"/projetos\"}");
            }
            return "redirect:/projetos";

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Erro ao criar conta.";
            if (isAjax) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"erro\",\"mensagem\":\"" + msg + "\"}");
            }
            redirectAttributes.addFlashAttribute("erro", msg);
            return "redirect:/signup";
        }
    }

    @PostMapping("/logout")
    public Object logoutPost(HttpServletRequest request, HttpServletResponse response) {
        encerrarSessao(request, response);
        String requestedWith = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith) || (accept != null && accept.contains("application/json"))) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"ok\",\"redirect\":\"/login\"}");
        }
        if (accept != null && accept.contains("text/html")) {
            return "redirect:/login";
        }
        return ResponseEntity.ok("Deslogado com sucesso");
    }

    private void adicionarCookiesAutenticacao(HttpServletRequest request, HttpServletResponse response, SupabaseUser user) {
        boolean isSecure = isSecureCookie(request);
        long accessAge = (user.getExpiresIn() != null && user.getExpiresIn() > 0) ? user.getExpiresIn() : 3600;

        if (user.getAccessToken() != null && !user.getAccessToken().isBlank()) {
            ResponseCookie accessCookie = ResponseCookie.from(AuthInterceptor.COOKIE_ACCESS_TOKEN, user.getAccessToken())
                    .path("/")
                    .httpOnly(true)
                    .secure(isSecure)
                    .sameSite("Lax")
                    .maxAge(accessAge)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        }

        if (user.getRefreshToken() != null && !user.getRefreshToken().isBlank()) {
            ResponseCookie refreshCookie = ResponseCookie.from(AuthInterceptor.COOKIE_REFRESH_TOKEN, user.getRefreshToken())
                    .path("/")
                    .httpOnly(true)
                    .secure(isSecure)
                    .sameSite("Lax")
                    .maxAge(2592000) // 30 dias
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
    }

    private void encerrarSessao(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthInterceptor.COOKIE_ACCESS_TOKEN.equals(c.getName())) {
                    accessToken = c.getValue();
                }
            }
        }

        try {
            if (accessToken != null && !accessToken.isBlank()) {
                supabaseAuthService.revogarSessao(accessToken);
            }
        } catch (Exception e) {
            log.debug("Revogação remota no Supabase Auth concluída ou não disponível: {}", e.getMessage());
        } finally {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            boolean isSecure = isSecureCookie(request);

            // Exclusão com os mesmos atributos de criação dos cookies
            ResponseCookie cleanAccess = ResponseCookie.from(AuthInterceptor.COOKIE_ACCESS_TOKEN, "")
                    .path("/").maxAge(0).httpOnly(true).secure(isSecure).sameSite("Lax").build();
            ResponseCookie cleanRefresh = ResponseCookie.from(AuthInterceptor.COOKIE_REFRESH_TOKEN, "")
                    .path("/").maxAge(0).httpOnly(true).secure(isSecure).sameSite("Lax").build();
            ResponseCookie cleanSession = ResponseCookie.from("JSESSIONID", "")
                    .path("/").maxAge(0).httpOnly(true).secure(isSecure).sameSite("Lax").build();

            response.addHeader(HttpHeaders.SET_COOKIE, cleanAccess.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cleanRefresh.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cleanSession.toString());

            response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
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
}
