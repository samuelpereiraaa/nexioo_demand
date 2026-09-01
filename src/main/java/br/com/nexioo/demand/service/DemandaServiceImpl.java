package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.exception.DemandaNaoEncontradaException;
import br.com.nexioo.demand.model.*;
import br.com.nexioo.demand.repository.DemandaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação completa dos casos de uso de demandas.
 */
@Service
public class DemandaServiceImpl implements DemandaService {

    private final DemandaRepository demandaRepository;
    private final ColunaService colunaService;
    private final AtomicLong checklistIdGenerator = new AtomicLong(100);
    private final AtomicLong itemIdGenerator = new AtomicLong(1000);

    public DemandaServiceImpl(DemandaRepository demandaRepository, ColunaService colunaService) {
        this.demandaRepository = demandaRepository;
        this.colunaService = colunaService;
    }

    @Override
    public Demanda criar(DemandaForm form) {
        Demanda demanda = new Demanda();
        aplicarForm(demanda, form);
        demanda.setCriadoEm(LocalDateTime.now());
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(
                demanda.getResponsavel() != null ? demanda.getResponsavel() : "Samuel Oliveira",
                "adicionou este cartão a " + demanda.getColuna().getDescricao()
        );
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
            if (resultado.containsKey(demanda.getColuna())) {
                resultado.get(demanda.getColuna()).add(demanda);
            } else {
                resultado.computeIfAbsent(demanda.getColuna(), k -> new ArrayList<>()).add(demanda);
            }
        }
        return resultado;
    }

    @Override
    public Map<Coluna, List<Demanda>> filtrar(String termo, Prioridade prioridade, String responsavel) {
        Map<Coluna, List<Demanda>> resultado = inicializarMapaPorColuna();
        for (Demanda demanda : demandaRepository.listarTodas()) {
            if (corresponde(demanda, termo, prioridade, responsavel)) {
                if (resultado.containsKey(demanda.getColuna())) {
                    resultado.get(demanda.getColuna()).add(demanda);
                } else {
                    resultado.computeIfAbsent(demanda.getColuna(), k -> new ArrayList<>()).add(demanda);
                }
            }
        }
        return resultado;
    }

    @Override
    public Demanda editar(Long id, DemandaForm form) {
        Demanda demanda = buscarPorId(id);
        Coluna colunaAnterior = demanda.getColuna();
        Prioridade prioridadeAnterior = demanda.getPrioridade();

        aplicarForm(demanda, form);
        demanda.setAtualizadoEm(LocalDateTime.now());

        if (colunaAnterior != null && !colunaAnterior.equals(demanda.getColuna())) {
            demanda.registrarAtividade("Samuel Oliveira", "moveu este cartão para " + demanda.getColuna().getDescricao());
        } else if (prioridadeAnterior != null && !prioridadeAnterior.equals(demanda.getPrioridade())) {
            demanda.registrarAtividade("Samuel Oliveira", "alterou a prioridade para " + demanda.getPrioridade().getDescricao());
        } else {
            demanda.registrarAtividade("Samuel Oliveira", "atualizou as informações deste cartão");
        }

        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda atualizarTitulo(Long id, String titulo) {
        Demanda demanda = buscarPorId(id);
        String tituloSanitizado = sanitizarTextoLimitado(titulo, 120);
        if (tituloSanitizado != null && !tituloSanitizado.isBlank() && !tituloSanitizado.equals(demanda.getTitulo())) {
            demanda.setTitulo(tituloSanitizado);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade("Samuel Oliveira", "alterou o título para \"" + tituloSanitizado + "\"");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda atualizarDescricao(Long id, String descricao) {
        Demanda demanda = buscarPorId(id);
        String descSanitizada = sanitizarTextoLimitado(descricao, 2000);
        demanda.setDescricao(descSanitizada);
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade("Samuel Oliveira", "atualizou a descrição deste cartão");
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda alterarColuna(Long id, Coluna novaColuna) {
        Demanda demanda = buscarPorId(id);
        Coluna colunaDestino = novaColuna != null ? novaColuna : colunaService.buscarPadrao();
        if (!colunaDestino.equals(demanda.getColuna())) {
            demanda.setColuna(colunaDestino);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade("Samuel Oliveira", "moveu este cartão para " + colunaDestino.getDescricao());
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda alternarConclusao(Long id) {
        Demanda demanda = buscarPorId(id);
        boolean concluida = demanda.getColuna() != null
                && Coluna.CONCLUIDO.getId().equalsIgnoreCase(demanda.getColuna().getId());

        Coluna destino = concluida
                ? colunaService.buscarPorId(Coluna.A_FAZER.getId())
                : colunaService.buscarPorId(Coluna.CONCLUIDO.getId());

        demanda.setColuna(destino);
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(
                "Samuel Oliveira",
                concluida ? "reabriu esta demanda" : "concluiu esta demanda"
        );
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarComentario(Long id, String texto, String autor) {
        Demanda demanda = buscarPorId(id);
        String textoSanitizado = sanitizarTexto(texto);
        if (textoSanitizado != null && !textoSanitizado.isBlank()) {
            demanda.registrarComentario(
                    (autor != null && !autor.isBlank()) ? autor : "Samuel Oliveira",
                    textoSanitizado
            );
            demanda.setAtualizadoEm(LocalDateTime.now());
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda adicionarEtiqueta(Long id, String nome, String corHex) {
        Demanda demanda = buscarPorId(id);
        String nomeSanitizado = sanitizarTexto(nome);
        if (nomeSanitizado != null && !nomeSanitizado.isBlank()) {
            String etiquetaId = nomeSanitizado.toLowerCase().replaceAll("\\s+", "-");
            Etiqueta etiqueta = new Etiqueta(etiquetaId, nomeSanitizado, corHex != null ? corHex : "#00E6A8");
            if (!demanda.getEtiquetas().contains(etiqueta)) {
                demanda.getEtiquetas().add(etiqueta);
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade("Samuel Oliveira", "adicionou a etiqueta \"" + nomeSanitizado + "\"");
                demandaRepository.salvar(demanda);
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerEtiqueta(Long id, String etiquetaId) {
        Demanda demanda = buscarPorId(id);
        boolean removido = demanda.getEtiquetas().removeIf(e -> e.getId().equalsIgnoreCase(etiquetaId));
        if (removido) {
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade("Samuel Oliveira", "removeu uma etiqueta");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda definirPrazo(Long id, LocalDate prazo) {
        Demanda demanda = buscarPorId(id);
        demanda.setPrazo(prazo);
        demanda.setAtualizadoEm(LocalDateTime.now());
        if (prazo != null) {
            String dataFmt = prazo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            demanda.registrarAtividade("Samuel Oliveira", "definiciu o prazo para " + dataFmt);
        } else {
            demanda.registrarAtividade("Samuel Oliveira", "removeu o prazo deste cartão");
        }
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarChecklist(Long id, String titulo) {
        Demanda demanda = buscarPorId(id);
        String tituloSanitizado = (titulo != null && !titulo.isBlank())
                ? sanitizarTextoLimitado(titulo, 120)
                : "Checklist";
        Checklist c = new Checklist(checklistIdGenerator.incrementAndGet(), tituloSanitizado);
        demanda.getChecklists().add(c);
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade("Samuel Oliveira", "adicionou a checklist \"" + tituloSanitizado + "\"");
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda renomearChecklist(Long id, Long checklistId, String titulo) {
        Demanda demanda = buscarPorId(id);
        String tituloSanitizado = sanitizarTextoLimitado(titulo, 120);
        if (tituloSanitizado == null || tituloSanitizado.isBlank()) {
            return demanda;
        }

        for (Checklist checklist : demanda.getChecklists()) {
            if (checklist.getId().equals(checklistId)
                    && !tituloSanitizado.equals(checklist.getTitulo())) {
                checklist.setTitulo(tituloSanitizado);
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade(
                        "Samuel Oliveira",
                        "renomeou uma checklist para \"" + tituloSanitizado + "\""
                );
                demandaRepository.salvar(demanda);
                break;
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerChecklist(Long id, Long checklistId) {
        Demanda demanda = buscarPorId(id);
        boolean removido = demanda.getChecklists().removeIf(c -> c.getId().equals(checklistId));
        if (removido) {
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade("Samuel Oliveira", "removeu uma checklist");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda adicionarItemChecklist(Long id, Long checklistId, String texto) {
        Demanda demanda = buscarPorId(id);
        String textoSanitizado = sanitizarTextoLimitado(texto, 240);
        if (textoSanitizado != null && !textoSanitizado.isBlank()) {
            for (Checklist c : demanda.getChecklists()) {
                if (c.getId().equals(checklistId)) {
                    ChecklistItem item = new ChecklistItem(itemIdGenerator.incrementAndGet(), textoSanitizado, false);
                    c.getItens().add(item);
                    demanda.setAtualizadoEm(LocalDateTime.now());
                    demanda.registrarAtividade("Samuel Oliveira", "adicionou o item \"" + textoSanitizado + "\" a checklist");
                    break;
                }
            }
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda toggleItemChecklist(Long id, Long checklistId, Long itemId) {
        Demanda demanda = buscarPorId(id);
        for (Checklist c : demanda.getChecklists()) {
            if (c.getId().equals(checklistId)) {
                for (ChecklistItem item : c.getItens()) {
                    if (item.getId().equals(itemId)) {
                        item.setConcluido(!item.isConcluido());
                        demanda.setAtualizadoEm(LocalDateTime.now());
                        demanda.registrarAtividade("Samuel Oliveira",
                                (item.isConcluido() ? "concluiu" : "reabriu") + " o item \"" + item.getTexto() + "\"");
                        break;
                    }
                }
                break;
            }
        }
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda atualizarItemChecklist(Long id, Long checklistId, Long itemId, String texto) {
        Demanda demanda = buscarPorId(id);
        String textoSanitizado = sanitizarTextoLimitado(texto, 240);
        if (textoSanitizado == null || textoSanitizado.isBlank()) {
            return demanda;
        }

        for (Checklist checklist : demanda.getChecklists()) {
            if (!checklist.getId().equals(checklistId)) {
                continue;
            }
            for (ChecklistItem item : checklist.getItens()) {
                if (item.getId().equals(itemId) && !textoSanitizado.equals(item.getTexto())) {
                    item.setTexto(textoSanitizado);
                    demanda.setAtualizadoEm(LocalDateTime.now());
                    demanda.registrarAtividade(
                            "Samuel Oliveira",
                            "atualizou um item da checklist para \"" + textoSanitizado + "\""
                    );
                    demandaRepository.salvar(demanda);
                    return demanda;
                }
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerItemChecklist(Long id, Long checklistId, Long itemId) {
        Demanda demanda = buscarPorId(id);
        for (Checklist c : demanda.getChecklists()) {
            if (c.getId().equals(checklistId)) {
                boolean removido = c.getItens().removeIf(i -> i.getId().equals(itemId));
                if (removido) {
                    demanda.setAtualizadoEm(LocalDateTime.now());
                    demanda.registrarAtividade("Samuel Oliveira", "removeu um item da checklist");
                }
                break;
            }
        }
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarMembro(Long id, String membro) {
        Demanda demanda = buscarPorId(id);
        String membroSanitizado = sanitizarTexto(membro);
        if (membroSanitizado != null && !membroSanitizado.isBlank()) {
            if (!demanda.getMembros().contains(membroSanitizado)) {
                demanda.getMembros().add(membroSanitizado);
                if (demanda.getResponsavel() == null) {
                    demanda.setResponsavel(membroSanitizado);
                }
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade("Samuel Oliveira", "adicionou o membro " + membroSanitizado);
                demandaRepository.salvar(demanda);
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerMembro(Long id, String membro) {
        Demanda demanda = buscarPorId(id);
        if (demanda.getMembros().remove(membro)) {
            if (membro.equalsIgnoreCase(demanda.getResponsavel())) {
                demanda.setResponsavel(demanda.getMembros().isEmpty() ? null : demanda.getMembros().get(0));
            }
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade("Samuel Oliveira", "removeu o membro " + membro);
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda toggleAcompanhar(Long id) {
        Demanda demanda = buscarPorId(id);
        demanda.setAcompanhando(!demanda.isAcompanhando());
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade("Samuel Oliveira",
                demanda.isAcompanhando() ? "passou a acompanhar este cartão" : "deixou de acompanhar este cartão");
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

    private String sanitizarTexto(String input) {
        if (input == null) return null;
        return input.replaceAll("<script.*?>.*?</script>", "")
                .replaceAll("(?i)<script", "&lt;script")
                .trim();
    }

    private String sanitizarTextoLimitado(String input, int limite) {
        String texto = sanitizarTexto(input);
        if (texto == null || texto.length() <= limite) {
            return texto;
        }
        return texto.substring(0, limite);
    }

    private void aplicarForm(Demanda demanda, DemandaForm form) {
        demanda.setTitulo(sanitizarTextoLimitado(form.getTitulo(), 120));
        demanda.setDescricao(sanitizarTextoLimitado(form.getDescricao(), 2000));
        demanda.setColuna(form.getColuna() != null ? form.getColuna() : colunaService.buscarPadrao());
        demanda.setPrioridade(form.getPrioridade() != null ? form.getPrioridade() : Prioridade.MEDIA);
        String resp = (form.getResponsavel() != null && !form.getResponsavel().isBlank())
                ? sanitizarTextoLimitado(form.getResponsavel(), 80)
                : null;
        demanda.setResponsavel(resp);
        if (resp != null && !demanda.getMembros().contains(resp)) {
            demanda.getMembros().add(resp);
        }
        demanda.setPrazo(form.getPrazo());
    }

    private Map<Coluna, List<Demanda>> inicializarMapaPorColuna() {
        Map<Coluna, List<Demanda>> mapa = new LinkedHashMap<>();
        for (Coluna coluna : colunaService.listarTodas()) {
            mapa.put(coluna, new ArrayList<>());
        }
        return mapa;
    }

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
