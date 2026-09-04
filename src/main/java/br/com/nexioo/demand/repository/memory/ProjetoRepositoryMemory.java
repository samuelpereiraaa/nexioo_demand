package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.ProjetoRepository;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Implementação em memória do repositório de {@link Projeto}.
 */
@Repository
public class ProjetoRepositoryMemory implements ProjetoRepository {

    private final Map<Long, Projeto> armazenamento = new ConcurrentHashMap<>();
    private final AtomicLong geradorId = new AtomicLong(100);

    @Override
    public Projeto salvar(Projeto projeto) {
        if (projeto.getId() == null) {
            projeto.setId(geradorId.getAndIncrement());
        }
        armazenamento.put(projeto.getId(), projeto);
        return projeto;
    }

    @Override
    public Optional<Projeto> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(armazenamento.get(id));
    }

    @Override
    public List<Projeto> listarTodos() {
        return new ArrayList<>(armazenamento.values());
    }

    @Override
    public List<Projeto> listarPorArea(Long areaTrabalhoId) {
        if (areaTrabalhoId == null) {
            return listarTodos();
        }
        return armazenamento.values().stream()
                .filter(p -> p.getAreaTrabalhoId() == null || p.getAreaTrabalhoId().equals(areaTrabalhoId))
                .collect(Collectors.toList());
    }


    @Override
    public List<Projeto> listarRecentes() {
        return armazenamento.values().stream()
                .filter(Projeto::isRecentementeVisualizado)
                .collect(Collectors.toList());
    }

    @Override
    public void excluir(Long id) {
        if (id != null) {
            armazenamento.remove(id);
        }
    }
}
