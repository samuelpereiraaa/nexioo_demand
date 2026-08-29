package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.DemandaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Responsável pela tela principal: o quadro Kanban.
 * Mapeia as rotas "/" e "/quadro".
 * Delega toda lógica ao {@link DemandaService}.
 */
@Controller
public class QuadroController {

    private final DemandaService demandaService;

    public QuadroController(DemandaService demandaService) {
        this.demandaService = demandaService;
    }

    @GetMapping({"/", "/quadro"})
    public String quadro(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String responsavel,
            Model model) {

        boolean temFiltro = temFiltroAtivo(termo, prioridade, responsavel);
        Map<Coluna, List<Demanda>> demandas = temFiltro
                ? demandaService.filtrar(termo, prioridade, responsavel)
                : demandaService.listarPorColuna();

        // Formulário padrão para criação rápida via modal / inline
        DemandaForm novoForm = new DemandaForm();
        novoForm.setColuna(Coluna.BACKLOG);
        novoForm.setPrioridade(Prioridade.MEDIA);

        model.addAttribute("demandas", demandas);
        model.addAttribute("colunas", Coluna.values());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("demandaForm", novoForm);
        model.addAttribute("filtroTermo", termo);
        model.addAttribute("filtroPrioridade", prioridade);
        model.addAttribute("filtroResponsavel", responsavel);
        model.addAttribute("temFiltro", temFiltro);

        return "quadro/index";
    }

    private boolean temFiltroAtivo(String termo, Prioridade prioridade, String responsavel) {
        return (termo != null && !termo.isBlank())
                || prioridade != null
                || (responsavel != null && !responsavel.isBlank());
    }
}
