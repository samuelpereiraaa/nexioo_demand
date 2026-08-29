package br.com.nexioo.demand.model;

/**
 * Representa as colunas do quadro Kanban.
 * A ordem de declaração define a ordem de exibição no quadro.
 */
public enum Coluna {

    BACKLOG("Backlog"),
    A_FAZER("A Fazer"),
    EM_ANDAMENTO("Em Andamento"),
    CONCLUIDO("Concluído");

    private final String descricao;

    Coluna(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
