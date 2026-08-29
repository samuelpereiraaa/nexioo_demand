package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.DemandaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

/**
 * Gerencia o ciclo de vida das demandas: criação, visualização, edição,
 * alteração de status e exclusão.
 * Sem lógica de negócio — toda regra está em {@link DemandaService}.
 */
@Controller
@RequestMapping("/demandas")
public class DemandaController {

    private final DemandaService demandaService;

    public DemandaController(DemandaService demandaService) {
        this.demandaService = demandaService;
    }

    // ── Criar ────────────────────────────────────────────────────────────────

    @GetMapping("/nova")
    public String novaForm(Model model) {
        DemandaForm form = new DemandaForm();
        form.setColuna(Coluna.BACKLOG);
        form.setPrioridade(Prioridade.MEDIA);
        model.addAttribute("demandaForm", form);
        model.addAttribute("colunas", Coluna.values());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("paginaTitulo", "Nova Demanda");
        model.addAttribute("formAction", "/demandas");
        return "demanda/form";
    }

    @PostMapping
    public String criar(
            @Valid @ModelAttribute("demandaForm") DemandaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("colunas", Coluna.values());
            model.addAttribute("prioridades", Prioridade.values());
            model.addAttribute("paginaTitulo", "Nova Demanda");
            model.addAttribute("formAction", "/demandas");
            return "demanda/form";
        }

        Demanda demanda = demandaService.criar(form);
        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Demanda \"" + demanda.getTitulo() + "\" criada com sucesso.");
        return "redirect:/";
    }

    // ── Visualizar ───────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("demanda", demandaService.buscarPorId(id));
        model.addAttribute("colunas", Coluna.values());
        return "demanda/detalhe";
    }

    // ── Editar ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        Demanda demanda = demandaService.buscarPorId(id);
        model.addAttribute("demandaForm", demandaParaForm(demanda));
        model.addAttribute("demanda", demanda);
        model.addAttribute("colunas", Coluna.values());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("paginaTitulo", "Editar Demanda");
        model.addAttribute("formAction", "/demandas/" + id + "/editar");
        return "demanda/form";
    }

    @PostMapping("/{id}/editar")
    public String editar(
            @PathVariable Long id,
            @Valid @ModelAttribute("demandaForm") DemandaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("demanda", demandaService.buscarPorId(id));
            model.addAttribute("colunas", Coluna.values());
            model.addAttribute("prioridades", Prioridade.values());
            model.addAttribute("paginaTitulo", "Editar Demanda");
            model.addAttribute("formAction", "/demandas/" + id + "/editar");
            return "demanda/form";
        }

        demandaService.editar(id, form);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Demanda atualizada com sucesso.");
        return "redirect:/demandas/" + id;
    }

    // ── Alterar status ───────────────────────────────────────────────────────

    @PostMapping("/{id}/status")
    public String alterarStatus(
            @PathVariable Long id,
            @RequestParam Coluna coluna,
            RedirectAttributes redirectAttributes) {
        demandaService.alterarColuna(id, coluna);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Status atualizado com sucesso.");
        return "redirect:/";
    }

    // ── Alternar Concluído / Reabrir ─────────────────────────────────────────

    @PostMapping("/{id}/toggle-concluido")
    public String toggleConcluido(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        Demanda demanda = demandaService.buscarPorId(id);
        if (demanda.getColuna() == Coluna.CONCLUIDO) {
            demandaService.alterarColuna(id, Coluna.A_FAZER);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Demanda \"" + demanda.getTitulo() + "\" reaberta em A Fazer.");
        } else {
            demandaService.alterarColuna(id, Coluna.CONCLUIDO);
            redirectAttributes.addFlashAttribute("mensagemSucesso", "Demanda \"" + demanda.getTitulo() + "\" marcada como concluída!");
        }
        return "redirect:/";
    }

    // ── Excluir ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/excluir")
    public String confirmarExclusaoForm(@PathVariable Long id, Model model) {
        model.addAttribute("demanda", demandaService.buscarPorId(id));
        return "demanda/confirmar-exclusao";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String titulo = demandaService.buscarPorId(id).getTitulo();
        demandaService.excluir(id);
        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Demanda \"" + titulo + "\" excluída com sucesso.");
        return "redirect:/";
    }

    // ── Auxiliar ─────────────────────────────────────────────────────────────

    private DemandaForm demandaParaForm(Demanda demanda) {
        DemandaForm form = new DemandaForm();
        form.setTitulo(demanda.getTitulo());
        form.setDescricao(demanda.getDescricao());
        form.setColuna(demanda.getColuna());
        form.setPrioridade(demanda.getPrioridade());
        form.setResponsavel(demanda.getResponsavel());
        form.setPrazo(demanda.getPrazo());
        return form;
    }
}
