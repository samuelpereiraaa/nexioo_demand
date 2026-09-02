package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Gerencia o ciclo de vida e interações detalhadas das demandas.
 */
@Controller
@CrossOrigin(origins = "*")
@RequestMapping("/demandas")
public class DemandaController {

    private static final Logger log = LoggerFactory.getLogger(DemandaController.class);

    private final DemandaService demandaService;
    private final ColunaService colunaService;


    private static final List<String> MEMBROS_SUGERIDOS = Arrays.asList(
            "Samuel Oliveira", "Ana Silva", "Carlos Oliveira", "Mariana Costa", "Pedro Santos"
    );

    public DemandaController(DemandaService demandaService, ColunaService colunaService) {
        this.demandaService = demandaService;
        this.colunaService = colunaService;
    }

    // ── Criar ────────────────────────────────────────────────────────────────

    @GetMapping("/nova")
    public String novaForm(Model model) {
        DemandaForm form = new DemandaForm();
        form.setColuna(colunaService.buscarPadrao());
        form.setPrioridade(Prioridade.MEDIA);
        model.addAttribute("demandaForm", form);
        model.addAttribute("colunas", colunaService.listarTodas());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("paginaTitulo", "Nova Demanda");
        model.addAttribute("formAction", "/demandas");
        return "demanda/form";
    }

    @PostMapping
    public String criar(
            @Valid @ModelAttribute("demandaForm") DemandaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("colunas", colunaService.listarTodas());
            model.addAttribute("prioridades", Prioridade.values());
            model.addAttribute("paginaTitulo", "Nova Demanda");
            model.addAttribute("formAction", "/demandas");
            return "demanda/form";
        }

