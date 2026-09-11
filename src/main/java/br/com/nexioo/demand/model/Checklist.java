package br.com.nexioo.demand.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de verificação (Checklist) associada a uma demanda.
 */
public class Checklist {

    private java.util.UUID id;
    private String titulo;
    private List<ChecklistItem> itens = new ArrayList<>();

    public Checklist() {
        this.id = java.util.UUID.randomUUID();
    }

    public Checklist(java.util.UUID id, String titulo) {
        this.id = id != null ? id : java.util.UUID.randomUUID();
        this.titulo = (titulo != null && !titulo.isBlank()) ? titulo.trim() : "Checklist";
    }

    public Checklist(Object id, String titulo) {
        this(br.com.nexioo.demand.util.IdUtils.parseUuid(id), titulo);
    }

    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID id) { this.id = id; }
    public void setId(Object id) { this.id = br.com.nexioo.demand.util.IdUtils.parseUuid(id); }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public List<ChecklistItem> getItens() { return itens; }
    public void setItens(List<ChecklistItem> itens) { this.itens = itens; }

    public int getItensConcluidos() {
        if (itens == null) return 0;
        int count = 0;
        for (ChecklistItem item : itens) {
            if (item.isConcluido()) count++;
        }
        return count;
    }

    public int getTotalItens() {
        return itens != null ? itens.size() : 0;
    }

    public int getProgressoPercentual() {
        int total = getTotalItens();
        if (total == 0) return 0;
        return (int) Math.round(((double) getItensConcluidos() / total) * 100);
    }

    public String getProgressoTexto() {
        return getItensConcluidos() + "/" + getTotalItens();
    }
}
