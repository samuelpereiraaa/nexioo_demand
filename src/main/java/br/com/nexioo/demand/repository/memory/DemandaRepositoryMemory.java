package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.repository.DemandaRepository;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação em memória do {@link DemandaRepository}.
 * <p>
 * Utiliza um {@code LinkedHashMap} sincronizado para preservar a ordem de inserção
 * e suportar acesso básico concorrente. Adequada para desenvolvimento e validação
 * de fluxos sem necessidade de banco de dados.
 * <p>
 * Para migrar para JPA (Fase 2), basta criar uma implementação que estenda
 * {@code JpaRepository} e anotar com {@code @Repository}.
 * Este arquivo pode então ser removido juntamente com o {@code @Bean} em
 * {@code AppConfig}.
 */
public class DemandaRepositoryMemory implements DemandaRepository {

    private final Map<Long, Demanda> armazenamento =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private final AtomicLong contadorId = new AtomicLong(1);

    @Override
    public Demanda salvar(Demanda demanda) {
        if (demanda.getId() == null) {
            demanda.setId(contadorId.getAndIncrement());
        }
        armazenamento.put(demanda.getId(), demanda);
        return demanda;
    }

    @Override
    public Optional<Demanda> buscarPorId(Long id) {
        return Optional.ofNullable(armazenamento.get(id));
    }

    @Override
    public List<Demanda> listarTodas() {
        return new ArrayList<>(armazenamento.values());
    }

    @Override
    public void excluir(Long id) {
        armazenamento.remove(id);
    }

    @Override
    public boolean existePorId(Long id) {
        return armazenamento.containsKey(id);
    }
}
