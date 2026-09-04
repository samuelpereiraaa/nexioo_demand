package br.com.nexioo.demand;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.service.AreaTrabalhoService;
import br.com.nexioo.demand.service.AreaTrabalhoServiceImpl;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import br.com.nexioo.demand.service.ProjetoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Popula o repositório com a área de trabalho, projeto e cartões de exemplo conforme a imagem de referência.
 */
@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final DemandaService demandaService;
    private final ColunaService colunaService;
    private final ProjetoService projetoService;
    private final AreaTrabalhoService areaTrabalhoService;
    private volatile boolean carregado = false;

    @Autowired
    public DataLoader(DemandaService demandaService, ColunaService colunaService, ProjetoService projetoService, AreaTrabalhoService areaTrabalhoService) {
        this.demandaService = demandaService;
        this.colunaService = colunaService;
        this.projetoService = projetoService;
        this.areaTrabalhoService = areaTrabalhoService;
    }

    public DataLoader(DemandaService demandaService, ColunaService colunaService, ProjetoService projetoService) {
        this(demandaService, colunaService, projetoService, new AreaTrabalhoServiceImpl(new br.com.nexioo.demand.repository.memory.AreaTrabalhoRepositoryMemory()));
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        if (carregado || !demandaService.listarTodas().isEmpty()) {
            return;
        }

        log.info("Inicializando área de trabalho, projetos e demandas de demonstração...");

        // Área de trabalho inicial
        AreaTrabalho a1 = areaTrabalhoService.obterOuCriarPadrao("samuel@nexioo.com.br");

        // Projeto inicial "Quadro Nexioo Demand"
        ProjetoForm p1 = new ProjetoForm();
        p1.setNome("Quadro Nexioo Demand");
        p1.setDescricao("Área de trabalho Nexioo Demand - Quadro principal de demandas");
        p1.setGradiente("linear-gradient(135deg, #a855f7, #ec4899)");
        p1.setAreaTrabalhoId(a1.getId());
        Projeto proj = projetoService.criar(p1);

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
        f1.setProjetoId(proj.getId());
        demandaService.criar(f1);

        carregado = true;
        log.info("Projetos e demandas de demonstração criados com sucesso.");
    }
}
