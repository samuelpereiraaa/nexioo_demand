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

    @Autowired
    private br.com.nexioo.demand.repository.DemandaRepository demandaRepository;

    @Test
    @DisplayName("Tela de Login: sem opções sociais, logo verde #00E6A8 e título Nexioo Demand")
    void testIdentidadeLoginSemOpcoesSociais() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nexioo Demand")))
                .andExpect(content().string(containsString("#00E6A8")))
                .andExpect(content().string(not(containsString("Google"))))
                .andExpect(content().string(not(containsString("GitHub"))));
    }

    @Test
    @DisplayName("Quadro: estrutura DOM do visualizador de imagens incluída no layout")
    void testEstruturaVisualizadorNoQuadro() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", "dev@empresa.com");

        mockMvc.perform(get("/quadro").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"image-viewer-lightbox\"")))
                .andExpect(content().string(containsString("id=\"viewer-img-element\"")))
                .andExpect(content().string(containsString("id=\"viewer-file-title\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-open\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-download\"")))
                .andExpect(content().string(containsString("id=\"viewer-action-capa\"")));
    }

    @Test
    @DisplayName("Modal de detalhes: miniaturas clicáveis configuradas para abrir o visualizador ampliado")
    void testMiniaturasClicaveisNoModal() throws Exception {
        java.util.UUID userUid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        Demanda salva = new Demanda();
        salva.setId(java.util.UUID.randomUUID());
        salva.setTitulo("Demanda com Anexo Visual");
        salva.setColuna(Coluna.BACKLOG);
        salva.setPrioridade(Prioridade.ALTA);
        salva.setUsuarioId(userUid);
        salva.adicionarAnexo("screenshot.png", "https://picsum.photos/800/600");
        salva = demandaRepository.salvar(salva);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("usuarioLogado", "dev@empresa.com");
        session.setAttribute("usuarioId", userUid.toString());

        mockMvc.perform(get("/demandas/" + salva.getId() + "/modal").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("attachment-info-clickable")))
                .andExpect(content().string(containsString("data-anexo-img=\"true\"")))
                .andExpect(content().string(containsString("window.abrirVisualizadorPorAnexo")));
    }
}
