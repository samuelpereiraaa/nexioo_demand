package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.Projeto;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de repositório para persistência de {@link Projeto}.
 */
public interface ProjetoRepository {

    Projeto salvar(Projeto projeto);

    Optional<Projeto> buscarPorId(Long id);

    List<Projeto> listarTodos();

    List<Projeto> listarPorArea(Long areaTrabalhoId);

    List<Projeto> listarRecentes();

    void excluir(Long id);
}

