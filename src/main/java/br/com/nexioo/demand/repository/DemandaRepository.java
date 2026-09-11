package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.util.IdUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de acesso a dados para {@link Demanda}.
 */
public interface DemandaRepository {

    Demanda salvar(Demanda demanda);

    Optional<Demanda> buscarPorUuid(UUID uuid);

    default Optional<Demanda> buscarPorId(UUID id) {
        return buscarPorUuid(id);
    }

    default Optional<Demanda> buscarPorId(Object id) {
        if (id == null) return Optional.empty();
        return buscarPorUuid(IdUtils.parseUuid(id));
    }

    Optional<Demanda> buscarPorIdEUsuario(UUID id, UUID usuarioId);

    default Optional<Demanda> buscarPorIdEUsuario(Object id, UUID usuarioId) {
        if (id == null) return Optional.empty();
        return buscarPorIdEUsuario(IdUtils.parseUuid(id), usuarioId);
    }

    List<Demanda> listarPorUsuario(UUID usuarioId);

    List<Demanda> listarPorProjetoEUsuario(UUID projetoId, UUID usuarioId);

    default List<Demanda> listarPorProjetoEUsuario(Object projetoId, UUID usuarioId) {
        if (projetoId == null) return java.util.Collections.emptyList();
        return listarPorProjetoEUsuario(IdUtils.parseUuid(projetoId), usuarioId);
    }

    boolean existePorIdEUsuario(UUID id, UUID usuarioId);

    List<Demanda> listarTodas();

    List<Demanda> listarPorProjeto(UUID projetoId);

    default List<Demanda> listarPorProjeto(Object projetoId) {
        if (projetoId == null) return listarTodas();
        return listarPorProjeto(IdUtils.parseUuid(projetoId));
    }

    void excluir(UUID uuid);

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

    boolean existePorUuid(UUID uuid);

    default boolean existePorId(UUID id) {
        return existePorUuid(id);
    }

    default boolean existePorId(Object id) {
        return id != null && existePorUuid(IdUtils.parseUuid(id));
    }
}
