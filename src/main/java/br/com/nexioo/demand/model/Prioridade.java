package br.com.nexioo.demand.model;

/**
 * Nível de prioridade de uma demanda.
 * A ordem de declaração reflete o peso crescente.
 */
public enum Prioridade {

    BAIXA("Baixa"),
    MEDIA("Média"),
    ALTA("Alta");

    private final String descricao;

    Prioridade(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
