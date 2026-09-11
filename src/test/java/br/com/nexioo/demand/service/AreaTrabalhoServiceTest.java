package br.com.nexioo.demand.service;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import br.com.nexioo.demand.repository.memory.AreaTrabalhoRepositoryMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import br.com.nexioo.demand.config.UserContext;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AreaTrabalhoService — Regras de negócio de Áreas de Trabalho")
class AreaTrabalhoServiceTest {

    @Mock
    private UserContext userContext;

    private AreaTrabalhoService service;
    private AreaTrabalhoRepository repository;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        lenient().when(userContext.getUsuarioId()).thenReturn(USER_ID);
        lenient().when(userContext.requireUsuarioId()).thenReturn(USER_ID);
        lenient().when(userContext.getEmail()).thenReturn("dev@empresa.com");
        lenient().when(userContext.isAutenticado()).thenReturn(true);

        repository = new AreaTrabalhoRepositoryMemory();
        service = new AreaTrabalhoServiceImpl(repository, userContext);
    }

    @Test
    @DisplayName("Deve criar nova área de trabalho com inicial gerada automaticamente")
    void deveCriarNovaAreaComInicial() {
        AreaTrabalho area = service.criar("Engenharia de Software", "dev@empresa.com");
        assertNotNull(area.getId());
        assertEquals("Engenharia de Software", area.getNome());
        assertEquals("E", area.getInicial());
        assertEquals("dev@empresa.com", area.getUsuarioProprietario());
    }

    @Test
    @DisplayName("Não deve permitir criação com nome em branco ou duplicado")
    void naoDevePermitirNomeInvalidoOuDuplicado() {
        assertThrows(IllegalArgumentException.class, () -> service.criar("", "dev@empresa.com"));
        assertThrows(IllegalArgumentException.class, () -> service.criar("   ", "dev@empresa.com"));

        service.criar("Design", "dev@empresa.com");
        assertThrows(IllegalArgumentException.class, () -> service.criar("Design", "dev@empresa.com"));
        assertThrows(IllegalArgumentException.class, () -> service.criar("design", "dev@empresa.com"));
    }

    @Test
    @DisplayName("Deve listar áreas do usuário ou criar padrão se vazio")
    void deveListarOuCriarPadrao() {
        List<AreaTrabalho> lista = service.listarPorUsuario("novo@empresa.com");
        assertFalse(lista.isEmpty());
        assertEquals("Área de trabalho Nexioo Demand", lista.get(0).getNome());
        assertEquals("Á", lista.get(0).getInicial());
    }

    @Test
    @DisplayName("Deve editar e renomear área com sucesso")
    void deveEditarAreaComSucesso() {
        AreaTrabalho criada = service.criar("Marketing", "dev@empresa.com");
        AreaTrabalho editada = service.editar(criada.getId(), "Growth & Marketing", "dev@empresa.com");
        assertEquals("Growth & Marketing", editada.getNome());
        assertEquals("G", editada.getInicial());
    }

    @Test
    @DisplayName("Deve excluir área de trabalho")
    void deveExcluirArea() {
        AreaTrabalho criada = service.criar("Temporária", "dev@empresa.com");
        service.excluir(criada.getId(), "dev@empresa.com");
        assertThrows(IllegalArgumentException.class, () -> service.buscarPorId(criada.getId()));
    }
}
