package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.ProjetoRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementação em memória do repositório de {@link Projeto}.
 * Particionada rigorosamente por usuarioId com validação estrita de propriedade.
 */
@Repository
@Profile({"test", "dev-memory"})
public class ProjetoRepositoryMemory implements ProjetoRepository {

    private final Map<UUID, Map<UUID, Projeto>> dadosPorUsuario = new ConcurrentHashMap<>();

    @Override
    public Projeto salvar(Projeto projeto) {
        if (projeto.getId() == null) {
            projeto.setId(UUID.randomUUID());
        }
        UUID uid = projeto.getUsuarioId();
        if (uid == null) {
            throw new IllegalArgumentException("Não é permitido persistir projeto privado sem usuarioId.");
        }
        dadosPorUsuario.computeIfAbsent(uid, k -> new ConcurrentHashMap<>()).put(projeto.getId(), projeto);
        return projeto;
    }

    @Override
    public Optional<Projeto> buscarPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return Optional.empty();
        Map<UUID, Projeto> map = dadosPorUsuario.get(usuarioId);
        if (map != null) {
            return Optional.ofNullable(map.get(id));
        }
        return Optional.empty();
    }

    @Override
    public List<Projeto> listarPorUsuario(UUID usuarioId) {
        if (usuarioId == null) return Collections.emptyList();
        Map<UUID, Projeto> map = dadosPorUsuario.get(usuarioId);
        if (map == null) return Collections.emptyList();
        return new ArrayList<>(map.values());
    }

    @Override
    public List<Projeto> listarPorAreaEUsuario(UUID areaTrabalhoId, UUID usuarioId) {
        if (usuarioId == null || areaTrabalhoId == null) return Collections.emptyList();
        Map<UUID, Projeto> map = dadosPorUsuario.get(usuarioId);
        if (map == null) return Collections.emptyList();
        return map.values().stream()
                .filter(p -> areaTrabalhoId.equals(p.getAreaTrabalhoUuid()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Projeto> buscarPorIdsEUsuario(List<UUID> ids, UUID usuarioId) {
        if (ids == null || ids.isEmpty() || usuarioId == null) return Collections.emptyList();
        Map<UUID, Projeto> map = dadosPorUsuario.get(usuarioId);
        if (map == null) return Collections.emptyList();

        List<Projeto> result = new ArrayList<>();
        for (UUID id : ids) {
            Projeto p = map.get(id);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public void excluirPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return;
        Map<UUID, Projeto> map = dadosPorUsuario.get(usuarioId);
        if (map != null) {
            map.remove(id);
        }
    }

    @Override
    public Optional<Projeto> buscarPorId(UUID uuid) {
        if (uuid == null) return Optional.empty();
        for (Map<UUID, Projeto> map : dadosPorUsuario.values()) {
            Projeto p = map.get(uuid);
            if (p != null) return Optional.of(p);
        }
        return Optional.empty();
    }

    @Override
    public List<Projeto> listarTodos() {
        return Collections.emptyList();
    }

    @Override
    public List<Projeto> listarPorArea(UUID areaTrabalhoId) {
        return Collections.emptyList();
    }

    @Override
    public List<Projeto> listarRecentes() {
        return Collections.emptyList();
    }

    @Override
    public void excluir(UUID uuid) {
        if (uuid != null) {
            for (Map<UUID, Projeto> map : dadosPorUsuario.values()) {
                map.remove(uuid);
            }
        }
    }
}
