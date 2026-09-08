package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.SupabaseUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupabaseLiveIntegrationTest — Validação direta com a API do Supabase")
class SupabaseLiveIntegrationTest {

    private SupabaseAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new SupabaseAuthService(
                "https://bbxeajlvzajcjkdzhmcz.supabase.co",
                "sb_publishable_Ekx7QfVbyHtXOjFEtPFGAg_v5KLHPQA",
                new RestTemplateBuilder(),
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Deve autenticar com a conta real se TEST_SUPABASE_PASSWORD estiver configurada")
    void testLiveAutenticacaoContaReal() {
        String testPass = System.getProperty("test.supabase.password", System.getenv().getOrDefault("TEST_SUPABASE_PASSWORD", ""));
        if (testPass == null || testPass.isBlank()) {
            System.out.println("Pulando teste live com conta real: TEST_SUPABASE_PASSWORD não informada.");
            return;
        }

        try {
            SupabaseUser user = authService.autenticar("nexioo@gmail.com", testPass);
            assertNotNull(user);
            assertEquals("nexioo@gmail.com", user.getEmail());
            assertNotNull(user.getAccessToken());
            assertFalse(user.getAccessToken().isBlank());

            // Testa validação do token recém-obtido
            SupabaseUser userFromToken = authService.validarToken(user.getAccessToken());
            assertNotNull(userFromToken);
            assertEquals("nexioo@gmail.com", userFromToken.getEmail());
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Não foi possível conectar")) {
                // Em ambiente sem conexão de rede externa (sandbox estrito), ignora graciosamente
                System.out.println("Pulando teste live por indisponibilidade de rede externa: " + e.getMessage());
                return;
            }
            throw e;
        }
    }

    @Test
    @DisplayName("Deve rejeitar senha incorreta na API real com mensagem segura")
    void testLiveSenhaIncorreta() {
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    authService.autenticar("nexioo@gmail.com", "senha_completamente_errada"));
            assertEquals("E-mail ou senha inválidos.", ex.getMessage());
        } catch (Throwable t) {
            if (t.getMessage() != null && t.getMessage().contains("Não foi possível conectar")) {
                System.out.println("Pulando teste live por indisponibilidade de rede externa");
                return;
            }
            throw t;
        }
    }
}
