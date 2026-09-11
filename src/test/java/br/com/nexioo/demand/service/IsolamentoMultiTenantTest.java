package br.com.nexioo.demand.service;

import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.memory.AreaTrabalhoRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ColunaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.DemandaRepositoryMemory;
import br.com.nexioo.demand.repository.memory.ProjetoRepositoryMemory;
import br.com.nexioo.demand.repository.memory.QuadroRecenteRepositoryMemory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes obrigatórios de isolamento multi-tenant entre dois usuários independentes (A e B).
 * Comprova que informações privadas de uma conta jamais aparecem, são alteradas ou são excluídas por outra conta.
 */
class IsolamentoMultiTenantTest {

    private UserContext userContext;
    private AreaTrabalhoRepositoryMemory areaTrabalhoRepository;
    private AreaTrabalhoServiceImpl areaTrabalhoService;
    private ProjetoRepositoryMemory projetoRepository;
    private QuadroRecenteRepositoryMemory quadroRecenteRepository;
    private ProjetoServiceImpl projetoService;
    private DemandaRepositoryMemory demandaRepository;
    private ColunaRepositoryMemory colunaRepository;
    private ColunaServiceImpl colunaService;
    private DemandaServiceImpl demandaService;

    private final UUID userAId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final String userAEmail = "usuario.a@nexioo.com.br";

    private final UUID userBId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final String userBEmail = "usuario.b@nexioo.com.br";

    @BeforeEach
    void setUp() {
        userContext = new UserContext();
        areaTrabalhoRepository = new AreaTrabalhoRepositoryMemory();
        areaTrabalhoService = new AreaTrabalhoServiceImpl(areaTrabalhoRepository, userContext);
        projetoRepository = new ProjetoRepositoryMemory();
        quadroRecenteRepository = new QuadroRecenteRepositoryMemory();
        colunaRepository = new ColunaRepositoryMemory();
        colunaService = new ColunaServiceImpl(colunaRepository, userContext, projetoRepository);
        demandaRepository = new DemandaRepositoryMemory();

        projetoService = new ProjetoServiceImpl(projetoRepository, userContext, quadroRecenteRepository, areaTrabalhoRepository);
        demandaService = new DemandaServiceImpl(demandaRepository, colunaService, userContext, projetoRepository);
    }

    @AfterEach
    void tearDown() {
        userContext.limpar();
    }

    private void autenticarComo(UUID userId, String email) {
        userContext.limpar();
        userContext.inicializar(userId, email, "Usuário " + email, "fake-jwt-token-" + userId);
    }

    @Test
    @DisplayName("Cenário 1 e 2: Usuário A cria e visualiza quadro -> aparece em seus recentes")
    void testUsuarioACriaEVisualizaQuadro() {
        autenticarComo(userAId, userAEmail);

        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Quadro do Usuário A");
        formA.setDescricao("Projetos confidenciais A");
        Projeto projA = projetoService.criar(formA);

        assertNotNull(projA.getId());
        assertEquals(userAId, projA.getUsuarioId());

        List<Projeto> recentesA = projetoService.listarRecentes();
        assertEquals(1, recentesA.size(), "O quadro de A deve estar nos recentes de A");
        assertEquals("Quadro do Usuário A", recentesA.get(0).getNome());
    }

    @Test
    @DisplayName("Cenário 3, 4 e 5: Usuário A desloga, Usuário B entra -> B NÃO vê quadro nem recentes de A")
    void testUsuarioBEntraENaoVeRecentesNemQuadrosDeA() {
        // A cria seu quadro
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Quadro Privado A");
        Projeto projA = projetoService.criar(formA);

        // A desloga
        userContext.limpar();
        assertFalse(userContext.isAutenticado());

        // B faz login
        autenticarComo(userBId, userBEmail);

        // B consulta recentes -> Deve ser vazia (ZERO vazamento de A, sem fallback global todos.get(0))
        List<Projeto> recentesB = projetoService.listarRecentes();
        assertTrue(recentesB.isEmpty(), "Usuário B recém-criado não pode ver os recentes do Usuário A");

        // B consulta todos os seus projetos -> Deve ser vazia
        List<Projeto> todosB = projetoService.listarTodos();
        assertTrue(todosB.isEmpty(), "Usuário B não pode ver os projetos do Usuário A");
    }

