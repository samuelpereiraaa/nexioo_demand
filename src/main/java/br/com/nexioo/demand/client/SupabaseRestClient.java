package br.com.nexioo.demand.client;

import br.com.nexioo.demand.config.UserContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para a Data API do Supabase (PostgREST /rest/v1/).
 * Propaga obrigatoriamente o JWT do UserContext como Authorization: Bearer,
 * garantindo a avaliação nativa do Row Level Security (RLS) no PostgreSQL.
 * A anonKey nunca é utilizada como token de autorização Bearer.
 */
@Component
public class SupabaseRestClient {

    private static final Logger log = LoggerFactory.getLogger(SupabaseRestClient.class);

    private final String restUrl;
    private final String anonKey;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserContext userContext;

    public SupabaseRestClient(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.anon-key:}") String anonKey,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            UserContext userContext) {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new IllegalStateException("Configuração obrigatória ausente: SUPABASE_URL não foi informada.");
        }
        if (anonKey == null || anonKey.isBlank()) {
            throw new IllegalStateException("Configuração obrigatória ausente: SUPABASE_ANON_KEY não foi informada.");
        }
        String cleanUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.restUrl = cleanUrl + "/rest/v1";
        this.anonKey = anonKey;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = objectMapper;
        this.userContext = userContext;
    }

    private HttpHeaders criarHeaders(boolean returnRepresentation, boolean upsert) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", anonKey);

        if (userContext == null) {
            throw new SecurityException("Recurso privado solicitado sem contexto de usuário.");
        }

        // Exige expressamente o ID do usuário e o token JWT válido
        userContext.requireUsuarioId();
        String token = userContext.requireAccessToken();
        headers.setBearerAuth(token);

        List<String> prefers = new ArrayList<>();
        if (returnRepresentation) {
            prefers.add("return=representation");
        }
        if (upsert) {
            prefers.add("resolution=merge-duplicates");
        }
        if (!prefers.isEmpty()) {
            headers.set("Prefer", String.join(",", prefers));
        }
        return headers;
    }

    private HttpHeaders criarHeaders(boolean returnRepresentation) {
        return criarHeaders(returnRepresentation, false);
    }

    public <T> List<T> getList(String endpointAndQuery, Class<T> elementType) {
        String url = restUrl + (endpointAndQuery.startsWith("/") ? endpointAndQuery : "/" + endpointAndQuery);
        HttpEntity<Void> entity = new HttpEntity<>(criarHeaders(false));

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
                return objectMapper.readValue(response.getBody(), type);
            }
            return new ArrayList<>();
        } catch (HttpClientErrorException.NotFound e) {
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Erro ao consultar PostgREST [GET {}]: {}", endpointAndQuery, e.getMessage());
            throw new IllegalStateException("Falha ao consultar dados no Supabase: " + e.getMessage(), e);
        }
    }

    public <T> T post(String endpoint, Object body, Class<T> responseType) {
        String url = restUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        HttpEntity<Object> entity = new HttpEntity<>(body, criarHeaders(true));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, responseType);
                List<T> list = objectMapper.readValue(response.getBody(), listType);
                if (!list.isEmpty()) {
                    return list.get(0);
                }
            }
            throw new IllegalStateException("Falha na gravação PostgREST [" + endpoint + "]: nenhuma linha foi retornada pelo Supabase.");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao inserir via PostgREST [POST {}]: {}", endpoint, e.getMessage());
            throw new IllegalStateException("Falha ao gravar dados no Supabase: " + e.getMessage(), e);
        }
    }

    public void upsert(String endpoint, Object body) {
        String url = restUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        HttpEntity<Object> entity = new HttpEntity<>(body, criarHeaders(false, true));

        try {
            restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
            log.error("Erro ao realizar upsert via PostgREST [POST {}]: {}", endpoint, e.getMessage());
            throw new IllegalStateException("Falha ao salvar dados no Supabase: " + e.getMessage(), e);
        }
    }

    public int patch(String endpointAndQuery, Object body) {
        String url = restUrl + (endpointAndQuery.startsWith("/") ? endpointAndQuery : "/" + endpointAndQuery);
        HttpHeaders headers = criarHeaders(true);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PATCH, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<?> list = objectMapper.readValue(response.getBody(), List.class);
                int count = list.size();
                if (count == 0) {
                    throw new IllegalStateException("Nenhum registro atualizado no Supabase (não encontrado ou acesso bloqueado por RLS): " + endpointAndQuery);
                }
                return count;
            }
            throw new IllegalStateException("Falha ao atualizar dados no Supabase: resposta vazia para " + endpointAndQuery);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao atualizar via PostgREST [PATCH {}]: {}", endpointAndQuery, e.getMessage());
            throw new IllegalStateException("Falha ao atualizar dados no Supabase: " + e.getMessage(), e);
        }
    }

    public int delete(String endpointAndQuery) {
        String url = restUrl + (endpointAndQuery.startsWith("/") ? endpointAndQuery : "/" + endpointAndQuery);
        HttpHeaders headers = criarHeaders(true);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<?> list = objectMapper.readValue(response.getBody(), List.class);
                int count = list.size();
                if (count == 0) {
                    throw new IllegalStateException("Nenhum registro excluído no Supabase (não encontrado ou acesso bloqueado por RLS): " + endpointAndQuery);
                }
                return count;
            }
            throw new IllegalStateException("Falha ao excluir dados no Supabase: resposta vazia para " + endpointAndQuery);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao excluir via PostgREST [DELETE {}]: {}", endpointAndQuery, e.getMessage());
            throw new IllegalStateException("Falha ao excluir dados no Supabase: " + e.getMessage(), e);
        }
    }

    public int deleteOptional(String endpointAndQuery) {
        String url = restUrl + (endpointAndQuery.startsWith("/") ? endpointAndQuery : "/" + endpointAndQuery);
        HttpHeaders headers = criarHeaders(true);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<?> list = objectMapper.readValue(response.getBody(), List.class);
                return list.size();
            }
            return 0;
        } catch (Exception e) {
            log.error("Erro ao realizar exclusão opcional via PostgREST [DELETE {}]: {}", endpointAndQuery, e.getMessage());
            throw new IllegalStateException("Falha ao excluir dados no Supabase: " + e.getMessage(), e);
        }
    }

    public <T> T rpc(String functionName, Map<String, Object> params, Class<T> responseType) {
        String url = restUrl + "/rpc/" + functionName;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, criarHeaders(false));

        try {
            ResponseEntity<T> response = restTemplate.postForEntity(url, entity, responseType);
            return response.getBody();
        } catch (Exception e) {
            log.error("Erro ao executar RPC PostgREST [POST /rpc/{}]: {}", functionName, e.getMessage());
            throw new IllegalStateException("Falha ao executar função transacional no Supabase: " + e.getMessage(), e);
        }
    }
}
