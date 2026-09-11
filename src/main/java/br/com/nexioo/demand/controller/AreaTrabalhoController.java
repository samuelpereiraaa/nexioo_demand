package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.service.AreaTrabalhoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/areas")
public class AreaTrabalhoController {

    private final AreaTrabalhoService areaTrabalhoService;
    private final UserContext userContext;

    public AreaTrabalhoController(AreaTrabalhoService areaTrabalhoService, UserContext userContext) {
        this.areaTrabalhoService = areaTrabalhoService;
        this.userContext = userContext;
    }

    private String getUsuarioAtivo() {
        if (userContext != null && userContext.isAutenticado()) {
            return userContext.getEmail();
        }
        return "";
    }

    @GetMapping
    @ResponseBody
    public List<AreaTrabalho> listar() {
        return areaTrabalhoService.listarPorUsuario(getUsuarioAtivo());
    }

    @PostMapping
    public Object criar(
            @RequestParam String nome,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String usuario = getUsuarioAtivo();
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
            @PathVariable java.util.UUID id,
            @RequestParam String nome,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String usuario = getUsuarioAtivo();
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
            @PathVariable java.util.UUID id,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        String usuario = getUsuarioAtivo();
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        areaTrabalhoService.excluir(id, usuario);
        if (isAjax) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Área de trabalho excluída com sucesso.");
        return "redirect:/projetos";
    }
}
