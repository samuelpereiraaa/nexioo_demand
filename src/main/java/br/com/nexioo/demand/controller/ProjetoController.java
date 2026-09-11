package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.UserContext;
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

import javax.validation.Valid;
import java.util.List;

/**
 * Controller responsável pelo gerenciamento e exibição da tela de Projetos (/projetos)
 * organizados por Área de Trabalho.
 */
@Controller
@RequestMapping("/projetos")
public class ProjetoController {

    private final ProjetoService projetoService;
    private final AreaTrabalhoService areaTrabalhoService;
    private final UserContext userContext;

    public ProjetoController(ProjetoService projetoService,
                             AreaTrabalhoService areaTrabalhoService,
                             UserContext userContext) {
        this.projetoService = projetoService;
        this.areaTrabalhoService = areaTrabalhoService;
        this.userContext = userContext;
    }

    @GetMapping
    public String index(
            @RequestParam(required = false) java.util.UUID areaId,
            Model model) {

        String email = (userContext != null && userContext.isAutenticado()) ? userContext.getEmail() : "";
        List<AreaTrabalho> areas = areaTrabalhoService.listarPorUsuario(email);

        AreaTrabalho areaAtual;
        if (areaId != null) {
            areaAtual = areas.stream()
                    .filter(a -> a.getId().equals(areaId))
                    .findFirst()
                    .orElse(!areas.isEmpty() ? areas.get(0) : areaTrabalhoService.obterOuCriarPadrao(email));
        } else {
            areaAtual = !areas.isEmpty() ? areas.get(0) : areaTrabalhoService.obterOuCriarPadrao(email);
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
    public String excluir(@PathVariable java.util.UUID id, @RequestParam(required = false) java.util.UUID areaId, RedirectAttributes redirectAttributes) {
        projetoService.excluir(id);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Projeto excluído com sucesso.");
        return "redirect:/projetos" + (areaId != null ? "?areaId=" + areaId : "");
    }
}
