package br.com.nexioo.demand.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa um anexo vinculado a uma Demanda (arquivo local ou URL externa).
 */
public class Anexo {

    private String id;
    private String nome;
    private String url;
    private String storagePath;
    private java.util.UUID usuarioId;
    private LocalDateTime dataCriacao;
    private boolean capa;

    public Anexo() {
        this.id = UUID.randomUUID().toString();
        this.dataCriacao = LocalDateTime.now();
        this.capa = false;
    }

    public Anexo(String nome, String url) {
        this.id = UUID.randomUUID().toString();
        this.nome = (nome != null && !nome.isBlank()) ? nome.trim() : "Anexo";
        this.url = url;
        this.dataCriacao = LocalDateTime.now();
        this.capa = false;
    }

    public Anexo(String id, String nome, String url, LocalDateTime dataCriacao, boolean capa) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        this.nome = (nome != null && !nome.isBlank()) ? nome.trim() : "Anexo";
        this.url = url;
        this.dataCriacao = (dataCriacao != null) ? dataCriacao : LocalDateTime.now();
        this.capa = capa;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public java.util.UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(java.util.UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public boolean isCapa() {
        return capa;
    }

    public void setCapa(boolean capa) {
        this.capa = capa;
    }

    public boolean isImagem() {
        if (url != null) {
            String lower = url.toLowerCase();
            if (lower.startsWith("data:image/")
                    || lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".gif")
                    || lower.contains("images.unsplash.com")
                    || lower.contains("picsum.photos")) {
                return true;
            }
        }
        if (nome != null) {
            String nomeLower = nome.toLowerCase();
            return nomeLower.endsWith(".png")
                    || nomeLower.endsWith(".jpg")
                    || nomeLower.endsWith(".jpeg")
                    || nomeLower.endsWith(".webp")
                    || nomeLower.endsWith(".gif");
        }
        return false;
    }


    public String getDataFormatada() {
        return "Adicionado há pouco";
    }
}
