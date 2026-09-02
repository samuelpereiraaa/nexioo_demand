package br.com.nexioo.demand.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade central do sistema.
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

    private boolean concluido = false;
    private String imagemUrl;

    private List<Etiqueta> etiquetas = new ArrayList<>();

    private List<Checklist> checklists = new ArrayList<>();
    private List<String> membros = new ArrayList<>();
    private boolean acompanhando = false;

    private List<ItemAtividade> atividades = new ArrayList<>();

    public Demanda() {
    }

    public static class ItemAtividade {
        private String autor;
        private String texto;
        private LocalDateTime dataHora;
        private boolean comentario;

        public ItemAtividade() {}

        public ItemAtividade(String autor, String texto, LocalDateTime dataHora, boolean comentario) {
            this.autor = autor;
            this.texto = texto;
            this.dataHora = dataHora;
            this.comentario = comentario;
        }

        public String getAutor() { return autor; }
        public void setAutor(String autor) { this.autor = autor; }

        public String getTexto() { return texto; }
        public void setTexto(String texto) { this.texto = texto; }

        public LocalDateTime getDataHora() { return dataHora; }
        public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

        public boolean isComentario() { return comentario; }
        public void setComentario(boolean comentario) { this.comentario = comentario; }

        public String getIniciaisAutor() {
            if (autor == null || autor.isBlank()) return "ND";
            String[] partes = autor.trim().split("\\s+");
            if (partes.length == 1) return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase();
            return ("" + partes[0].charAt(0) + partes[partes.length - 1].charAt(0)).toUpperCase();
        }
    }

    public boolean isPrazoVencido() {
        return prazo != null && prazo.isBefore(LocalDate.now());
    }

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

    public int getAvatarCorIndex() {
        if (responsavel == null || responsavel.isBlank()) {
            return 0;
        }
        return Math.abs(responsavel.hashCode() % 5);
    }

    public void registrarAtividade(String autor, String texto) {
        registrarAtividade(autor, texto, false);
    }

    public void registrarComentario(String autor, String texto) {
        registrarAtividade(autor, texto, true);
    }

    private void registrarAtividade(String autor, String texto, boolean isComentario) {
        if (this.atividades == null) {
            this.atividades = new ArrayList<>();
        }
        this.atividades.add(0, new ItemAtividade(
                (autor != null && !autor.isBlank()) ? autor : "Samuel Oliveira",
                texto,
                LocalDateTime.now(),
                isComentario
        ));
    }

    public boolean hasChecklist() {
        return checklists != null && !checklists.isEmpty();
    }

    public int getTotalChecklistItens() {
        if (checklists == null) return 0;
        int total = 0;
        for (Checklist c : checklists) {
            total += c.getTotalItens();
        }
        return total;
    }

    public int getConcluidosChecklistItens() {
        if (checklists == null) return 0;
        int count = 0;
        for (Checklist c : checklists) {
            count += c.getItensConcluidos();
        }
        return count;
    }

    public String getChecklistProgressoTexto() {
        return getConcluidosChecklistItens() + "/" + getTotalChecklistItens();
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

    public List<Etiqueta> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(List<Etiqueta> etiquetas) {
        this.etiquetas = etiquetas;
    }

    public List<Checklist> getChecklists() {
        return checklists;
    }

    public void setChecklists(List<Checklist> checklists) {
        this.checklists = checklists;
    }

    public List<String> getMembros() {
        return membros;
    }

    public void setMembros(List<String> membros) {
        this.membros = membros;
    }

    public boolean isAcompanhando() {
        return acompanhando;
    }

    public void setAcompanhando(boolean acompanhando) {
        this.acompanhando = acompanhando;
    }

    public List<ItemAtividade> getAtividades() {
        return atividades;
    }

    public void setAtividades(List<ItemAtividade> atividades) {
        this.atividades = atividades;
    }

    public boolean isConcluido() {
        return concluido;
    }

    public boolean isConcluida() {
        return concluido;
    }

    public void setConcluido(boolean concluido) {
        this.concluido = concluido;
    }

    public void setConcluida(boolean concluida) {
        this.concluido = concluida;
    }

    public String getImagemUrl() {
        return imagemUrl;
    }

    public void setImagemUrl(String imagemUrl) {
        this.imagemUrl = imagemUrl;
    }
}