    @Test
    @DisplayName("Cenário 6 e 7: B cria seu próprio quadro -> A não vê o quadro de B")
    void testIsolamentoBidirecionalDeProjetos() {
        // A cria seu quadro
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Quadro Alpha (A)");
        projetoService.criar(formA);

        // B cria seu quadro
        autenticarComo(userBId, userBEmail);
        ProjetoForm formB = new ProjetoForm();
        formB.setNome("Quadro Beta (B)");
        projetoService.criar(formB);

        // B vê apenas o seu
        List<Projeto> listaB = projetoService.listarTodos();
        assertEquals(1, listaB.size());
        assertEquals("Quadro Beta (B)", listaB.get(0).getNome());

        // A faz login novamente
        autenticarComo(userAId, userAEmail);
        List<Projeto> listaA = projetoService.listarTodos();
        assertEquals(1, listaA.size());
        assertEquals("Quadro Alpha (A)", listaA.get(0).getNome());
    }

    @Test
    @DisplayName("Cenário 8 e 9: B tenta acessar diretamente o ID do quadro de A via URL/API -> Rejeitado")
    void testTentativaAcessoCruzadoDiretoPorIdRejeitada() {
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Quadro Secreto A");
        Projeto projA = projetoService.criar(formA);
        UUID idProjetoA = projA.getId();

        // B tenta acessar o ID do projeto de A
        autenticarComo(userBId, userBEmail);

        assertThrows(IllegalArgumentException.class, () -> {
            projetoService.buscarPorId(idProjetoA);
        }, "Tentativa de acessar quadro de outra conta deve ser terminantemente rejeitada");
    }

    @Test
    @DisplayName("Cenário 10: B tenta excluir o quadro de A -> Operação rejeitada e quadro de A permanece intacto")
    void testTentativaExclusaoCruzadaRejeitada() {
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Quadro Indestrutível por B");
        Projeto projA = projetoService.criar(formA);
        UUID idProjetoA = projA.getId();

        // B tenta excluir o projeto de A
        autenticarComo(userBId, userBEmail);
        projetoService.excluir(idProjetoA);

        // Verifica que o projeto de A continua intacto
        autenticarComo(userAId, userAEmail);
        Projeto projVerificado = projetoService.buscarPorId(idProjetoA);
        assertNotNull(projVerificado);
        assertEquals("Quadro Indestrutível por B", projVerificado.getNome());
    }

    @Test
    @DisplayName("Cenário 11: B tenta registrar visualização ou injetar demanda no quadro de A -> Rejeitado")
    void testTentativaAdulteracaoVisualizacaoEDemandaRejeitada() {
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Quadro Auditado A");
        Projeto projA = projetoService.criar(formA);

        // B tenta marcar o quadro de A como recente em sua própria conta
        autenticarComo(userBId, userBEmail);
        projetoService.registrarVisualizacao(projA.getUuid());

        // Os recentes de B continuam vazios porque B não é dono do quadro
        List<Projeto> recentesB = projetoService.listarRecentes();
        assertTrue(recentesB.isEmpty(), "Usuário B não pode adicionar quadro de A aos seus recentes");
    }

    @Test
    @DisplayName("Cenário 12: Preservação estrita da ordenação cronológica decrescente dos recentes por usuário")
    void testOrdenacaoCronologicaRecentesPorUsuario() throws InterruptedException {
        autenticarComo(userAId, userAEmail);

        ProjetoForm f1 = new ProjetoForm();
        f1.setNome("Projeto 1");
        Projeto p1 = projetoService.criar(f1);

        Thread.sleep(10);

        ProjetoForm f2 = new ProjetoForm();
        f2.setNome("Projeto 2");
        Projeto p2 = projetoService.criar(f2);

        Thread.sleep(10);

        // A visualiza p1 novamente (tornando-o o mais recente)
        projetoService.registrarVisualizacao(p1.getUuid());

        List<Projeto> recentes = projetoService.listarRecentes();
        assertEquals(2, recentes.size());
        assertEquals("Projeto 1", recentes.get(0).getNome(), "O projeto visualizado por último deve ser o primeiro");
        assertEquals("Projeto 2", recentes.get(1).getNome());
    }

