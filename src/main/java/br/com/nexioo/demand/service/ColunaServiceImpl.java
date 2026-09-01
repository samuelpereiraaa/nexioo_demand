package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ColunaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação dos casos de uso de colunas/listas.
 */
@Service
public class ColunaServiceImpl implements ColunaService {

    private final ColunaRepository colunaRepository;
    private final AtomicLong contador = new AtomicLong(System.currentTimeMillis() % 100000);

    public ColunaServiceImpl(ColunaRepository colunaRepository) {
        this.colunaRepository = colunaRepository;
    }

    @Override
    public List<Coluna> listarTodas() {
        return colunaRepository.listarTodas();
    }

    @Override
    public Coluna buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return buscarPadrao();
        }
        return colunaRepository.buscarPorId(id)
                .orElseGet(() -> {
                    // Tenta busca insensível a maiúsculas/minúsculas
                    return colunaRepository.listarTodas().stream()
                            .filter(c -> c.getId().equalsIgnoreCase(id))
                            .findFirst()
                            .orElseGet(this::buscarPadrao);
                });
    }

    @Override
    public Coluna criar(ColunaForm form) {
        if (form == null || form.getNome() == null || form.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome da lista é obrigatório.");
        }

        String nomeLimpo = form.getNome().trim();
        List<Coluna> existentes = colunaRepository.listarTodas();
        int proximaOrdem = existentes.stream()
                .mapToInt(Coluna::getOrdem)
                .max()
                .orElse(0) + 1;

        String idGerado = gerarIdUnico(nomeLimpo);

        Coluna novaColuna = new Coluna(idGerado, nomeLimpo, proximaOrdem);
        return colunaRepository.salvar(novaColuna);
    }

    @Override
    public Coluna buscarPadrao() {
        List<Coluna> colunas = colunaRepository.listarTodas();
        if (colunas.isEmpty()) {
            return Coluna.BACKLOG;
        }
        return colunas.get(0);
    }

    @Override
    public void excluir(String id) {
        if (id != null && !id.isBlank()) {
            colunaRepository.excluir(id);
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
            idCandidate = base + "_" + contador.incrementAndGet();
        }
        return idCandidate;
    }
}
