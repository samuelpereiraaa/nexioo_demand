package br.com.nexioo.demand;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import br.com.nexioo.demand.service.ProjetoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Popula o repositório com a lista inicial, cartões e projetos de exemplo conforme a imagem de referência.
 */
@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final DemandaService demandaService;
    private final ColunaService colunaService;
    private final ProjetoService projetoService;
    private volatile boolean carregado = false;

    public DataLoader(DemandaService demandaService, ColunaService colunaService, ProjetoService projetoService) {
        this.demandaService = demandaService;
        this.colunaService = colunaService;
        this.projetoService = projetoService;
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        if (carregado || !demandaService.listarTodas().isEmpty()) {
            return;
        }

        log.info("Inicializando projetos e demandas de demonstração...");

        // Projeto inicial "Meu quadro do Trello" (fiel à imagem de referência)
        ProjetoForm p1 = new ProjetoForm();
        p1.setNome("Meu quadro do Trello");
        p1.setDescricao("Área de trabalho do Trello - Quadro principal de demandas");
        p1.setGradiente("linear-gradient(135deg, #a855f7, #ec4899)");
        projetoService.criar(p1);

        Coluna starterGuide = colunaService.buscarPorId("BACKLOG");
        if (starterGuide != null) {
            starterGuide.setDescricao("Trello Starter Guide");
        } else {
            ColunaForm cf = new ColunaForm();
            cf.setNome("Trello Starter Guide");
            starterGuide = colunaService.criar(cf);
        }

        // Cartão 1: "teste" (não concluído)
        DemandaForm f1 = new DemandaForm();
        f1.setTitulo("teste");
        f1.setColuna(starterGuide);
        f1.setPrioridade(Prioridade.MEDIA);
        demandaService.criar(f1);

        // Cartão 2: "aaaa" (concluído)
        DemandaForm f2 = new DemandaForm();
        f2.setTitulo("aaaa");
        f2.setColuna(starterGuide);
        f2.setPrioridade(Prioridade.MEDIA);
        Demanda d2 = demandaService.criar(f2);
        demandaService.alternarConclusao(d2.getId());

        // Cartão 3: "teste" (concluído)
        DemandaForm f3 = new DemandaForm();
        f3.setTitulo("teste");
        f3.setColuna(starterGuide);
        f3.setPrioridade(Prioridade.MEDIA);
        Demanda d3 = demandaService.criar(f3);
        demandaService.alternarConclusao(d3.getId());

        carregado = true;
        log.info("Projetos e demandas de demonstração criados com sucesso.");
    }
}
