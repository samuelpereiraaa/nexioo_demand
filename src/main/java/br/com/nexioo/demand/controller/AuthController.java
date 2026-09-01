package br.com.nexioo.demand.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller responsável por renderizar as telas de Autenticação (/login e /signup).
 */
@Controller
@CrossOrigin(origins = "*")
public class AuthController {

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("titulo", "Login");
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("titulo", "Sign Up");
        return "auth/signup";
    }
}