    @Test
    @DisplayName("Cenário 13: B tenta associar demanda a um projeto de A -> Rejeitado por violação da cadeia de propriedade")
    void testTentativaInjecaoDemandaEmProjetoAlheioRejeitada() {
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Projeto Exclusivo A");
        Projeto projA = projetoService.criar(formA);

        // B autentica e tenta criar demanda apontando para o projeto de A
        autenticarComo(userBId, userBEmail);
        DemandaForm dForm = new DemandaForm();
        dForm.setTitulo("Demanda Injetada por B");
        dForm.setColuna(Coluna.BACKLOG);
        dForm.setPrioridade(Prioridade.MEDIA);
        dForm.setProjetoId(projA.getId());

        // A criação deve ser rejeitada com SecurityException ou falha de autorização
        assertThrows(SecurityException.class, () -> {
            demandaService.criar(dForm);
        }, "Usuário B não pode associar demandas a projetos de outros usuários");
    }

    @Test
    @DisplayName("Cenário 14: B tenta acessar, alterar ou excluir demanda de A -> Rejeitado e isolado")
    void testIsolamentoCompletoDeDemandasEntreContas() {
        // Usuário A cria seu projeto e sua demanda
        autenticarComo(userAId, userAEmail);
        ProjetoForm formA = new ProjetoForm();
        formA.setNome("Projeto Base A");
        Projeto projA = projetoService.criar(formA);

        DemandaForm dFormA = new DemandaForm();
        dFormA.setTitulo("Demanda Confidencial A");
        dFormA.setColuna(Coluna.BACKLOG);
        dFormA.setPrioridade(Prioridade.ALTA);
        dFormA.setProjetoId(projA.getId());
        Demanda demandaA = demandaService.criar(dFormA);
        UUID idDemandaA = demandaA.getId();

        // Usuário B autentica
        autenticarComo(userBId, userBEmail);

        // B tenta buscar a demanda de A por ID direto -> Rejeitado
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.buscarPorId(idDemandaA);
        }, "Usuário B não pode obter detalhes da demanda de A");

        // B tenta alterar a descrição da demanda de A -> Rejeitado
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.atualizarDescricao(idDemandaA, "Hackeado por B");
        });

        // B tenta comentar na demanda de A -> Rejeitado
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.adicionarComentario(idDemandaA, "Comentário invasivo", "Hacker");
        });

        // B consulta lista geral de demandas -> Não contém a demanda de A
        List<Demanda> demandasB = demandaService.listarTodas();
        assertTrue(demandasB.isEmpty(), "Demandas de B devem estar vazias");

        // Usuário A confere que sua demanda continua íntegra
        autenticarComo(userAId, userAEmail);
        Demanda demandaAposTentativa = demandaService.buscarPorId(idDemandaA);
        assertNotNull(demandaAposTentativa);
        assertEquals("Demanda Confidencial A", demandaAposTentativa.getTitulo());
    }

    @Test
    @DisplayName("Cenário 15: B tenta excluir a demanda de A -> Rejeitado e demanda permanece intacta")
    void testUsuarioBNaoPodeExcluirDemandaDeA() {
        autenticarComo(userAId, userAEmail);
        Projeto projA = projetoService.criar(new ProjetoForm("Proj A", "Desc", null, null));
        DemandaForm dFormA = new DemandaForm();
        dFormA.setTitulo("Demanda Protegida A");
        dFormA.setProjetoId(projA.getId());
        Demanda demandaA = demandaService.criar(dFormA);
        UUID idDemandaA = demandaA.getId();

        // B autentica e tenta excluir a demanda de A
        autenticarComo(userBId, userBEmail);
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.excluir(idDemandaA);
        }, "Usuário B não pode excluir a demanda de A");

        // A confere que sua demanda continua existindo normalmente
        autenticarComo(userAId, userAEmail);
        Demanda demandaAindaExiste = demandaService.buscarPorId(idDemandaA);
        assertNotNull(demandaAindaExiste);
        assertEquals("Demanda Protegida A", demandaAindaExiste.getTitulo());
    }

    @Test
    @DisplayName("Cenário 16: B tenta anexar arquivo ou manipular checklists na demanda de A -> Rejeitado")
    void testUsuarioBNaoPodeAlterarSubEntidadesDemandaDeA() {
        autenticarComo(userAId, userAEmail);
        Projeto projA = projetoService.criar(new ProjetoForm("Proj A Sub", "Desc", null, null));
        DemandaForm dFormA = new DemandaForm();
        dFormA.setTitulo("Demanda Subentidades A");
        dFormA.setProjetoId(projA.getId());
        Demanda demandaA = demandaService.criar(dFormA);
        UUID idDemandaA = demandaA.getId();

        autenticarComo(userBId, userBEmail);

        // B tenta anexar arquivo em demanda de A
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.adicionarAnexo(idDemandaA, "virus.exe", "https://malicious.url/virus.exe");
        });

        // B tenta adicionar checklist em demanda de A
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.adicionarChecklist(idDemandaA, "Checklist Invasiva");
        });

        // B tenta adicionar etiqueta em demanda de A
        assertThrows(br.com.nexioo.demand.exception.DemandaNaoEncontradaException.class, () -> {
            demandaService.adicionarEtiqueta(idDemandaA, "Invasao", "#ff0000");
        });

        // A confere que nenhum anexo ou checklist foi inserido
        autenticarComo(userAId, userAEmail);
        Demanda demandaLimpa = demandaService.buscarPorId(idDemandaA);
        assertTrue(demandaLimpa.getAnexos().isEmpty());
        assertTrue(demandaLimpa.getChecklists().isEmpty());
        assertTrue(demandaLimpa.getEtiquetas().isEmpty());
    }

    @Test
    @DisplayName("Cenário 17: B tenta associar projeto a uma Área de Trabalho de A -> Rejeitado por violação de cadeia")
    void testUsuarioBNaoPodeAssociarProjetoAAreaDeTrabalhoDeA() {
        autenticarComo(userAId, userAEmail);
        br.com.nexioo.demand.model.AreaTrabalho areaA = areaTrabalhoService.criar("Área Restrita A", userAEmail);
        assertNotNull(areaA.getId());

        // B tenta criar um projeto associando a areaTrabalhoId de A
        autenticarComo(userBId, userBEmail);
        ProjetoForm formB = new ProjetoForm();
        formB.setNome("Projeto Invasor B");
        formB.setAreaTrabalhoId(areaA.getId());

        assertThrows(SecurityException.class, () -> {
            projetoService.criar(formB);
        }, "Usuário B não pode associar projeto a uma área de outro usuário");
    }

    @Test
    @DisplayName("Cenário 18: B tenta buscar, editar ou excluir Área de Trabalho de A -> Rejeitado")
    void testUsuarioBNaoPodeAcessarNemExcluirAreaDeTrabalhoDeA() {
        autenticarComo(userAId, userAEmail);
        br.com.nexioo.demand.model.AreaTrabalho areaA = areaTrabalhoService.criar("Área Soberana A", userAEmail);

        autenticarComo(userBId, userBEmail);

        // B tenta buscar a área de A
        assertThrows(IllegalArgumentException.class, () -> {
            areaTrabalhoService.buscarPorId(areaA.getId());
        });

        // B tenta editar a área de A
        assertThrows(IllegalArgumentException.class, () -> {
            areaTrabalhoService.editar(areaA.getId(), "Área Hackeada", userBEmail);
        });

        // B tenta excluir a área de A
        areaTrabalhoService.excluir(areaA.getId(), userBEmail);

        // A confere que sua área continua intacta
        autenticarComo(userAId, userAEmail);
        br.com.nexioo.demand.model.AreaTrabalho areaVerificada = areaTrabalhoService.buscarPorId(areaA.getId());
        assertNotNull(areaVerificada);
        assertEquals("Área Soberana A", areaVerificada.getNome());
    }

    @Test
    @DisplayName("Cenário 19: B tenta criar ou excluir coluna em projeto de A -> Rejeitado")
    void testUsuarioBNaoPodeCriarColunaEmProjetoDeA() {
        autenticarComo(userAId, userAEmail);
        Projeto projA = projetoService.criar(new ProjetoForm("Projeto Colunas A", "Desc", null, null));

        // B tenta criar uma coluna no projeto de A
        autenticarComo(userBId, userBEmail);
        br.com.nexioo.demand.dto.ColunaForm cForm = new br.com.nexioo.demand.dto.ColunaForm();
        cForm.setNome("Coluna Injetada por B");
        cForm.setProjetoId(projA.getId());

        assertThrows(SecurityException.class, () -> {
            colunaService.criar(cForm);
        }, "Usuário B não pode criar colunas no projeto de A");
    }
}
