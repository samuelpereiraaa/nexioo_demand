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

import org.springframework.context.annotation.Import;
import br.com.nexioo.demand.config.StringToColunaConverter;
import br.com.nexioo.demand.config.StringToUuidConverter;
import br.com.nexioo.demand.config.UserContext;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;

@WebMvcTest(DemandaController.class)
@Import({StringToColunaConverter.class, StringToUuidConverter.class})
@DisplayName("GlobalExceptionHandler — Tratamento de Erros e Exceções")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private br.com.nexioo.demand.service.DemandaService demandaService;

    @MockBean
    private br.com.nexioo.demand.service.ColunaService colunaService;

    @MockBean
    private UserContext userContext;

    private static final UUID DEMANDA_INEXISTENTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000999");

    @Test
    @DisplayName("Navegação normal quando demanda não existe deve retornar view de erro 404")
    void deveRetornarViewQuandoDemandaNaoEncontrada() throws Exception {
        when(demandaService.buscarPorId(any(UUID.class))).thenThrow(new DemandaNaoEncontradaException(DEMANDA_INEXISTENTE_ID));

        mockMvc.perform(get("/demandas/" + DEMANDA_INEXISTENTE_ID)
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("erro/nao-encontrado"))
                .andExpect(model().attributeExists("mensagem"));
    }

    @Test
    @DisplayName("Requisição AJAX quando demanda não existe deve retornar status 404 com mensagem em texto")
    void deveRetornarStatus404ParaAjax() throws Exception {
        when(demandaService.buscarPorId(any(UUID.class))).thenThrow(new DemandaNaoEncontradaException(DEMANDA_INEXISTENTE_ID));

        mockMvc.perform(get("/demandas/" + DEMANDA_INEXISTENTE_ID + "/modal")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Demanda com ID " + DEMANDA_INEXISTENTE_ID + " não encontrada."));
    }
}
