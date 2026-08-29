package br.com.nexioo.demand.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * DTO de entrada para criação de novas colunas/listas no Kanban.
 */
public class ColunaForm {

    @NotBlank(message = "O nome da lista não pode ser vazio")
    @Size(max = 60, message = "O nome da lista deve ter no máximo 60 caracteres")
    private String nome;

    public ColunaForm() {
    }

    public ColunaForm(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
