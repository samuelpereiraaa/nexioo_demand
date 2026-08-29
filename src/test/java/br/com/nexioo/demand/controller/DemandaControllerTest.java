package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.DemandaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemandaController.class)
@DisplayName("DemandaController — camada web")
class DemandaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemandaService demandaService;

    @Test
    @DisplayName("GET /demandas/nova deve retornar o formulário de criação com status 200")
    void deveExibirFormularioNovaDemanda() throws Exception {
        mockMvc.perform(get("/demandas/nova"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().attributeExists("demandaForm"))
                .andExpect(model().attribute("paginaTitulo", "Nova Demanda"));
    }

    @Test
    @DisplayName("POST /demandas com dados válidos deve criar e redirecionar para o quadro")
    void deveCriarDemandaERedirecionarParaQuadro() throws Exception {
        Demanda demandaCriada = demandaFake(1L, "Nova tarefa de teste");
        when(demandaService.criar(any())).thenReturn(demandaCriada);

        mockMvc.perform(post("/demandas")
                        .param("titulo", "Nova tarefa de teste")
                        .param("coluna", "BACKLOG")
                        .param("prioridade", "MEDIA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /demandas com título vazio deve retornar formulário com erros de validação")
    void deveRetornarFormularioComErrosDeTituloVazio() throws Exception {
        mockMvc.perform(post("/demandas")
                        .param("titulo", "")
                        .param("coluna", "BACKLOG")
                        .param("prioridade", "MEDIA"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("demandaForm", "titulo"));
    }

    @Test
    @DisplayName("POST /demandas sem prioridade deve retornar formulário com erro de validação")
    void deveRetornarFormularioComErroDePrioridadeAusente() throws Exception {
        mockMvc.perform(post("/demandas")
                        .param("titulo", "Título válido")
                        .param("coluna", "BACKLOG"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().attributeHasFieldErrors("demandaForm", "prioridade"));
    }

    @Test
    @DisplayName("GET /demandas/{id} deve exibir os detalhes da demanda")
    void deveExibirDetalhesDaDemanda() throws Exception {
        Demanda demanda = demandaFake(1L, "Tarefa de detalhe");
        when(demandaService.buscarPorId(1L)).thenReturn(demanda);

        mockMvc.perform(get("/demandas/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/detalhe"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("GET /demandas/{id}/excluir deve exibir a tela de confirmação")
    void deveExibirTelaDeConfirmacaoDeExclusao() throws Exception {
        Demanda demanda = demandaFake(1L, "Tarefa a excluir");
        when(demandaService.buscarPorId(1L)).thenReturn(demanda);

        mockMvc.perform(get("/demandas/1/excluir"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/confirmar-exclusao"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/status deve alterar coluna e redirecionar para o quadro")
    void deveAlterarStatusERedirecionar() throws Exception {
        mockMvc.perform(post("/demandas/1/status")
                        .param("coluna", "CONCLUIDO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/toggle-concluido deve alternar conclusão e redirecionar")
    void deveAlternarConclusaoERedirecionar() throws Exception {
        Demanda demanda = demandaFake(1L, "Tarefa para concluir");
        demanda.setColuna(Coluna.A_FAZER);
        when(demandaService.buscarPorId(1L)).thenReturn(demanda);

        mockMvc.perform(post("/demandas/1/toggle-concluido"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private Demanda demandaFake(Long id, String titulo) {
        Demanda d = new Demanda();
        d.setId(id);
        d.setTitulo(titulo);
        d.setColuna(Coluna.BACKLOG);
        d.setPrioridade(Prioridade.MEDIA);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        return d;
    }
}
