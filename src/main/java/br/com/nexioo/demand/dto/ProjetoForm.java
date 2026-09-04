package br.com.nexioo.demand.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * DTO para recebimento de dados do formulário de criação de Projeto.
 */
public class ProjetoForm {

    @NotBlank(message = "O nome do projeto é obrigatório.")
    @Size(max = 100, message = "O nome não pode exceder 100 caracteres.")
    private String nome;

    @Size(max = 255, message = "A descrição não pode exceder 255 caracteres.")
    private String descricao;

    private String gradiente = "linear-gradient(135deg, #a855f7, #ec4899)";

    private Long areaTrabalhoId;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getGradiente() {
        return gradiente;
    }

    public void setGradiente(String gradiente) {
        this.gradiente = gradiente;
    }

    public Long getAreaTrabalhoId() {
        return areaTrabalhoId;
    }

    public void setAreaTrabalhoId(Long areaTrabalhoId) {
        this.areaTrabalhoId = areaTrabalhoId;
    }
}

