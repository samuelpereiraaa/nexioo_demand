package br.com.nexioo.demand.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade central do sistema.
 * <p>
 * Na Fase 1, é um POJO puro mantido em memória.
 * Na Fase 2, basta adicionar as anotações JPA ({@code @Entity}, {@code @Id}, etc.)
 * sem alterar nenhuma outra camada.
 */
public class Demanda {

    private Long id;
    private String titulo;
    private String descricao;
    private Coluna coluna;
    private Prioridade prioridade;
    private String responsavel;
    private LocalDate prazo;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public Demanda() {
    }

    /**
     * Retorna {@code true} se o prazo já passou em relação à data atual.
     * Útil para destacar visualmente demandas atrasadas nos templates.
     */
    public boolean isPrazoVencido() {
        return prazo != null && prazo.isBefore(LocalDate.now());
    }

    /**
     * Retorna as iniciais do responsável (até 2 caracteres) para exibição
     * no avatar circular do cartão.
     */
    public String getIniciaisResponsavel() {
        if (responsavel == null || responsavel.isBlank()) {
            return "ND";
        }
        String[] partes = responsavel.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase();
        }
        return ("" + partes[0].charAt(0) + partes[partes.length - 1].charAt(0)).toUpperCase();
    }

    /**
     * Retorna um índice numérico estável baseado no nome do responsável
     * para alternar as cores dos avatares circular de forma harmoniosa.
     */
    public int getAvatarCorIndex() {
        if (responsavel == null || responsavel.isBlank()) {
            return 0;
        }
        return Math.abs(responsavel.hashCode() % 5);
    }

    // ── Getters e Setters ────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Coluna getColuna() {
        return coluna;
    }

    public void setColuna(Coluna coluna) {
        this.coluna = coluna;
    }

    public Prioridade getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(Prioridade prioridade) {
        this.prioridade = prioridade;
    }

    public String getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(String responsavel) {
        this.responsavel = responsavel;
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public void setPrazo(LocalDate prazo) {
        this.prazo = prazo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
