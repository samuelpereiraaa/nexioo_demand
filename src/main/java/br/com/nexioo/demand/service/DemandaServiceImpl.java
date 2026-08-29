package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.exception.DemandaNaoEncontradaException;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.repository.DemandaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Implementação dos casos de uso de demandas.
 * Depende apenas da interface {@link DemandaRepository} — sem acoplamento
 * a nenhuma implementação de persistência concreta.
 */
@Service
public class DemandaServiceImpl implements DemandaService {

    private final DemandaRepository demandaRepository;

    public DemandaServiceImpl(DemandaRepository demandaRepository) {
        this.demandaRepository = demandaRepository;
    }

    @Override
    public Demanda criar(DemandaForm form) {
        Demanda demanda = new Demanda();
        aplicarForm(demanda, form);
        demanda.setCriadoEm(LocalDateTime.now());
        demanda.setAtualizadoEm(LocalDateTime.now());
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda buscarPorId(Long id) {
        return demandaRepository.buscarPorId(id)
                .orElseThrow(() -> new DemandaNaoEncontradaException(id));
    }

    @Override
    public List<Demanda> listarTodas() {
        return demandaRepository.listarTodas();
    }

    @Override
    public Map<Coluna, List<Demanda>> listarPorColuna() {
        Map<Coluna, List<Demanda>> resultado = inicializarMapaPorColuna();
        for (Demanda demanda : demandaRepository.listarTodas()) {
            resultado.get(demanda.getColuna()).add(demanda);
        }
        return resultado;
    }

    @Override
    public Map<Coluna, List<Demanda>> filtrar(String termo, Prioridade prioridade, String responsavel) {
        Map<Coluna, List<Demanda>> resultado = inicializarMapaPorColuna();
        for (Demanda demanda : demandaRepository.listarTodas()) {
            if (corresponde(demanda, termo, prioridade, responsavel)) {
                resultado.get(demanda.getColuna()).add(demanda);
            }
        }
        return resultado;
    }

    @Override
    public Demanda editar(Long id, DemandaForm form) {
        Demanda demanda = buscarPorId(id);
        aplicarForm(demanda, form);
        demanda.setAtualizadoEm(LocalDateTime.now());
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda alterarColuna(Long id, Coluna novaColuna) {
        Demanda demanda = buscarPorId(id);
        demanda.setColuna(novaColuna);
        demanda.setAtualizadoEm(LocalDateTime.now());
        return demandaRepository.salvar(demanda);
    }

    @Override
    public void excluir(Long id) {
        if (!demandaRepository.existePorId(id)) {
            throw new DemandaNaoEncontradaException(id);
        }
        demandaRepository.excluir(id);
    }

    // ── Métodos auxiliares privados ──────────────────────────────────────────

    /**
     * Aplica os campos do formulário à entidade, centralizando o mapeamento
     * tanto para criação quanto para edição.
     */
    private void aplicarForm(Demanda demanda, DemandaForm form) {
        demanda.setTitulo(form.getTitulo());
        demanda.setDescricao(form.getDescricao());
        demanda.setColuna(form.getColuna() != null ? form.getColuna() : Coluna.BACKLOG);
        demanda.setPrioridade(form.getPrioridade() != null ? form.getPrioridade() : Prioridade.MEDIA);
        demanda.setResponsavel(
                (form.getResponsavel() != null && !form.getResponsavel().isBlank())
                        ? form.getResponsavel().trim()
                        : null);
        demanda.setPrazo(form.getPrazo());
    }

    /**
     * Inicializa o mapa com todas as colunas e listas vazias, garantindo
     * que colunas sem demandas apareçam no quadro.
     */
    private Map<Coluna, List<Demanda>> inicializarMapaPorColuna() {
        Map<Coluna, List<Demanda>> mapa = new LinkedHashMap<>();
        for (Coluna coluna : Coluna.values()) {
            mapa.put(coluna, new ArrayList<>());
        }
        return mapa;
    }

    /**
     * Verifica se uma demanda corresponde aos critérios de filtro informados.
     * Critérios nulos ou em branco são ignorados (não filtram).
     */
    private boolean corresponde(Demanda demanda, String termo, Prioridade prioridade, String responsavel) {
        if (termo != null && !termo.isBlank()) {
            String t = termo.toLowerCase().trim();
            boolean noTitulo = demanda.getTitulo() != null
                    && demanda.getTitulo().toLowerCase().contains(t);
            boolean naDescricao = demanda.getDescricao() != null
                    && demanda.getDescricao().toLowerCase().contains(t);
            if (!noTitulo && !naDescricao) {
                return false;
            }
        }
        if (prioridade != null && prioridade != demanda.getPrioridade()) {
            return false;
        }
        if (responsavel != null && !responsavel.isBlank()) {
            String r = responsavel.toLowerCase().trim();
            if (demanda.getResponsavel() == null
                    || !demanda.getResponsavel().toLowerCase().contains(r)) {
                return false;
            }
        }
        return true;
    }
}
