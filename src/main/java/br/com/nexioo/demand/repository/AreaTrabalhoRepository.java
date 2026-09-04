package br.com.nexioo.demand.repository;

import br.com.nexioo.demand.model.AreaTrabalho;

import java.util.List;
import java.util.Optional;

public interface AreaTrabalhoRepository {

    List<AreaTrabalho> listarTodas();

    List<AreaTrabalho> listarPorUsuario(String usuario);

    Optional<AreaTrabalho> buscarPorId(Long id);

    AreaTrabalho salvar(AreaTrabalho areaTrabalho);

    void excluir(Long id);
}
