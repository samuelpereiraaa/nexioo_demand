package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.service.SupabaseAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Controller responsável pelas rotas de autenticação (/login, /signup, /logout),
 * integrando de forma segura com o Supabase Auth como fonte de verdade.
 */
@Controller
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SupabaseAuthService supabaseAuthService;

    public AuthController(SupabaseAuthService supabaseAuthService) {
        this.supabaseAuthService = supabaseAuthService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (session != null && session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO) != null) {
            return "redirect:/projetos";
        }
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

            // Prevenção contra Session Fixation
            request.changeSessionId();
            HttpSession activeSession = request.getSession(true);
            activeSession.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, user.getEmail());
            activeSession.setAttribute("usuarioNome", user.getNome());
            if (user.getAccessToken() != null && !user.getAccessToken().isBlank()) {
                activeSession.setAttribute("supabaseAccessToken", user.getAccessToken());
            }

            log.info("Login bem-sucedido para o usuário: {}", user.getEmail());

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
    public String signupPage(HttpSession session, Model model) {
        if (session != null && session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO) != null) {
            return "redirect:/projetos";
        }
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

            // Prevenção contra Session Fixation
            request.changeSessionId();
            HttpSession activeSession = request.getSession(true);
            activeSession.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, user.getEmail());
            activeSession.setAttribute("usuarioNome", user.getNome());
            if (user.getAccessToken() != null && !user.getAccessToken().isBlank()) {
                activeSession.setAttribute("supabaseAccessToken", user.getAccessToken());
            }

            log.info("Cadastro realizado com sucesso para o usuário: {}", user.getEmail());

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

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        encerrarSessao(request, response);
        return "redirect:/login";
    }

    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<String> logoutPost(HttpServletRequest request, HttpServletResponse response) {
        encerrarSessao(request, response);
        return ResponseEntity.ok("Deslogado com sucesso");
    }

    private void encerrarSessao(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }
}
