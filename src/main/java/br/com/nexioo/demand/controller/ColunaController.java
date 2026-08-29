package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.service.ColunaService;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

/**
 * Controller responsável pelo gerenciamento de colunas/listas.
 */
@Controller
@RequestMapping("/colunas")
public class ColunaController {

    private final ColunaService colunaService;

    public ColunaController(ColunaService colunaService) {
        this.colunaService = colunaService;
    }

    @PostMapping
    public String criar(
            @Valid @ModelAttribute("colunaForm") ColunaForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors() || form.getNome() == null || form.getNome().isBlank()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "O nome da lista é obrigatório.");
            return "redirect:/";
        }

        try {
            Coluna novaColuna = colunaService.criar(form);
            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Lista \"" + novaColuna.getDescricao() + "\" criada com sucesso.");
            return "redirect:/?novaColunaId=" + novaColuna.getId() + "#coluna-" + novaColuna.getId().toLowerCase();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/";
        }
    }
}
