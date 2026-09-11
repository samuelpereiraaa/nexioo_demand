package br.com.nexioo.demand.service;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.util.IdUtils;

import java.util.List;
import java.util.UUID;

public interface AreaTrabalhoService {

    List<AreaTrabalho> listarPorUsuario(String usuario);

    AreaTrabalho buscarPorId(UUID id);

    default AreaTrabalho buscarPorId(Object id) {
        return buscarPorId(IdUtils.parseUuid(id));
    }

    AreaTrabalho criar(String nome, String usuario);

    AreaTrabalho editar(UUID id, String novoNome, String usuario);

    default AreaTrabalho editar(Object id, String novoNome, String usuario) {
        return editar(IdUtils.parseUuid(id), novoNome, usuario);
    }

    void excluir(UUID id, String usuario);

    default void excluir(Object id, String usuario) {
        excluir(IdUtils.parseUuid(id), usuario);
    }

    AreaTrabalho obterOuCriarPadrao(String usuario);
}
