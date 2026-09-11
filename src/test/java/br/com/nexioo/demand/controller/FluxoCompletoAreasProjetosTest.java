package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.config.AuthInterceptor;
import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Demanda;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.service.AreaTrabalhoService;
import br.com.nexioo.demand.service.ColunaService;
import br.com.nexioo.demand.service.DemandaService;
import br.com.nexioo.demand.service.ProjetoService;
import br.com.nexioo.demand.service.SupabaseAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static br.com.nexioo.demand.util.CsrfTestUtils.withCsrf;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Fluxo Completo: Áreas de Trabalho, Projetos, Avatar Dinâmico e Isolamento de Demandas")
class FluxoCompletoAreasProjetosTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AreaTrabalhoService areaTrabalhoService;

    @Autowired
    private ProjetoService projetoService;

    @Autowired
    private DemandaService demandaService;

    @Autowired
    private ColunaService colunaService;

    @Autowired
    private br.com.nexioo.demand.config.UserContext userContext;

    @MockBean
    private SupabaseAuthService supabaseAuthService;

    private static final java.util.UUID UID_ANA = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final java.util.UUID UID_JOAO = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        when(supabaseAuthService.autenticar("ana.silva@nexioo.com.br", "123456"))
                .thenReturn(new SupabaseUser(UID_ANA.toString(), "ana.silva@nexioo.com.br", "Ana Silva", "fake-token-ana"));
        when(supabaseAuthService.autenticar("joao@empresa.com", "123456"))
                .thenReturn(new SupabaseUser(UID_JOAO.toString(), "joao@empresa.com", "João", "fake-token-joao"));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Validação dos 20 passos do fluxo de Áreas, Projetos e Demandas")
    void testFluxoCompletoAreasProjetosDemandas() throws Exception {
        MockHttpSession session = new MockHttpSession();
        org.springframework.mock.web.MockHttpServletRequest testRequest = new org.springframework.mock.web.MockHttpServletRequest();
        testRequest.setSession(session);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(testRequest));
        userContext.inicializar(UID_ANA, "ana.silva@nexioo.com.br", "Ana Silva", "fake-token-ana");

        // 1. Fazer login como usuário "Ana Silva" (ana.silva@nexioo.com.br)
        mockMvc.perform(post("/login")
                        .session(session)
                        .param("email", "ana.silva@nexioo.com.br")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"));

        // 2. Confirmar avatar com as iniciais corretas "AS" e ausência de "Templates" e "Início"
        mockMvc.perform(get("/projetos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span>AS</span>")))
                .andExpect(content().string(not(containsString("Templates"))))
                .andExpect(content().string(not(containsString("<span>Início</span>"))))
                .andExpect(content().string(containsString("Áreas de trabalho")));

        // 3 e 4. Criar nova área de trabalho "Tecnologia e Inovação" via POST /areas
        mockMvc.perform(post("/areas")
                        .with(withCsrf())
                        .session(session)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("nome", "Tecnologia e Inovação"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.inicial").value("T"));

        // 5 e 6. Abrir a área criada e confirmar a inicial "T"
        AreaTrabalho areaT = areaTrabalhoService.listarPorUsuario("ana.silva@nexioo.com.br").stream()
                .filter(a -> a.getNome().equals("Tecnologia e Inovação"))
                .findFirst()
                .orElseThrow();
        assertEquals("T", areaT.getInicial());

        mockMvc.perform(get("/projetos").session(session).param("areaId", areaT.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tecnologia e Inovação")));

        // 7. Criar dois projetos dentro desta área
        ProjetoForm pForm1 = new ProjetoForm();
        pForm1.setNome("Projeto Frontend App");
        pForm1.setAreaTrabalhoId(areaT.getId());
        Projeto proj1 = projetoService.criar(pForm1);

        ProjetoForm pForm2 = new ProjetoForm();
        pForm2.setNome("Projeto Backend API");
        pForm2.setAreaTrabalhoId(areaT.getId());
        Projeto proj2 = projetoService.criar(pForm2);

        assertNotNull(proj1.getId());
        assertNotNull(proj2.getId());
        assertNotEquals(proj1.getId(), proj2.getId());

        // 8 e 9. Abrir o primeiro projeto e criar demanda exclusiva nele
        DemandaForm dForm1 = new DemandaForm();
        dForm1.setTitulo("Criar componente Header");
        dForm1.setColuna(Coluna.BACKLOG);
        dForm1.setPrioridade(Prioridade.ALTA);
        dForm1.setProjetoId(proj1.getId());
        Demanda card1 = demandaService.criar(dForm1);

        // Acessar quadro do projeto 1 e confirmar que card1 está presente
        mockMvc.perform(get("/quadro").session(session).param("projetoId", proj1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Projeto Frontend App")))
                .andExpect(content().string(containsString("Criar componente Header")));

        // 10, 11 e 12. Abrir o segundo projeto e confirmar que NÃO contém demandas do primeiro!
        mockMvc.perform(get("/quadro").session(session).param("projetoId", proj2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Projeto Backend API")))
                .andExpect(content().string(not(containsString("Criar componente Header"))));

        // 13, 14 e 15. Trocar novamente de área e confirmar isolamento
        AreaTrabalho area2 = areaTrabalhoService.criar("Design e Criação", "ana.silva@nexioo.com.br");
        assertEquals("D", area2.getInicial());

        mockMvc.perform(get("/projetos").session(session).param("areaId", area2.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Design e Criação")));

        // 16. Editar e renomear área de trabalho
        mockMvc.perform(post("/areas/" + area2.getId() + "/editar")
                        .with(withCsrf())
                        .session(session)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .param("nome", "Design UX & UI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Design UX & UI"))
                .andExpect(jsonPath("$.inicial").value("D"));

        // Excluir área
        mockMvc.perform(post("/areas/" + area2.getId() + "/excluir")
                        .with(withCsrf())
                        .session(session)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk());

        // 17. Confirmar ausência de Templates e Início
        mockMvc.perform(get("/projetos").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Templates"))))
                .andExpect(content().string(not(containsString("<span>Início</span>"))));

        // 18 e 19. Fazer logout via POST com CSRF e confirmar sucesso
        mockMvc.perform(post("/logout").with(withCsrf()).session(session))
                .andExpect(status().isOk());

        // Confirmar que rotas protegidas sem sessão voltam para /login
        mockMvc.perform(get("/projetos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/quadro"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // 20. Fazer login como outro usuário ("João", joao@empresa.com) e validar iniciais "J"
        MockHttpSession sessionJoao = new MockHttpSession();
        mockMvc.perform(post("/login")
                        .session(sessionJoao)
                        .param("email", "joao@empresa.com")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"));

        mockMvc.perform(get("/projetos").session(sessionJoao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span>J</span>")));
    }
}
