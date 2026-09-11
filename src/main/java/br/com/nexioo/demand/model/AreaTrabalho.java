package br.com.nexioo.demand.model;

import br.com.nexioo.demand.util.IdUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade que representa uma Área de Trabalho (Workspace) no sistema.
 */
public class AreaTrabalho {

    private UUID id;
    private UUID usuarioId;
    private String nome;
    private String inicial;
    private String usuarioProprietario;
    private LocalDateTime criadoEm;

    public AreaTrabalho() {
        this.criadoEm = LocalDateTime.now();
    }

    public AreaTrabalho(UUID id, String nome, String usuarioProprietario) {
        this.id = id != null ? id : UUID.randomUUID();
        this.nome = nome;
        this.inicial = gerarInicial(nome);
        this.usuarioProprietario = usuarioProprietario;
        this.criadoEm = LocalDateTime.now();
    }

    public AreaTrabalho(Object id, String nome, String usuarioProprietario) {
        this(IdUtils.parseUuid(id), nome, usuarioProprietario);
    }

    public AreaTrabalho(UUID id, String nome, UUID usuarioId, String usuarioProprietario) {
        this.id = id != null ? id : UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.inicial = gerarInicial(nome);
        this.usuarioProprietario = usuarioProprietario;
        this.criadoEm = LocalDateTime.now();
    }

    public static String gerarInicial(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Á";
        }
        String limpo = nome.trim();
        return limpo.substring(0, 1).toUpperCase();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setId(Object id) {
        this.id = IdUtils.parseUuid(id);
    }

    public UUID getUuid() {
        return id;
    }

    public void setUuid(UUID uuid) {
        this.id = uuid;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
        this.inicial = gerarInicial(nome);
    }

    public String getInicial() {
        if (inicial == null || inicial.isBlank()) {
            inicial = gerarInicial(nome);
        }
        return inicial;
    }

    public void setInicial(String inicial) {
        this.inicial = inicial;
    }

    public String getUsuarioProprietario() {
        return usuarioProprietario;
    }

    public void setUsuarioProprietario(String usuarioProprietario) {
        this.usuarioProprietario = usuarioProprietario;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AreaTrabalho that = (AreaTrabalho) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
