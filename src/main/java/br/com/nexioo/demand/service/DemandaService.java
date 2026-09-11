package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.util.IdUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Casos de uso relacionados a demandas.
 */
public interface DemandaService {

    Demanda criar(DemandaForm form);

    Demanda buscarPorId(UUID id);

    default Demanda buscarPorId(Object id) {
        return buscarPorId(IdUtils.parseUuid(id));
    }

    List<Demanda> listarTodas();

    List<Demanda> listarPorProjeto(UUID projetoId);

    default List<Demanda> listarPorProjeto(Object projetoId) {
        return listarPorProjeto(IdUtils.parseUuid(projetoId));
    }

    Map<Coluna, List<Demanda>> listarPorColuna();

    Map<Coluna, List<Demanda>> listarPorColuna(UUID projetoId);

    default Map<Coluna, List<Demanda>> listarPorColuna(Object projetoId) {
        return listarPorColuna(IdUtils.parseUuid(projetoId));
    }

    Map<Coluna, List<Demanda>> filtrar(String termo, Prioridade prioridade, String responsavel);

    Map<Coluna, List<Demanda>> filtrar(UUID projetoId, String termo, Prioridade prioridade, String responsavel);

    default Map<Coluna, List<Demanda>> filtrar(Object projetoId, String termo, Prioridade prioridade, String responsavel) {
        return filtrar(IdUtils.parseUuid(projetoId), termo, prioridade, responsavel);
    }

    Demanda editar(UUID id, DemandaForm form);

    default Demanda editar(Object id, DemandaForm form) {
        return editar(IdUtils.parseUuid(id), form);
    }

    Demanda atualizarTitulo(UUID id, String titulo);

    default Demanda atualizarTitulo(Object id, String titulo) {
        return atualizarTitulo(IdUtils.parseUuid(id), titulo);
    }

    Demanda atualizarDescricao(UUID id, String descricao);

    default Demanda atualizarDescricao(Object id, String descricao) {
        return atualizarDescricao(IdUtils.parseUuid(id), descricao);
    }

    Demanda alterarColuna(UUID id, Coluna novaColuna);

    default Demanda alterarColuna(Object id, Coluna novaColuna) {
        return alterarColuna(IdUtils.parseUuid(id), novaColuna);
    }

    Demanda mover(UUID id, String colunaOrigemId, String colunaDestinoId, Integer novaPosicao, UUID projetoId, String usuario);

    default Demanda mover(Object id, String colunaOrigemId, String colunaDestinoId, Integer novaPosicao, Object projetoId, String usuario) {
        return mover(IdUtils.parseUuid(id), colunaOrigemId, colunaDestinoId, novaPosicao, IdUtils.parseUuid(projetoId), usuario);
    }

    Demanda alternarConclusao(UUID id);

    default Demanda alternarConclusao(Object id) {
        return alternarConclusao(IdUtils.parseUuid(id));
    }

    Demanda adicionarComentario(UUID id, String texto, String autor);

    default Demanda adicionarComentario(Object id, String texto, String autor) {
        return adicionarComentario(IdUtils.parseUuid(id), texto, autor);
    }

    Demanda adicionarEtiqueta(UUID id, String nome, String corHex);

    default Demanda adicionarEtiqueta(Object id, String nome, String corHex) {
        return adicionarEtiqueta(IdUtils.parseUuid(id), nome, corHex);
    }

    Demanda removerEtiqueta(UUID id, String etiquetaId);

    default Demanda removerEtiqueta(Object id, String etiquetaId) {
        return removerEtiqueta(IdUtils.parseUuid(id), etiquetaId);
    }

    Demanda definirPrazo(UUID id, LocalDate prazo);

    default Demanda definirPrazo(Object id, LocalDate prazo) {
        return definirPrazo(IdUtils.parseUuid(id), prazo);
    }

    Demanda adicionarChecklist(UUID id, String titulo);

    default Demanda adicionarChecklist(Object id, String titulo) {
        return adicionarChecklist(IdUtils.parseUuid(id), titulo);
    }

    Demanda renomearChecklist(UUID id, UUID checklistId, String titulo);

    default Demanda renomearChecklist(Object id, Object checklistId, String titulo) {
        return renomearChecklist(IdUtils.parseUuid(id), IdUtils.parseUuid(checklistId), titulo);
    }

