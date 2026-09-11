package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
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
import org.springframework.test.web.servlet.MockMvc;

import static br.com.nexioo.demand.util.CsrfTestUtils.withCsrf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DemandaAnexosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemandaService demandaService;

    @Autowired
    private br.com.nexioo.demand.repository.DemandaRepository demandaRepository;

    @Test
    @DisplayName("Ciclo de vida completo da seção de Anexos (estilo Trello)")
    void testCicloDeVidaAnexosTrello() throws Exception {
        java.util.UUID userUid = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com");
        session.setAttribute("usuarioId", userUid.toString());

        // 1. Criar uma nova demanda sem anexos
        Demanda salva = new Demanda();
        salva.setId(java.util.UUID.randomUUID());
        salva.setTitulo("Demanda Anexo Test");
        salva.setColuna(Coluna.BACKLOG);
        salva.setPrioridade(Prioridade.ALTA);
        salva.setResponsavel("Samuel");
        salva.setUsuarioId(userUid);
        salva = demandaRepository.salvar(salva);
        java.util.UUID id = salva.getId();

        // 2. Verificar que no modal inicial SEM anexos:
        // - Aba "Imagem Anexada" ESTÁ VISÍVEL abaixo da descrição
        // - Seção "Anexos" NÃO é renderizada
        mockMvc.perform(get("/demandas/" + id + "/modal")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"modal-section-upload-imagem\"")))
                .andExpect(content().string(containsString("Escolher Imagem do Computador")))
                .andExpect(content().string(containsString("Anexar via URL")))
                .andExpect(content().string(not(containsString("id=\"modal-section-anexos\""))))
                .andExpect(content().string(not(containsString("id=\"popover-adicionar\""))));


        // 3. Adicionar primeiro anexo via upload Multipart
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "arquivo",
                "foto_computador.png",
                "image/png",
                "conteudo fake da imagem".getBytes()
        );

        // 2.1 Verificar que upload sem CSRF é bloqueado com 403 Forbidden
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/demandas/" + id + "/imagem/upload")
                        .file(mockFile)
                        .session(session))
                .andExpect(status().isForbidden());

        // 3. Adicionar primeiro anexo via upload Multipart com CSRF
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/demandas/" + id + "/imagem/upload")
                        .file(mockFile)
                        .with(withCsrf())
                        .session(session))
                .andExpect(status().isOk())
                // A aba "Imagem Anexada" continua SEMPRE visível!
                .andExpect(content().string(containsString("id=\"modal-section-upload-imagem\"")))
                // A seção "Anexos" agora DEVE ser renderizada!
                .andExpect(content().string(containsString("id=\"modal-section-anexos\"")))
                .andExpect(content().string(containsString("Anexos</h2>")))
                .andExpect(content().string(containsString("Arquivos</h3>")))
                .andExpect(content().string(containsString("foto_computador.png")))
                .andExpect(content().string(containsString("Adicionado há pouco")))
                .andExpect(content().string(containsString("Capa</span>")))
                .andExpect(content().string(containsString("Remover capa</button>")))
                .andExpect(content().string(containsString("Editar</button>")))
                .andExpect(content().string(containsString("Comentário</button>")))
                .andExpect(content().string(containsString("Baixar</a>")));

        // 4. Obter o anexo criado
        Demanda atualizada = demandaRepository.buscarPorId(id).orElseThrow();
        String anexoId = atualizada.getAnexos().get(0).getId();

        // 5. Renomear o anexo
        mockMvc.perform(post("/demandas/" + id + "/anexos/renomear")
                        .with(withCsrf())
                        .session(session)
                        .param("anexoId", anexoId)
                        .param("nome", "documento_renomeado.png"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("documento_renomeado.png")));

        // 6. Remover capa do anexo
        mockMvc.perform(post("/demandas/" + id + "/anexos/capa")
                        .with(withCsrf())
                        .session(session)
                        .param("anexoId", anexoId)
                        .param("capa", "false"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tornar capa</button>")));

        // 7. Remover o anexo (quando removido o único anexo, a seção "Anexos" some, mas "Imagem Anexada" continua lá!)
        mockMvc.perform(post("/demandas/" + id + "/anexos/remover")
                        .with(withCsrf())
                        .session(session)
                        .param("anexoId", anexoId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"modal-section-anexos\""))))
                .andExpect(content().string(containsString("id=\"modal-section-upload-imagem\"")));
    }
}
