package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import br.com.nexioo.demand.service.ProjetoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Responsável pela tela principal: o quadro Kanban.
 * Mapeia as rotas "/" e "/quadro".
 * Delega a lógica aos serviços {@link DemandaService}, {@link ColunaService} e {@link ProjetoService}.
 */
@Controller
public class QuadroController {

    private final DemandaService demandaService;
    private final ColunaService colunaService;
    private final ProjetoService projetoService;

    public QuadroController(DemandaService demandaService, ColunaService colunaService, ProjetoService projetoService) {
        this.demandaService = demandaService;
        this.colunaService = colunaService;
        this.projetoService = projetoService;
    }

    @GetMapping("/")
    public String raiz() {
        return "redirect:/login";
    }

    @GetMapping("/quadro")
    public String quadro(
            @RequestParam(required = false) Long projetoId,
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String responsavel,
            Model model) {

        Projeto projetoAtual = null;
        if (projetoId != null) {
            try {
                projetoAtual = projetoService.buscarPorId(projetoId);
            } catch (Exception ignored) {}
        }
        if (projetoAtual == null) {
            List<Projeto> todos = projetoService.listarTodos();
            if (!todos.isEmpty()) {
                projetoAtual = todos.get(0);
            }
        }

        Long pid = projetoAtual != null ? projetoAtual.getId() : null;

        boolean temFiltro = temFiltroAtivo(termo, prioridade, responsavel);
        Map<Coluna, List<Demanda>> demandas;
        if (temFiltro) {
            demandas = (pid != null)
                    ? demandaService.filtrar(pid, termo, prioridade, responsavel)
                    : demandaService.filtrar(termo, prioridade, responsavel);
        } else {
            demandas = (pid != null)
                    ? demandaService.listarPorColuna(pid)
                    : demandaService.listarPorColuna();
        }

        List<Coluna> colunas = colunaService.listarTodas();

        // Formulário padrão para criação rápida de demandas
        DemandaForm novoForm = new DemandaForm();
        novoForm.setColuna(colunaService.buscarPadrao());
        novoForm.setPrioridade(Prioridade.MEDIA);
        if (pid != null) {
            novoForm.setProjetoId(pid);
        }

        model.addAttribute("demandas", demandas);
        model.addAttribute("colunas", colunas);
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("demandaForm", novoForm);
        model.addAttribute("colunaForm", new ColunaForm());
        model.addAttribute("filtroTermo", termo);
        model.addAttribute("filtroPrioridade", prioridade);
        model.addAttribute("filtroResponsavel", responsavel);
        model.addAttribute("temFiltro", temFiltro);
        model.addAttribute("projetoAtual", projetoAtual);
        model.addAttribute("projetoId", pid);

        return "quadro/index";
    }

    private boolean temFiltroAtivo(String termo, Prioridade prioridade, String responsavel) {
        return (termo != null && !termo.isBlank())
                || prioridade != null
                || (responsavel != null && !responsavel.isBlank());
    }
}
