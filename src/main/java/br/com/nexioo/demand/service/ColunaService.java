package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.model.Coluna;

import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de gerenciamento de colunas/listas no Kanban.
 */
public interface ColunaService {

    List<Coluna> listarTodas();

    List<Coluna> listarPorProjeto(UUID projetoId);

    Coluna buscarPorId(String id);

    Coluna buscarPorId(String id, UUID projetoId);

    Coluna criar(ColunaForm form);

    Coluna buscarPadrao();

    Coluna buscarPadrao(UUID projetoId);

    void excluir(String id);

    void excluir(String id, UUID projetoId);
}

