package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.Coluna;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de repositório para persistência de {@link Coluna}.
 */
public interface ColunaRepository {

    Coluna salvar(Coluna coluna);

    Optional<Coluna> buscarPorId(String id);

    List<Coluna> listarTodas();

    boolean existePorId(String id);
}
