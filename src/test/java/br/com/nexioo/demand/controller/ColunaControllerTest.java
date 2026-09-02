package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.service.ColunaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ColunaController.class)
@DisplayName("ColunaController — criação de listas")
class ColunaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ColunaService colunaService;

    @Test
    @DisplayName("POST /colunas com nome válido deve criar lista e redirecionar para o quadro")
    void deveCriarColunaERedirecionar() throws Exception {
        Coluna novaColuna = new Coluna("REVISAO", "Revisão", 5);
        when(colunaService.criar(any())).thenReturn(novaColuna);

        mockMvc.perform(post("/colunas")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("nome", "Revisão"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro?novaColunaId=REVISAO#coluna-revisao"));
    }

    @Test
    @DisplayName("POST /colunas com nome vazio deve redirecionar com mensagem de erro")
    void deveRedirecionarComErroSeNomeVazio() throws Exception {
        mockMvc.perform(post("/colunas")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("nome", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro"))
                .andExpect(flash().attributeExists("mensagemErro"));
    }

}
