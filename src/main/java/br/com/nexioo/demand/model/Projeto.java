package br.com.nexioo.demand.model;

import br.com.nexioo.demand.util.IdUtils;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade que representa um Projeto / Quadro no sistema.
 */
public class Projeto {

    private UUID id;
    private UUID usuarioId;
    private UUID areaTrabalhoId;
    private String nome;
    private String descricao;
    private String gradiente;
    private LocalDate dataCriacao;
    private int membrosCount;
    private int quantidadeDemandas;
    private String usuarioProprietario;

    public Projeto() {
        this.dataCriacao = LocalDate.now();
        this.membrosCount = 1;
        this.gradiente = "linear-gradient(135deg, #a855f7, #ec4899)";
    }

    public Projeto(UUID id, String nome, String descricao, String gradiente) {
        this.id = id != null ? id : UUID.randomUUID();
        this.nome = nome;
        this.descricao = descricao;
        this.gradiente = gradiente != null ? gradiente : "linear-gradient(135deg, #a855f7, #ec4899)";
        this.dataCriacao = LocalDate.now();
        this.membrosCount = 3;
        this.quantidadeDemandas = 5;
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

    public UUID getAreaTrabalhoUuid() {
        return areaTrabalhoId;
    }

    public void setAreaTrabalhoUuid(UUID areaTrabalhoUuid) {
        this.areaTrabalhoId = areaTrabalhoUuid;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getGradiente() {
        return gradiente;
    }

    public void setGradiente(String gradiente) {
        this.gradiente = gradiente;
    }

    public LocalDate getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDate dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public int getMembrosCount() {
        return membrosCount;
    }

    public void setMembrosCount(int membrosCount) {
        this.membrosCount = membrosCount;
    }



    public int getQuantidadeDemandas() {
        return quantidadeDemandas;
    }

    public void setQuantidadeDemandas(int quantidadeDemandas) {
        this.quantidadeDemandas = quantidadeDemandas;
    }

    public UUID getAreaTrabalhoId() {
        return areaTrabalhoId;
    }

    public void setAreaTrabalhoId(UUID areaTrabalhoId) {
        this.areaTrabalhoId = areaTrabalhoId;
    }

    public void setAreaTrabalhoId(Object areaTrabalhoId) {
        this.areaTrabalhoId = IdUtils.parseUuid(areaTrabalhoId);
    }

    public String getUsuarioProprietario() {
        return usuarioProprietario;
    }

    public void setUsuarioProprietario(String usuarioProprietario) {
        this.usuarioProprietario = usuarioProprietario;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Projeto projeto = (Projeto) o;
        return Objects.equals(id, projeto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
