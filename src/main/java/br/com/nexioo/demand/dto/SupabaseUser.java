package br.com.nexioo.demand.dto;

import java.io.Serializable;
import java.util.UUID;

public class SupabaseUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String email;
    private String nome;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;

    public SupabaseUser() {
    }

    public SupabaseUser(String id, String email, String nome, String accessToken) {
        this(id, email, nome, accessToken, null, null);
    }

    public SupabaseUser(String id, String email, String nome, String accessToken, String refreshToken) {
        this(id, email, nome, accessToken, refreshToken, null);
    }

    public SupabaseUser(String id, String email, String nome, String accessToken, String refreshToken, Long expiresIn) {
        this.id = id;
        this.email = email;
        this.nome = nome;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getId() {
        return id;
    }

    public UUID getUsuarioIdUuid() {
        return br.com.nexioo.demand.util.IdUtils.parseUuid(id);
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    @Override
    public String toString() {
        return "SupabaseUser{id='" + id + "', email='" + email + "', nome='" + nome + "'}";
    }
}
