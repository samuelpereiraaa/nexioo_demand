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

        // Projeto inicial "Quadro Nexioo Demand"
        ProjetoForm p1 = new ProjetoForm();
        p1.setNome("Quadro Nexioo Demand");
        p1.setDescricao("Área de trabalho Nexioo Demand - Quadro principal de demandas");
        p1.setGradiente("linear-gradient(135deg, #a855f7, #ec4899)");
        projetoService.criar(p1);

        Coluna starterGuide = colunaService.buscarPorId("BACKLOG");
        if (starterGuide != null) {
            starterGuide.setDescricao("Guia Inicial Nexioo Demand");
        } else {
            ColunaForm cf = new ColunaForm();
            cf.setNome("Guia Inicial Nexioo Demand");
            starterGuide = colunaService.criar(cf);
        }


        // Cartão único de demonstração ("teste") na lista inicial
        DemandaForm f1 = new DemandaForm();
        f1.setTitulo("teste");
        f1.setColuna(starterGuide);
        f1.setPrioridade(Prioridade.MEDIA);
        demandaService.criar(f1);

        carregado = true;
        log.info("Projetos e demandas de demonstração criados com sucesso.");
    }
}
