package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.service.ProjetoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

/**
 * Controller responsável pelo gerenciamento e exibição da tela de Projetos (/projetos).
 */
@Controller
@CrossOrigin(origins = "*")
@RequestMapping("/projetos")
public class ProjetoController {

    private final ProjetoService projetoService;

    public ProjetoController(ProjetoService projetoService) {
        this.projetoService = projetoService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("projetos", projetoService.listarTodos());
        model.addAttribute("recentes", projetoService.listarRecentes());
        model.addAttribute("projetoForm", new ProjetoForm());
        model.addAttribute("titulo", "Quadros");
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
            return "redirect:/projetos";
        }

        try {
            Projeto novoProjeto = projetoService.criar(form);
            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Projeto \"" + novoProjeto.getNome() + "\" criado com sucesso.");
            return "redirect:/projetos";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/projetos";
        }
    }
}
