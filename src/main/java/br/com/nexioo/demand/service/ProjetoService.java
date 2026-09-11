package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.util.IdUtils;

import java.util.List;
import java.util.UUID;

/**
 * Casos de uso e regras de negócio para Projetos.
 */
public interface ProjetoService {

    List<Projeto> listarTodos();

    List<Projeto> listarPorArea(UUID areaTrabalhoId);

    default List<Projeto> listarPorArea(Object areaTrabalhoId) {
        if (areaTrabalhoId == null) return listarTodos();
        return listarPorArea(IdUtils.parseUuid(areaTrabalhoId));
    }

    List<Projeto> listarRecentes();

    Projeto buscarPorId(UUID id);

    default Projeto buscarPorId(Object id) {
        if (id == null) return null;
        return buscarPorId(IdUtils.parseUuid(id));
    }

    Projeto criar(ProjetoForm form);

    void excluir(UUID id);

    default void excluir(Object id) {
        if (id != null) {
            excluir(IdUtils.parseUuid(id));
        }
    }

    void marcarComoRecente(UUID id);

    default void marcarComoRecente(Object id) {
        if (id != null) {
            marcarComoRecente(IdUtils.parseUuid(id));
        }
    }

    void registrarVisualizacao(UUID quadroId);

    default void registrarVisualizacao(Object quadroId) {
        if (quadroId != null) {
            registrarVisualizacao(IdUtils.parseUuid(quadroId));
        }
    }
}
