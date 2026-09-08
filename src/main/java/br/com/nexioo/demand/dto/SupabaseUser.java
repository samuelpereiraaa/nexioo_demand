package br.com.nexioo.demand.dto;

import java.io.Serializable;

public class SupabaseUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String email;
    private String nome;
    private String accessToken;

    public SupabaseUser() {
    }

    public SupabaseUser(String id, String email, String nome, String accessToken) {
        this.id = id;
        this.email = email;
        this.nome = nome;
        this.accessToken = accessToken;
    }

    public String getId() {
        return id;
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

    @Override
    public String toString() {
        return "SupabaseUser{id='" + id + "', email='" + email + "', nome='" + nome + "'}";
    }
}
