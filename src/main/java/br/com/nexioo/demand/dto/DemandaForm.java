package br.com.nexioo.demand.dto;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Prioridade;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO de entrada para criação e edição de demandas.
 * Carrega as validações Bean Validation e o binding dos formulários HTML,
 * isolando essas responsabilidades da entidade de domínio.
 */
public class DemandaForm {

    @NotBlank(message = "O título é obrigatório")
    @Size(max = 120, message = "O título deve ter no máximo 120 caracteres")
    private String titulo;

    @Size(max = 2000, message = "A descrição deve ter no máximo 2.000 caracteres")
    private String descricao;

    @NotNull(message = "Selecione uma coluna")
    private Coluna coluna;

    @NotNull(message = "Selecione uma prioridade")
    private Prioridade prioridade;

    @Size(max = 80, message = "O nome do responsável deve ter no máximo 80 caracteres")
    private String responsavel;

    /**
     * Binding com o input type="date" do HTML5 (formato ISO: yyyy-MM-dd).
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate prazo;

    // ── Getters e Setters ────────────────────────────────────────────────────

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

    private java.util.UUID projetoId;

    public java.util.UUID getProjetoId() {
        return projetoId;
    }

    public void setProjetoId(java.util.UUID projetoId) {
        this.projetoId = projetoId;
    }

    public void setProjetoId(Object projetoId) {
        this.projetoId = br.com.nexioo.demand.util.IdUtils.parseUuid(projetoId);
    }
}

