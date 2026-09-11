package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ColunaService — regras de negócio de colunas")
class ColunaServiceTest {

    @Mock
    private ColunaRepository colunaRepository;

    @Mock
    private br.com.nexioo.demand.config.UserContext userContext;

    @InjectMocks
    private ColunaServiceImpl colunaService;

    private static final java.util.UUID USUARIO_ID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        lenient().when(userContext.isAutenticado()).thenReturn(true);
        lenient().when(userContext.getUsuarioId()).thenReturn(USUARIO_ID);
        lenient().when(userContext.requireUsuarioId()).thenReturn(USUARIO_ID);
    }

    @Test
    @DisplayName("deve criar nova coluna com nome válido, projeto vinculado e ordem incrementada")
    void deveCriarNovaColunaComSucesso() {
        java.util.UUID projetoId = java.util.UUID.randomUUID();
        when(colunaRepository.listarPorProjetoEUsuario(eq(projetoId), eq(USUARIO_ID)))
                .thenReturn(List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO));
        when(colunaRepository.salvar(any(Coluna.class))).thenAnswer(inv -> inv.getArgument(0));

        ColunaForm form = new ColunaForm("Testes de QA");
        form.setProjetoId(projetoId);
        Coluna resultado = colunaService.criar(form);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getDescricao()).isEqualTo("Testes de QA");
        assertThat(resultado.getOrdem()).isEqualTo(5);
        assertThat(resultado.getProjetoId()).isEqualTo(projetoId);
        verify(colunaRepository).salvar(any(Coluna.class));
    }

    @Test
    @DisplayName("deve lançar exceção ao tentar criar coluna sem projeto vinculado")
    void deveLancarExcecaoAoCriarSemProjeto() {
        ColunaForm form = new ColunaForm("Testes de QA");
        form.setProjetoId(null);

        assertThatThrownBy(() -> colunaService.criar(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projeto é obrigatório");
    }

    @Test
    @DisplayName("deve lançar exceção ao tentar criar coluna com nome em branco")
    void deveLancarExcecaoAoCriarComNomeEmBranco() {
        ColunaForm form = new ColunaForm("   ");
        form.setProjetoId(java.util.UUID.randomUUID());

        assertThatThrownBy(() -> colunaService.criar(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatório");
    }
}
