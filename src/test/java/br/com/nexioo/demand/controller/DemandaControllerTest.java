package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.config.StringToColunaConverter;
import br.com.nexioo.demand.config.StringToUuidConverter;
import br.com.nexioo.demand.config.UserContext;
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
import java.util.UUID;

import static br.com.nexioo.demand.util.CsrfTestUtils.withCsrf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(DemandaController.class)
@Import({StringToColunaConverter.class, StringToUuidConverter.class})
@DisplayName("DemandaController — camada web")
class DemandaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemandaService demandaService;

    @MockBean
    private ColunaService colunaService;

    @MockBean
    private UserContext userContext;

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
                        .with(withCsrf())
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
                        .with(withCsrf())
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
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("titulo", "Título válido")
                        .param("coluna", "BACKLOG"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/form"))
                .andExpect(model().attributeHasFieldErrors("demandaForm", "prioridade"));
    }

    private static final UUID DEMANDA_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEMANDA_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEMANDA_ID_INEXISTENTE = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID PROJETO_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Test
    @DisplayName("GET /demandas/{id} deve exibir os detalhes da demanda quando autenticado")
    void deveExibirDetalhesDaDemanda() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Tarefa de detalhe");
        when(demandaService.buscarPorId(any(UUID.class))).thenReturn(demanda);

        mockMvc.perform(get("/demandas/" + DEMANDA_ID)
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/detalhe"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("GET /demandas/{id}/excluir deve exibir a tela de confirmação quando autenticado")
    void deveExibirTelaDeConfirmacaoDeExclusao() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Tarefa a excluir");
        when(demandaService.buscarPorId(any(UUID.class))).thenReturn(demanda);

        mockMvc.perform(get("/demandas/" + DEMANDA_ID + "/excluir")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("demanda/confirmar-exclusao"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/status deve alterar coluna e redirecionar para o quadro quando autenticado")
    void deveAlterarStatusERedirecionar() throws Exception {
        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/status")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("coluna", "CONCLUIDO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/toggle-concluido deve alternar conclusão e redirecionar quando autenticado")
    void deveAlternarConclusaoERedirecionar() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Tarefa para concluir");
        demanda.setColuna(Coluna.CONCLUIDO);
        when(demandaService.alternarConclusao(any(UUID.class))).thenReturn(demanda);

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/toggle-concluido")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/quadro"));
    }

    @Test
    @DisplayName("POST /demandas/compositor com dados válidos deve criar cartão e retornar fragmento quando autenticado")
    void deveCriarCartaoPeloCompositor() throws Exception {
        Demanda demandaCriada = demandaFake(DEMANDA_ID_2, "Demanda rápida compositor");
        when(demandaService.criar(any())).thenReturn(demandaCriada);

        mockMvc.perform(post("/demandas/compositor")
                        .with(withCsrf())
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
        Demanda demanda = demandaFake(DEMANDA_ID, "Demanda modal");
        when(demandaService.buscarPorId(any(UUID.class))).thenReturn(demanda);

        mockMvc.perform(get("/demandas/" + DEMANDA_ID + "/modal")
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/descricao deve atualizar descrição e retornar fragmento do modal")
    void deveAtualizarDescricao() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Demanda com descrição");
        demanda.setDescricao("Nova descrição detalhada");
        when(demandaService.atualizarDescricao(any(UUID.class), anyString())).thenReturn(demanda);

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/descricao")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("descricao", "Nova descrição detalhada"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/comentar deve adicionar comentário e retornar fragmento do modal")
    void deveAdicionarComentario() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Demanda com comentário");
        when(demandaService.adicionarComentario(any(UUID.class), anyString(), anyString())).thenReturn(demanda);

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/comentar")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("texto", "Este é um comentário de teste"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"))
                .andExpect(model().attributeExists("demanda"));
    }

    @Test
    @DisplayName("POST /demandas/{id}/imagem e /imagem/remover devem gerenciar anexo de imagem")
    void deveAdicionarERemoverImagemController() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Demanda com imagem");
        demanda.setImagemUrl("https://exemplo.com/imagem.png");
        when(demandaService.adicionarImagem(any(UUID.class), anyString())).thenReturn(demanda);
        when(demandaService.removerImagem(any(UUID.class))).thenReturn(demanda);

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/imagem")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("imagemUrl", "https://exemplo.com/imagem.png"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"));

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/imagem/remover")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/modal-detalhe :: modalDetalheConteudo"));
    }

    @Test
    @DisplayName("DELETE e POST /demandas/{id}/api devem excluir demanda via API com sucesso (200 OK)")
    void deveExcluirDemandaViaApi() throws Exception {
        mockMvc.perform(delete("/demandas/" + DEMANDA_ID + "/api")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/api")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /demandas/{id}/mover deve mover demanda e retornar 200 OK com dados atualizados")
    void deveMoverDemandaComSucesso() throws Exception {
        Demanda demanda = demandaFake(DEMANDA_ID, "Card teste");
        demanda.setColuna(Coluna.EM_ANDAMENTO);
        demanda.setPosicao(2);

        when(demandaService.mover(any(UUID.class), eq("BACKLOG"), eq("EM_ANDAMENTO"), eq(2), any(), anyString()))
                .thenReturn(demanda);

        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/mover")
                        .with(withCsrf())
                        .sessionAttr(AuthInterceptor.CHAVE_USUARIO_LOGADO, "usuario@test.com")
                        .param("colunaOrigemId", "BACKLOG")
                        .param("colunaDestinoId", "EM_ANDAMENTO")
                        .param("novaPosicao", "2")
                        .param("projetoId", PROJETO_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.id").value(demanda.getId().toString()))
                .andExpect(jsonPath("$.colunaDestino").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.posicao").value(2));
    }

    @Test
    @DisplayName("POST /demandas/{id}/mover deve retornar 404 quando demanda não for encontrada")
    void deveRetornar404AoMoverDemandaInexistente() throws Exception {
        when(demandaService.mover(any(UUID.class), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new br.com.nexioo.demand.exception.DemandaNaoEncontradaException(DEMANDA_ID_INEXISTENTE));

        mockMvc.perform(post("/demandas/" + DEMANDA_ID_INEXISTENTE + "/mover")
                        .with(withCsrf())
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
        mockMvc.perform(post("/demandas/" + DEMANDA_ID + "/mover")
                        .param("colunaOrigemId", "BACKLOG")
                        .param("colunaDestinoId", "EM_ANDAMENTO")
                        .param("novaPosicao", "0"))
                .andExpect(status().is3xxRedirection());
    }

    private Demanda demandaFake(Object id, String titulo) {
        Demanda d = new Demanda();
        UUID uuid = id instanceof UUID ? (UUID) id : (id != null ? br.com.nexioo.demand.util.IdUtils.parseUuid(id.toString()) : null);
        d.setId(uuid != null ? uuid : DEMANDA_ID);
        d.setTitulo(titulo);
        d.setColuna(Coluna.BACKLOG);
        d.setPrioridade(Prioridade.MEDIA);
        d.setCriadoEm(LocalDateTime.now());
        d.setAtualizadoEm(LocalDateTime.now());
        return d;
    }
}
