package br.com.nexioo.demand.service;

import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.ProjetoRepository;
import br.com.nexioo.demand.repository.QuadroRecenteRepository;
import br.com.nexioo.demand.util.IdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação da regra de negócios de projetos com isolamento multi-tenant por usuarioId.
 */
@Service
public class ProjetoServiceImpl implements ProjetoService {

    private final ProjetoRepository projetoRepository;
    private final UserContext userContext;
    private final QuadroRecenteRepository quadroRecenteRepository;
    private final br.com.nexioo.demand.repository.AreaTrabalhoRepository areaTrabalhoRepository;

    @Autowired
    public ProjetoServiceImpl(ProjetoRepository projetoRepository,
                              UserContext userContext,
                              QuadroRecenteRepository quadroRecenteRepository,
                              @org.springframework.context.annotation.Lazy br.com.nexioo.demand.repository.AreaTrabalhoRepository areaTrabalhoRepository) {
        this.projetoRepository = projetoRepository;
        this.userContext = userContext;
        this.quadroRecenteRepository = quadroRecenteRepository;
        this.areaTrabalhoRepository = areaTrabalhoRepository;
    }

    public ProjetoServiceImpl(ProjetoRepository projetoRepository,
                              UserContext userContext,
                              QuadroRecenteRepository quadroRecenteRepository) {
        this(projetoRepository, userContext, quadroRecenteRepository, null);
    }

    public ProjetoServiceImpl(ProjetoRepository projetoRepository) {
        this(projetoRepository, null, null, null);
    }

    @Override
    public List<Projeto> listarTodos() {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return projetoRepository.listarPorUsuario(userContext.requireUsuarioId());
    }

    @Override
    public List<Projeto> listarPorArea(UUID areaTrabalhoId) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return projetoRepository.listarPorAreaEUsuario(areaTrabalhoId, userContext.requireUsuarioId());
    }

    @Override
    public List<Projeto> listarRecentes() {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        if (quadroRecenteRepository != null) {
            List<UUID> ids = quadroRecenteRepository.listarQuadrosRecentesIds(uid, 10);
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            return projetoRepository.buscarPorIdsEUsuario(ids, uid);
        }
        return Collections.emptyList();
    }

    @Override
    public Projeto buscarPorId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("ID do projeto não pode ser nulo.");
        }
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        return projetoRepository.buscarPorIdEUsuario(id, uid)
                .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado ou acesso não autorizado: " + id));
    }

    @Override
    public Projeto criar(ProjetoForm form) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        if (form == null || form.getNome() == null || form.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome do projeto é obrigatório.");
        }

        UUID uid = userContext.requireUsuarioId();
        Projeto projeto = new Projeto();
        projeto.setNome(form.getNome().trim());
        projeto.setDescricao(form.getDescricao() != null ? form.getDescricao().trim() : "");
        if (form.getGradiente() != null && !form.getGradiente().isBlank()) {
            projeto.setGradiente(form.getGradiente());
        }
        projeto.setUsuarioId(uid);
        projeto.setUsuarioProprietario(userContext.getEmail());

        if (form.getAreaTrabalhoId() != null) {
            if (areaTrabalhoRepository != null) {
                areaTrabalhoRepository.buscarPorIdEUsuario(form.getAreaTrabalhoId(), uid)
                        .orElseThrow(() -> new SecurityException("Tentativa de associar projeto a uma área de trabalho que não pertence ao usuário."));
            }
            projeto.setAreaTrabalhoId(form.getAreaTrabalhoId());
        } else if (areaTrabalhoRepository != null) {
            AreaTrabalho padrao = areaTrabalhoRepository.listarPorUsuario(uid).stream().findFirst()
                    .orElseGet(() -> areaTrabalhoRepository.salvar(new AreaTrabalho(null, "Área Geral", uid, userContext.getEmail())));
            projeto.setAreaTrabalhoId(padrao.getId());
        }

        Projeto salvo = projetoRepository.salvar(projeto);

        if (quadroRecenteRepository != null) {
            quadroRecenteRepository.registrarVisualizacao(uid, salvo.getId());
        }

        return salvo;
    }

    @Override
    public void excluir(UUID id) {
        if (id == null) return;
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        if (quadroRecenteRepository != null) {
            quadroRecenteRepository.removerRecente(uid, id);
        }
        projetoRepository.excluirPorIdEUsuario(id, uid);
    }

    @Override
    public void marcarComoRecente(UUID id) {
        if (id == null) return;
        registrarVisualizacao(id);
    }

    @Override
    public void registrarVisualizacao(UUID quadroId) {
        if (quadroId == null) return;
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        // Valida se o quadro pertence ao usuário autenticado antes de registrar no histórico
        Optional<Projeto> p = projetoRepository.buscarPorIdEUsuario(quadroId, uid);
        if (p.isPresent() && quadroRecenteRepository != null) {
            quadroRecenteRepository.registrarVisualizacao(uid, quadroId);
        }
    }
}
