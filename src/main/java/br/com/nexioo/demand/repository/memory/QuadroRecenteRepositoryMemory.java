package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.repository.QuadroRecenteRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@Profile({"test", "dev-memory"})
public class QuadroRecenteRepositoryMemory implements QuadroRecenteRepository {

    private final Map<UUID, Map<UUID, Instant>> historicoPorUsuario = new ConcurrentHashMap<>();

    @Override
    public void registrarVisualizacao(UUID usuarioId, UUID quadroId) {
        if (usuarioId == null || quadroId == null) return;
        historicoPorUsuario
                .computeIfAbsent(usuarioId, k -> new ConcurrentHashMap<>())
                .put(quadroId, Instant.now());
    }

    @Override
    public List<UUID> listarQuadrosRecentesIds(UUID usuarioId, int limite) {
        if (usuarioId == null) return Collections.emptyList();
        Map<UUID, Instant> userRecentes = historicoPorUsuario.get(usuarioId);
        if (userRecentes == null || userRecentes.isEmpty()) {
            return Collections.emptyList();
        }

        int lim = (limite > 0) ? limite : 10;
        return userRecentes.entrySet().stream()
                .sorted(Map.Entry.<UUID, Instant>comparingByValue().reversed())
                .limit(lim)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public void removerRecente(UUID usuarioId, UUID quadroId) {
        if (usuarioId == null || quadroId == null) return;
        Map<UUID, Instant> userRecentes = historicoPorUsuario.get(usuarioId);
        if (userRecentes != null) {
            userRecentes.remove(quadroId);
        }
    }
}
