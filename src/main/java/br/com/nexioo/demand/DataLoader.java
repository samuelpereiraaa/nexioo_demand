package br.com.nexioo.demand;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import br.com.nexioo.demand.repository.ColunaRepository;
import br.com.nexioo.demand.repository.DemandaRepository;
import br.com.nexioo.demand.repository.ProjetoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Popula o repositório com a área de trabalho, projeto e cartões de exemplo conforme a imagem de referência.
 * Ativo apenas no profile dev-memory com identidade demo particionada.
 */
@Component
@Profile({"dev-memory"})
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    public static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String DEMO_USER_EMAIL = "samuel@nexioo.com.br";

    private final DemandaRepository demandaRepository;
    private final ColunaRepository colunaRepository;
    private final ProjetoRepository projetoRepository;
    private final AreaTrabalhoRepository areaTrabalhoRepository;
    private volatile boolean carregado = false;

    @Autowired
    public DataLoader(DemandaRepository demandaRepository,
                      ColunaRepository colunaRepository,
                      ProjetoRepository projetoRepository,
                      AreaTrabalhoRepository areaTrabalhoRepository) {
        this.demandaRepository = demandaRepository;
        this.colunaRepository = colunaRepository;
        this.projetoRepository = projetoRepository;
        this.areaTrabalhoRepository = areaTrabalhoRepository;
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        if (carregado || !demandaRepository.listarPorUsuario(DEMO_USER_ID).isEmpty()) {
            return;
        }

        log.info("Inicializando área de trabalho, projetos e demandas de demonstração...");

        // Área de trabalho inicial
        AreaTrabalho a1 = new AreaTrabalho();
        a1.setId(UUID.randomUUID());
        a1.setNome("Área de trabalho Nexioo Demand");
        a1.setInicial("Á");
        a1.setUsuarioProprietario(DEMO_USER_EMAIL);
        a1.setUsuarioId(DEMO_USER_ID);
        areaTrabalhoRepository.salvar(a1);

        // Projeto inicial "Quadro Nexioo Demand"
        Projeto proj = new Projeto();
        proj.setId(UUID.randomUUID());
        proj.setNome("Quadro Nexioo Demand");
        proj.setDescricao("Área de trabalho Nexioo Demand - Quadro principal de demandas");
        proj.setGradiente("linear-gradient(135deg, #a855f7, #ec4899)");
        proj.setAreaTrabalhoId(a1.getId());
        proj.setUsuarioProprietario(DEMO_USER_EMAIL);
        proj.setUsuarioId(DEMO_USER_ID);
        projetoRepository.salvar(proj);

        // 4 Colunas padrão vinculadas ao projeto e usuário
        Coluna colBacklog = new Coluna(UUID.randomUUID(), proj.getId(), DEMO_USER_ID, "BACKLOG", "Guia Inicial Nexioo Demand", 1);
        Coluna colAFazer = new Coluna(UUID.randomUUID(), proj.getId(), DEMO_USER_ID, "A_FAZER", "A Fazer", 2);
        Coluna colEmAndamento = new Coluna(UUID.randomUUID(), proj.getId(), DEMO_USER_ID, "EM_ANDAMENTO", "Em Andamento", 3);
        Coluna colConcluido = new Coluna(UUID.randomUUID(), proj.getId(), DEMO_USER_ID, "CONCLUIDO", "Concluído", 4);

        colunaRepository.salvar(colBacklog);
        colunaRepository.salvar(colAFazer);
        colunaRepository.salvar(colEmAndamento);
        colunaRepository.salvar(colConcluido);

        // Cartão único de demonstração ("teste") na lista inicial
        Demanda f1 = new Demanda();
        f1.setId(UUID.randomUUID());
        f1.setTitulo("teste");
        f1.setDescricao("Cartão de demonstração inicial.");
        f1.setColuna(colBacklog);
        f1.setPrioridade(Prioridade.MEDIA);
        f1.setProjetoId(proj.getId());
        f1.setUsuarioId(DEMO_USER_ID);
        f1.setPosicao(0);
        f1.setCriadoEm(LocalDateTime.now());
        f1.setAtualizadoEm(LocalDateTime.now());
        demandaRepository.salvar(f1);

        carregado = true;
        log.info("Projetos e demandas de demonstração criados com sucesso.");
    }
}
