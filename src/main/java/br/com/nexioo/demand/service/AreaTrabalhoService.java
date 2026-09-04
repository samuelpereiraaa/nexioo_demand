package br.com.nexioo.demand.service;

import br.com.nexioo.demand.model.AreaTrabalho;

import java.util.List;

public interface AreaTrabalhoService {

    List<AreaTrabalho> listarPorUsuario(String usuario);

    AreaTrabalho buscarPorId(Long id);

    AreaTrabalho criar(String nome, String usuario);

    AreaTrabalho editar(Long id, String novoNome, String usuario);

    void excluir(Long id, String usuario);

    AreaTrabalho obterOuCriarPadrao(String usuario);
}
