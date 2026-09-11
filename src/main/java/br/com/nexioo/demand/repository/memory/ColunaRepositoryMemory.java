package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import br.com.nexioo.demand.util.IdUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementação em memória do {@link ColunaRepository}.
 * Particionada rigorosamente por (usuarioId, projetoId) sem compartilhamento global.
 */
@Repository
@Profile({"test", "dev-memory"})
public class ColunaRepositoryMemory implements ColunaRepository {

    private final Map<UUID, Map<String, Coluna>> dadosPorUsuario = new ConcurrentHashMap<>();

    @Override
    public Coluna salvar(Coluna coluna) {
        if (coluna.getId() == null && coluna.getCodigo() != null) {
            coluna.setId(coluna.getCodigo());
        }
        if (coluna.getUuid() == null && coluna.getId() != null) {
            coluna.setUuid(IdUtils.parseUuid(coluna.getId()));
        }
        if (coluna.getUuid() == null) {
            coluna.setUuid(UUID.randomUUID());
            coluna.setId(coluna.getUuid().toString());
        }

        UUID uid = coluna.getUsuarioId();
        if (uid == null) {
            throw new IllegalArgumentException("Não é permitido persistir coluna privada sem usuarioId.");
        }

        Map<String, Coluna> userMap = dadosPorUsuario.computeIfAbsent(uid, k -> new ConcurrentHashMap<>());
        userMap.put(coluna.getId(), coluna);
        if (coluna.getUuid() != null) {
            userMap.put(coluna.getUuid().toString(), coluna);
        }
        if (coluna.getCodigo() != null) {
            userMap.put(coluna.getCodigo().toUpperCase(), coluna);
        }
        return coluna;
    }

    @Override
    public Optional<Coluna> buscarPorId(String id) {
        if (id == null) return Optional.empty();
        for (Map<String, Coluna> userMap : dadosPorUsuario.values()) {
            Coluna c = userMap.get(id);
            if (c != null) return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Coluna> buscarPorIdEUsuario(String id, UUID usuarioId) {
        if (id == null || usuarioId == null) return Optional.empty();
        Map<String, Coluna> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap != null) {
            Coluna c = userMap.get(id);
            if (c != null) return Optional.of(c);
            return userMap.values().stream()
                    .filter(col -> (col.getCodigo() != null && col.getCodigo().equalsIgnoreCase(id))
                            || (col.getUuid() != null && col.getUuid().toString().equalsIgnoreCase(id)))
                    .findFirst();
        }
        return Optional.empty();
    }

    @Override
    public Optional<Coluna> buscarPorIdEProjetoEUsuario(String id, UUID projetoId, UUID usuarioId) {
        if (id == null || usuarioId == null) return Optional.empty();
        Map<String, Coluna> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap != null) {
            return userMap.values().stream()
                    .filter(col -> (projetoId == null || projetoId.equals(col.getProjetoId())))
                    .filter(col -> id.equalsIgnoreCase(col.getId())
                            || (col.getCodigo() != null && col.getCodigo().equalsIgnoreCase(id))
                            || (col.getUuid() != null && col.getUuid().toString().equalsIgnoreCase(id)))
                    .findFirst();
        }
        return Optional.empty();
    }

    @Override
    public List<Coluna> listarTodas() {
        return Collections.emptyList();
    }

    @Override
    public List<Coluna> listarPorProjeto(UUID projetoId) {
        return Collections.emptyList();
    }

    @Override
    public List<Coluna> listarPorProjetoEUsuario(UUID projetoId, UUID usuarioId) {
        if (usuarioId == null) return Collections.emptyList();
        Map<String, Coluna> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap == null) {
            return inicializarColunasPadrao(projetoId, usuarioId);
        }

        List<Coluna> filtradas = userMap.values().stream()
                .filter(c -> projetoId == null || projetoId.equals(c.getProjetoId()))
                .distinct()
                .sorted(Comparator.comparingInt(Coluna::getOrdem))
                .collect(Collectors.toList());

        if (filtradas.isEmpty() && projetoId != null) {
            return inicializarColunasPadrao(projetoId, usuarioId);
        }
        return filtradas;
    }

    private List<Coluna> inicializarColunasPadrao(UUID projetoId, UUID usuarioId) {
        List<Coluna> padroes = List.of(
                new Coluna(UUID.randomUUID(), projetoId, usuarioId, "BACKLOG", "Backlog", 1),
                new Coluna(UUID.randomUUID(), projetoId, usuarioId, "A_FAZER", "A Fazer", 2),
                new Coluna(UUID.randomUUID(), projetoId, usuarioId, "EM_ANDAMENTO", "Em Andamento", 3),
                new Coluna(UUID.randomUUID(), projetoId, usuarioId, "CONCLUIDO", "Concluído", 4)
        );
        for (Coluna c : padroes) {
            salvar(c);
        }
        return padroes;
    }

    @Override
    public boolean existePorId(String id) {
        if (id == null) return false;
        return buscarPorId(id).isPresent();
    }

    @Override
    public void excluir(String id) {
        if (id != null) {
            for (Map<String, Coluna> userMap : dadosPorUsuario.values()) {
                userMap.remove(id);
                userMap.values().removeIf(c -> id.equalsIgnoreCase(c.getId())
                        || (c.getCodigo() != null && c.getCodigo().equalsIgnoreCase(id))
                        || (c.getUuid() != null && c.getUuid().toString().equalsIgnoreCase(id)));
            }
        }
    }

    @Override
    public void excluirPorIdEProjetoEUsuario(String id, UUID projetoId, UUID usuarioId) {
        if (id == null || usuarioId == null) return;
        Map<String, Coluna> userMap = dadosPorUsuario.get(usuarioId);
        if (userMap != null) {
            userMap.values().removeIf(c -> (projetoId == null || projetoId.equals(c.getProjetoId()))
                    && (id.equalsIgnoreCase(c.getId())
                    || (c.getCodigo() != null && c.getCodigo().equalsIgnoreCase(id))
                    || (c.getUuid() != null && c.getUuid().toString().equalsIgnoreCase(id))));
        }
    }
}
