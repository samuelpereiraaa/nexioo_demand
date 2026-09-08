package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.SupabaseUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("SupabaseAuthService — Testes unitários com MockRestServiceServer")
class SupabaseAuthServiceTest {

    private SupabaseAuthService authService;
    private MockRestServiceServer mockServer;
    private final String baseUrl = "https://mock-supabase.co";
    private final String anonKey = "mock-anon-key";

    @BeforeEach
    void setUp() throws Exception {
        RestTemplateBuilder builder = new RestTemplateBuilder();
        ObjectMapper mapper = new ObjectMapper();
        authService = new SupabaseAuthService(baseUrl, anonKey, builder, mapper);

        Field restTemplateField = SupabaseAuthService.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) restTemplateField.get(authService);
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    @DisplayName("autenticar com email e senha corretos deve retornar SupabaseUser populado")
    void autenticarSucesso() {
        String jsonResponse = "{\n" +
                "  \"access_token\": \"jwt-access-token-123\",\n" +
                "  \"user\": {\n" +
                "    \"id\": \"user-uuid-1\",\n" +
                "    \"email\": \"nexioo@gmail.com\",\n" +
                "    \"user_metadata\": {\"full_name\": \"Nexioo Admin\"}\n" +
                "  }\n" +
                "}";

        mockServer.expect(requestTo(baseUrl + "/auth/v1/token?grant_type=password"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", anonKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        SupabaseUser user = authService.autenticar("nexioo@gmail.com", "123456");

        mockServer.verify();
        assertNotNull(user);
        assertEquals("user-uuid-1", user.getId());
        assertEquals("nexioo@gmail.com", user.getEmail());
        assertEquals("Nexioo Admin", user.getNome());
        assertEquals("jwt-access-token-123", user.getAccessToken());
    }

    @Test
    @DisplayName("autenticar com credenciais inválidas deve lançar mensagem genérica e segura")
    void autenticarCredenciaisInvalidas() {
        String errorResponse = "{\"code\":400,\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"}";

        mockServer.expect(requestTo(baseUrl + "/auth/v1/token?grant_type=password"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body(errorResponse).contentType(MediaType.APPLICATION_JSON));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.autenticar("inexistente@gmail.com", "senhaerrada"));

        assertEquals("E-mail ou senha inválidos.", ex.getMessage());
    }

    @Test
    @DisplayName("autenticar com campos em branco deve falhar localmente com mensagem clara")
    void autenticarCamposVazios() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                authService.autenticar("", "123456"));
        assertEquals("E-mail e senha são obrigatórios.", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                authService.autenticar("usuario@test.com", "   "));
        assertEquals("E-mail e senha são obrigatórios.", ex2.getMessage());
    }

    @Test
    @DisplayName("validarToken com token válido deve retornar SupabaseUser")
    void validarTokenSucesso() {
        String jsonResponse = "{\n" +
                "  \"id\": \"user-uuid-2\",\n" +
                "  \"email\": \"joao.silva@empresa.com\",\n" +
                "  \"user_metadata\": {\"full_name\": \"João Silva\"}\n" +
                "}";

        mockServer.expect(requestTo(baseUrl + "/auth/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("apikey", anonKey))
                .andExpect(header("Authorization", "Bearer valid-jwt-token"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        SupabaseUser user = authService.validarToken("valid-jwt-token");

        mockServer.verify();
        assertNotNull(user);
        assertEquals("user-uuid-2", user.getId());
        assertEquals("joao.silva@empresa.com", user.getEmail());
        assertEquals("João Silva", user.getNome());
        assertEquals("valid-jwt-token", user.getAccessToken());
    }

    @Test
    @DisplayName("validarToken com token expirado ou inválido deve lançar exceção")
    void validarTokenInvalido() {
        mockServer.expect(requestTo(baseUrl + "/auth/v1/user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.validarToken("token-expirado"));

        assertEquals("Sessão inválida ou expirada.", ex.getMessage());
    }

    @Test
    @DisplayName("cadastrar com usuário já existente deve traduzir para mensagem adequada")
    void cadastrarUsuarioJaCadastrado() {
        String errorResponse = "{\"code\":422,\"error_code\":\"user_already_exists\",\"msg\":\"User already registered\"}";

        mockServer.expect(requestTo(baseUrl + "/auth/v1/signup"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withRawStatus(422).body(errorResponse).contentType(MediaType.APPLICATION_JSON));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.cadastrar("nexioo@gmail.com", "123456", "Nexioo"));

        assertEquals("Este e-mail já está cadastrado.", ex.getMessage());
    }
}
