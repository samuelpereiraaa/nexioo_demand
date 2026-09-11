package br.com.nexioo.demand.config;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Objects;
import java.util.UUID;

/**
 * Contexto real do usuário autenticado no escopo da requisição HTTP (@RequestScope).
 * Descartado automaticamente ao término de cada requisição pelo container web.
 */
@Component
@RequestScope
public class UserContext {

    private UUID usuarioId;
    private String email;
    private String nome;
    private String accessToken;

    public void inicializar(UUID usuarioId, String email, String nome, String accessToken) {
        if (this.usuarioId != null) {
            throw new IllegalStateException("UserContext já foi inicializado para esta requisição.");
        }
        this.usuarioId = Objects.requireNonNull(usuarioId, "usuarioId é obrigatório.");
        this.email = (email != null) ? email.trim().toLowerCase() : "";
        this.nome = (nome != null && !nome.isBlank()) ? nome.trim() : this.email;
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken é obrigatório.");
    }

    public boolean isAutenticado() {
        return usuarioId != null && accessToken != null && !accessToken.isBlank();
    }

    public void limpar() {
        this.usuarioId = null;
        this.email = null;
        this.nome = null;
        this.accessToken = null;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public UUID requireUsuarioId() {
        if (usuarioId == null) {
            throw new SecurityException("Operação privada não autorizada: identidade de usuário ausente.");
        }
        return usuarioId;
    }

    public String requireAccessToken() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new SecurityException("Recurso privado solicitado sem JWT autenticado.");
        }
        return accessToken;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public String toString() {
        return "UserContext{" +
                "usuarioId=" + usuarioId +
                ", email='" + email + '\'' +
                ", nome='" + nome + '\'' +
                ", accessToken='[PROTECTED]'" +
                '}';
    }
}
