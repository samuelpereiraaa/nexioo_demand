package br.com.nexioo.demand.model;

/**
 * Item individual dentro de uma checklist de demanda.
 */
public class ChecklistItem {

    private java.util.UUID id;
    private String texto;
    private boolean concluido;

    public ChecklistItem() {
        this.id = java.util.UUID.randomUUID();
    }

    public ChecklistItem(java.util.UUID id, String texto, boolean concluido) {
        this.id = id != null ? id : java.util.UUID.randomUUID();
        this.texto = texto;
        this.concluido = concluido;
    }

    public ChecklistItem(Object id, String texto, boolean concluido) {
        this(br.com.nexioo.demand.util.IdUtils.parseUuid(id), texto, concluido);
    }

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }
    public void setId(Object id) { this.id = br.com.nexioo.demand.util.IdUtils.parseUuid(id); }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public boolean isConcluido() { return concluido; }
    public void setConcluido(boolean concluido) { this.concluido = concluido; }
}
