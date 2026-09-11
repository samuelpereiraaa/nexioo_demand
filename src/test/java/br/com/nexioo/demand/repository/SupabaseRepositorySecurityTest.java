package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.client.SupabaseRestClient;
import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.repository.supabase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SupabaseRepositorySecurityTest — Validação de Imutabilidade da Identidade do Usuário nos Repositórios Supabase")
class SupabaseRepositorySecurityTest {

    private UserContext userContext;
    private SupabaseRestClient client;

    private AreaTrabalhoRepositorySupabase areaRepo;
    private ProjetoRepositorySupabase projetoRepo;
    private ColunaRepositorySupabase colunaRepo;
    private DemandaRepositorySupabase demandaRepo;
    private QuadroRecenteRepositorySupabase quadroRecenteRepo;

    private final UUID authenticatedUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID foreignUserId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        userContext = new UserContext();
        userContext.inicializar(authenticatedUserId, "user@test.com", "Test User", "valid-jwt-token");

        client = Mockito.mock(SupabaseRestClient.class);

        areaRepo = new AreaTrabalhoRepositorySupabase(client, userContext);
        projetoRepo = new ProjetoRepositorySupabase(client, userContext);
        colunaRepo = new ColunaRepositorySupabase(client, userContext);
        demandaRepo = new DemandaRepositorySupabase(client, userContext);
        quadroRecenteRepo = new QuadroRecenteRepositorySupabase(client, userContext);
    }

    @Test
    @DisplayName("AreaTrabalhoRepositorySupabase: Rejeita usuarioId divergente com SecurityException")
    void areaRepoRejeitaUsuarioDivergente() {
        UUID areaId = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> areaRepo.listarPorUsuario(foreignUserId));
        assertThrows(SecurityException.class, () -> areaRepo.buscarPorIdEUsuario(areaId, foreignUserId));
        assertThrows(SecurityException.class, () -> areaRepo.excluirPorIdEUsuario(areaId, foreignUserId));
    }

    @Test
    @DisplayName("ProjetoRepositorySupabase: Rejeita usuarioId divergente com SecurityException")
    void projetoRepoRejeitaUsuarioDivergente() {
        UUID projId = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> projetoRepo.listarPorUsuario(foreignUserId));
        assertThrows(SecurityException.class, () -> projetoRepo.buscarPorIdEUsuario(projId, foreignUserId));
        assertThrows(SecurityException.class, () -> projetoRepo.excluirPorIdEUsuario(projId, foreignUserId));
    }

    @Test
    @DisplayName("ColunaRepositorySupabase: Rejeita usuarioId divergente com SecurityException")
    void colunaRepoRejeitaUsuarioDivergente() {
        String colId = UUID.randomUUID().toString();
        assertThrows(SecurityException.class, () -> colunaRepo.listarPorProjetoEUsuario(UUID.randomUUID(), foreignUserId));
        assertThrows(SecurityException.class, () -> colunaRepo.buscarPorIdEUsuario(colId, foreignUserId));
        assertThrows(SecurityException.class, () -> colunaRepo.excluirPorIdEProjetoEUsuario(colId, UUID.randomUUID(), foreignUserId));
    }

    @Test
    @DisplayName("DemandaRepositorySupabase: Rejeita usuarioId divergente com SecurityException")
    void demandaRepoRejeitaUsuarioDivergente() {
        UUID demId = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> demandaRepo.listarPorUsuario(foreignUserId));
        assertThrows(SecurityException.class, () -> demandaRepo.buscarPorIdEUsuario(demId, foreignUserId));
        assertThrows(SecurityException.class, () -> demandaRepo.excluirPorIdEUsuario(demId, foreignUserId));
    }

    @Test
    @DisplayName("QuadroRecenteRepositorySupabase: Rejeita usuarioId divergente com SecurityException")
    void quadroRecenteRepoRejeitaUsuarioDivergente() {
        UUID qId = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> quadroRecenteRepo.registrarVisualizacao(foreignUserId, qId));
        assertThrows(SecurityException.class, () -> quadroRecenteRepo.listarQuadrosRecentesIds(foreignUserId, 10));
        assertThrows(SecurityException.class, () -> quadroRecenteRepo.removerRecente(foreignUserId, qId));
    }
}
