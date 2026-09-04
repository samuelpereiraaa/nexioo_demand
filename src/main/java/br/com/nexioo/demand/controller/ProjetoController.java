package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.service.AreaTrabalhoService;
import br.com.nexioo.demand.service.ProjetoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * Controller responsável pelo gerenciamento e exibição da tela de Projetos (/projetos)
 * organizados por Área de Trabalho.
 */
@Controller
@CrossOrigin(origins = "*")
@RequestMapping("/projetos")
public class ProjetoController {

    private final ProjetoService projetoService;
    private final AreaTrabalhoService areaTrabalhoService;

    public ProjetoController(ProjetoService projetoService, AreaTrabalhoService areaTrabalhoService) {
        this.projetoService = projetoService;
        this.areaTrabalhoService = areaTrabalhoService;
    }

    private String obterUsuarioLogado(HttpSession session) {
        if (session != null && session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO) != null) {
            return (String) session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO);
        }
        return "samuel@nexioo.com.br";
    }

    @GetMapping
    public String index(
            @RequestParam(required = false) Long areaId,
            HttpSession session,
            Model model) {

        String usuario = obterUsuarioLogado(session);
        List<AreaTrabalho> areas = areaTrabalhoService.listarPorUsuario(usuario);

        AreaTrabalho areaAtual;
        if (areaId != null) {
            areaAtual = areas.stream()
                    .filter(a -> a.getId().equals(areaId))
                    .findFirst()
                    .orElse(!areas.isEmpty() ? areas.get(0) : areaTrabalhoService.obterOuCriarPadrao(usuario));
        } else {
            areaAtual = !areas.isEmpty() ? areas.get(0) : areaTrabalhoService.obterOuCriarPadrao(usuario);
        }

        List<Projeto> projetosDaArea = projetoService.listarPorArea(areaAtual.getId());
        List<Projeto> recentes = projetoService.listarRecentes();

        ProjetoForm form = new ProjetoForm();
        form.setAreaTrabalhoId(areaAtual.getId());

        model.addAttribute("areas", areas);
        model.addAttribute("areaAtual", areaAtual);
        model.addAttribute("areaAtualId", areaAtual.getId());
        model.addAttribute("projetos", projetosDaArea);
        model.addAttribute("recentes", recentes);
        model.addAttribute("projetoForm", form);
        model.addAttribute("titulo", "Quadros - " + areaAtual.getNome());

        return "projetos/index";
    }

    @PostMapping
    public String criar(
            @Valid @ModelAttribute("projetoForm") ProjetoForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors() || form.getNome() == null || form.getNome().isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "O nome do projeto é obrigatório.");
            return "redirect:/projetos" + (form.getAreaTrabalhoId() != null ? "?areaId=" + form.getAreaTrabalhoId() : "");
        }

        try {
            Projeto novoProjeto = projetoService.criar(form);
            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Projeto \"" + novoProjeto.getNome() + "\" criado com sucesso.");
            return "redirect:/projetos?areaId=" + novoProjeto.getAreaTrabalhoId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/projetos" + (form.getAreaTrabalhoId() != null ? "?areaId=" + form.getAreaTrabalhoId() : "");
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, @RequestParam(required = false) Long areaId, RedirectAttributes redirectAttributes) {
        projetoService.excluir(id);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Projeto excluído com sucesso.");
        return "redirect:/projetos" + (areaId != null ? "?areaId=" + areaId : "");
    }
}
