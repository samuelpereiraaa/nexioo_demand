package br.com.nexioo.demand.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de verificação (Checklist) associada a uma demanda.
 */
public class Checklist {

    private Long id;
    private String titulo;
    private List<ChecklistItem> itens = new ArrayList<>();

    public Checklist() {}

    public Checklist(Long id, String titulo) {
        this.id = id;
        this.titulo = (titulo != null && !titulo.isBlank()) ? titulo.trim() : "Checklist";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
