package br.com.nexioo.demand.config;

import br.com.nexioo.demand.NexiooDemandApplication;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import br.com.nexioo.demand.repository.ColunaRepository;
import br.com.nexioo.demand.repository.DemandaRepository;
import br.com.nexioo.demand.repository.ProjetoRepository;
import br.com.nexioo.demand.repository.QuadroRecenteRepository;
import br.com.nexioo.demand.repository.memory.AreaTrabalhoRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ColunaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.DemandaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ProjetoRepositoryMemory;
import br.com.nexioo.demand.repository.memory.QuadroRecenteRepositoryMemory;
import br.com.nexioo.demand.repository.supabase.AreaTrabalhoRepositorySupabase;
import br.com.nexioo.demand.repository.supabase.ColunaRepositorySupabase;
import br.com.nexioo.demand.repository.supabase.DemandaRepositorySupabase;
import br.com.nexioo.demand.repository.supabase.ProjetoRepositorySupabase;
import br.com.nexioo.demand.repository.supabase.QuadroRecenteRepositorySupabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileConfigurationTest — Validação de injeção correta de repositórios e regras de profiles")
class ProfileConfigurationTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @DisplayName("Profile 'test'")
    class TestProfileConfiguration {

        @Autowired
        private AreaTrabalhoRepository areaTrabalhoRepository;

        @Autowired
        private ProjetoRepository projetoRepository;

        @Autowired
        private DemandaRepository demandaRepository;

        @Autowired
        private ColunaRepository colunaRepository;

        @Autowired
        private QuadroRecenteRepository quadroRecenteRepository;

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("No profile 'test', os repositórios ativos devem ser exclusivamente implementações em memória isoladas")
        void deveInjetarRepositoriosEmMemoriaNoProfileTest() {
            assertTrue(areaTrabalhoRepository instanceof AreaTrabalhoRepositoryMemory,
                    "Esperado AreaTrabalhoRepositoryMemory no profile de teste");
            assertTrue(projetoRepository instanceof ProjetoRepositoryMemory,
                    "Esperado ProjetoRepositoryMemory no profile de teste");
            assertTrue(demandaRepository instanceof DemandaRepositoryMemory,
                    "Esperado DemandaRepositoryMemory no profile de teste");
            assertTrue(colunaRepository instanceof ColunaRepositoryMemory,
                    "Esperado ColunaRepositoryMemory no profile de teste");
            assertTrue(quadroRecenteRepository instanceof QuadroRecenteRepositoryMemory,
                    "Esperado QuadroRecenteRepositoryMemory no profile de teste");

            assertFalse(areaTrabalhoRepository instanceof AreaTrabalhoRepositorySupabase);
            assertFalse(projetoRepository instanceof ProjetoRepositorySupabase);
            assertFalse(demandaRepository instanceof DemandaRepositorySupabase);
            assertFalse(colunaRepository instanceof ColunaRepositorySupabase);
            assertFalse(quadroRecenteRepository instanceof QuadroRecenteRepositorySupabase);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "supabase.url=https://mock-instance-for-tests.supabase.co",
            "supabase.anon-key=mock-anon-key-strictly-for-unit-testing"
    })
    @ActiveProfiles("prod")
    @DisplayName("Profile 'prod'")
    class ProdProfileConfiguration {

        @Autowired
        private AreaTrabalhoRepository areaTrabalhoRepository;

        @Autowired
        private ProjetoRepository projetoRepository;

        @Autowired
        private DemandaRepository demandaRepository;

        @Autowired
        private ColunaRepository colunaRepository;

        @Autowired
        private QuadroRecenteRepository quadroRecenteRepository;

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        @DisplayName("No profile 'prod', os repositórios ativos devem ser exclusivamente implementações Supabase")
        void deveInjetarRepositoriosSupabaseNoProfileProd() {
            assertTrue(areaTrabalhoRepository instanceof AreaTrabalhoRepositorySupabase,
                    "Esperado AreaTrabalhoRepositorySupabase no profile prod");
            assertTrue(projetoRepository instanceof ProjetoRepositorySupabase,
                    "Esperado ProjetoRepositorySupabase no profile prod");
            assertTrue(demandaRepository instanceof DemandaRepositorySupabase,
                    "Esperado DemandaRepositorySupabase no profile prod");
            assertTrue(colunaRepository instanceof ColunaRepositorySupabase,
                    "Esperado ColunaRepositorySupabase no profile prod");
            assertTrue(quadroRecenteRepository instanceof QuadroRecenteRepositorySupabase,
                    "Esperado QuadroRecenteRepositorySupabase no profile prod");

            // Nenhuma implementação em memória permitida no profile prod
            assertFalse(areaTrabalhoRepository instanceof AreaTrabalhoRepositoryMemory);
            assertFalse(projetoRepository instanceof ProjetoRepositoryMemory);
            assertFalse(demandaRepository instanceof DemandaRepositoryMemory);
            assertFalse(colunaRepository instanceof ColunaRepositoryMemory);
            assertFalse(quadroRecenteRepository instanceof QuadroRecenteRepositoryMemory);
        }

        @Test
        @DisplayName("No profile 'prod', o DataLoader em memória NÃO deve ser instanciado")
        void naoDeveCarregarDataLoaderEmProducao() {
            assertFalse(applicationContext.containsBean("dataLoader"),
                    "DataLoader não deve estar presente no contexto de produção.");
        }
    }

    @Nested
    @DisplayName("Validação de inicialização no profile 'prod' sem variáveis obrigatórias")
    class ProdProfileMissingVars {

        @Test
        @DisplayName("Inicialização com profile 'prod' DEVE falhar se SUPABASE_URL ou SUPABASE_ANON_KEY estiverem ausentes")
        void deveFalharInicializacaoSemVariaveisObrigatorias() {
            assertThrows(Exception.class, () -> {
                new SpringApplicationBuilder(NexiooDemandApplication.class)
                        .profiles("prod")
                        .run();
            }, "A inicialização em produção deve falhar se SUPABASE_URL/SUPABASE_ANON_KEY não forem fornecidas.");
        }
    }
}
