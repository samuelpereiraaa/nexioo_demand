package br.com.nexioo.demand;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.repository.ColunaRepository;
import br.com.nexioo.demand.repository.DemandaRepository;
import br.com.nexioo.demand.repository.ProjetoRepository;
import br.com.nexioo.demand.repository.memory.ColunaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.DemandaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ProjetoRepositoryMemory;
import br.com.nexioo.demand.service.ColunaServiceImpl;
import br.com.nexioo.demand.service.DemandaServiceImpl;
import br.com.nexioo.demand.service.ProjetoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataLoader — Inicialização da Demanda de Demonstração")
class DataLoaderTest {

    private DemandaServiceImpl demandaService;
    private ColunaServiceImpl colunaService;
    private ProjetoServiceImpl projetoService;
    private DataLoader dataLoader;

    @BeforeEach
    void setUp() {
        DemandaRepository demandaRepo = new DemandaRepositoryMemory();
        ColunaRepository colunaRepo = new ColunaRepositoryMemory();
        ProjetoRepository projetoRepo = new ProjetoRepositoryMemory();

        colunaRepo.salvar(new Coluna("A_FAZER", "A Fazer", 2));
        colunaRepo.salvar(new Coluna("EM_ANDAMENTO", "Em Andamento", 3));
        colunaRepo.salvar(new Coluna("CONCLUIDO", "Concluído", 4));

        colunaService = new ColunaServiceImpl(colunaRepo);
        demandaService = new DemandaServiceImpl(demandaRepo, colunaService);
        projetoService = new ProjetoServiceImpl(projetoRepo);

        dataLoader = new DataLoader(demandaService, colunaService, projetoService);
    }


    @Test
    @DisplayName("Deve criar exatamente uma demanda de demonstração no DataLoader")
    void deveCriarExatamenteUmaDemandaDeDemonstracao() {
        dataLoader.run(new DefaultApplicationArguments(new String[0]));

        List<Demanda> todas = demandaService.listarTodas();
        assertEquals(1, todas.size(), "Deve existir exatamente 1 demanda inicial.");
        assertEquals("teste", todas.get(0).getTitulo());
    }

    @Test
    @DisplayName("Inicialização do DataLoader deve ser idempotente ao executar múltiplas vezes")
    void inicializacaoDeveSerIdempotente() {
        dataLoader.run(new DefaultApplicationArguments(new String[0]));
        dataLoader.run(new DefaultApplicationArguments(new String[0]));

        List<Demanda> todas = demandaService.listarTodas();
        assertEquals(1, todas.size(), "A reinicialização do DataLoader não pode duplicar demandas.");
    }

    @Test
    @DisplayName("Somente a lista inicial deve possuir contador 1 e as outras contador 0")
    void contadoresPorListaDevemEstarCorretos() {
        dataLoader.run(new DefaultApplicationArguments(new String[0]));

        Map<Coluna, List<Demanda>> porColuna = demandaService.listarPorColuna();
        int totalComDemandas = 0;
        int totalSemDemandas = 0;

        for (Map.Entry<Coluna, List<Demanda>> entry : porColuna.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                assertEquals(1, entry.getValue().size(), "A lista com demanda deve conter exatamente 1 item.");
                totalComDemandas++;
            } else {
                assertEquals(0, entry.getValue().size(), "Lista sem demanda deve conter 0 itens.");
                totalSemDemandas++;
            }
        }

        assertEquals(1, totalComDemandas, "Exatamente uma lista deve ter demandas.");
        assertTrue(totalSemDemandas >= 1, "As outras listas devem estar vazias.");
    }
}
