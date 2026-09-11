package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementação dos casos de uso de colunas/listas com particionamento por projeto e usuário.
 */
@Service
public class ColunaServiceImpl implements ColunaService {

    private final ColunaRepository colunaRepository;
    private final br.com.nexioo.demand.config.UserContext userContext;
    private final br.com.nexioo.demand.repository.ProjetoRepository projetoRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ColunaServiceImpl(ColunaRepository colunaRepository,
                             br.com.nexioo.demand.config.UserContext userContext,
                             @org.springframework.context.annotation.Lazy br.com.nexioo.demand.repository.ProjetoRepository projetoRepository) {
        this.colunaRepository = colunaRepository;
        this.userContext = userContext;
        this.projetoRepository = projetoRepository;
    }

    public ColunaServiceImpl(ColunaRepository colunaRepository, br.com.nexioo.demand.config.UserContext userContext) {
        this(colunaRepository, userContext, null);
    }

    public ColunaServiceImpl(ColunaRepository colunaRepository) {
        this(colunaRepository, null, null);
    }

    @Override
    public List<Coluna> listarTodas() {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return colunaRepository.listarPorProjetoEUsuario(null, userContext.requireUsuarioId());
    }

    @Override
    public List<Coluna> listarPorProjeto(UUID projetoId) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return colunaRepository.listarPorProjetoEUsuario(projetoId, userContext.requireUsuarioId());
    }

    @Override
    public Coluna buscarPorId(String id) {
        return buscarPorId(id, null);
    }

    @Override
    public Coluna buscarPorId(String id, UUID projetoId) {
        if (id == null || id.isBlank()) {
            return buscarPadrao(projetoId);
        }
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        if (projetoId != null) {
            return colunaRepository.buscarPorIdEProjetoEUsuario(id, projetoId, uid)
                    .orElseGet(() -> buscarPadrao(projetoId));
        }
        return colunaRepository.buscarPorIdEUsuario(id, uid)
                .orElseGet(() -> buscarPadrao(null));
    }

    @Override
    public Coluna criar(ColunaForm form) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        if (form == null || form.getNome() == null || form.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome da lista é obrigatório.");
        }
        if (form.getProjetoId() == null) {
            throw new IllegalArgumentException("O projeto é obrigatório para a criação de coluna.");
        }

        UUID uid = userContext.requireUsuarioId();
        String nomeLimpo = form.getNome().trim();
        UUID projetoId = form.getProjetoId();

        if (projetoRepository != null) {
            projetoRepository.buscarPorIdEUsuario(projetoId, uid)
                    .orElseThrow(() -> new SecurityException("Tentativa de criar coluna em projeto que não pertence ao usuário."));
        }
        List<Coluna> existentes = listarPorProjeto(projetoId);
        int proximaOrdem = existentes.stream()
                .mapToInt(Coluna::getOrdem)
                .max()
                .orElse(0) + 1;

        String idGerado = gerarIdUnico(nomeLimpo);

        Coluna novaColuna = new Coluna(idGerado, nomeLimpo, proximaOrdem);
        novaColuna.setProjetoId(projetoId);
        novaColuna.setUsuarioId(uid);
        return colunaRepository.salvar(novaColuna);
    }

    @Override
    public Coluna buscarPadrao() {
        return buscarPadrao(null);
    }

    @Override
    public Coluna buscarPadrao(UUID projetoId) {
        List<Coluna> colunas = (projetoId != null) ? listarPorProjeto(projetoId) : listarTodas();
        if (colunas.isEmpty()) {
            return Coluna.BACKLOG;
        }
        return colunas.get(0);
    }

    @Override
    public void excluir(String id) {
        excluir(id, null);
    }

    @Override
    public void excluir(String id, UUID projetoId) {
        if (id != null && !id.isBlank()) {
            if (userContext == null || !userContext.isAutenticado()) {
                throw new SecurityException("Usuário não autenticado.");
            }
            colunaRepository.excluirPorIdEProjetoEUsuario(id, projetoId, userContext.requireUsuarioId());
        }
    }

    private String gerarIdUnico(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (base.isBlank()) {
            base = "LISTA";
        }

        String idCandidate = base;
        if (colunaRepository.existePorId(idCandidate)) {
            idCandidate = base + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        return idCandidate;
    }
}
