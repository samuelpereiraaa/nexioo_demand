package br.com.nexioo.demand.model;

import java.util.Objects;

/**
 * Entidade POJO para etiquetas coloridas aplicadas às demandas.
 */
public class Etiqueta {

    private String id;
    private String nome;
    private String corHex;

    public Etiqueta() {}

    public Etiqueta(String id, String nome, String corHex) {
        this.id = id;
        this.nome = nome;
        this.corHex = corHex;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCorHex() { return corHex; }
    public void setCorHex(String corHex) { this.corHex = corHex; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Etiqueta etiqueta = (Etiqueta) o;
        return Objects.equals(id, etiqueta.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
