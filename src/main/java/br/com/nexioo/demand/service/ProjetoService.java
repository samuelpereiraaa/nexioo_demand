package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.Projeto;

import java.util.List;

/**
 * Casos de uso e regras de negócio para Projetos.
 */
public interface ProjetoService {

    List<Projeto> listarTodos();

    List<Projeto> listarRecentes();

    Projeto buscarPorId(Long id);

    Projeto criar(ProjetoForm form);

    void excluir(Long id);

    void marcarComoRecente(Long id);
}
