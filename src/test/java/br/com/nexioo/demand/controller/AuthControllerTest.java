package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.service.SupabaseAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@DisplayName("AuthController — Testes de Autenticação e Sessão com Supabase")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupabaseAuthService supabaseAuthService;

    @MockBean
    private br.com.nexioo.demand.service.ColunaService colunaService;

    @Test
    @DisplayName("POST /login com credenciais válidas deve criar sessão e redirecionar para /projetos")
    void loginComCredenciaisValidasForm() throws Exception {
        when(supabaseAuthService.autenticar("nexioo@gmail.com", "123456"))
                .thenReturn(new SupabaseUser("uid-1", "nexioo@gmail.com", "Nexioo Admin", "token-123"));

        MvcResult result = mockMvc.perform(post("/login")
                        .param("email", "nexioo@gmail.com")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        assertEquals("nexioo@gmail.com", session.getAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO));
        assertEquals("Nexioo Admin", session.getAttribute("usuarioNome"));
    }

    @Test
    @DisplayName("POST /login com AJAX deve responder 200 JSON")
    void loginComAjax() throws Exception {
        when(supabaseAuthService.autenticar("nexioo@gmail.com", "123456"))
                .thenReturn(new SupabaseUser("uid-1", "nexioo@gmail.com", "Nexioo Admin", "token-123"));

        mockMvc.perform(post("/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("email", "nexioo@gmail.com")
                        .param("password", "123456"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.redirect").value("/projetos"));
    }

    @Test
    @DisplayName("POST /login com token deve validar no Supabase e autenticar")
    void loginComToken() throws Exception {
        when(supabaseAuthService.validarToken("token-jwt-cliente"))
                .thenReturn(new SupabaseUser("uid-1", "nexioo@gmail.com", "Nexioo Admin", "token-jwt-cliente"));

        mockMvc.perform(post("/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("token", "token-jwt-cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.user").value("nexioo@gmail.com"));

        verify(supabaseAuthService).validarToken("token-jwt-cliente");
        verify(supabaseAuthService, never()).autenticar(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /login com campos vazios deve retornar 400 com mensagem apropriada")
    void loginCamposVazios() throws Exception {
        mockMvc.perform(post("/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("email", "")
                        .param("password", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("erro"))
                .andExpect(jsonPath("$.mensagem").value("E-mail e senha são obrigatórios."));
    }

    @Test
    @DisplayName("POST /login com credenciais inválidas deve retornar 401 com mensagem genérica")
    void loginCredenciaisInvalidas() throws Exception {
        when(supabaseAuthService.autenticar("nexioo@gmail.com", "errada"))
                .thenThrow(new IllegalArgumentException("E-mail ou senha inválidos."));

        mockMvc.perform(post("/login")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("email", "nexioo@gmail.com")
                        .param("password", "errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("erro"))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos."));
    }

    @Test
    @DisplayName("GET /logout não deve ser permitido (deve rejeitar métodos não POST)")
    void logoutGet() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, "nexioo@gmail.com");

        mockMvc.perform(get("/logout").session(session))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /logout deve exigir token CSRF e quando fornecido limpar cookies e invalidar sessão")
    void logoutPost() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthInterceptor.CHAVE_USUARIO_LOGADO, "nexioo@gmail.com");
        String csrfToken = "csrf-test-token-123";
        session.setAttribute(br.com.nexioo.demand.config.CsrfTokenService.CSRF_ATTR_NAME, csrfToken);

        // 1. Sem token CSRF deve ser bloqueado com 403
        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());

        // 2. Com token CSRF válido deve ter sucesso
        MvcResult result = mockMvc.perform(post("/logout")
                        .session(session)
                        .header(br.com.nexioo.demand.config.CsrfTokenService.CSRF_HEADER_NAME, csrfToken))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(session.isInvalid());
        Cookie cookie = result.getResponse().getCookie("JSESSIONID");
        assertNotNull(cookie);
        assertEquals(0, cookie.getMaxAge());
    }

    @Test
    @DisplayName("POST /signup com dados válidos deve autenticar e retornar redirecionamento")
    void signupSucesso() throws Exception {
        when(supabaseAuthService.cadastrar("novo@gmail.com", "123456", "Novo Usuário"))
                .thenReturn(new SupabaseUser("uid-novo", "novo@gmail.com", "Novo Usuário", "token-novo"));

        mockMvc.perform(post("/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("email", "novo@gmail.com")
                        .param("password", "123456")
                        .param("nome", "Novo Usuário"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.user").value("novo@gmail.com"))
                .andExpect(jsonPath("$.redirect").value("/projetos"));
    }

    @Test
    @DisplayName("POST /signup com erro deve retornar 400 JSON")
    void signupErro() throws Exception {
        when(supabaseAuthService.cadastrar("existente@gmail.com", "123456", "Nome"))
                .thenThrow(new IllegalArgumentException("Este e-mail já está cadastrado."));

        mockMvc.perform(post("/signup")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("email", "existente@gmail.com")
                        .param("password", "123456")
                        .param("nome", "Nome"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("erro"))
                .andExpect(jsonPath("$.mensagem").value("Este e-mail já está cadastrado."));
    }
}
