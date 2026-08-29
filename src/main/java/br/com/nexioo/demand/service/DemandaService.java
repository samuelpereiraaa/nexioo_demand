package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;

import java.util.List;
import java.util.Map;

/**
 * Casos de uso relacionados a demandas.
 * Toda regra de negócio reside aqui, nunca em controllers ou templates.
 */
public interface DemandaService {

    Demanda criar(DemandaForm form);

    Demanda buscarPorId(Long id);

    List<Demanda> listarTodas();

    /** Retorna todas as demandas agrupadas por coluna (todas as colunas presentes, mesmo as vazias). */
    Map<Coluna, List<Demanda>> listarPorColuna();

    /** Filtra demandas por termo (título/descrição), prioridade e/ou responsável. */
    Map<Coluna, List<Demanda>> filtrar(String termo, Prioridade prioridade, String responsavel);

    Demanda editar(Long id, DemandaForm form);

    Demanda alterarColuna(Long id, Coluna novaColuna);

    void excluir(Long id);
}
