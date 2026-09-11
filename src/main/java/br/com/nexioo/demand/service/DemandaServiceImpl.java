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
import java.util.stream.Collectors;


/**
 * Implementação completa dos casos de uso de demandas.
 */
@Service
public class DemandaServiceImpl implements DemandaService {

    private final DemandaRepository demandaRepository;
    private final ColunaService colunaService;
    private final br.com.nexioo.demand.config.UserContext userContext;
    private final br.com.nexioo.demand.repository.ProjetoRepository projetoRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public DemandaServiceImpl(DemandaRepository demandaRepository,
                              ColunaService colunaService,
                              br.com.nexioo.demand.config.UserContext userContext,
                              @org.springframework.context.annotation.Lazy br.com.nexioo.demand.repository.ProjetoRepository projetoRepository) {
        this.demandaRepository = demandaRepository;
        this.colunaService = colunaService;
        this.userContext = userContext;
        this.projetoRepository = projetoRepository;
    }

    public DemandaServiceImpl(DemandaRepository demandaRepository,
                              ColunaService colunaService,
                              br.com.nexioo.demand.config.UserContext userContext) {
        this(demandaRepository, colunaService, userContext, null);
    }

    public DemandaServiceImpl(DemandaRepository demandaRepository, ColunaService colunaService) {
        this(demandaRepository, colunaService, null, null);
    }

    private String obterNomeUsuario() {
        if (userContext != null && userContext.isAutenticado()) {
            if (userContext.getNome() != null && !userContext.getNome().isBlank()) {
                return userContext.getNome().trim();
            }
            if (userContext.getEmail() != null && !userContext.getEmail().isBlank()) {
                return userContext.getEmail().trim();
            }
        }
        return "Usuário";
    }

    @Override
    public Demanda criar(DemandaForm form) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        Demanda demanda = new Demanda();
        aplicarForm(demanda, form);
        demanda.setUsuarioId(uid);
        if (demanda.getProjetoId() != null) {
            if (projetoRepository != null) {
                projetoRepository.buscarPorIdEUsuario(demanda.getProjetoId(), uid)
                        .orElseThrow(() -> new SecurityException("Tentativa de associar demanda a um projeto que não pertence ao usuário."));
            }
        } else {
            throw new IllegalArgumentException("O projeto é obrigatório para criar uma demanda.");
        }
        demanda.setCriadoEm(LocalDateTime.now());
        demanda.setAtualizadoEm(LocalDateTime.now());

        // Atribui posição ao final da lista da coluna correspondente
        List<Demanda> demandasColuna = listarPorProjeto(demanda.getProjetoId()).stream()
                .filter(d -> d.getColuna() != null && d.getColuna().equals(demanda.getColuna()))
                .collect(Collectors.toList());
        demanda.setPosicao(demandasColuna.size());

        demanda.registrarAtividade(
                demanda.getResponsavel() != null ? demanda.getResponsavel() : obterNomeUsuario(),
                "adicionou este cartão a " + (demanda.getColuna() != null ? demanda.getColuna().getDescricao() : "quadro")
        );
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda buscarPorId(UUID id) {
        if (id == null) {
            throw new DemandaNaoEncontradaException(id);
        }
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        return demandaRepository.buscarPorIdEUsuario(id, uid)
                .orElseThrow(() -> new DemandaNaoEncontradaException(id));
    }

    @Override
    public List<Demanda> listarTodas() {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        return demandaRepository.listarPorUsuario(uid);
    }

    @Override
    public List<Demanda> listarPorProjeto(UUID projetoId) {
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        if (projetoId == null) {
            return demandaRepository.listarPorUsuario(uid);
        }
        return demandaRepository.listarPorProjetoEUsuario(projetoId, uid);
    }

    @Override
    public Map<Coluna, List<Demanda>> listarPorColuna() {
        return listarPorColuna(null);
    }

