package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.util.SupabaseTestClientHelper;
import br.com.nexioo.demand.util.SupabaseTestClientHelper.TestUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupabaseLiveIntegrationTest — Validação Completa de Conectividade e Autenticação com Supabase Auth")
class SupabaseLiveIntegrationTest {

    private static SupabaseTestClientHelper helper;
    private static TestUser testUser;
    private SupabaseAuthService authService;

    @BeforeAll
    static void provisionarUsuarioTeste() {
        helper = new SupabaseTestClientHelper();
        Assumptions.assumeTrue(helper.isDisponivel(),
                "SKIPPED: Supabase não está acessível no endpoint: " + helper.getUrl());

        testUser = helper.criarUsuario("live_auth_user");
        assertNotNull(testUser);
    }

    @AfterAll
    static void removerUsuarioTeste() {
        if (helper != null && testUser != null) {
            helper.removerUsuario(testUser.getId());
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(helper != null && helper.isDisponivel());

        authService = new SupabaseAuthService(
                helper.getUrl(),
                helper.getAnonKey(),
                new RestTemplateBuilder(),
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Deve autenticar com conta real de teste e validar token JWT no Supabase Auth")
    void testLiveAutenticacaoContaReal() {
        SupabaseUser user = authService.autenticar(testUser.getEmail(), testUser.getSenha());
        assertNotNull(user);
        assertEquals(testUser.getEmail(), user.getEmail());
        assertNotNull(user.getAccessToken());
        assertFalse(user.getAccessToken().isBlank());

        SupabaseUser userFromToken = authService.validarToken(user.getAccessToken());
        assertNotNull(userFromToken);
        assertEquals(testUser.getEmail(), userFromToken.getEmail());
        assertEquals(testUser.getId().toString(), userFromToken.getId());
    }

    @Test
    @DisplayName("Deve rejeitar senha incorreta na API real com mensagem amigável e segura")
    void testLiveSenhaIncorreta() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.autenticar(testUser.getEmail(), "SenhaCompletamenteIncorreta@999"));
        assertEquals("E-mail ou senha inválidos.", ex.getMessage());
    }

    @Test
    @DisplayName("Deve rejeitar token adulterado ou inválido na validação")
    void testLiveTokenInvalido() {
        assertThrows(IllegalArgumentException.class, () ->
                authService.validarToken("token_completamente_invalido_jwt_xyz"));
    }
}
