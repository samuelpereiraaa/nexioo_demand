package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.exception.DemandaNaoEncontradaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemandaController.class)
@DisplayName("GlobalExceptionHandler — Tratamento de Erros e Exceções")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private br.com.nexioo.demand.service.DemandaService demandaService;

    @MockBean
    private br.com.nexioo.demand.service.ColunaService colunaService;

    @Test
    @DisplayName("Navegação normal quando demanda não existe deve retornar view de erro 404")
    void deveRetornarViewQuandoDemandaNaoEncontrada() throws Exception {
        when(demandaService.buscarPorId(999L)).thenThrow(new DemandaNaoEncontradaException(999L));

        mockMvc.perform(get("/demandas/999")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("erro/nao-encontrado"))
                .andExpect(model().attributeExists("mensagem"));
    }

    @Test
    @DisplayName("Requisição AJAX quando demanda não existe deve retornar status 404 com mensagem em texto")
    void deveRetornarStatus404ParaAjax() throws Exception {
        when(demandaService.buscarPorId(999L)).thenThrow(new DemandaNaoEncontradaException(999L));

        mockMvc.perform(get("/demandas/999/modal")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Demanda com ID 999 não encontrada."));
    }
}
