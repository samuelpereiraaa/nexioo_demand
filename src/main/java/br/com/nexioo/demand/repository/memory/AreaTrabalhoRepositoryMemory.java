package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementação em memória do repositório de {@link AreaTrabalho}.
 * Particionada rigorosamente por usuarioId com validação estrita de propriedade.
 */
@Repository
@Profile({"test", "dev-memory"})
public class AreaTrabalhoRepositoryMemory implements AreaTrabalhoRepository {

    private final Map<UUID, Map<UUID, AreaTrabalho>> dadosPorUsuario = new ConcurrentHashMap<>();

    @Override
    public AreaTrabalho salvar(AreaTrabalho areaTrabalho) {
        if (areaTrabalho.getId() == null) {
            areaTrabalho.setId(UUID.randomUUID());
        }
        UUID uid = areaTrabalho.getUsuarioId();
        if (uid == null) {
            throw new IllegalArgumentException("Não é permitido persistir área de trabalho privada sem usuarioId.");
        }
        dadosPorUsuario.computeIfAbsent(uid, k -> new ConcurrentHashMap<>()).put(areaTrabalho.getId(), areaTrabalho);
        return areaTrabalho;
    }

    @Override
    public Optional<AreaTrabalho> buscarPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return Optional.empty();
        Map<UUID, AreaTrabalho> map = dadosPorUsuario.get(usuarioId);
        if (map != null) {
            return Optional.ofNullable(map.get(id));
        }
        return Optional.empty();
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(UUID usuarioId) {
        if (usuarioId == null) return Collections.emptyList();
        Map<UUID, AreaTrabalho> map = dadosPorUsuario.get(usuarioId);
        if (map == null) return Collections.emptyList();
        return new ArrayList<>(map.values());
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(String usuario) {
        if (usuario == null || usuario.isBlank()) return Collections.emptyList();
        for (Map<UUID, AreaTrabalho> map : dadosPorUsuario.values()) {
            List<AreaTrabalho> matches = map.values().stream()
                    .filter(a -> a.getUsuarioProprietario() != null && a.getUsuarioProprietario().equalsIgnoreCase(usuario.trim()))
                    .collect(Collectors.toList());
            if (!matches.isEmpty()) return matches;
        }
        return Collections.emptyList();
    }

    @Override
    public void excluirPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null || usuarioId == null) return;
        Map<UUID, AreaTrabalho> map = dadosPorUsuario.get(usuarioId);
        if (map != null) {
            map.remove(id);
        }
    }

    @Override
    public Optional<AreaTrabalho> buscarPorId(UUID uuid) {
        if (uuid == null) return Optional.empty();
        for (Map<UUID, AreaTrabalho> map : dadosPorUsuario.values()) {
            AreaTrabalho a = map.get(uuid);
            if (a != null) return Optional.of(a);
        }
        return Optional.empty();
    }

    @Override
    public List<AreaTrabalho> listarTodas() {
        return Collections.emptyList();
    }

    @Override
    public void excluir(UUID uuid) {
        if (uuid != null) {
            for (Map<UUID, AreaTrabalho> map : dadosPorUsuario.values()) {
                map.remove(uuid);
            }
        }
    }
}