    @Override
    public Map<Coluna, List<Demanda>> listarPorColuna(UUID projetoId) {
        Map<Coluna, List<Demanda>> resultado = inicializarMapaPorColuna(projetoId);
        for (Demanda demanda : listarPorProjeto(projetoId)) {
            if (resultado.containsKey(demanda.getColuna())) {
                resultado.get(demanda.getColuna()).add(demanda);
            } else {
                resultado.computeIfAbsent(demanda.getColuna(), k -> new ArrayList<>()).add(demanda);
            }
        }
        for (List<Demanda> lista : resultado.values()) {
            lista.sort(Comparator.comparing(d -> d.getPosicao() != null ? d.getPosicao() : 0));
        }
        return resultado;
    }

    @Override
    public Map<Coluna, List<Demanda>> filtrar(String termo, Prioridade prioridade, String responsavel) {
        return filtrar(null, termo, prioridade, responsavel);
    }

    @Override
    public Map<Coluna, List<Demanda>> filtrar(UUID projetoId, String termo, Prioridade prioridade, String responsavel) {
        Map<Coluna, List<Demanda>> resultado = inicializarMapaPorColuna(projetoId);
        for (Demanda demanda : listarPorProjeto(projetoId)) {
            if (corresponde(demanda, termo, prioridade, responsavel)) {
                if (resultado.containsKey(demanda.getColuna())) {
                    resultado.get(demanda.getColuna()).add(demanda);
                } else {
                    resultado.computeIfAbsent(demanda.getColuna(), k -> new ArrayList<>()).add(demanda);
                }
            }
        }
        for (List<Demanda> lista : resultado.values()) {
            lista.sort(Comparator.comparing(d -> d.getPosicao() != null ? d.getPosicao() : 0));
        }
        return resultado;
    }


