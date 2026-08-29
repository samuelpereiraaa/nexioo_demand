package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuadroController.class)
@DisplayName("QuadroController — tela principal")
class QuadroControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemandaService demandaService;

    @MockBean
    private ColunaService colunaService;

    @Test
    @DisplayName("GET / deve renderizar o quadro Kanban com status 200 e todas as colunas")
    void deveRenderizarQuadroComSucesso() throws Exception {
        Map<Coluna, List<Demanda>> mapaDemandas = criarMapaDemandas();
        List<Coluna> listaColunas = List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO);
        when(demandaService.listarPorColuna()).thenReturn(mapaDemandas);
        when(colunaService.listarTodas()).thenReturn(listaColunas);
        when(colunaService.buscarPadrao()).thenReturn(Coluna.BACKLOG);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("quadro/index"))
                .andExpect(model().attributeExists("demandas"))
                .andExpect(model().attributeExists("colunas"))
                .andExpect(model().attributeExists("prioridades"))
                .andExpect(model().attributeExists("demandaForm"))
                .andExpect(model().attributeExists("colunaForm"));
    }

    @Test
    @DisplayName("GET /quadro deve renderizar a mesma tela do quadro Kanban com status 200")
    void deveRenderizarRotaQuadroComSucesso() throws Exception {
        Map<Coluna, List<Demanda>> mapaDemandas = criarMapaDemandas();
        List<Coluna> listaColunas = List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO);
        when(demandaService.listarPorColuna()).thenReturn(mapaDemandas);
        when(colunaService.listarTodas()).thenReturn(listaColunas);
        when(colunaService.buscarPadrao()).thenReturn(Coluna.BACKLOG);

        mockMvc.perform(get("/quadro"))
                .andExpect(status().isOk())
                .andExpect(view().name("quadro/index"))
                .andExpect(model().attributeExists("demandas"));
    }

    private Map<Coluna, List<Demanda>> criarMapaDemandas() {
        Map<Coluna, List<Demanda>> mapaDemandas = new LinkedHashMap<>();
        for (Coluna coluna : List.of(Coluna.BACKLOG, Coluna.A_FAZER, Coluna.EM_ANDAMENTO, Coluna.CONCLUIDO)) {
            mapaDemandas.put(coluna, new ArrayList<>());
        }

        Demanda d = new Demanda();
        d.setId(1L);
        d.setTitulo("Definir identidade visual");
        d.setColuna(Coluna.BACKLOG);
        d.setPrioridade(Prioridade.ALTA);
        d.setResponsavel("Samuel Oliveira");
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        mapaDemandas.get(Coluna.BACKLOG).add(d);
        return mapaDemandas;
    }
}
