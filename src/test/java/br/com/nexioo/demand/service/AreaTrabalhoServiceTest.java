package br.com.nexioo.demand.service;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import br.com.nexioo.demand.repository.memory.AreaTrabalhoRepositoryMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AreaTrabalhoService — Regras de negócio de Áreas de Trabalho")
class AreaTrabalhoServiceTest {

    private AreaTrabalhoService service;
    private AreaTrabalhoRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AreaTrabalhoRepositoryMemory();
        service = new AreaTrabalhoServiceImpl(repository);
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
