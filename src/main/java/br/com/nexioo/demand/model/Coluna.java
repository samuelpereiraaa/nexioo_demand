package br.com.nexioo.demand.model;

import java.util.Objects;

/**
 * Representa as colunas/listas do quadro Kanban.
 * Permite suporte a listas dinâmicas mantendo compatibilidade com as colunas padrão.
 */
public class Coluna {

    private String id;
    private String descricao;
    private int ordem;

    // Colunas padrão do sistema
    public static final Coluna BACKLOG = new Coluna("BACKLOG", "Backlog", 1);
    public static final Coluna A_FAZER = new Coluna("A_FAZER", "A Fazer", 2);
    public static final Coluna EM_ANDAMENTO = new Coluna("EM_ANDAMENTO", "Em Andamento", 3);
    public static final Coluna CONCLUIDO = new Coluna("CONCLUIDO", "Concluído", 4);

    public Coluna() {
    }

    public Coluna(String id, String descricao, int ordem) {
        this.id = id;
        this.descricao = descricao;
        this.ordem = ordem;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    /**
     * Retorna o ID para manter compatibilidade com chamadas ${coluna.name()} nos templates Thymeleaf.
     */
    public String name() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coluna coluna = (Coluna) o;
        return Objects.equals(id, coluna.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
