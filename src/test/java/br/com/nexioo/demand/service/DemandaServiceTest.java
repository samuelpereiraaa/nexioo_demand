package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.exception.DemandaNaoEncontradaException;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.repository.DemandaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemandaService — regras de negócio")
class DemandaServiceTest {

    @Mock
    private DemandaRepository demandaRepository;

    @Mock
    private ColunaService colunaService;

    @Mock
    private br.com.nexioo.demand.config.UserContext userContext;

    @Mock
    private br.com.nexioo.demand.repository.ProjetoRepository projetoRepository;

    @InjectMocks
    private DemandaServiceImpl demandaService;

    private DemandaForm formValido;

    private static final java.util.UUID USUARIO_ID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final java.util.UUID ID_1 = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final java.util.UUID ID_2 = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final java.util.UUID ID_3 = java.util.UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final java.util.UUID ID_99 = java.util.UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final java.util.UUID PROJETO_100 = java.util.UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final java.util.UUID PROJETO_200 = java.util.UUID.fromString("00000000-0000-0000-0000-000000000200");

    @BeforeEach
    void setUp() {
        lenient().when(userContext.isAutenticado()).thenReturn(true);
        lenient().when(userContext.getUsuarioId()).thenReturn(USUARIO_ID);
        lenient().when(userContext.requireUsuarioId()).thenReturn(USUARIO_ID);
        lenient().when(userContext.getEmail()).thenReturn("teste@nexioo.com");
        lenient().when(userContext.getNome()).thenReturn("Usuário Teste");

        formValido = new DemandaForm();
        formValido.setTitulo("Implementar módulo de login");
        formValido.setDescricao("Criar tela com autenticação por e-mail e senha.");
        formValido.setColuna(Coluna.BACKLOG);
        formValido.setPrioridade(Prioridade.ALTA);
        formValido.setResponsavel("Maria Santos");
        formValido.setPrazo(LocalDate.now().plusDays(7));
        formValido.setProjetoId(PROJETO_100);

        br.com.nexioo.demand.model.Projeto proj = new br.com.nexioo.demand.model.Projeto();
        proj.setId(PROJETO_100);
        proj.setUsuarioId(USUARIO_ID);
        lenient().when(projetoRepository.buscarPorIdEUsuario(eq(PROJETO_100), eq(USUARIO_ID))).thenReturn(Optional.of(proj));
    }

    @Test
    @DisplayName("deve criar demanda com todos os campos preenchidos corretamente")
    void deveCriarDemandaComTodosOsCampos() {
        Demanda demandaSalva = new Demanda();
        demandaSalva.setId(ID_1);
        when(demandaRepository.salvar(any(Demanda.class))).thenReturn(demandaSalva);

        Demanda resultado = demandaService.criar(formValido);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(ID_1);
        verify(demandaRepository, times(1)).salvar(any(Demanda.class));
    }

    @Test
    @DisplayName("deve lançar DemandaNaoEncontradaException ao buscar ID inexistente")
    void deveLancarExcecaoAoBuscarIdInexistente() {
        when(demandaRepository.buscarPorIdEUsuario(ID_99, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> demandaService.buscarPorId(ID_99))
                .isInstanceOf(DemandaNaoEncontradaException.class);
    }

    @Test
    @DisplayName("deve retornar mapa com todas as colunas, incluindo as sem demandas")
    void deveListarPorColunaComTodasAsColunas() {
        when(colunaService.listarTodas()).thenReturn(List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO));
        Demanda d1 = demandaComColuna(Coluna.BACKLOG);
        Demanda d2 = demandaComColuna(Coluna.A_FAZER);
        when(demandaRepository.listarPorUsuario(USUARIO_ID)).thenReturn(Arrays.asList(d1, d2));

        Map<Coluna, List<Demanda>> resultado = demandaService.listarPorColuna();

        assertThat(resultado).containsKeys(
                Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO);
        assertThat(resultado.get(Coluna.BACKLOG)).hasSize(1);
        assertThat(resultado.get(Coluna.EM_ANDAMENTO)).isEmpty();
    }

