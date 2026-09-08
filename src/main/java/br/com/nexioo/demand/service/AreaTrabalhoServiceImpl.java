package br.com.nexioo.demand.service;

import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AreaTrabalhoServiceImpl implements AreaTrabalhoService {

    private final AreaTrabalhoRepository areaTrabalhoRepository;

    public AreaTrabalhoServiceImpl(AreaTrabalhoRepository areaTrabalhoRepository) {
        this.areaTrabalhoRepository = areaTrabalhoRepository;
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(String usuario) {
        List<AreaTrabalho> lista = areaTrabalhoRepository.listarPorUsuario(usuario);
        if (lista.isEmpty()) {
            AreaTrabalho padrao = obterOuCriarPadrao(usuario);
            lista = List.of(padrao);
        }
        return lista;
    }

    @Override
    public AreaTrabalho buscarPorId(Long id) {
        return areaTrabalhoRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Área de trabalho não encontrada com ID: " + id));
    }

    @Override
    public AreaTrabalho criar(String nome, String usuario) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da área de trabalho é obrigatório.");
        }
        String nomeSanitizado = nome.trim();
        List<AreaTrabalho> existentes = areaTrabalhoRepository.listarPorUsuario(usuario);
        boolean duplicado = existentes.stream()
                .anyMatch(a -> a.getNome().equalsIgnoreCase(nomeSanitizado));
        if (duplicado) {
            throw new IllegalArgumentException("Já existe uma área de trabalho com o nome \"" + nomeSanitizado + "\".");
        }

        AreaTrabalho nova = new AreaTrabalho();
        nova.setNome(nomeSanitizado);
        nova.setInicial(AreaTrabalho.gerarInicial(nomeSanitizado));
        nova.setUsuarioProprietario(usuario != null ? usuario.trim() : "samuel@nexioo.com.br");

        return areaTrabalhoRepository.salvar(nova);
    }

    @Override
    public AreaTrabalho editar(Long id, String novoNome, String usuario) {
        AreaTrabalho area = buscarPorId(id);
        if (novoNome == null || novoNome.isBlank()) {
            throw new IllegalArgumentException("O nome da área de trabalho é obrigatório.");
        }
        String nomeSanitizado = novoNome.trim();
        List<AreaTrabalho> existentes = areaTrabalhoRepository.listarPorUsuario(usuario);
        boolean duplicado = existentes.stream()
                .anyMatch(a -> !a.getId().equals(id) && a.getNome().equalsIgnoreCase(nomeSanitizado));
        if (duplicado) {
            throw new IllegalArgumentException("Já existe outra área de trabalho com este nome.");
        }

        area.setNome(nomeSanitizado);
        area.setInicial(AreaTrabalho.gerarInicial(nomeSanitizado));
        return areaTrabalhoRepository.salvar(area);
    }

    @Override
    public void excluir(Long id, String usuario) {
        areaTrabalhoRepository.excluir(id);
    }

    @Override
    public AreaTrabalho obterOuCriarPadrao(String usuario) {
        String userSanitizado = (usuario != null && !usuario.isBlank()) ? usuario.trim() : "samuel@nexioo.com.br";
        List<AreaTrabalho> existentes = areaTrabalhoRepository.listarPorUsuario(userSanitizado);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        AreaTrabalho padrao = new AreaTrabalho();
        padrao.setNome("Área de trabalho Nexioo Demand");
        padrao.setInicial("Á");
        padrao.setUsuarioProprietario(userSanitizado);
        return areaTrabalhoRepository.salvar(padrao);
    }
}
