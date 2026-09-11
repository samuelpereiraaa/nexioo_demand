package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.util.IdUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de repositório para persistência de {@link Projeto}.
 */
public interface ProjetoRepository {

    Projeto salvar(Projeto projeto);

    Optional<Projeto> buscarPorId(UUID id);

    default Optional<Projeto> buscarPorId(Object id) {
        if (id == null) return Optional.empty();
        return buscarPorId(IdUtils.parseUuid(id));
    }

    Optional<Projeto> buscarPorIdEUsuario(UUID id, UUID usuarioId);

    default Optional<Projeto> buscarPorIdEUsuario(Object id, UUID usuarioId) {
        if (id == null) return Optional.empty();
        return buscarPorIdEUsuario(IdUtils.parseUuid(id), usuarioId);
    }

    List<Projeto> listarPorUsuario(UUID usuarioId);

    List<Projeto> listarPorAreaEUsuario(UUID areaTrabalhoId, UUID usuarioId);

    default List<Projeto> listarPorAreaEUsuario(Object areaTrabalhoId, UUID usuarioId) {
        if (areaTrabalhoId == null) return listarPorUsuario(usuarioId);
        return listarPorAreaEUsuario(IdUtils.parseUuid(areaTrabalhoId), usuarioId);
    }

    List<Projeto> buscarPorIdsEUsuario(List<UUID> ids, UUID usuarioId);

    List<Projeto> listarTodos();

    List<Projeto> listarPorArea(UUID areaTrabalhoId);

    default List<Projeto> listarPorArea(Object areaTrabalhoId) {
        if (areaTrabalhoId == null) return listarTodos();
        return listarPorArea(IdUtils.parseUuid(areaTrabalhoId));
    }

    List<Projeto> listarRecentes();

    void excluir(UUID id);

    default void excluir(Object id) {
        if (id != null) {
            excluir(IdUtils.parseUuid(id));
        }
    }

    void excluirPorIdEUsuario(UUID id, UUID usuarioId);

    default void excluirPorIdEUsuario(Object id, UUID usuarioId) {
        if (id != null) {
            excluirPorIdEUsuario(IdUtils.parseUuid(id), usuarioId);
        }
    }
}
