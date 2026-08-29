package br.com.nexioo.demand.model;

/**
 * Item individual dentro de uma checklist de demanda.
 */
public class ChecklistItem {

    private Long id;
    private String texto;
    private boolean concluido;

    public ChecklistItem() {}

    public ChecklistItem(Long id, String texto, boolean concluido) {
        this.id = id;
        this.texto = texto;
        this.concluido = concluido;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public boolean isConcluido() { return concluido; }
    public void setConcluido(boolean concluido) { this.concluido = concluido; }
}
