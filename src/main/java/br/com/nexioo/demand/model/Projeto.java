package br.com.nexioo.demand.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidade que representa um Projeto / Quadro no sistema.
 */
public class Projeto {

    private Long id;
    private String nome;
    private String descricao;
    private String gradiente;
    private LocalDate dataCriacao;
    private int membrosCount;
    private boolean recentementeVisualizado;
    private int quantidadeDemandas;

    private Long areaTrabalhoId;
    private String usuarioProprietario;

    public Projeto() {
        this.dataCriacao = LocalDate.now();
        this.membrosCount = 1;
        this.gradiente = "linear-gradient(135deg, #a855f7, #ec4899)";
        this.areaTrabalhoId = 1L;
    }


    public Projeto(Long id, String nome, String descricao, String gradiente, boolean recentementeVisualizado) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.gradiente = gradiente != null ? gradiente : "linear-gradient(135deg, #a855f7, #ec4899)";
        this.dataCriacao = LocalDate.now();
        this.membrosCount = 3;
        this.recentementeVisualizado = recentementeVisualizado;
        this.quantidadeDemandas = 5;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isRecentementeVisualizado() {
        return recentementeVisualizado;
    }

    public void setRecentementeVisualizado(boolean recentementeVisualizado) {
        this.recentementeVisualizado = recentementeVisualizado;
    }

    public int getQuantidadeDemandas() {
        return quantidadeDemandas;
    }

    public void setQuantidadeDemandas(int quantidadeDemandas) {
        this.quantidadeDemandas = quantidadeDemandas;
    }

    public Long getAreaTrabalhoId() {
        return areaTrabalhoId;
    }

    public void setAreaTrabalhoId(Long areaTrabalhoId) {
        this.areaTrabalhoId = areaTrabalhoId;
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
