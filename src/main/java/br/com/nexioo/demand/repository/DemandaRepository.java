package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.Demanda;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acesso a dados para {@link Demanda}.
 * <p>
 * Isola completamente a camada de serviço do mecanismo de persistência.
 * Fase 1: implementado por {@code DemandaRepositoryMemory}.
 * Fase 2: implementado por um repositório Spring Data JPA (sem alterar nenhum serviço).
 */
public interface DemandaRepository {

    Demanda salvar(Demanda demanda);

    Optional<Demanda> buscarPorId(Long id);

    List<Demanda> listarTodas();

    void excluir(Long id);

    boolean existePorId(Long id);
}
