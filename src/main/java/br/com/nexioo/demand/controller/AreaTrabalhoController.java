package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.service.AreaTrabalhoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Controller
@CrossOrigin(origins = "*")
@RequestMapping("/areas")
public class AreaTrabalhoController {

    private final AreaTrabalhoService areaTrabalhoService;

    public AreaTrabalhoController(AreaTrabalhoService areaTrabalhoService) {
        this.areaTrabalhoService = areaTrabalhoService;
    }

    private String obterUsuarioLogado(HttpSession session) {
        if (session != null && session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO) != null) {
            return (String) session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO);
        }
        return "samuel@nexioo.com.br";
    }

    @GetMapping
    @ResponseBody
    public List<AreaTrabalho> listar(HttpSession session) {
        String usuario = obterUsuarioLogado(session);
        return areaTrabalhoService.listarPorUsuario(usuario);
    }

    @PostMapping
    public Object criar(
            @RequestParam String nome,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String usuario = obterUsuarioLogado(session);
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        try {
            AreaTrabalho nova = areaTrabalhoService.criar(nome, usuario);
            if (isAjax) {
                return ResponseEntity.ok(Map.of("status", "ok", "id", nova.getId(), "nome", nova.getNome(), "inicial", nova.getInicial()));
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Área de trabalho criada com sucesso!");
            return "redirect:/projetos?areaId=" + nova.getId();
        } catch (IllegalArgumentException e) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("status", "erro", "mensagem", e.getMessage()));
            }
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/projetos";
        }
    }

    @PostMapping("/{id}/editar")
    public Object editar(
            @PathVariable Long id,
            @RequestParam String nome,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String usuario = obterUsuarioLogado(session);
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        try {
            AreaTrabalho editada = areaTrabalhoService.editar(id, nome, usuario);
            if (isAjax) {
                return ResponseEntity.ok(Map.of("status", "ok", "id", editada.getId(), "nome", editada.getNome(), "inicial", editada.getInicial()));
            }
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Área de trabalho renomeada com sucesso!");
            return "redirect:/projetos?areaId=" + editada.getId();
        } catch (IllegalArgumentException e) {
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("status", "erro", "mensagem", e.getMessage()));
            }
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/projetos?areaId=" + id;
        }
    }

    @PostMapping("/{id}/excluir")
    public Object excluir(
            @PathVariable Long id,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String usuario = obterUsuarioLogado(session);
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        areaTrabalhoService.excluir(id, usuario);
        if (isAjax) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Área de trabalho excluída com sucesso.");
        return "redirect:/projetos";
    }
}
