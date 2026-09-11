package br.com.nexioo.demand.service;

import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class AreaTrabalhoServiceImpl implements AreaTrabalhoService {

    private final AreaTrabalhoRepository areaTrabalhoRepository;
    private final UserContext userContext;

    @Autowired
    public AreaTrabalhoServiceImpl(AreaTrabalhoRepository areaTrabalhoRepository, UserContext userContext) {
        this.areaTrabalhoRepository = areaTrabalhoRepository;
        this.userContext = userContext;
    }

    public AreaTrabalhoServiceImpl(AreaTrabalhoRepository areaTrabalhoRepository) {
        this(areaTrabalhoRepository, null);
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(String usuario) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        List<AreaTrabalho> lista = areaTrabalhoRepository.listarPorUsuario(uid);
        if (lista.isEmpty()) {
            AreaTrabalho padrao = obterOuCriarPadrao(userContext.getEmail());
            lista = List.of(padrao);
        }
        return lista;
    }

    @Override
    public AreaTrabalho buscarPorId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID da área de trabalho não pode ser nulo.");
        }
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        return areaTrabalhoRepository.buscarPorIdEUsuario(id, uid)
                .orElseThrow(() -> new IllegalArgumentException("Área de trabalho não encontrada ou acesso não autorizado: " + id));
    }

    @Override
    public AreaTrabalho criar(String nome, String usuario) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da área de trabalho é obrigatório.");
        }
        String nomeSanitizado = nome.trim();
        UUID uid = userContext.requireUsuarioId();
        String user = userContext.getEmail();

        List<AreaTrabalho> existentes = areaTrabalhoRepository.listarPorUsuario(uid);

        boolean duplicado = existentes.stream()
                .anyMatch(a -> a.getNome().equalsIgnoreCase(nomeSanitizado));
        if (duplicado) {
            throw new IllegalArgumentException("Já existe uma área de trabalho com o nome \"" + nomeSanitizado + "\".");
        }

        AreaTrabalho nova = new AreaTrabalho();
        nova.setNome(nomeSanitizado);
        nova.setInicial(AreaTrabalho.gerarInicial(nomeSanitizado));
        nova.setUsuarioProprietario(user);
        nova.setUsuarioId(uid);

        return areaTrabalhoRepository.salvar(nova);
    }

    @Override
    public AreaTrabalho editar(UUID id, String novoNome, String usuario) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        AreaTrabalho area = buscarPorId(id);
        if (novoNome == null || novoNome.isBlank()) {
            throw new IllegalArgumentException("O nome da área de trabalho é obrigatório.");
        }
        String nomeSanitizado = novoNome.trim();
        UUID uid = userContext.requireUsuarioId();

        List<AreaTrabalho> existentes = areaTrabalhoRepository.listarPorUsuario(uid);
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
    public void excluir(UUID id, String usuario) {
        if (id == null) return;
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        areaTrabalhoRepository.excluirPorIdEUsuario(id, userContext.requireUsuarioId());
    }

    @Override
    public AreaTrabalho obterOuCriarPadrao(String usuario) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        String userSanitizado = userContext.getEmail();

        List<AreaTrabalho> existentes = areaTrabalhoRepository.listarPorUsuario(uid);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        AreaTrabalho padrao = new AreaTrabalho();
        padrao.setNome("Área de trabalho Nexioo Demand");
        padrao.setInicial("Á");
        padrao.setUsuarioProprietario(userSanitizado);
        padrao.setUsuarioId(uid);
        return areaTrabalhoRepository.salvar(padrao);
    }
}