    @Override
    public Demanda editar(UUID id, DemandaForm form) {
        Demanda demanda = buscarPorId(id);
        Coluna colunaAnterior = demanda.getColuna();
        Prioridade prioridadeAnterior = demanda.getPrioridade();

        aplicarForm(demanda, form);
        demanda.setAtualizadoEm(LocalDateTime.now());

        if (colunaAnterior != null && !colunaAnterior.equals(demanda.getColuna())) {
            demanda.registrarAtividade(obterNomeUsuario(), "moveu este cartão para " + demanda.getColuna().getDescricao());
        } else if (prioridadeAnterior != null && !prioridadeAnterior.equals(demanda.getPrioridade())) {
            demanda.registrarAtividade(obterNomeUsuario(), "alterou a prioridade para " + demanda.getPrioridade().getDescricao());
        } else {
            demanda.registrarAtividade(obterNomeUsuario(), "atualizou as informações deste cartão");
        }

        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda atualizarTitulo(UUID id, String titulo) {
        Demanda demanda = buscarPorId(id);
        String tituloSanitizado = sanitizarTextoLimitado(titulo, 120);
        if (tituloSanitizado != null && !tituloSanitizado.isBlank() && !tituloSanitizado.equals(demanda.getTitulo())) {
            demanda.setTitulo(tituloSanitizado);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "alterou o título para \"" + tituloSanitizado + "\"");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda atualizarDescricao(UUID id, String descricao) {
        Demanda demanda = buscarPorId(id);
        String descSanitizada = sanitizarTextoLimitado(descricao, 2000);
        demanda.setDescricao(descSanitizada);
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(obterNomeUsuario(), "atualizou a descrição deste cartão");
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda alterarColuna(UUID id, Coluna novaColuna) {
        Demanda demanda = buscarPorId(id);
        Coluna colunaDestino = novaColuna != null ? novaColuna : colunaService.buscarPadrao(demanda.getProjetoId());
        if (!colunaDestino.equals(demanda.getColuna())) {
            demanda.setColuna(colunaDestino);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "moveu este cartão para " + colunaDestino.getDescricao());
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda mover(UUID id, String colunaOrigemId, String colunaDestinoId, Integer novaPosicao, UUID projetoId, String usuario) {
        Demanda demanda = buscarPorId(id);

        UUID effectiveProjetoId = (projetoId != null) ? projetoId : demanda.getProjetoId();

        Coluna colOrig = (colunaOrigemId != null && !colunaOrigemId.isBlank())
                ? colunaService.buscarPorId(colunaOrigemId, effectiveProjetoId)
                : demanda.getColuna();

        Coluna colDest = (colunaDestinoId != null && !colunaDestinoId.isBlank())
                ? colunaService.buscarPorId(colunaDestinoId, effectiveProjetoId)
                : demanda.getColuna();

        if (colDest == null) {
            colDest = colunaService.buscarPadrao(effectiveProjetoId);
        }

        final Coluna colunaOrigem = colOrig;
        final Coluna colunaDestino = colDest;

        if (projetoId != null && demanda.getProjetoId() != null && !projetoId.equals(demanda.getProjetoId())) {
            throw new IllegalArgumentException("Demanda não pertence ao projeto informado.");
        }

        List<Demanda> todasDoProjeto = listarPorProjeto(effectiveProjetoId);

        boolean mesmaColuna = colunaOrigem != null && colunaOrigem.equals(colunaDestino);

        if (mesmaColuna) {
            List<Demanda> cardsDaColuna = todasDoProjeto.stream()
                    .filter(d -> colunaDestino.equals(d.getColuna()) && !d.getId().equals(id))
                    .sorted(Comparator.comparing(Demanda::getPosicao))
                    .collect(Collectors.toList());

            int pos = Math.max(0, Math.min(novaPosicao != null ? novaPosicao : 0, cardsDaColuna.size()));
            cardsDaColuna.add(pos, demanda);

            for (int i = 0; i < cardsDaColuna.size(); i++) {
                Demanda d = cardsDaColuna.get(i);
                d.setPosicao(i);
                if (!d.getId().equals(id)) {
                    demandaRepository.salvar(d);
                }
            }

            demanda.setPosicao(pos);
            demanda.setAtualizadoEm(LocalDateTime.now());
            String autor = (usuario != null && !usuario.isBlank()) ? usuario : obterNomeUsuario();
            demanda.registrarAtividade(autor, "reordenou este cartão para a posição " + (pos + 1));
            return demandaRepository.salvar(demanda);
        } else {
            // Remove da coluna de origem e renumera
            List<Demanda> cardsOrigem = todasDoProjeto.stream()
                    .filter(d -> (colunaOrigem != null ? colunaOrigem.equals(d.getColuna()) : false) && !d.getId().equals(id))
                    .sorted(Comparator.comparing(Demanda::getPosicao))
                    .collect(Collectors.toList());

            for (int i = 0; i < cardsOrigem.size(); i++) {
                Demanda d = cardsOrigem.get(i);
                d.setPosicao(i);
                demandaRepository.salvar(d);
            }

            // Insere na coluna de destino e renumera
            List<Demanda> cardsDestino = todasDoProjeto.stream()
                    .filter(d -> colunaDestino.equals(d.getColuna()) && !d.getId().equals(id))
                    .sorted(Comparator.comparing(Demanda::getPosicao))
                    .collect(Collectors.toList());

            int pos = Math.max(0, Math.min(novaPosicao != null ? novaPosicao : 0, cardsDestino.size()));
            cardsDestino.add(pos, demanda);

            for (int i = 0; i < cardsDestino.size(); i++) {
                Demanda d = cardsDestino.get(i);
                d.setPosicao(i);
                if (!d.getId().equals(id)) {
                    demandaRepository.salvar(d);
                }
            }

            demanda.setColuna(colunaDestino);
            demanda.setPosicao(pos);
            demanda.setAtualizadoEm(LocalDateTime.now());
            String autor = (usuario != null && !usuario.isBlank()) ? usuario : obterNomeUsuario();
            demanda.registrarAtividade(autor, "moveu este cartão para " + colunaDestino.getDescricao() + " (posição " + (pos + 1) + ")");
            return demandaRepository.salvar(demanda);
        }
    }

    @Override
    public Demanda alternarConclusao(UUID id) {
        Demanda demanda = buscarPorId(id);
        boolean novoEstado = !demanda.isConcluido();
        demanda.setConcluido(novoEstado);
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(
                obterNomeUsuario(),
                novoEstado ? "concluiu esta demanda" : "reabriu esta demanda"
        );
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarImagem(UUID id, String imagemUrl) {
        Demanda demanda = buscarPorId(id);
        if (imagemUrl != null && !imagemUrl.isBlank()) {
            demanda.adicionarImagemNaLista(imagemUrl.trim());
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "anexou uma imagem à demanda");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda removerImagem(UUID id) {
        Demanda demanda = buscarPorId(id);
        demanda.setImagemUrl(null);
        demanda.getAnexos().clear();
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(obterNomeUsuario(), "removeu os anexos da demanda");
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda removerImagemEspecifica(UUID id, String imagemUrl) {
        Demanda demanda = buscarPorId(id);
        if (imagemUrl != null) {
            demanda.removerImagemDaLista(imagemUrl);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "removeu uma imagem da demanda");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda adicionarAnexo(UUID id, String nome, String url) {
        Demanda demanda = buscarPorId(id);
        if (url != null && !url.isBlank()) {
            Anexo anexo = demanda.adicionarAnexo(nome, url.trim());
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "anexou " + anexo.getNome() + " a este cartão");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda removerAnexo(UUID id, String anexoId) {
        Demanda demanda = buscarPorId(id);
        if (anexoId != null) {
            Anexo a = demanda.getAnexoById(anexoId);
            String nome = a != null ? a.getNome() : "anexo";
            if (demanda.removerAnexo(anexoId)) {
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade(obterNomeUsuario(), "removeu o anexo \"" + nome + "\"");
                demandaRepository.salvar(demanda);
            }
        }
        return demanda;
    }

    @Override
    public Demanda definirCapaAnexo(UUID id, String anexoId, boolean capa) {
        Demanda demanda = buscarPorId(id);
        if (anexoId != null) {
            demanda.definirCapa(anexoId, capa);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), capa ? "definiu o anexo como capa" : "removeu a capa deste cartão");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda renomearAnexo(UUID id, String anexoId, String novoNome) {
        Demanda demanda = buscarPorId(id);
        if (anexoId != null && novoNome != null && !novoNome.isBlank()) {
            demanda.renomearAnexo(anexoId, novoNome);
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "renomeou o anexo para \"" + novoNome.trim() + "\"");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda comentarAnexo(UUID id, String anexoId, String texto, String autor) {
        Demanda demanda = buscarPorId(id);
        Anexo a = demanda.getAnexoById(anexoId);
        String prefixo = (a != null) ? ("[" + a.getNome() + "] ") : "";
        String autorFinal = (autor != null && !autor.isBlank()) ? autor : obterNomeUsuario();
        demanda.registrarComentario(autorFinal, prefixo + (texto != null ? texto.trim() : ""));
        demanda.setAtualizadoEm(LocalDateTime.now());
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarComentario(UUID id, String texto, String autor) {
        Demanda demanda = buscarPorId(id);
        String textoSanitizado = sanitizarTexto(texto);
        if (textoSanitizado != null && !textoSanitizado.isBlank()) {
            demanda.registrarComentario(
                    (autor != null && !autor.isBlank()) ? autor : obterNomeUsuario(),
                    textoSanitizado
            );
            demanda.setAtualizadoEm(LocalDateTime.now());
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda adicionarEtiqueta(UUID id, String nome, String corHex) {
        Demanda demanda = buscarPorId(id);
        String nomeSanitizado = sanitizarTexto(nome);
        if (nomeSanitizado != null && !nomeSanitizado.isBlank()) {
            boolean jaExiste = demanda.getEtiquetas().stream()
                    .anyMatch(e -> e.getNome().equalsIgnoreCase(nomeSanitizado));
            if (!jaExiste) {
                String etiquetaId = nomeSanitizado.toLowerCase().replaceAll("\\s+", "-");
                String corValida = (corHex != null && corHex.matches("^#(?:[0-9a-fA-F]{3}){1,2}$")) ? corHex : "#00E6A8";
                Etiqueta etiqueta = new Etiqueta(etiquetaId, nomeSanitizado, corValida);
                demanda.getEtiquetas().add(etiqueta);
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade(obterNomeUsuario(), "adicionou a etiqueta \"" + nomeSanitizado + "\"");
                demandaRepository.salvar(demanda);
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerEtiqueta(UUID id, String etiquetaId) {
        Demanda demanda = buscarPorId(id);
        if (etiquetaId != null && !etiquetaId.isBlank()) {
            boolean removido = demanda.getEtiquetas().removeIf(e ->
                    e.getId().equalsIgnoreCase(etiquetaId) || e.getNome().equalsIgnoreCase(etiquetaId));
            if (removido) {
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade(obterNomeUsuario(), "removeu uma etiqueta");
                demandaRepository.salvar(demanda);
            }
        }
        return demanda;
    }

    @Override
    public Demanda definirPrazo(UUID id, LocalDate prazo) {
        Demanda demanda = buscarPorId(id);
        demanda.setPrazo(prazo);
        demanda.setAtualizadoEm(LocalDateTime.now());
        if (prazo != null) {
            String dataFmt = prazo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            demanda.registrarAtividade(obterNomeUsuario(), "definiciu o prazo para " + dataFmt);
        } else {
            demanda.registrarAtividade(obterNomeUsuario(), "removeu o prazo deste cartão");
        }
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarChecklist(UUID id, String titulo) {
        Demanda demanda = buscarPorId(id);
        String tituloSanitizado = (titulo != null && !titulo.isBlank())
                ? sanitizarTextoLimitado(titulo, 120)
                : "Checklist";
        Checklist c = new Checklist(UUID.randomUUID(), tituloSanitizado);
        demanda.getChecklists().add(c);
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(obterNomeUsuario(), "adicionou a checklist \"" + tituloSanitizado + "\"");
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda renomearChecklist(UUID id, UUID checklistId, String titulo) {
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
                        obterNomeUsuario(),
                        "renomeou uma checklist para \"" + tituloSanitizado + "\""
                );
                demandaRepository.salvar(demanda);
                break;
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerChecklist(UUID id, UUID checklistId) {
        Demanda demanda = buscarPorId(id);
        boolean removido = demanda.getChecklists().removeIf(c -> c.getId().equals(checklistId));
        if (removido) {
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "removeu uma checklist");
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda adicionarItemChecklist(UUID id, UUID checklistId, String texto) {
        Demanda demanda = buscarPorId(id);
        String textoSanitizado = sanitizarTextoLimitado(texto, 240);
        if (textoSanitizado != null && !textoSanitizado.isBlank()) {
            for (Checklist c : demanda.getChecklists()) {
                if (c.getId().equals(checklistId)) {
                    ChecklistItem item = new ChecklistItem(UUID.randomUUID(), textoSanitizado, false);
                    c.getItens().add(item);
                    demanda.setAtualizadoEm(LocalDateTime.now());
                    demanda.registrarAtividade(obterNomeUsuario(), "adicionou o item \"" + textoSanitizado + "\" a checklist");
                    break;
                }
            }
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda toggleItemChecklist(UUID id, UUID checklistId, UUID itemId) {
        Demanda demanda = buscarPorId(id);
        for (Checklist c : demanda.getChecklists()) {
            if (c.getId().equals(checklistId)) {
                for (ChecklistItem item : c.getItens()) {
                    if (item.getId().equals(itemId)) {
                        item.setConcluido(!item.isConcluido());
                        demanda.setAtualizadoEm(LocalDateTime.now());
                        demanda.registrarAtividade(obterNomeUsuario(),
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
    public Demanda atualizarItemChecklist(UUID id, UUID checklistId, UUID itemId, String texto) {
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
                            obterNomeUsuario(),
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
    public Demanda removerItemChecklist(UUID id, UUID checklistId, UUID itemId) {
        Demanda demanda = buscarPorId(id);
        for (Checklist c : demanda.getChecklists()) {
            if (c.getId().equals(checklistId)) {
                boolean removido = c.getItens().removeIf(i -> i.getId().equals(itemId));
                if (removido) {
                    demanda.setAtualizadoEm(LocalDateTime.now());
                    demanda.registrarAtividade(obterNomeUsuario(), "removeu um item da checklist");
                }
                break;
            }
        }
        return demandaRepository.salvar(demanda);
    }

    @Override
    public Demanda adicionarMembro(UUID id, String membro) {
        Demanda demanda = buscarPorId(id);
        String membroSanitizado = sanitizarTexto(membro);
        if (membroSanitizado != null && !membroSanitizado.isBlank()) {
            if (!demanda.getMembros().contains(membroSanitizado)) {
                demanda.getMembros().add(membroSanitizado);
                if (demanda.getResponsavel() == null) {
                    demanda.setResponsavel(membroSanitizado);
                }
                demanda.setAtualizadoEm(LocalDateTime.now());
                demanda.registrarAtividade(obterNomeUsuario(), "adicionou o membro " + membroSanitizado);
                demandaRepository.salvar(demanda);
            }
        }
        return demanda;
    }

    @Override
    public Demanda removerMembro(UUID id, String membro) {
        Demanda demanda = buscarPorId(id);
        if (demanda.getMembros().remove(membro)) {
            if (membro.equalsIgnoreCase(demanda.getResponsavel())) {
                demanda.setResponsavel(demanda.getMembros().isEmpty() ? null : demanda.getMembros().get(0));
            }
            demanda.setAtualizadoEm(LocalDateTime.now());
            demanda.registrarAtividade(obterNomeUsuario(), "removeu o membro " + membro);
            demandaRepository.salvar(demanda);
        }
        return demanda;
    }

    @Override
    public Demanda toggleAcompanhar(UUID id) {
        Demanda demanda = buscarPorId(id);
        demanda.setAcompanhando(!demanda.isAcompanhando());
        demanda.setAtualizadoEm(LocalDateTime.now());
        demanda.registrarAtividade(obterNomeUsuario(),
                demanda.isAcompanhando() ? "passou a acompanhar este cartão" : "deixou de acompanhar este cartão");
        return demandaRepository.salvar(demanda);
    }

    @Override
    public void excluir(UUID id) {
        if (id == null) return;
        if (userContext == null || !userContext.isAutenticado()) {
            throw new SecurityException("Usuário não autenticado.");
        }
        UUID uid = userContext.requireUsuarioId();
        Demanda d = demandaRepository.buscarPorIdEUsuario(id, uid)
                .orElseThrow(() -> new DemandaNaoEncontradaException(id));
        demandaRepository.excluirPorIdEUsuario(id, uid);
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
        Coluna col = form.getColuna();
        if (col != null && form.getProjetoId() != null && colunaService != null) {
            String colIdentifier = col.getUuid() != null ? col.getUuid().toString() : (col.getId() != null ? col.getId() : col.getCodigo());
            col = colunaService.buscarPorId(colIdentifier, form.getProjetoId());
        } else if (col == null && colunaService != null) {
            col = colunaService.buscarPadrao(form.getProjetoId());
        }
        demanda.setColuna(col);
        demanda.setPrioridade(form.getPrioridade() != null ? form.getPrioridade() : Prioridade.MEDIA);
        String resp = (form.getResponsavel() != null && !form.getResponsavel().isBlank())
                ? sanitizarTextoLimitado(form.getResponsavel(), 80)
                : null;
        demanda.setResponsavel(resp);
        if (resp != null && !demanda.getMembros().contains(resp)) {
            demanda.getMembros().add(resp);
        }
        demanda.setPrazo(form.getPrazo());
        if (form.getProjetoId() != null) {
            demanda.setProjetoId(form.getProjetoId());
        }
    }

    private Map<Coluna, List<Demanda>> inicializarMapaPorColuna(UUID projetoId) {
        Map<Coluna, List<Demanda>> mapa = new LinkedHashMap<>();
        List<Coluna> colunas = (projetoId != null) ? colunaService.listarPorProjeto(projetoId) : colunaService.listarTodas();
        for (Coluna coluna : colunas) {
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
