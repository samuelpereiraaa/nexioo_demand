package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
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
 * Controller responsável pelas telas e processamento de Autenticação (/login, /signup e /logout).
 */
@Controller
@CrossOrigin(origins = "*")
public class AuthController {

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
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String userEmail = (email != null && !email.isBlank()) ? email.trim() : "nexiooo@nexioo.com.br";
        session.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, userEmail);

        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return ResponseEntity.ok().body("{\"status\":\"ok\",\"user\":\"" + userEmail + "\"}");
        }

        return "redirect:/projetos";
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
            HttpServletRequest request,
            HttpSession session) {

        String userEmail = (email != null && !email.isBlank()) ? email.trim() : "novo_usuario@nexioo.com.br";
        session.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, userEmail);

        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return ResponseEntity.ok().body("{\"status\":\"ok\",\"user\":\"" + userEmail + "\"}");
        }

        return "redirect:/projetos";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return "redirect:/login";
    }

    @PostMapping("/logout")
    @ResponseBody
    public ResponseEntity<String> logoutPost(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        javax.servlet.http.Cookie cookie = new javax.servlet.http.Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        return ResponseEntity.ok("Deslogado com sucesso");
    }
}
