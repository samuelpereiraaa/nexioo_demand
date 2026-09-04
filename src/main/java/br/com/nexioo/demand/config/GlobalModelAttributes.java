package br.com.nexioo.demand.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Fornece atributos globais do usuário logado (nome, email e iniciais automáticas)
 * para todos os templates do Spring MVC.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute
    public void popularAtributosUsuario(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO) != null) {
            String email = (String) session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO);
            String nome = (String) session.getAttribute("usuarioNome");
            if (nome == null || nome.isBlank()) {
                nome = extrairNomeDeEmail(email);
            }
            String iniciais = gerarIniciais(nome);

            model.addAttribute("usuarioLogadoEmail", email);
            model.addAttribute("usuarioLogadoNome", nome);
            model.addAttribute("usuarioIniciais", iniciais);
        } else {
            model.addAttribute("usuarioLogadoEmail", "convidado@nexioo.com.br");
            model.addAttribute("usuarioLogadoNome", "Convidado");
            model.addAttribute("usuarioIniciais", "C");
        }
    }

    public static String extrairNomeDeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Samuel Oliveira";
        }
        String local = email.contains("@") ? email.substring(0, email.indexOf("@")) : email;
        String[] partes = local.split("[._-]");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (!p.isBlank()) {
                sb.append(Character.toUpperCase(p.charAt(0)))
                  .append(p.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static String gerarIniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "SP";
        }
        String limpo = nome.trim();
        String[] partes = limpo.split("\\s+");
        if (partes.length == 1) {
            return partes[0].substring(0, 1).toUpperCase();
        } else if (partes.length >= 2) {
            String primeira = partes[0].substring(0, 1).toUpperCase();
            String ultima = partes[partes.length - 1].substring(0, 1).toUpperCase();
            return primeira + ultima;
        }
        return "SP";
    }
}