    @Test
    @DisplayName("deve filtrar demandas pelo termo presente no título")
    void deveFiltrarPeloTitulo() {
        when(colunaService.listarTodas()).thenReturn(List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO));
        Demanda d1 = demandaComTitulo("Implementar login", Coluna.BACKLOG);
        Demanda d2 = demandaComTitulo("Criar relatório", Coluna.A_FAZER);
        when(demandaRepository.listarPorUsuario(USUARIO_ID)).thenReturn(Arrays.asList(d1, d2));

        Map<Coluna, List<Demanda>> resultado = demandaService.filtrar("login", null, null);

        assertThat(resultado.get(Coluna.BACKLOG)).hasSize(1);
        assertThat(resultado.get(Coluna.A_FAZER)).isEmpty();
    }

    @Test
    @DisplayName("deve filtrar demandas pela prioridade")
    void deveFiltrarPelaPrioridade() {
        when(colunaService.listarTodas()).thenReturn(List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO));
        Demanda d1 = demandaComColuna(Coluna.BACKLOG);
        d1.setPrioridade(Prioridade.ALTA);
        Demanda d2 = demandaComColuna(Coluna.A_FAZER);
        d2.setPrioridade(Prioridade.BAIXA);
        when(demandaRepository.listarPorUsuario(USUARIO_ID)).thenReturn(List.of(d1, d2));

        Map<Coluna, List<Demanda>> resultado = demandaService.filtrar(null, Prioridade.ALTA, null);

        assertThat(resultado.get(Coluna.BACKLOG)).hasSize(1);
        assertThat(resultado.get(Coluna.A_FAZER)).isEmpty();
    }

    @Test
    @DisplayName("deve alterar a coluna da demanda e persistir a mudança")
    void deveAlterarColunaComSucesso() {
        Demanda demanda = demandaComColuna(Coluna.BACKLOG);
        demanda.setId(ID_1);
        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.alterarColuna(ID_1, Coluna.EM_ANDAMENTO);

        assertThat(resultado.getColuna()).isEqualTo(Coluna.EM_ANDAMENTO);
        assertThat(resultado.getAtualizadoEm()).isNotNull();
        verify(demandaRepository).salvar(demanda);
    }

    @Test
    @DisplayName("deve excluir demanda existente com sucesso")
    void deveExcluirDemandaExistente() {
        Demanda d = demandaComColuna(Coluna.BACKLOG);
        d.setId(ID_1);
        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(d));

        demandaService.excluir(ID_1);

        verify(demandaRepository).excluirPorIdEUsuario(ID_1, USUARIO_ID);
    }

    @Test
    @DisplayName("deve lançar exceção ao tentar excluir demanda inexistente")
    void deveLancarExcecaoAoExcluirDemandaInexistente() {
        when(demandaRepository.buscarPorIdEUsuario(ID_99, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> demandaService.excluir(ID_99))
                .isInstanceOf(DemandaNaoEncontradaException.class);

        verify(demandaRepository, never()).excluirPorIdEUsuario(any(), any());
    }

    @Test
    @DisplayName("deve alternar apenas a conclusão sem alterar a coluna/lista da demanda")
    void deveAlternarApenasConclusaoSemMoverColuna() {
        Demanda demanda = demandaComColuna(Coluna.A_FAZER);
        demanda.setConcluido(false);
        demanda.setId(ID_1);
        demanda.setUsuarioId(USUARIO_ID);
        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.alternarConclusao(ID_1);

        assertThat(resultado.isConcluido()).isTrue();
        assertThat(resultado.getColuna()).isEqualTo(Coluna.A_FAZER);

        Demanda reaberta = demandaService.alternarConclusao(ID_1);
        assertThat(reaberta.isConcluido()).isFalse();
        assertThat(reaberta.getColuna()).isEqualTo(Coluna.A_FAZER);
    }

    @Test
    @DisplayName("deve adicionar e remover imagem da demanda")
    void deveAdicionarERemoverImagem() {
        Demanda demanda = demandaComColuna(Coluna.BACKLOG);
        demanda.setId(ID_1);
        demanda.setUsuarioId(USUARIO_ID);
        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda comImagem = demandaService.adicionarImagem(ID_1, "https://exemplo.com/imagem.png");
        assertThat(comImagem.getImagemUrl()).isEqualTo("https://exemplo.com/imagem.png");

        Demanda semImagem = demandaService.removerImagem(ID_1);
        assertThat(semImagem.getImagemUrl()).isNull();
    }

    @Test
    @DisplayName("deve gerenciar anexos com capa, renomeação e remoção individual")
    void deveGerenciarAnexosCompletamente() {
        Demanda demanda = demandaComColuna(Coluna.BACKLOG);
        demanda.setId(ID_1);
        demanda.setUsuarioId(USUARIO_ID);
        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda comAnexo = demandaService.adicionarAnexo(ID_1, "Foto1.png", "https://exemplo.com/foto1.png");
        assertThat(comAnexo.hasAnexos()).isTrue();
        assertThat(comAnexo.getAnexos()).hasSize(1);
        assertThat(comAnexo.getAnexos().get(0).isCapa()).isTrue();

        String anexoId = comAnexo.getAnexos().get(0).getId();
        demandaService.renomearAnexo(ID_1, anexoId, "FotoRenomeada.png");
        assertThat(comAnexo.getAnexoById(anexoId).getNome()).isEqualTo("FotoRenomeada.png");

        demandaService.definirCapaAnexo(ID_1, anexoId, false);
        assertThat(comAnexo.hasCapa()).isFalse();

        demandaService.definirCapaAnexo(ID_1, anexoId, true);
        assertThat(comAnexo.hasCapa()).isTrue();

        demandaService.removerAnexo(ID_1, anexoId);
        assertThat(comAnexo.hasAnexos()).isFalse();
        assertThat(comAnexo.hasCapa()).isFalse();
    }

    @Test
    @DisplayName("deve reordenar demanda dentro da mesma coluna")
    void deveReordenarDemandaDentroDaMesmaColuna() {
        Demanda d1 = demandaComTitulo("Card 1", Coluna.BACKLOG);
        d1.setId(ID_1);
        d1.setPosicao(0);
        d1.setProjetoId(PROJETO_100);
        d1.setUsuarioId(USUARIO_ID);

        Demanda d2 = demandaComTitulo("Card 2", Coluna.BACKLOG);
        d2.setId(ID_2);
        d2.setPosicao(1);
        d2.setProjetoId(PROJETO_100);
        d2.setUsuarioId(USUARIO_ID);

        Demanda d3 = demandaComTitulo("Card 3", Coluna.BACKLOG);
        d3.setId(ID_3);
        d3.setPosicao(2);
        d3.setProjetoId(PROJETO_100);
        d3.setUsuarioId(USUARIO_ID);

        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(d1));
        when(demandaRepository.listarPorProjetoEUsuario(PROJETO_100, USUARIO_ID)).thenReturn(Arrays.asList(d1, d2, d3));
        when(colunaService.buscarPorId(eq("BACKLOG"), any())).thenReturn(Coluna.BACKLOG);
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.mover(ID_1, "BACKLOG", "BACKLOG", 2, PROJETO_100, "Usuário");

        assertThat(resultado.getPosicao()).isEqualTo(2);
        assertThat(resultado.getColuna()).isEqualTo(Coluna.BACKLOG);
    }

    @Test
    @DisplayName("deve mover demanda para outra coluna na posição indicada")
    void deveMoverDemandaParaOutraColuna() {
        Demanda d1 = demandaComTitulo("Card 1", Coluna.BACKLOG);
        d1.setId(ID_1);
        d1.setPosicao(0);
        d1.setProjetoId(PROJETO_100);
        d1.setUsuarioId(USUARIO_ID);

        Demanda d2 = demandaComTitulo("Card 2", Coluna.EM_ANDAMENTO);
        d2.setId(ID_2);
        d2.setPosicao(0);
        d2.setProjetoId(PROJETO_100);
        d2.setUsuarioId(USUARIO_ID);

        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(d1));
        when(demandaRepository.listarPorProjetoEUsuario(PROJETO_100, USUARIO_ID)).thenReturn(Arrays.asList(d1, d2));
        when(colunaService.buscarPorId(eq("BACKLOG"), any())).thenReturn(Coluna.BACKLOG);
        when(colunaService.buscarPorId(eq("EM_ANDAMENTO"), any())).thenReturn(Coluna.EM_ANDAMENTO);
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.mover(ID_1, "BACKLOG", "EM_ANDAMENTO", 0, PROJETO_100, "Usuário");

        assertThat(resultado.getColuna()).isEqualTo(Coluna.EM_ANDAMENTO);
        assertThat(resultado.getPosicao()).isEqualTo(0);
    }

    @Test
    @DisplayName("deve mover demanda para coluna vazia e atribuir posição 0")
    void deveMoverDemandaParaColunaVazia() {
        Demanda d1 = demandaComTitulo("Card 1", Coluna.BACKLOG);
        d1.setId(ID_1);
        d1.setPosicao(0);
        d1.setProjetoId(PROJETO_100);
        d1.setUsuarioId(USUARIO_ID);

        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(d1));
        when(demandaRepository.listarPorProjetoEUsuario(PROJETO_100, USUARIO_ID)).thenReturn(Arrays.asList(d1));
        when(colunaService.buscarPorId(eq("BACKLOG"), any())).thenReturn(Coluna.BACKLOG);
        when(colunaService.buscarPorId(eq("CONCLUIDO"), any())).thenReturn(Coluna.CONCLUIDO);
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.mover(ID_1, "BACKLOG", "CONCLUIDO", 5, PROJETO_100, "Usuário");

        assertThat(resultado.getColuna()).isEqualTo(Coluna.CONCLUIDO);
        assertThat(resultado.getPosicao()).isEqualTo(0);
    }

    @Test
    @DisplayName("deve retornar demandas ordenadas por posição ascendente em listarPorColuna")
    void deveListarDemandasOrdenadasPorPosicao() {
        Demanda d1 = demandaComTitulo("Card B", Coluna.BACKLOG);
        d1.setPosicao(2);
        d1.setProjetoId(PROJETO_100);
        d1.setUsuarioId(USUARIO_ID);

        Demanda d2 = demandaComTitulo("Card A", Coluna.BACKLOG);
        d2.setPosicao(0);
        d2.setProjetoId(PROJETO_100);
        d2.setUsuarioId(USUARIO_ID);

        Demanda d3 = demandaComTitulo("Card C", Coluna.BACKLOG);
        d3.setPosicao(1);
        d3.setProjetoId(PROJETO_100);
        d3.setUsuarioId(USUARIO_ID);

        when(demandaRepository.listarPorProjetoEUsuario(PROJETO_100, USUARIO_ID)).thenReturn(Arrays.asList(d1, d2, d3));
        when(colunaService.listarPorProjeto(PROJETO_100)).thenReturn(Arrays.asList(Coluna.BACKLOG));

        Map<Coluna, List<Demanda>> mapa = demandaService.listarPorColuna(PROJETO_100);
        List<Demanda> lista = mapa.get(Coluna.BACKLOG);

        assertThat(lista).hasSize(3);
        assertThat(lista.get(0).getPosicao()).isEqualTo(0);
        assertThat(lista.get(1).getPosicao()).isEqualTo(1);
        assertThat(lista.get(2).getPosicao()).isEqualTo(2);
    }

    @Test
    @DisplayName("deve rejeitar mover demanda de outro projeto")
    void deveRejeitarMoverDemandaDeOutroProjeto() {
        Demanda d1 = demandaComTitulo("Card 1", Coluna.BACKLOG);
        d1.setId(ID_1);
        d1.setProjetoId(PROJETO_200);
        d1.setUsuarioId(USUARIO_ID);

        when(demandaRepository.buscarPorIdEUsuario(ID_1, USUARIO_ID)).thenReturn(Optional.of(d1));

        assertThatThrownBy(() -> demandaService.mover(ID_1, "BACKLOG", "EM_ANDAMENTO", 0, PROJETO_100, "Usuário"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pertence ao projeto informado");
    }


    // ── Helpers ─────────────────────────────────────────────────────────────


    private Demanda demandaComColuna(Coluna coluna) {
        Demanda d = new Demanda();
        d.setTitulo("Demanda de teste");
        d.setColuna(coluna);
        d.setPrioridade(Prioridade.MEDIA);
        return d;
    }

    private Demanda demandaComTitulo(String titulo, Coluna coluna) {
        Demanda d = demandaComColuna(coluna);
        d.setTitulo(titulo);
        return d;
    }
}
