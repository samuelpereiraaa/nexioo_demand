package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.util.IdUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AreaTrabalhoRepository {

    List<AreaTrabalho> listarTodas();

    List<AreaTrabalho> listarPorUsuario(String usuario);

    List<AreaTrabalho> listarPorUsuario(UUID usuarioId);

    Optional<AreaTrabalho> buscarPorId(UUID id);

    default Optional<AreaTrabalho> buscarPorId(Object id) {
        if (id == null) return Optional.empty();
        return buscarPorId(IdUtils.parseUuid(id));
    }

    Optional<AreaTrabalho> buscarPorIdEUsuario(UUID id, UUID usuarioId);

    default Optional<AreaTrabalho> buscarPorIdEUsuario(Object id, UUID usuarioId) {
        if (id == null) return Optional.empty();
        return buscarPorIdEUsuario(IdUtils.parseUuid(id), usuarioId);
    }

    AreaTrabalho salvar(AreaTrabalho areaTrabalho);

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