        Demanda demanda = demandaService.criar(form);
        redirectAttributes.addFlashAttribute("mensagemSucesso",
                "Demanda \"" + demanda.getTitulo() + "\" criada com sucesso.");
        return "redirect:/quadro";

    }

    // ── Visualizar Fragmento de Modal (AJAX) ──────────────────────────────────

    @GetMapping("/{id}/modal")
    public String detalheModal(@PathVariable Long id, Model model) {
        Demanda demanda = demandaService.buscarPorId(id);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @GetMapping("/{id}/cartao")
    public String cartaoAtualizado(@PathVariable Long id, Model model) {
        model.addAttribute("demanda", demandaService.buscarPorId(id));
        model.addAttribute("colunas", colunaService.listarTodas());
        return "fragments/cartao :: cartao";
    }

    /**
     * Criação rápida de demanda via compositor inline do quadro.
     * Não usa bean validation — valida manualmente para mensagens mais diretas.
     * Retorna o fragmento HTML do cartão recém-criado.
     */
    @PostMapping("/compositor")
    public String criarViaCompositor(
            @ModelAttribute DemandaForm form,
            Model model,
            javax.servlet.http.HttpServletResponse response) {

        String titulo = form.getTitulo();
        if (titulo == null || titulo.trim().isEmpty()) {
            response.setStatus(422);
            return "fragments/cartao :: cartao-vazio";
        }

        form.setTitulo(titulo.trim());
        if (form.getPrioridade() == null) {
            form.setPrioridade(Prioridade.MEDIA);
        }
        if (form.getColuna() == null) {
            form.setColuna(colunaService.buscarPadrao());
        }

        Demanda nova = demandaService.criar(form);
        model.addAttribute("demanda", nova);
        model.addAttribute("colunas", colunaService.listarTodas());
        return "fragments/cartao :: cartao";
    }


    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("demanda", demandaService.buscarPorId(id));
        model.addAttribute("colunas", colunaService.listarTodas());
        return "demanda/detalhe";
    }

    // ── Editar Formulário Tradicional ────────────────────────────────────────

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        Demanda demanda = demandaService.buscarPorId(id);
        model.addAttribute("demandaForm", demandaParaForm(demanda));
        model.addAttribute("demanda", demanda);
        model.addAttribute("colunas", colunaService.listarTodas());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("paginaTitulo", "Editar Demanda");
        model.addAttribute("formAction", "/demandas/" + id + "/editar");
        return "demanda/form";
    }

    @PostMapping("/{id}/editar")
    public String editar(
            @PathVariable Long id,
            @Valid @ModelAttribute("demandaForm") DemandaForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("demanda", demandaService.buscarPorId(id));
            model.addAttribute("colunas", colunaService.listarTodas());
            model.addAttribute("prioridades", Prioridade.values());
            model.addAttribute("paginaTitulo", "Editar Demanda");
            model.addAttribute("formAction", "/demandas/" + id + "/editar");
            return "demanda/form";
        }

        demandaService.editar(id, form);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Demanda atualizada com sucesso.");
        return "redirect:/demandas/" + id;
    }

    // ── Atualizações Específicas do Modal (AJAX) ─────────────────────────────

    @PostMapping("/{id}/titulo")
    public String atualizarTitulo(@PathVariable Long id, @RequestParam String titulo, Model model) {
        Demanda demanda = demandaService.atualizarTitulo(id, titulo);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/descricao")
    public String atualizarDescricao(@PathVariable Long id, @RequestParam(required = false) String descricao, Model model) {
        Demanda demanda = demandaService.atualizarDescricao(id, descricao);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/modal-update")
    public String modalUpdate(
            @PathVariable Long id,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String descricao,
            @RequestParam(required = false) Coluna coluna,
            @RequestParam(required = false) Prioridade prioridade,
            @RequestParam(required = false) String responsavel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prazo,
            Model model) {

        Demanda demanda = demandaService.buscarPorId(id);
        DemandaForm form = demandaParaForm(demanda);

        if (titulo != null && !titulo.isBlank()) form.setTitulo(titulo);
        if (descricao != null) form.setDescricao(descricao);
        if (coluna != null) form.setColuna(coluna);
        if (prioridade != null) form.setPrioridade(prioridade);
        if (responsavel != null) form.setResponsavel(responsavel);
        demanda = demandaService.editar(id, form);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Etiquetas (AJAX) ─────────────────────────────────────────────────────

    @PostMapping("/{id}/etiquetas/adicionar")
    public String adicionarEtiqueta(@PathVariable Long id, @RequestParam String nome, @RequestParam(required = false) String corHex, Model model) {
        Demanda demanda = demandaService.adicionarEtiqueta(id, nome, corHex);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/etiquetas/remover")
    public String removerEtiqueta(@PathVariable Long id, @RequestParam String etiquetaId, Model model) {
        Demanda demanda = demandaService.removerEtiqueta(id, etiquetaId);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Datas (AJAX) ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/prazo")
    public String definirPrazo(@PathVariable Long id, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate prazo, Model model) {
        Demanda demanda = demandaService.definirPrazo(id, prazo);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Checklists (AJAX) ────────────────────────────────────────────────────

    @PostMapping("/{id}/checklists/adicionar")
    public String adicionarChecklist(@PathVariable Long id, @RequestParam(required = false) String titulo, Model model) {
        Demanda demanda = demandaService.adicionarChecklist(id, titulo);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/checklists/renomear")
    public String renomearChecklist(
            @PathVariable Long id,
            @RequestParam Long checklistId,
            @RequestParam String titulo,
            Model model) {
        Demanda demanda = demandaService.renomearChecklist(id, checklistId, titulo);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/checklists/remover")
    public String removerChecklist(@PathVariable Long id, @RequestParam Long checklistId, Model model) {
        Demanda demanda = demandaService.removerChecklist(id, checklistId);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/checklists/itens/adicionar")
    public String adicionarItemChecklist(@PathVariable Long id, @RequestParam Long checklistId, @RequestParam String texto, Model model) {
        Demanda demanda = demandaService.adicionarItemChecklist(id, checklistId, texto);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/checklists/itens/toggle")
    public String toggleItemChecklist(@PathVariable Long id, @RequestParam Long checklistId, @RequestParam Long itemId, Model model) {
        Demanda demanda = demandaService.toggleItemChecklist(id, checklistId, itemId);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/checklists/itens/atualizar")
    public String atualizarItemChecklist(
            @PathVariable Long id,
            @RequestParam Long checklistId,
            @RequestParam Long itemId,
            @RequestParam String texto,
            Model model) {
        Demanda demanda = demandaService.atualizarItemChecklist(id, checklistId, itemId, texto);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/checklists/itens/remover")
    public String removerItemChecklist(@PathVariable Long id, @RequestParam Long checklistId, @RequestParam Long itemId, Model model) {
        Demanda demanda = demandaService.removerItemChecklist(id, checklistId, itemId);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Membros (AJAX) ───────────────────────────────────────────────────────

    @PostMapping("/{id}/membros/adicionar")
    public String adicionarMembro(@PathVariable Long id, @RequestParam String membro, Model model) {
        Demanda demanda = demandaService.adicionarMembro(id, membro);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/membros/remover")
    public String removerMembro(@PathVariable Long id, @RequestParam String membro, Model model) {
        Demanda demanda = demandaService.removerMembro(id, membro);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Acompanhar Demanda (AJAX) ────────────────────────────────────────────

    @PostMapping("/{id}/acompanhar")
    public String toggleAcompanhar(@PathVariable Long id, Model model) {
        Demanda demanda = demandaService.toggleAcompanhar(id);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Comentários (AJAX) ───────────────────────────────────────────────────

    @PostMapping("/{id}/comentar")
    public String adicionarComentario(@PathVariable Long id, @RequestParam String texto, Model model) {
        Demanda demanda = demandaService.adicionarComentario(id, texto, "Samuel Oliveira");
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    // ── Alterar status / Conclusão ───────────────────────────────────────────

    @PostMapping("/{id}/status")
    public String alterarStatus(
            @PathVariable Long id,
            @RequestParam Coluna coluna,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model,
            RedirectAttributes redirectAttributes) {
        Demanda demanda = demandaService.alterarColuna(id, coluna);
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            model.addAttribute("demanda", demanda);
            model.addAttribute("colunas", colunaService.listarTodas());
            return "fragments/cartao :: cartao";
        }
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Status atualizado com sucesso.");
        return "redirect:/quadro";
    }

    @PostMapping("/{id}/toggle-concluido")
    public String toggleConcluido(
            @PathVariable Long id,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            Model model,
            RedirectAttributes redirectAttributes) {
        Demanda demanda = demandaService.alternarConclusao(id);
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            model.addAttribute("demanda", demanda);
            model.addAttribute("colunas", colunaService.listarTodas());
            return "fragments/cartao :: cartao";
        }
        redirectAttributes.addFlashAttribute(
                "mensagemSucesso",
                demanda.isConcluido() ? "Demanda concluída!" : "Demanda reaberta."
        );
        return "redirect:/quadro";
    }

    // ── Imagem (AJAX) ────────────────────────────────────────────────────────

    @PostMapping("/{id}/imagem")
    public String adicionarImagem(@PathVariable Long id, @RequestParam String imagemUrl, Model model) {
        Demanda demanda = demandaService.adicionarImagem(id, imagemUrl);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/imagem/upload")
    public String uploadImagemArquivo(
            @PathVariable Long id,
            @RequestParam("arquivo") org.springframework.web.multipart.MultipartFile arquivo,
            Model model) {
        if (arquivo != null && !arquivo.isEmpty()) {
            try {
                String contentType = arquivo.getContentType();
                byte[] bytes = arquivo.getBytes();
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                String dataUrl = "data:" + (contentType != null ? contentType : "image/png") + ";base64," + base64;
                demandaService.adicionarImagem(id, dataUrl);
            } catch (java.io.IOException e) {
                log.error("Erro ao processar upload de imagem para demanda id {}", id, e);
            }
        }
        Demanda demanda = demandaService.buscarPorId(id);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }

    @PostMapping("/{id}/imagem/remover")
    public String removerImagem(@PathVariable Long id, Model model) {
        Demanda demanda = demandaService.removerImagem(id);
        preencherModelModal(model, demanda);
        return "fragments/modal-detalhe :: modalDetalheConteudo";
    }




    // ── Excluir ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/excluir")
    public String confirmarExclusaoForm(@PathVariable Long id, Model model) {
        model.addAttribute("demanda", demandaService.buscarPorId(id));
        return "demanda/confirmar-exclusao";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        String titulo = demandaService.buscarPorId(id).getTitulo();
        demandaService.excluir(id);
        redirectAttributes.addFlashAttribute("mensagemSucesso", "Demanda \"" + titulo + "\" excluída.");
        return "redirect:/quadro";
    }


    @PostMapping("/{id}/api")
    @DeleteMapping("/{id}/api")
    @ResponseBody
    public ResponseEntity<Void> excluirApi(@PathVariable Long id) {
        demandaService.excluir(id);
        return ResponseEntity.ok().build();
    }


    // ── Auxiliares Privados ──────────────────────────────────────────────────

    private void preencherModelModal(Model model, Demanda demanda) {
        model.addAttribute("demanda", demanda);
        model.addAttribute("colunas", colunaService.listarTodas());
        model.addAttribute("prioridades", Prioridade.values());
        model.addAttribute("membrosSugeridos", MEMBROS_SUGERIDOS);
    }

    private DemandaForm demandaParaForm(Demanda demanda) {
        DemandaForm form = new DemandaForm();
        form.setTitulo(demanda.getTitulo());
        form.setDescricao(demanda.getDescricao());
        form.setColuna(demanda.getColuna());
        form.setPrioridade(demanda.getPrioridade());
        form.setResponsavel(demanda.getResponsavel());
        form.setPrazo(demanda.getPrazo());
        return form;
    }
}
