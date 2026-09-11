package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.SupabaseUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Serviço de autenticação oficial integrado ao Supabase GoTrue Auth (/auth/v1/).
 * Não utiliza chaves ou URLs sensíveis hardcoded no código-fonte.
 */
@Service
public class SupabaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseAuthService.class);

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SupabaseAuthService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.anon-key:}") String supabaseAnonKey,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        String url = (supabaseUrl != null) ? supabaseUrl.trim() : "";
        this.supabaseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.supabaseAnonKey = (supabaseAnonKey != null) ? supabaseAnonKey.trim() : "";
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    private void validarConfiguracao() {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            throw new IllegalStateException("Configuração de autenticação incompleta: SUPABASE_URL e SUPABASE_ANON_KEY são obrigatórios.");
        }
    }

    public SupabaseUser autenticar(String email, String password) {
        validarConfiguracao();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
        }

        String url = supabaseUrl + "/auth/v1/token?grant_type=password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseAnonKey);

        Map<String, String> body = new HashMap<>();
        body.put("email", email.trim());
        body.put("password", password);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseUserResponse(response.getBody(), null);
            }
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        } catch (HttpClientErrorException e) {
            log.warn("Falha na autenticação via Supabase: status={}", e.getRawStatusCode());
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        } catch (ResourceAccessException e) {
            log.error("Falha de conexão com o servidor de autenticação Supabase");
            throw new IllegalArgumentException("Não foi possível conectar ao servidor de autenticação.");
        } catch (Exception e) {
            log.error("Erro inesperado durante autenticação: {}", e.getMessage());
            throw new IllegalArgumentException("E-mail ou senha inválidos.");
        }
    }

    public SupabaseUser validarToken(String token) {
        validarConfiguracao();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de autenticação não informado.");
        }

        String url = supabaseUrl + "/auth/v1/user";

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseAnonKey);
        headers.setBearerAuth(token.trim());

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseUserResponse(response.getBody(), token.trim());
            }
            throw new IllegalArgumentException("Sessão inválida ou expirada.");
        } catch (HttpClientErrorException e) {
            log.warn("Token inválido ou expirado no Supabase: status={}", e.getRawStatusCode());
            throw new IllegalArgumentException("Sessão inválida ou expirada.");
        } catch (ResourceAccessException e) {
            log.error("Falha de conexão ao validar token no Supabase");
            throw new IllegalArgumentException("Não foi possível conectar ao servidor de autenticação.");
        } catch (Exception e) {
            log.error("Erro ao validar token: {}", e.getMessage());
            throw new IllegalArgumentException("Sessão inválida ou expirada.");
        }
    }

    public SupabaseUser cadastrar(String email, String password, String nome) {
        validarConfiguracao();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("E-mail e senha são obrigatórios.");
        }

        String url = supabaseUrl + "/auth/v1/signup";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseAnonKey);

        Map<String, Object> body = new HashMap<>();
        body.put("email", email.trim());
        body.put("password", password);
        if (nome != null && !nome.isBlank()) {
            Map<String, String> data = new HashMap<>();
            data.put("full_name", nome.trim());
            body.put("data", data);
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseUserResponse(response.getBody(), null);
            }
            throw new IllegalArgumentException("Erro ao criar conta.");
        } catch (HttpClientErrorException e) {
            log.warn("Erro ao cadastrar usuário no Supabase: status={}", e.getRawStatusCode());
            if (e.getRawStatusCode() == 422) {
                throw new IllegalArgumentException("Este e-mail já está cadastrado.");
            }
            throw new IllegalArgumentException("Erro ao criar conta.");
        } catch (ResourceAccessException e) {
            log.error("Falha de conexão ao cadastrar usuário no Supabase");
            throw new IllegalArgumentException("Não foi possível conectar ao servidor de autenticação.");
        } catch (Exception e) {
            log.error("Erro inesperado ao cadastrar usuário: {}", e.getMessage());
            throw new IllegalArgumentException("Erro ao criar conta.");
        }
    }

    public SupabaseUser renovarToken(String refreshToken) {
        validarConfiguracao();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token não informado.");
        }

        String url = supabaseUrl + "/auth/v1/token?grant_type=refresh_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseAnonKey);

        Map<String, String> body = new HashMap<>();
        body.put("refresh_token", refreshToken.trim());

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseUserResponse(response.getBody(), null);
            }
            throw new IllegalArgumentException("Sessão expirada.");
        } catch (Exception e) {
            log.warn("Falha ao renovar token no Supabase: {}", e.getMessage());
            throw new IllegalArgumentException("Sessão expirada.");
        }
    }

    public void revogarSessao(String accessToken) {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank() || accessToken == null || accessToken.isBlank()) {
            return;
        }
        String url = supabaseUrl + "/auth/v1/logout";
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseAnonKey);
        headers.setBearerAuth(accessToken.trim());
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            log.debug("Erro ao revogar sessão no Supabase Auth: {}", e.getMessage());
        }
    }

    private SupabaseUser parseUserResponse(String jsonString, String fallbackToken) {
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            String accessToken = fallbackToken;
            if (root.has("access_token") && !root.get("access_token").isNull()) {
                accessToken = root.get("access_token").asText();
            }
            String refreshToken = null;
            if (root.has("refresh_token") && !root.get("refresh_token").isNull()) {
                refreshToken = root.get("refresh_token").asText();
            }
            Long expiresIn = null;
            if (root.has("expires_in") && !root.get("expires_in").isNull()) {
                expiresIn = root.get("expires_in").asLong();
            }

            JsonNode userNode = root.has("user") ? root.get("user") : root;

            String id = userNode.has("id") ? userNode.get("id").asText() : "";
            String email = userNode.has("email") ? userNode.get("email").asText() : "";

            String nome = "";
            if (userNode.has("user_metadata")) {
                JsonNode meta = userNode.get("user_metadata");
                if (meta.has("full_name") && !meta.get("full_name").asText().isBlank()) {
                    nome = meta.get("full_name").asText();
                } else if (meta.has("name") && !meta.get("name").asText().isBlank()) {
                    nome = meta.get("name").asText();
                }
            }

            if (nome.isBlank() && !email.isBlank()) {
                String prefix = email.split("@")[0];
                nome = capitalizeWords(prefix.replace(".", " ").replace("_", " ").replace("-", " "));
            }

            return new SupabaseUser(id, email, nome, accessToken, refreshToken, expiresIn);
        } catch (Exception e) {
            log.error("Erro ao fazer parse da resposta do Supabase: {}", e.getMessage());
            throw new IllegalArgumentException("Erro ao processar dados da autenticação.");
        }
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isBlank()) return "";
        String[] parts = str.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) {
                    sb.append(p.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
