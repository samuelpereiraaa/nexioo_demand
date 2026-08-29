package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Implementação em memória do {@link ColunaRepository}.
 * Inicializado com as 4 colunas padrão do Kanban.
 */
@Repository
public class ColunaRepositoryMemory implements ColunaRepository {

    private final Map<String, Coluna> armazenamento =
            Collections.synchronizedMap(new LinkedHashMap<>());

    public ColunaRepositoryMemory() {
        salvar(Coluna.BACKLOG);
        salvar(Coluna.A_FAZER);
        salvar(Coluna.EM_ANDAMENTO);
        salvar(Coluna.CONCLUIDO);
    }

    @Override
    public Coluna salvar(Coluna coluna) {
        armazenamento.put(coluna.getId(), coluna);
        return coluna;
    }

    @Override
    public Optional<Coluna> buscarPorId(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(armazenamento.get(id));
    }

    @Override
    public List<Coluna> listarTodas() {
        List<Coluna> colunas = new ArrayList<>(armazenamento.values());
        colunas.sort(Comparator.comparingInt(Coluna::getOrdem));
        return colunas;
    }

    @Override
    public boolean existePorId(String id) {
        if (id == null) return false;
        return armazenamento.containsKey(id);
    }
}
