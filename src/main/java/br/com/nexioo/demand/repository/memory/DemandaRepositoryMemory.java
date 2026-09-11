package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.repository.DemandaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementação em memória do {@link DemandaRepository}.
 * Particionada rigorosamente por usuarioId com validação estrita de propriedade.
 */
@Repository
@Profile({"test", "dev-memory"})
public class DemandaRepositoryMemory implements DemandaRepository {

    private final Map<UUID, Map<UUID, Demanda>> dadosPorUsuario = new ConcurrentHashMap<>();

    @Override
    public Demanda salvar(Demanda demanda) {
        if (demanda.getId() == null) {
            demanda.setId(UUID.randomUUID());
        }
        UUID uid = demanda.getUsuarioId();
        if (uid == null) {
            throw new IllegalArgumentException("Não é permitido persistir demanda privada sem usuarioId.");
        }
        dadosPorUsuario.computeIfAbsent(uid, k -> new ConcurrentHashMap<>()).put(demanda.getId(), demanda);
        return demanda;
    }

    @Override
    public Optional<Demanda> buscarPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return Optional.empty();
        Map<UUID, Demanda> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap != null) {
            return Optional.ofNullable(userMap.get(id));
        }
        return Optional.empty();
    }

    @Override
    public List<Demanda> listarPorUsuario(UUID usuarioId) {
        if (usuarioId == null) return Collections.emptyList();
        Map<UUID, Demanda> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap == null) return Collections.emptyList();
        return new ArrayList<>(userMap.values());
    }

    @Override
    public List<Demanda> listarPorProjetoEUsuario(UUID projetoId, UUID usuarioId) {
        if (usuarioId == null || projetoId == null) return Collections.emptyList();
        Map<UUID, Demanda> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap == null) return Collections.emptyList();

        return userMap.values().stream()
                .filter(d -> projetoId.equals(d.getProjetoId()))
                .collect(Collectors.toList());
    }

    @Override
    public void excluirPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return;
        Map<UUID, Demanda> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap != null) {
            userMap.remove(id);
        }
    }

    @Override
    public boolean existePorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return false;
        Map<UUID, Demanda> userMap = dadosPorUsuario.get(usuarioId);
        return userMap != null && userMap.containsKey(id);
    }

    @Override
    public Optional<Demanda> buscarPorUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        for (Map<UUID, Demanda> userMap : dadosPorUsuario.values()) {
            Demanda d = userMap.get(uuid);
            if (d != null) return Optional.of(d);
        }
        return Optional.empty();
    }

    @Override
    public List<Demanda> listarTodas() {
        return Collections.emptyList();
    }

    @Override
    public List<Demanda> listarPorProjeto(UUID projetoId) {
        return Collections.emptyList();
    }

    @Override
    public void excluir(UUID uuid) {
        if (uuid != null) {
            for (Map<UUID, Demanda> userMap : dadosPorUsuario.values()) {
                userMap.remove(uuid);
            }
        }
    }

    @Override
    public boolean existePorUuid(UUID uuid) {
        if (uuid == null) return false;
        for (Map<UUID, Demanda> map : dadosPorUsuario.values()) {
            if (map.containsKey(uuid)) return true;
        }
        return false;
    }
}
