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

    @InjectMocks
    private ColunaServiceImpl colunaService;

    @Test
    @DisplayName("deve criar nova coluna com nome válido e ordem incrementada")
    void deveCriarNovaColunaComSucesso() {
        when(colunaRepository.listarTodas()).thenReturn(List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO));
        when(colunaRepository.salvar(any(Coluna.class))).thenAnswer(inv -> inv.getArgument(0));

        ColunaForm form = new ColunaForm("Testes de QA");
        Coluna resultado = colunaService.criar(form);

        assertThat(resultado).isNotNull();
        assertThat(resultado.getDescricao()).isEqualTo("Testes de QA");
        assertThat(resultado.getOrdem()).isEqualTo(5);
        verify(colunaRepository).salvar(any(Coluna.class));
    }

    @Test
    @DisplayName("deve lançar exceção ao tentar criar coluna com nome em branco")
    void deveLancarExcecaoAoCriarComNomeEmBranco() {
        ColunaForm form = new ColunaForm("   ");

        assertThatThrownBy(() -> colunaService.criar(form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatório");
    }
}
