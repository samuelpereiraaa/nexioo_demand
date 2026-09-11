package br.com.nexioo.demand.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Fornece atributos globais do usuário logado diretamente a partir do UserContext
 * para todos os templates do Spring MVC.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final org.springframework.beans.factory.ObjectProvider<UserContext> userContextProvider;

    public GlobalModelAttributes(org.springframework.beans.factory.ObjectProvider<UserContext> userContextProvider) {
        this.userContextProvider = userContextProvider;
    }

    @ModelAttribute
    public void popularAtributosUsuario(HttpServletRequest request, Model model) {
        Object csrf = request.getAttribute(CsrfTokenService.CSRF_ATTR_NAME);
        if (csrf != null) {
            model.addAttribute("_csrf", csrf);
            model.addAttribute("csrfToken", csrf);
        }

        UserContext userContext = null;
        try {
            userContext = userContextProvider != null ? userContextProvider.getIfAvailable() : null;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(GlobalModelAttributes.class)
                    .debug("UserContext indisponível na requisição: {}", e.getMessage());
        }

        if (userContext != null && userContext.isAutenticado()) {
            String email = userContext.getEmail();
            String nome = userContext.getNome();
            if (nome == null || nome.isBlank()) {
                nome = extrairNomeDeEmail(email);
            }
            String iniciais = gerarIniciais(nome);

            model.addAttribute("usuarioLogadoId", userContext.getUsuarioId());
            model.addAttribute("usuarioLogadoEmail", email);
            model.addAttribute("usuarioLogadoNome", nome);
            model.addAttribute("usuarioIniciais", iniciais);
            return;
        }

        // Suporte retrocompatível para ambiente de teste via sessão
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
            return;
        }

        model.addAttribute("usuarioLogadoEmail", "");
        model.addAttribute("usuarioLogadoNome", "");
        model.addAttribute("usuarioIniciais", "");
    }

    public static String extrairNomeDeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Usuário";
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
        String resultado = sb.toString().trim();
        return resultado.isEmpty() ? "Usuário" : resultado;
    }

    public static String gerarIniciais(String nome) {
        if (nome == null || nome.isBlank()) {
            return "U";
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
        return "U";
    }
}
