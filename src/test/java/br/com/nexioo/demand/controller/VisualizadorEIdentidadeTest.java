package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.DemandaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Testes de Identidade Visual do Login e Visualizador de Imagens (Lightbox)")
class VisualizadorEIdentidadeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemandaService demandaService;

    @Test
    @DisplayName("Tela de Login: sem opções sociais, logo verde #00E6A8 e título Nexioo Demand")
    void testIdentidadeLoginSemOpcoesSociais() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1 class=\"auth-title\">Nexioo Demand</h1>")))
                .andExpect(content().string(containsString("fill=\"#00E6A8\"")))
                .andExpect(content().string(not(containsString("Welcome Back"))))
                .andExpect(content().string(not(containsString("<span>OR</span>"))))
                .andExpect(content().string(not(containsString("btn-social"))))
                .andExpect(content().string(not(containsString("Apple"))))
                .andExpect(content().string(not(containsString("Google"))))
                .andExpect(content().string(not(containsString("Twitter"))));
    }

    @Test
    @DisplayName("Quadro: presença do markup do Visualizador Ampliado de Imagens (Lightbox)")
    void testMarkupVisualizadorImagemNoQuadro() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", "dev@empresa.com");

        mockMvc.perform(get("/quadro").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"image-viewer-lightbox\"")))
                .andExpect(content().string(containsString("id=\"viewer-img-element\"")))
                .andExpect(content().string(containsString("id=\"viewer-file-title\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-open\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-download\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-capa\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-delete\"")));
    }

    @Test
    @DisplayName("Modal de detalhes: miniaturas clicáveis configuradas para abrir o visualizador ampliado")
    void testMiniaturasClicaveisNoModal() throws Exception {
        DemandaForm form = new DemandaForm();
        form.setTitulo("Demanda com Anexo Visual");
        form.setColuna(Coluna.BACKLOG);
        form.setPrioridade(Prioridade.ALTA);
        Demanda salva = demandaService.criar(form);

        demandaService.adicionarAnexo(salva.getId(), "screenshot.png", "https://picsum.photos/800/600");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", "dev@empresa.com");

        mockMvc.perform(get("/demandas/" + salva.getId() + "/modal").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("attachment-info-clickable")))
                .andExpect(content().string(containsString("data-anexo-img=\"true\"")))
                .andExpect(content().string(containsString("window.abrirVisualizadorPorAnexo")));
    }
}
