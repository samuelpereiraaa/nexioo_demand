package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Casos de uso relacionados a demandas.
 */
public interface DemandaService {

    Demanda criar(DemandaForm form);

    Demanda buscarPorId(Long id);

    List<Demanda> listarTodas();

    Map<Coluna, List<Demanda>> listarPorColuna();

    Map<Coluna, List<Demanda>> filtrar(String termo, Prioridade prioridade, String responsavel);

    Demanda editar(Long id, DemandaForm form);

    Demanda atualizarTitulo(Long id, String titulo);

    Demanda atualizarDescricao(Long id, String descricao);

    Demanda alterarColuna(Long id, Coluna novaColuna);

    Demanda alternarConclusao(Long id);

    Demanda adicionarComentario(Long id, String texto, String autor);

    Demanda adicionarEtiqueta(Long id, String nome, String corHex);

    Demanda removerEtiqueta(Long id, String etiquetaId);

    Demanda definirPrazo(Long id, LocalDate prazo);

    Demanda adicionarChecklist(Long id, String titulo);

    Demanda renomearChecklist(Long id, Long checklistId, String titulo);

    Demanda removerChecklist(Long id, Long checklistId);

    Demanda adicionarItemChecklist(Long id, Long checklistId, String texto);

    Demanda toggleItemChecklist(Long id, Long checklistId, Long itemId);

    Demanda atualizarItemChecklist(Long id, Long checklistId, Long itemId, String texto);

    Demanda removerItemChecklist(Long id, Long checklistId, Long itemId);

    Demanda adicionarMembro(Long id, String membro);

    Demanda removerMembro(Long id, String membro);

    Demanda toggleAcompanhar(Long id);

    Demanda adicionarImagem(Long id, String imagemUrl);

    Demanda removerImagem(Long id);

    void excluir(Long id);
}
