package br.com.nexioo.demand.controller;

import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.dto.SupabaseUser;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.service.AreaTrabalhoService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static br.com.nexioo.demand.util.CsrfTestUtils.withCsrf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("BrowserFlowMultiTenantTest — Teste de Isolamento entre Contas A e B no Fluxo de Navegador")
class BrowserFlowMultiTenantTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupabaseAuthService supabaseAuthService;

    @Autowired
    private br.com.nexioo.demand.repository.ProjetoRepository projetoRepository;

    private static final UUID UID_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL_A = "contaA@nexioo.com.br";

    private static final UUID UID_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String EMAIL_B = "contaB@nexioo.com.br";

    @BeforeEach
    void setUpAuth() {
        SupabaseUser userA = new SupabaseUser(UID_A.toString(), EMAIL_A, "Usuário A", "token-jwt-a");
        when(supabaseAuthService.autenticar(EMAIL_A, "123456")).thenReturn(userA);
        when(supabaseAuthService.validarToken("token-jwt-a")).thenReturn(userA);

        SupabaseUser userB = new SupabaseUser(UID_B.toString(), EMAIL_B, "Usuário B", "token-jwt-b");
        when(supabaseAuthService.autenticar(EMAIL_B, "123456")).thenReturn(userB);
        when(supabaseAuthService.validarToken("token-jwt-b")).thenReturn(userB);
    }

    @Test
    @DisplayName("Fluxo Sequencial no Mesmo Navegador: Login A -> Cria Quadro -> Logout -> Login B -> Confirma Isolamento -> Tenta URL Direta de A -> Retorna A")
    void fluxoNavegadorSequencialIsolamentoContas() throws Exception {
        MockHttpSession initialSessionA = new MockHttpSession();

        // 1. Entre como A
        MvcResult loginResultA = mockMvc.perform(post("/login")
                        .session(initialSessionA)
                        .param("email", EMAIL_A)
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"))
                .andReturn();

        MockHttpSession sessionA = (MockHttpSession) loginResultA.getRequest().getSession();
        assertNotNull(sessionA);

        // Cria o quadro na sessão de A
        mockMvc.perform(post("/projetos")
                        .with(withCsrf())
                        .session(sessionA)
                        .param("nome", "Quadro Ultrassecreto da Conta A")
                        .param("descricao", "Informações estritamente confidenciais de A"))
                .andExpect(status().is3xxRedirection());

        Projeto projetoA = projetoRepository.listarPorUsuario(UID_A).stream()
                .filter(p -> p.getNome().equals("Quadro Ultrassecreto da Conta A"))
                .findFirst()
                .orElseThrow();
        UUID projetoIdA = projetoA.getId();

        // Visualiza o quadro como A -> Sucesso 200 OK
        mockMvc.perform(get("/quadro")
                        .session(sessionA)
                        .param("projetoId", projetoIdA.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("quadro/index"))
                .andExpect(model().attributeExists("projetoAtual"));

        // 2. Saia (Logout) - Simula envio de formulário do navegador com Accept: text/html
        MvcResult logoutResult = mockMvc.perform(post("/logout")
                        .with(withCsrf())
                        .header("Accept", "text/html")
                        .session(sessionA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        assertEquals("no-cache, no-store, must-revalidate", logoutResult.getResponse().getHeader("Cache-Control"));

        // 3. Entre como B
        MockHttpSession initialSessionB = new MockHttpSession();
        MvcResult loginResultB = mockMvc.perform(post("/login")
                        .session(initialSessionB)
                        .param("email", EMAIL_B)
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"))
                .andReturn();

        MockHttpSession sessionB = (MockHttpSession) loginResultB.getRequest().getSession();
        assertNotNull(sessionB);

        // 4. Confirme que nenhum dado de A aparece na tela de projetos de B
        MvcResult projetosBResult = mockMvc.perform(get("/projetos")
                        .session(sessionB))
                .andExpect(status().isOk())
                .andExpect(view().name("projetos/index"))
                .andReturn();

        String htmlProjetosB = projetosBResult.getResponse().getContentAsString();
        assertFalse(htmlProjetosB.contains("Quadro Ultrassecreto da Conta A"),
                "VAZAMENTO DE DADOS: O projeto confidencial de A apareceu na interface de B!");

        // 5. Use o botão voltar / Tente abrir diretamente a URL do quadro de A logado como B
        mockMvc.perform(get("/quadro")
                        .session(sessionB)
                        .param("projetoId", projetoIdA.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos")); // Rejeitado e redirecionado com segurança

        // 6. Retorne para A (novo login após logout prévio) e confirme seus próprios dados intactos
        MockHttpSession initialSessionA2 = new MockHttpSession();
        MvcResult loginResultA2 = mockMvc.perform(post("/login")
                        .session(initialSessionA2)
                        .param("email", EMAIL_A)
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"))
                .andReturn();

        MockHttpSession sessionA2 = (MockHttpSession) loginResultA2.getRequest().getSession();

        mockMvc.perform(get("/quadro")
                        .session(sessionA2)
                        .param("projetoId", projetoIdA.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("projetoAtual"));
    }

    @Test
    @DisplayName("Sessões Simultâneas: Duas abas/navegadores abertos concorrentemente não vazam dados entre si")
    void sessoesSimultaneasSemContaminacaoCruzada() throws Exception {
        // Sessão A aberta
        MockHttpSession initialSessionA = new MockHttpSession();
        MvcResult loginResultA = mockMvc.perform(post("/login")
                        .session(initialSessionA)
                        .param("email", EMAIL_A)
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession sessionA = (MockHttpSession) loginResultA.getRequest().getSession();

        mockMvc.perform(post("/projetos")
                        .with(withCsrf())
                        .session(sessionA)
                        .param("nome", "Quadro Privado A")
                        .param("descricao", "Descrição A"))
                .andExpect(status().is3xxRedirection());

        Projeto projA = projetoRepository.listarPorUsuario(UID_A).stream()
                .filter(p -> p.getNome().equals("Quadro Privado A"))
                .findFirst()
                .orElseThrow();

        // Sessão B aberta simultaneamente
        MockHttpSession initialSessionB = new MockHttpSession();
        MvcResult loginResultB = mockMvc.perform(post("/login")
                        .session(initialSessionB)
                        .param("email", EMAIL_B)
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession sessionB = (MockHttpSession) loginResultB.getRequest().getSession();

        mockMvc.perform(post("/projetos")
                        .with(withCsrf())
                        .session(sessionB)
                        .param("nome", "Quadro Privado B")
                        .param("descricao", "Descrição B"))
                .andExpect(status().is3xxRedirection());

        Projeto projB = projetoRepository.listarPorUsuario(UID_B).stream()
                .filter(p -> p.getNome().equals("Quadro Privado B"))
                .findFirst()
                .orElseThrow();

        // Requisição da Sessão A
        MvcResult respA = mockMvc.perform(get("/projetos").session(sessionA))
                .andExpect(status().isOk())
                .andReturn();
        String htmlA = respA.getResponse().getContentAsString();
        assertTrue(htmlA.contains("Quadro Privado A"));
        assertFalse(htmlA.contains("Quadro Privado B"), "Sessão A enxergou projeto de B!");

        // Requisição da Sessão B concorrente
        MvcResult respB = mockMvc.perform(get("/projetos").session(sessionB))
                .andExpect(status().isOk())
                .andReturn();
        String htmlB = respB.getResponse().getContentAsString();
        assertTrue(htmlB.contains("Quadro Privado B"));
        assertFalse(htmlB.contains("Quadro Privado A"), "Sessão B enxergou projeto de A!");

        // Tentativa de B acessar o quadro de A
        mockMvc.perform(get("/quadro").session(sessionB).param("projetoId", projA.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"));

        // Tentativa de A acessar o quadro de B
        mockMvc.perform(get("/quadro").session(sessionA).param("projetoId", projB.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projetos"));
    }
}
