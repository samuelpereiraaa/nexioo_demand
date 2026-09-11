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
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            org.springframework.ui.Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors() || form.getNome() == null || form.getNome().isBlank()) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return "fragments/coluna :: coluna-vazio";
            }
            redirectAttributes.addFlashAttribute("mensagemErro", "O nome da lista é obrigatório.");
            return "redirect:/quadro";
        }

        try {
            Coluna novaColuna = colunaService.criar(form);
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                model.addAttribute("coluna", novaColuna);
                model.addAttribute("listaDemandas", java.util.Collections.emptyList());
                return "fragments/coluna :: coluna";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Lista \"" + novaColuna.getDescricao() + "\" criada com sucesso.");
            String projParam = form.getProjetoId() != null ? "projetoId=" + form.getProjetoId() + "&" : "";
            return "redirect:/quadro?" + projParam + "novaColunaId=" + novaColuna.getId() + "#coluna-" + novaColuna.getId().toLowerCase();
        } catch (IllegalArgumentException e) {
            if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
                return "fragments/coluna :: coluna-vazio";
            }
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            String projParam = form.getProjetoId() != null ? "?projetoId=" + form.getProjetoId() : "";
            return "redirect:/quadro" + projParam;
        }
    }


    @PostMapping("/{id}/excluir")
    public String excluir(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.UUID projetoId,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            RedirectAttributes redirectAttributes) {
        colunaService.excluir(id, projetoId);
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return "fragments/cartao :: cartao-vazio";
        }
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Lista excluída com sucesso.");
        return "redirect:/quadro" + (projetoId != null ? "?projetoId=" + projetoId : "");
    }

}

