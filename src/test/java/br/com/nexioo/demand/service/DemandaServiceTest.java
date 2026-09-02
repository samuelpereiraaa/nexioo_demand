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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemandaService — regras de negócio")
class DemandaServiceTest {

    @Mock
    private DemandaRepository demandaRepository;

    @Mock
    private ColunaService colunaService;

    @InjectMocks
    private DemandaServiceImpl demandaService;

    private DemandaForm formValido;

    @BeforeEach
    void setUp() {
        formValido = new DemandaForm();
        formValido.setTitulo("Implementar módulo de login");
        formValido.setDescricao("Criar tela com autenticação por e-mail e senha.");
        formValido.setColuna(Coluna.BACKLOG);
        formValido.setPrioridade(Prioridade.ALTA);
        formValido.setResponsavel("Maria Santos");
        formValido.setPrazo(LocalDate.now().plusDays(7));
    }

    @Test
    @DisplayName("deve criar demanda com todos os campos preenchidos corretamente")
    void deveCriarDemandaComTodosOsCampos() {
        Demanda demandaSalva = new Demanda();
        demandaSalva.setId(1L);
        when(demandaRepository.salvar(any(Demanda.class))).thenReturn(demandaSalva);

        Demanda resultado = demandaService.criar(formValido);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        verify(demandaRepository, times(1)).salvar(any(Demanda.class));
    }

    @Test
    @DisplayName("deve lançar DemandaNaoEncontradaException ao buscar ID inexistente")
    void deveLancarExcecaoAoBuscarIdInexistente() {
        when(demandaRepository.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> demandaService.buscarPorId(99L))
                .isInstanceOf(DemandaNaoEncontradaException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("deve retornar mapa com todas as colunas, incluindo as sem demandas")
    void deveListarPorColunaComTodasAsColunas() {
        when(colunaService.listarTodas()).thenReturn(List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO));
        Demanda d1 = demandaComColuna(Coluna.BACKLOG);
        Demanda d2 = demandaComColuna(Coluna.A_FAZER);
        when(demandaRepository.listarTodas()).thenReturn(Arrays.asList(d1, d2));

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
        when(demandaRepository.listarTodas()).thenReturn(Arrays.asList(d1, d2));

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
        when(demandaRepository.listarTodas()).thenReturn(List.of(d1, d2));

        Map<Coluna, List<Demanda>> resultado = demandaService.filtrar(null, Prioridade.ALTA, null);

        assertThat(resultado.get(Coluna.BACKLOG)).hasSize(1);
        assertThat(resultado.get(Coluna.A_FAZER)).isEmpty();
    }

    @Test
    @DisplayName("deve alterar a coluna da demanda e persistir a mudança")
    void deveAlterarColunaComSucesso() {
        Demanda demanda = demandaComColuna(Coluna.BACKLOG);
        demanda.setId(1L);
        when(demandaRepository.buscarPorId(1L)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.alterarColuna(1L, Coluna.EM_ANDAMENTO);

        assertThat(resultado.getColuna()).isEqualTo(Coluna.EM_ANDAMENTO);
        assertThat(resultado.getAtualizadoEm()).isNotNull();
        verify(demandaRepository).salvar(demanda);
    }

    @Test
    @DisplayName("deve excluir demanda existente com sucesso")
    void deveExcluirDemandaExistente() {
        when(demandaRepository.existePorId(1L)).thenReturn(true);

        demandaService.excluir(1L);

        verify(demandaRepository).excluir(1L);
    }

    @Test
    @DisplayName("deve lançar exceção ao tentar excluir demanda inexistente")
    void deveLancarExcecaoAoExcluirDemandaInexistente() {
        when(demandaRepository.existePorId(99L)).thenReturn(false);

        assertThatThrownBy(() -> demandaService.excluir(99L))
                .isInstanceOf(DemandaNaoEncontradaException.class);

        verify(demandaRepository, never()).excluir(any());
    }

    @Test
    @DisplayName("deve alternar apenas a conclusão sem alterar a coluna/lista da demanda")
    void deveAlternarApenasConclusaoSemMoverColuna() {
        Demanda demanda = demandaComColuna(Coluna.A_FAZER);
        demanda.setConcluido(false);
        when(demandaRepository.buscarPorId(1L)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda resultado = demandaService.alternarConclusao(1L);

        assertThat(resultado.isConcluido()).isTrue();
        assertThat(resultado.getColuna()).isEqualTo(Coluna.A_FAZER);

        Demanda reaberta = demandaService.alternarConclusao(1L);
        assertThat(reaberta.isConcluido()).isFalse();
        assertThat(reaberta.getColuna()).isEqualTo(Coluna.A_FAZER);
    }

    @Test
    @DisplayName("deve adicionar e remover imagem da demanda")
    void deveAdicionarERemoverImagem() {
        Demanda demanda = demandaComColuna(Coluna.BACKLOG);
        when(demandaRepository.buscarPorId(1L)).thenReturn(Optional.of(demanda));
        when(demandaRepository.salvar(any(Demanda.class))).thenAnswer(inv -> inv.getArgument(0));

        Demanda comImagem = demandaService.adicionarImagem(1L, "https://exemplo.com/imagem.png");
        assertThat(comImagem.getImagemUrl()).isEqualTo("https://exemplo.com/imagem.png");

        Demanda semImagem = demandaService.removerImagem(1L);
        assertThat(semImagem.getImagemUrl()).isNull();
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