    Demanda removerChecklist(UUID id, UUID checklistId);

    default Demanda removerChecklist(Object id, Object checklistId) {
        return removerChecklist(IdUtils.parseUuid(id), IdUtils.parseUuid(checklistId));
    }

    Demanda adicionarItemChecklist(UUID id, UUID checklistId, String texto);

    default Demanda adicionarItemChecklist(Object id, Object checklistId, String texto) {
        return adicionarItemChecklist(IdUtils.parseUuid(id), IdUtils.parseUuid(checklistId), texto);
    }

    Demanda toggleItemChecklist(UUID id, UUID checklistId, UUID itemId);

    default Demanda toggleItemChecklist(Object id, Object checklistId, Object itemId) {
        return toggleItemChecklist(IdUtils.parseUuid(id), IdUtils.parseUuid(checklistId), IdUtils.parseUuid(itemId));
    }

    Demanda atualizarItemChecklist(UUID id, UUID checklistId, UUID itemId, String texto);

    default Demanda atualizarItemChecklist(Object id, Object checklistId, Object itemId, String texto) {
        return atualizarItemChecklist(IdUtils.parseUuid(id), IdUtils.parseUuid(checklistId), IdUtils.parseUuid(itemId), texto);
    }

    Demanda removerItemChecklist(UUID id, UUID checklistId, UUID itemId);

    default Demanda removerItemChecklist(Object id, Object checklistId, Object itemId) {
        return removerItemChecklist(IdUtils.parseUuid(id), IdUtils.parseUuid(checklistId), IdUtils.parseUuid(itemId));
    }

    Demanda adicionarMembro(UUID id, String membro);

    default Demanda adicionarMembro(Object id, String membro) {
        return adicionarMembro(IdUtils.parseUuid(id), membro);
    }

    Demanda removerMembro(UUID id, String membro);

    default Demanda removerMembro(Object id, String membro) {
        return removerMembro(IdUtils.parseUuid(id), membro);
    }

    Demanda toggleAcompanhar(UUID id);

    default Demanda toggleAcompanhar(Object id) {
        return toggleAcompanhar(IdUtils.parseUuid(id));
    }

    Demanda adicionarImagem(UUID id, String imagemUrl);

    default Demanda adicionarImagem(Object id, String imagemUrl) {
        return adicionarImagem(IdUtils.parseUuid(id), imagemUrl);
    }

    Demanda removerImagem(UUID id);

    default Demanda removerImagem(Object id) {
        return removerImagem(IdUtils.parseUuid(id));
    }

    Demanda removerImagemEspecifica(UUID id, String imagemUrl);

    default Demanda removerImagemEspecifica(Object id, String imagemUrl) {
        return removerImagemEspecifica(IdUtils.parseUuid(id), imagemUrl);
    }

    Demanda adicionarAnexo(UUID id, String nome, String url);

    default Demanda adicionarAnexo(Object id, String nome, String url) {
        return adicionarAnexo(IdUtils.parseUuid(id), nome, url);
    }

    Demanda removerAnexo(UUID id, String anexoId);

    default Demanda removerAnexo(Object id, String anexoId) {
        return removerAnexo(IdUtils.parseUuid(id), anexoId);
    }

    Demanda definirCapaAnexo(UUID id, String anexoId, boolean capa);

    default Demanda definirCapaAnexo(Object id, String anexoId, boolean capa) {
        return definirCapaAnexo(IdUtils.parseUuid(id), anexoId, capa);
    }

    Demanda renomearAnexo(UUID id, String anexoId, String novoNome);

    default Demanda renomearAnexo(Object id, String anexoId, String novoNome) {
        return renomearAnexo(IdUtils.parseUuid(id), anexoId, novoNome);
    }

    Demanda comentarAnexo(UUID id, String anexoId, String texto, String autor);

    default Demanda comentarAnexo(Object id, String anexoId, String texto, String autor) {
        return comentarAnexo(IdUtils.parseUuid(id), anexoId, texto, autor);
    }

    void excluir(UUID id);

    default void excluir(Object id) {
        if (id != null) {
            excluir(IdUtils.parseUuid(id));
        }
    }
}

