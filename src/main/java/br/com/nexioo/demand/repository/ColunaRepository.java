package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.Coluna;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de repositório para persistência de {@link Coluna}.
 */
public interface ColunaRepository {

    Coluna salvar(Coluna coluna);

    Optional<Coluna> buscarPorId(String id);

    Optional<Coluna> buscarPorIdEUsuario(String id, UUID usuarioId);

    Optional<Coluna> buscarPorIdEProjetoEUsuario(String id, UUID projetoId, UUID usuarioId);

    default Optional<Coluna> buscarPorUuid(UUID uuid) {
        return uuid != null ? buscarPorId(uuid.toString()) : Optional.empty();
    }

    List<Coluna> listarTodas();

    List<Coluna> listarPorProjeto(UUID projetoId);

    List<Coluna> listarPorProjetoEUsuario(UUID projetoId, UUID usuarioId);

    boolean existePorId(String id);

    void excluir(String id);

    void excluirPorIdEProjetoEUsuario(String id, UUID projetoId, UUID usuarioId);
}
