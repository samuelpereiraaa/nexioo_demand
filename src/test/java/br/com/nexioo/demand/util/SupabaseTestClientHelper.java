package br.com.nexioo.demand.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Utilitário seguro para provisionar e destruir contas reais de teste no Supabase.
 * - Utiliza EXCLUSIVAMENTE variáveis com prefixo SUPABASE_TEST_* (nunca variáveis de produção).
 * - Possui trava contra testes destrutivos em destinos que não sejam localhost/homologação explícita.
 * - Verifica estritamente o sucesso da limpeza de usuários de teste.
 */
public class SupabaseTestClientHelper {

    public static final String DEFAULT_URL = "http://127.0.0.1:54321";
    public static final String DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0";
    public static final String DEFAULT_SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU";

    private final String url;
    private final String anonKey;
    private final String serviceRoleKey;
    private final RestTemplate restTemplate;

    public SupabaseTestClientHelper() {
        // Regra estrita: lê EXCLUSIVAMENTE propriedades e variáveis SUPABASE_TEST_*
        String propUrl = System.getProperty("supabase.test.url", System.getenv().getOrDefault("SUPABASE_TEST_URL", ""));
        this.url = (!propUrl.isBlank()) ? propUrl.trim() : DEFAULT_URL;

        String propAnon = System.getProperty("supabase.test.anon-key", System.getenv().getOrDefault("SUPABASE_TEST_ANON_KEY", ""));
        this.anonKey = (!propAnon.isBlank()) ? propAnon.trim() : DEFAULT_ANON_KEY;

        String propService = System.getProperty("supabase.test.service-role-key", System.getenv().getOrDefault("SUPABASE_TEST_SERVICE_ROLE_KEY", ""));
        this.serviceRoleKey = (!propService.isBlank()) ? propService.trim() : DEFAULT_SERVICE_ROLE_KEY;

        // Trava explícita contra testes destrutivos fora de ambiente seguro
        validarDestinoSeguro();

        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    private void validarDestinoSeguro() {
        try {
            URI uri = URI.create(this.url);
            String host = uri.getHost();
            boolean isLocal = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);

            boolean isHomologacaoExplicitamenteConfirmada = "homologacao".equalsIgnoreCase(System.getProperty("supabase.test.env"))
                    || "homologacao".equalsIgnoreCase(System.getenv("SUPABASE_TEST_ENV"))
                    || "true".equalsIgnoreCase(System.getProperty("supabase.test.allow-destructive"))
                    || "true".equalsIgnoreCase(System.getenv("SUPABASE_TEST_ALLOW_DESTRUCTIVE"));

            if (!isLocal && !isHomologacaoExplicitamenteConfirmada) {
                throw new SecurityException("BLOQUEIO DE SEGURANÇA: Execução destrutiva de testes recusada no host remoto: '" + host + "'. "
                        + "Testes que criam e deletam usuários só são autorizados em localhost ou quando explicitamente configurado SUPABASE_TEST_ENV=homologacao ou SUPABASE_TEST_ALLOW_DESTRUCTIVE=true.");
            }

            if (!isLocal && DEFAULT_SERVICE_ROLE_KEY.equals(this.serviceRoleKey)) {
                throw new SecurityException("BLOQUEIO DE SEGURANÇA: É proibido utilizar a chave service_role padrão em endpoint remoto!");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL de teste inválida: " + this.url, e);
        }
    }

    public String getUrl() {
        return url;
    }

    public String getAnonKey() {
        return anonKey;
    }

    public boolean isDisponivel() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", anonKey);
            ResponseEntity<String> res = restTemplate.exchange(url + "/rest/v1/", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    public TestUser criarUsuario(String prefixo) {
        String timestamp = UUID.randomUUID().toString().substring(0, 8);
        String email = prefixo.toLowerCase() + "_" + timestamp + "@teste.local";
        String senha = "SenhaSegura@" + timestamp + "A!";

        // 1. Cria usuário via Admin API
        String adminUrl = url + "/auth/v1/admin/users";
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.set("apikey", anonKey);
        adminHeaders.setBearerAuth(serviceRoleKey);

        Map<String, Object> body = Map.of(
                "email", email,
                "password", senha,
                "email_confirm", true
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, adminHeaders);
        ResponseEntity<Map> adminRes = restTemplate.postForEntity(adminUrl, entity, Map.class);
        if (!adminRes.getStatusCode().is2xxSuccessful() || adminRes.getBody() == null) {
            throw new IllegalStateException("Falha ao criar usuário de teste no Supabase: status=" + adminRes.getStatusCode());
        }

        UUID userId = UUID.fromString((String) adminRes.getBody().get("id"));

        // 2. Autentica usuário para obter tokens JWT
        String tokenUrl = url + "/auth/v1/token?grant_type=password";
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.set("apikey", anonKey);

        Map<String, Object> tokenBody = Map.of(
                "email", email,
                "password", senha
        );

        ResponseEntity<TokenResponse> tokenRes = restTemplate.postForEntity(
                tokenUrl,
                new HttpEntity<>(tokenBody, authHeaders),
                TokenResponse.class
        );

        if (!tokenRes.getStatusCode().is2xxSuccessful() || tokenRes.getBody() == null) {
            throw new IllegalStateException("Falha ao autenticar usuário recém-criado: status=" + tokenRes.getStatusCode());
        }

        return new TestUser(
                userId,
                email,
                senha,
                tokenRes.getBody().accessToken,
                tokenRes.getBody().refreshToken
        );
    }

    public void removerUsuario(UUID userId) {
        if (userId == null) return;
        String adminUrl = url + "/auth/v1/admin/users/" + userId;
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.set("apikey", anonKey);
        adminHeaders.setBearerAuth(serviceRoleKey);

        ResponseEntity<String> response = restTemplate.exchange(adminUrl, HttpMethod.DELETE, new HttpEntity<>(adminHeaders), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Falha comprovada ao excluir usuário de teste " + userId + ": status=" + response.getStatusCode());
        }
    }

    public static class TestUser {
        private final UUID id;
        private final String email;
        private final String senha;
        private final String accessToken;
        private final String refreshToken;

        public TestUser(UUID id, String email, String senha, String accessToken, String refreshToken) {
            this.id = id;
            this.email = email;
            this.senha = senha;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public UUID getId() { return id; }
        public String getEmail() { return email; }
        public String getSenha() { return senha; }
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {
        @JsonProperty("access_token")
        public String accessToken;
        @JsonProperty("refresh_token")
        public String refreshToken;
    }
}
