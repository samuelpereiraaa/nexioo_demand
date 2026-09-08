package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.config.StringToColunaConverter;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(DemandaController.class)
@Import(StringToColunaConverter.class)
@DisplayName("DemandaController — camada web")
class DemandaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemandaService demandaService;

    @MockBean
    private ColunaService colunaService;

    @BeforeEach
    void setUp() {
        when(colunaService.buscarPorId("BACKLOG")).thenReturn(Coluna.BACKLOG);
        when(colunaService.buscarPorId("A_FAZER")).thenReturn(Coluna.A_FAZER);
        when(colunaService.buscarPorId("EM_ANDAMENTO")).thenReturn(Coluna.EM_ANDAMENTO);
        when(colunaService.buscarPorId("CONCLUIDO")).thenReturn(Coluna.CONCLUIDO);
        when(colunaService.buscarPadrao()).thenReturn(Coluna.BACKLOG);
    }

    @Test
    @DisplayName("GET /demandas/nova deve retornar o formulário de criação com status 200 quando autenticado")
    void deveExibirFormularioNovaDemanda() throws Exception {
        mockMvc.perform(get("/demandas/nova")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().attributeExists("demandaForm"))
                .andExpect(model().attribute("paginaTitulo", "Nova Demanda"));
    }

    @Test
    @DisplayName("POST /demandas com dados válidos deve criar e redirecionar para o quadro quando autenticado")
    void deveCriarDemandaERedirecionarParaQuadro() throws Exception {
        Demanda demandaCriada = demandaFake(1L, "Nova tarefa de teste");
        when(demandaService.criar(any())).thenReturn(demandaCriada);

        mockMvc.perform(post("/demandas")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("titulo", "Nova tarefa de teste")
                        .param("coluna", "BACKLOG")
                        .param("prioridade", "MEDIA"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro"));
    }

    @Test
    @DisplayName("POST /demandas com título vazio deve retornar formulário com erros de validação quando autenticado")
    void deveRetornarFormularioComErrosDeTituloVazio() throws Exception {
        mockMvc.perform(post("/demandas")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("titulo", "")
                        .param("coluna", "BACKLOG")
                        .param("prioridade", "MEDIA"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().hasErrors())
                .andExpect(model().attributeHasFieldErrors("demandaForm", "titulo"));
    }

    @Test
    @DisplayName("POST /demandas sem prioridade deve retornar formulário com erro de validação quando autenticado")
    void deveRetornarFormularioComErroDePrioridadeAusente() throws Exception {
        mockMvc.perform(post("/demandas")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("titulo", "Título válido")
                        .param("coluna", "BACKLOG"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().attributeHasFieldErrors("demandaForm", "prioridade"));
    }

    @Test
    @DisplayName("GET /demandas/{id} deve exibir os detalhes da demanda quando autenticado")
    void deveExibirDetalhesDaDemanda() throws Exception {
        Demanda demanda = demandaFake(1L, "Tarefa de detalhe");
        when(demandaService.buscarPorId(1L)).thenReturn(demanda);

        mockMvc.perform(get("/demandas/1")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/detalhe"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("GET /demandas/{id}/excluir deve exibir a tela de confirmação quando autenticado")
    void deveExibirTelaDeConfirmacaoDeExclusao() throws Exception {
        Demanda demanda = demandaFake(1L, "Tarefa a excluir");
        when(demandaService.buscarPorId(1L)).thenReturn(demanda);

        mockMvc.perform(get("/demandas/1/excluir")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/confirmar-exclusao"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/status deve alterar coluna e redirecionar para o quadro quando autenticado")
    void deveAlterarStatusERedirecionar() throws Exception {
        mockMvc.perform(post("/demandas/1/status")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("coluna", "CONCLUIDO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/toggle-concluido deve alternar conclusão e redirecionar quando autenticado")
    void deveAlternarConclusaoERedirecionar() throws Exception {
        Demanda demanda = demandaFake(1L, "Tarefa para concluir");
        demanda.setColuna(Coluna.CONCLUIDO);
        when(demandaService.alternarConclusao(1L)).thenReturn(demanda);

        mockMvc.perform(post("/demandas/1/toggle-concluido")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro"));
    }

    @Test
    @DisplayName("POST /demandas/compositor com dados válidos deve criar cartão e retornar fragmento quando autenticado")
    void deveCriarCartaoPeloCompositor() throws Exception {
        Demanda demandaCriada = demandaFake(2L, "Demanda rápida compositor");
        when(demandaService.criar(any())).thenReturn(demandaCriada);

        mockMvc.perform(post("/demandas/compositor")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("titulo", "Demanda rápida compositor")
                        .param("coluna", "BACKLOG")
                        .param("prioridade", "MEDIA"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/cartao :: cartao"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("GET /demandas/{id}/modal deve retornar fragmento do modal quando autenticado")
    void deveRetornarFragmentoDoModal() throws Exception {
        Demanda demanda = demandaFake(1L, "Demanda modal");
        when(demandaService.buscarPorId(1L)).thenReturn(demanda);

        mockMvc.perform(get("/demandas/1/modal")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/descricao deve atualizar descrição e retornar fragmento do modal")
    void deveAtualizarDescricao() throws Exception {
        Demanda demanda = demandaFake(1L, "Demanda com descrição");
        demanda.setDescricao("Nova descrição detalhada");
        when(demandaService.atualizarDescricao(eq(1L), anyString())).thenReturn(demanda);

        mockMvc.perform(post("/demandas/1/descricao")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("descricao", "Nova descrição detalhada"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/comentar deve adicionar comentário e retornar fragmento do modal")
    void deveAdicionarComentario() throws Exception {
        Demanda demanda = demandaFake(1L, "Demanda com comentário");
        when(demandaService.adicionarComentario(eq(1L), anyString(), anyString())).thenReturn(demanda);

        mockMvc.perform(post("/demandas/1/comentar")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("texto", "Este é um comentário de teste"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/imagem e /imagem/remover devem gerenciar anexo de imagem")
    void deveAdicionarERemoverImagemController() throws Exception {
        Demanda demanda = demandaFake(1L, "Demanda com imagem");
        demanda.setImagemUrl("https://exemplo.com/imagem.png");
        when(demandaService.adicionarImagem(eq(1L), anyString())).thenReturn(demanda);
        when(demandaService.removerImagem(1L)).thenReturn(demanda);

        mockMvc.perform(post("/demandas/1/imagem")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("imagemUrl", "https://exemplo.com/imagem.png"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"));

        mockMvc.perform(post("/demandas/1/imagem/remover")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"));
    }

    @Test
    @DisplayName("DELETE e POST /demandas/{id}/api devem excluir demanda via API com sucesso (200 OK)")
    void deveExcluirDemandaViaApi() throws Exception {
        mockMvc.perform(delete("/demandas/1/api")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/demandas/1/api")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /demandas/{id}/mover deve mover demanda e retornar 200 OK com dados atualizados")
    void deveMoverDemandaComSucesso() throws Exception {
        Demanda demanda = demandaFake(1L, "Card teste");
        demanda.setColuna(Coluna.EM_ANDAMENTO);
        demanda.setPosicao(2);

        when(demandaService.mover(eq(1L), eq("BACKLOG"), eq("EM_ANDAMENTO"), eq(2), eq(100L), anyString()))
                .thenReturn(demanda);

        mockMvc.perform(post("/demandas/1/mover")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("colunaOrigemId", "BACKLOG")
                        .param("colunaDestinoId", "EM_ANDAMENTO")
                        .param("novaPosicao", "2")
                        .param("projetoId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.colunaDestino").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.posicao").value(2));
    }

    @Test
    @DisplayName("POST /demandas/{id}/mover deve retornar 404 quando demanda não for encontrada")
    void deveRetornar404AoMoverDemandaInexistente() throws Exception {
        when(demandaService.mover(eq(999L), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new br.com.nexioo.demand.exception.DemandaNaoEncontradaException(999L));

        mockMvc.perform(post("/demandas/999/mover")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("colunaOrigemId", "BACKLOG")
                        .param("colunaDestinoId", "EM_ANDAMENTO")
                        .param("novaPosicao", "0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("erro"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/mover deve redirecionar quando não autenticado")
    void deveRejeitarMoverDemandaSemAutenticacao() throws Exception {
        mockMvc.perform(post("/demandas/1/mover")
                        .param("colunaOrigemId", "BACKLOG")
                        .param("colunaDestinoId", "EM_ANDAMENTO")
                        .param("novaPosicao", "0"))
                .andExpect(status().is3xxRedirection());
    }



    private Demanda demandaFake(Long id, String titulo) {
        Demanda d = new Demanda();
        d.setId(id);
        d.setTitulo(titulo);
        d.setColuna(Coluna.BACKLOG);
        d.setPrioridade(Prioridade.MEDIA);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        return d;
    }
}
