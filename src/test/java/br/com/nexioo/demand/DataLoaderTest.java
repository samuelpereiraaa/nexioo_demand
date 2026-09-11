package br.com.nexioo.demand;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import br.com.nexioo.demand.repository.ColunaRepository;
import br.com.nexioo.demand.repository.DemandaRepository;
import br.com.nexioo.demand.repository.ProjetoRepository;
import br.com.nexioo.demand.repository.memory.AreaTrabalhoRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ColunaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.DemandaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ProjetoRepositoryMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataLoader — Inicialização da Demanda de Demonstração")
class DataLoaderTest {

    private DemandaRepository demandaRepo;
    private ColunaRepository colunaRepo;
    private ProjetoRepository projetoRepo;
    private AreaTrabalhoRepository areaRepo;
    private DataLoader dataLoader;

    @BeforeEach
    void setUp() {
        demandaRepo = new DemandaRepositoryMemory();
        colunaRepo = new ColunaRepositoryMemory();
        projetoRepo = new ProjetoRepositoryMemory();
        areaRepo = new AreaTrabalhoRepositoryMemory();

        dataLoader = new DataLoader(demandaRepo, colunaRepo, projetoRepo, areaRepo);
    }

    @Test
    @DisplayName("Deve criar exatamente uma demanda de demonstração no DataLoader")
    void deveCriarExatamenteUmaDemandaDeDemonstracao() {
        dataLoader.run(new DefaultApplicationArguments(new String[0]));

        List<Demanda> todas = demandaRepo.listarPorUsuario(DataLoader.DEMO_USER_ID);
        assertEquals(1, todas.size(), "Deve existir exatamente 1 demanda inicial.");
        assertEquals("teste", todas.get(0).getTitulo());
    }

    @Test
    @DisplayName("Inicialização do DataLoader deve ser idempotente ao executar múltiplas vezes")
    void inicializacaoDeveSerIdempotente() {
        dataLoader.run(new DefaultApplicationArguments(new String[0]));
        dataLoader.run(new DefaultApplicationArguments(new String[0]));

        List<Demanda> todas = demandaRepo.listarPorUsuario(DataLoader.DEMO_USER_ID);
        assertEquals(1, todas.size(), "A reinicialização do DataLoader não pode duplicar demandas.");
    }

    @Test
    @DisplayName("Somente a lista inicial deve possuir contador 1 e as outras contador 0")
    void contadoresPorListaDevemEstarCorretos() {
        dataLoader.run(new DefaultApplicationArguments(new String[0]));

        List<Projeto> projetos = projetoRepo.listarPorUsuario(DataLoader.DEMO_USER_ID);
        assertFalse(projetos.isEmpty());
        Projeto proj = projetos.get(0);

        List<Coluna> colunas = colunaRepo.listarPorProjetoEUsuario(proj.getId(), DataLoader.DEMO_USER_ID);
        List<Demanda> demandas = demandaRepo.listarPorProjetoEUsuario(proj.getId(), DataLoader.DEMO_USER_ID);

        assertEquals(4, colunas.size(), "Devem existir 4 colunas para o projeto demo.");
        assertEquals(1, demandas.size(), "Deve existir exatamente 1 demanda.");
        assertEquals("BACKLOG", demandas.get(0).getColuna().getCodigo());
    }
}
