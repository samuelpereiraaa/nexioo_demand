package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Implementação em memória do {@link ColunaRepository}.
 * Inicializado com a lista inicial "Trello Starter Guide".
 */
@Repository
public class ColunaRepositoryMemory implements ColunaRepository {

    private final Map<String, Coluna> armazenamento =
            Collections.synchronizedMap(new LinkedHashMap<>());

    public ColunaRepositoryMemory() {
        salvar(new Coluna("BACKLOG", "Guia Inicial Nexioo Demand", 1));
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

    @Override
    public void excluir(String id) {
        if (id != null) {
            armazenamento.remove(id);
            armazenamento.keySet().removeIf(k -> k.equalsIgnoreCase(id));
        }
    }
}

