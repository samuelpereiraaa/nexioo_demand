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
import java.util.UUID;

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
            @RequestParam(required = false) String projetoId,
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String responsavel,
            Model model) {

        UUID idBuscado = br.com.nexioo.demand.util.IdUtils.parseUuid(projetoId);
        Projeto projetoAtual = null;
        if (idBuscado != null) {
            try {
                projetoAtual = projetoService.buscarPorId(idBuscado);
            } catch (Exception e) {
                // Acesso negado ou projeto de outro usuário -> Redireciona para /projetos com segurança
                return "redirect:/projetos";
            }
            if (projetoAtual == null) {
                return "redirect:/projetos";
            }
        }

        if (projetoAtual == null) {
            List<Projeto> recentes = projetoService.listarRecentes();
            if (!recentes.isEmpty()) {
                projetoAtual = recentes.get(0);
            } else {
                List<Projeto> meusProjetos = projetoService.listarTodos();
                if (!meusProjetos.isEmpty()) {
                    projetoAtual = meusProjetos.get(0);
                }
            }
        }

        if (projetoAtual != null) {
            // Registra a visualização recente para o usuário autenticado
            projetoService.registrarVisualizacao(projetoAtual.getId());
        }

        UUID pid = projetoAtual != null ? projetoAtual.getId() : null;

        boolean temFiltro = temFiltroAtivo(termo, prioridade, responsavel);
        Map<Coluna, List<Demanda>> demandas;
        if (temFiltro) {
            demandas = demandaService.filtrar(pid, termo, prioridade, responsavel);
        } else {
            demandas = demandaService.listarPorColuna(pid);
        }

        List<Coluna> colunas = (pid != null)
                ? colunaService.listarPorProjeto(pid)
                : colunaService.listarTodas();

        // Formulário padrão para criação rápida de demandas
        DemandaForm novoForm = new DemandaForm();
        novoForm.setColuna(colunaService.buscarPadrao());
        novoForm.setPrioridade(Prioridade.MEDIA);
        novoForm.setProjetoId(pid);

        ColunaForm colunaForm = new ColunaForm();
        colunaForm.setProjetoId(pid);

        model.addAttribute("demandas", demandas);
        model.addAttribute("colunas", colunas);
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("demandaForm", novoForm);
        model.addAttribute("colunaForm", colunaForm);
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
