package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.nexioo.demand.service.ProjetoService;

@WebMvcTest(controllers = {AuthController.class, QuadroController.class, ProjetoController.class})
@DisplayName("AuthInterceptor — Proteção de Rotas e Autenticação")
class AuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemandaService demandaService;

    @MockBean
    private ColunaService colunaService;

    @MockBean
    private ProjetoService projetoService;

    @MockBean
    private br.com.nexioo.demand.service.AreaTrabalhoService areaTrabalhoService;



    @Test
    @DisplayName("GET /login deve estar disponível sem autenticação (200 OK)")
    void devePermitirLoginSemAutenticacao() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /signup deve estar disponível sem autenticação (200 OK)")
    void devePermitirSignupSemAutenticacao() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /quadro sem sessão de login deve redirecionar para /login (302 Redirect)")
    void deveRedirecionarQuadroParaLoginSeNaoAutenticado() throws Exception {
        mockMvc.perform(get("/quadro"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /projetos sem sessão de login deve redirecionar para /login (302 Redirect)")
    void deveRedirecionarProjetosParaLoginSeNaoAutenticado() throws Exception {
        mockMvc.perform(get("/projetos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /login com sessão de login ativa deve redirecionar para /projetos (302 Redirect)")
    void deveRedirecionarLoginParaProjetosSeJaAutenticado() throws Exception {
        mockMvc.perform(get("/login")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"));
    }

    @Test
    @DisplayName("GET /quadro com sessão de login deve permitir acesso (200 OK)")
    void devePermitirQuadroComSessaoAutenticada() throws Exception {
        when(demandaService.listarPorColuna()).thenReturn(Collections.emptyMap());
        when(colunaService.listarTodas()).thenReturn(List.of(Coluna.BACKLOG));
        when(colunaService.buscarPadrao()).thenReturn(Coluna.BACKLOG);

        mockMvc.perform(get("/quadro")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk());
    }
}

