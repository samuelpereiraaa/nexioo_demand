package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.model.Coluna;

import java.util.List;

/**
 * Casos de uso de gerenciamento de colunas/listas no Kanban.
 */
public interface ColunaService {

    List<Coluna> listarTodas();

    Coluna buscarPorId(String id);

    Coluna criar(ColunaForm form);

    Coluna buscarPadrao();
}
