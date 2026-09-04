package br.com.nexioo.demand.repository.memory;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class AreaTrabalhoRepositoryMemory implements AreaTrabalhoRepository {

    private final Map<Long, AreaTrabalho> dados = new ConcurrentHashMap<>();
    private final AtomicLong sequenciaId = new AtomicLong(1);

    @Override
    public List<AreaTrabalho> listarTodas() {
        return new ArrayList<>(dados.values());
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            return listarTodas();
        }
        return dados.values().stream()
                .filter(a -> a.getUsuarioProprietario() == null || a.getUsuarioProprietario().equalsIgnoreCase(usuario.trim()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AreaTrabalho> buscarPorId(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(dados.get(id));
    }

    @Override
    public AreaTrabalho salvar(AreaTrabalho areaTrabalho) {
        if (areaTrabalho.getId() == null) {
            areaTrabalho.setId(sequenciaId.getAndIncrement());
        }
        dados.put(areaTrabalho.getId(), areaTrabalho);
        return areaTrabalho;
    }

    @Override
    public void excluir(Long id) {
        if (id != null) {
            dados.remove(id);
        }
    }
}
