package br.com.nexioo.demand.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CsrfProtectionTest — Validação Completa de Proteção CSRF, Same-Origin e Const-Time Comparison")
class CsrfProtectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /login deve emitir token CSRF na sessão e no cookie XSRF-TOKEN")
    void deveEmitirTokenCsrfNoGet() throws Exception {
        MvcResult result = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertNotNull(session.getAttribute(CsrfTokenService.CSRF_ATTR_NAME));

        Cookie cookie = result.getResponse().getCookie(CsrfTokenService.CSRF_COOKIE_NAME);
        assertNotNull(cookie);
        assertTrue(cookie.getValue().length() >= 32);
    }

    @Test
    @DisplayName("POST com Origin externa maliciosa deve ser sumariamente bloqueado com 403 Forbidden")
    void deveBloquearOriginExternaMaliciosa() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Origin", "https://attacker.evil.com")
                        .header("Host", "localhost:8080"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST para /logout sem token CSRF deve retornar 403 Forbidden")
    void deveBloquearLogoutSemCsrf() throws Exception {
        mockMvc.perform(post("/logout")
                        .header("Host", "localhost"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST para /logout com token CSRF válido na sessão deve ser autorizado")
    void devePermitirLogoutComCsrfValido() throws Exception {
        // 1. Obtém sessão com token válido
        MvcResult getResult = mockMvc.perform(get("/login")).andReturn();
        MockHttpSession session = (MockHttpSession) getResult.getRequest().getSession(false);
        String token = (String) session.getAttribute(CsrfTokenService.CSRF_ATTR_NAME);

        // 2. Executa POST enviando o mesmo token
        mockMvc.perform(post("/logout")
                        .session(session)
                        .header("Origin", "http://localhost")
                        .header("Host", "localhost")
                        .header(CsrfTokenService.CSRF_HEADER_NAME, token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST deve aceitar double-submit cookie sem depender da sessão da instância")
    void devePermitirCsrfStatelessComCookie() throws Exception {
        MvcResult getResult = mockMvc.perform(get("/login")).andReturn();
        Cookie csrfCookie = getResult.getResponse().getCookie(CsrfTokenService.CSRF_COOKIE_NAME);
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/logout")
                        .cookie(csrfCookie)
                        .header("Origin", "http://localhost")
                        .header("Host", "localhost")
                        .header(CsrfTokenService.CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST com token CSRF divergente deve retornar 403 Forbidden (comparação estrita)")
    void deveRejeitarTokenCsrfDivergente() throws Exception {
        MvcResult getResult = mockMvc.perform(get("/login")).andReturn();
        MockHttpSession session = (MockHttpSession) getResult.getRequest().getSession(false);

        mockMvc.perform(post("/logout")
                        .session(session)
                        .header("Origin", "http://localhost")
                        .header("Host", "localhost")
                        .header(CsrfTokenService.CSRF_HEADER_NAME, "token-forjado-invalido"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /logout NÃO deve existir ou ser aceito (apenas POST permitido)")
    void deveRejeitarGetLogout() throws Exception {
        mockMvc.perform(get("/logout"))
                .andExpect(status().isMethodNotAllowed());
    }
}
