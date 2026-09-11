package br.com.nexioo.demand.service;

import br.com.nexioo.demand.util.SupabaseTestClientHelper;
import br.com.nexioo.demand.util.SupabaseTestClientHelper.TestUser;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupabasePostgrestMultiTenantLiveTest — Validação Completa de RLS, FKs, RPCs e Storage com Contas Reais A e B")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SupabasePostgrestMultiTenantLiveTest {

    private static SupabaseTestClientHelper helper;
    private static TestUser userA;
    private static TestUser userB;
    private static RestTemplate restTemplate;

    // Entidades reais persistidas do Usuário A
    private static UUID areaAId;
    private static UUID projAId;
    private static UUID colA1Id;
    private static UUID colA2Id;
    private static UUID demAId;

    // Entidades reais persistidas do Usuário B
    private static UUID areaBId;
    private static UUID projBId;
    private static UUID colB1Id;
    private static UUID colB2Id;
    private static UUID demBId;

    @BeforeAll
    static void provisionarAmbienteEContasReais() {
        helper = new SupabaseTestClientHelper();
        Assumptions.assumeTrue(helper.isDisponivel(),
                "SKIPPED: Supabase não está acessível no endpoint: " + helper.getUrl());

        restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        // 1. Criação dinâmica de duas contas reais isoladas no Supabase Auth
        userA = helper.criarUsuario("user_tenant_a");
        userB = helper.criarUsuario("user_tenant_b");

        assertNotNull(userA);
        assertNotNull(userB);
        assertNotEquals(userA.getId(), userB.getId());

        // 2. Criação da árvore completa de entidades reais do Usuário A usando JWT_A
        areaAId = UUID.randomUUID();
        postEntidade(userA.getAccessToken(), "areas_trabalho", Map.of(
                "id", areaAId,
                "usuario_id", userA.getId(),
                "nome", "Área de Trabalho Alpha",
                "inicial", "AA"
        ));

        projAId = UUID.randomUUID();
        postEntidade(userA.getAccessToken(), "projetos", Map.of(
                "id", projAId,
                "area_trabalho_id", areaAId,
                "usuario_id", userA.getId(),
                "nome", "Projeto Alpha de A",
                "descricao", "Projeto confidencial de A",
                "gradiente", "linear-gradient(135deg, #0f172a, #1e293b)"
        ));

        colA1Id = UUID.randomUUID();
        postEntidade(userA.getAccessToken(), "colunas", Map.of(
                "id", colA1Id,
                "projeto_id", projAId,
                "usuario_id", userA.getId(),
                "nome", "A Fazer A",
                "codigo", "A_FAZER_A",
                "ordem", 0
        ));

        colA2Id = UUID.randomUUID();
        postEntidade(userA.getAccessToken(), "colunas", Map.of(
                "id", colA2Id,
                "projeto_id", projAId,
                "usuario_id", userA.getId(),
                "nome", "Concluído A",
                "codigo", "CONCLUIDO_A",
                "ordem", 1
        ));

        demAId = UUID.randomUUID();
        postEntidade(userA.getAccessToken(), "demandas", Map.of(
                "id", demAId,
                "projeto_id", projAId,
                "coluna_id", colA1Id,
                "usuario_id", userA.getId(),
                "titulo", "Demanda Principal de A",
                "descricao", "Dados confidenciais de A",
                "prioridade", "ALTA",
                "posicao", 0
        ));

        // 3. Criação da árvore completa de entidades reais do Usuário B usando JWT_B
        areaBId = UUID.randomUUID();
        postEntidade(userB.getAccessToken(), "areas_trabalho", Map.of(
                "id", areaBId,
                "usuario_id", userB.getId(),
                "nome", "Área de Trabalho Beta",
                "inicial", "BB"
        ));

        projBId = UUID.randomUUID();
        postEntidade(userB.getAccessToken(), "projetos", Map.of(
                "id", projBId,
                "area_trabalho_id", areaBId,
                "usuario_id", userB.getId(),
                "nome", "Projeto Beta de B",
                "descricao", "Projeto confidencial de B",
                "gradiente", "linear-gradient(135deg, #1e293b, #334155)"
        ));

        colB1Id = UUID.randomUUID();
        postEntidade(userB.getAccessToken(), "colunas", Map.of(
                "id", colB1Id,
                "projeto_id", projBId,
                "usuario_id", userB.getId(),
                "nome", "Backlog B",
                "codigo", "BACKLOG_B",
                "ordem", 0
        ));

        colB2Id = UUID.randomUUID();
        postEntidade(userB.getAccessToken(), "colunas", Map.of(
                "id", colB2Id,
                "projeto_id", projBId,
                "usuario_id", userB.getId(),
                "nome", "Em Andamento B",
                "codigo", "ANDAMENTO_B",
                "ordem", 1
        ));

        demBId = UUID.randomUUID();
        postEntidade(userB.getAccessToken(), "demandas", Map.of(
                "id", demBId,
                "projeto_id", projBId,
                "coluna_id", colB1Id,
                "usuario_id", userB.getId(),
                "titulo", "Demanda Principal de B",
                "descricao", "Dados confidenciais de B",
                "prioridade", "MEDIA",
                "posicao", 0
        ));
    }

    @AfterAll
    static void limparContasEAmbiente() {
        if (helper != null) {
            if (userA != null) helper.removerUsuario(userA.getId());
            if (userB != null) helper.removerUsuario(userB.getId());
        }
    }

    private static HttpHeaders headersPara(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", helper.getAnonKey());
        headers.setBearerAuth(token);
        return headers;
    }

    private static void postEntidade(String token, String tabela, Map<String, Object> body) {
        String url = helper.getUrl() + "/rest/v1/" + tabela;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headersPara(token));
        ResponseEntity<String> res = restTemplate.postForEntity(url, entity, String.class);
        assertTrue(res.getStatusCode().is2xxSuccessful(), "Falha ao persistir entidade " + tabela + ": " + res.getStatusCode());
    }

    // ==========================================
    // 1. ISOLAMENTO DE LEITURA (RLS SELECT)
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("RLS: Usuário B não visualiza áreas, projetos, colunas ou demandas de A")
    void bNaoVisualizaRegistrosDeA() {
        // Verifica projetos de A
        ResponseEntity<List> resProj = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/projetos?id=eq." + projAId,
                HttpMethod.GET,
                new HttpEntity<>(headersPara(userB.getAccessToken())),
                List.class
        );
        assertTrue(resProj.getStatusCode().is2xxSuccessful());
        assertNotNull(resProj.getBody());
        assertTrue(resProj.getBody().isEmpty(), "VIOLAÇÃO DE RLS: Usuário B conseguiu listar projeto de A!");

        // Verifica demandas de A
        ResponseEntity<List> resDem = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/demandas?id=eq." + demAId,
                HttpMethod.GET,
                new HttpEntity<>(headersPara(userB.getAccessToken())),
                List.class
        );
        assertTrue(resDem.getStatusCode().is2xxSuccessful());
        assertNotNull(resDem.getBody());
        assertTrue(resDem.getBody().isEmpty(), "VIOLAÇÃO DE RLS: Usuário B conseguiu listar demanda de A!");

        // Confirma que A visualiza normalmente suas próprias entidades
        ResponseEntity<List> resDemA = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/demandas?id=eq." + demAId,
                HttpMethod.GET,
                new HttpEntity<>(headersPara(userA.getAccessToken())),
                List.class
        );
        assertEquals(1, resDemA.getBody().size());
    }

    // ==========================================
    // 2. ISOLAMENTO DE ATUALIZAÇÃO (RLS UPDATE)
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("RLS: Usuário B não atualiza registros do Usuário A")
    void bNaoAtualizaRegistrosDeA() {
        String urlPatch = helper.getUrl() + "/rest/v1/projetos?id=eq." + projAId;
        HttpHeaders headers = headersPara(userB.getAccessToken());
        headers.set("Prefer", "return=representation");

        ResponseEntity<List> resPatch = restTemplate.exchange(
                urlPatch,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nome", "PROJETO_HACKEADO"), headers),
                List.class
        );
        assertNotNull(resPatch.getBody());
        assertTrue(resPatch.getBody().isEmpty(), "VIOLAÇÃO DE RLS: B atualizou projeto de A!");

        // Confirma integridade do projeto de A
        ResponseEntity<List> resVerifica = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/projetos?id=eq." + projAId,
                HttpMethod.GET,
                new HttpEntity<>(headersPara(userA.getAccessToken())),
                List.class
        );
        Map<?, ?> proj = (Map<?, ?>) resVerifica.getBody().get(0);
        assertEquals("Projeto Alpha de A", proj.get("nome"));
    }

    // ==========================================
    // 3. ISOLAMENTO DE EXCLUSÃO (RLS DELETE)
    // ==========================================

    @Test
    @Order(3)
    @DisplayName("RLS: Usuário B não exclui registros do Usuário A")
    void bNaoExcluiRegistrosDeA() {
        String urlDelete = helper.getUrl() + "/rest/v1/projetos?id=eq." + projAId;
        HttpHeaders headers = headersPara(userB.getAccessToken());
        headers.set("Prefer", "return=representation");

        ResponseEntity<List> resDelete = restTemplate.exchange(
                urlDelete,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                List.class
        );
        assertNotNull(resDelete.getBody());
        assertTrue(resDelete.getBody().isEmpty(), "VIOLAÇÃO DE RLS: B conseguiu excluir projeto de A!");

        // Confirma que o projeto de A continua existindo
        ResponseEntity<List> resVerifica = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/projetos?id=eq." + projAId,
                HttpMethod.GET,
                new HttpEntity<>(headersPara(userA.getAccessToken())),
                List.class
        );
        assertEquals(1, resVerifica.getBody().size());
    }

    // ==========================================
    // 4. FK COMPOSTA: COLUNA EM PROJETO DE OUTRO USUÁRIO
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("Integridade: Usuário B não cria coluna vinculada ao projeto de A")
    void bNaoCriaColunaEmProjetoDeA() {
        String urlPost = helper.getUrl() + "/rest/v1/colunas";
        Map<String, Object> body = Map.of(
                "projeto_id", projAId, // Projeto de A
                "usuario_id", userB.getId(), // Mas usuário B
                "codigo", "COL_INVASORA",
                "nome", "Coluna Invasora",
                "ordem", 99
        );

        HttpStatusCodeException ex = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlPost, new HttpEntity<>(body, headersPara(userB.getAccessToken())), String.class));
        assertTrue(ex.getStatusCode().is4xxClientError(), "FK Composta deve bloquear criação com HTTP 4xx");
    }

    // ==========================================
    // 5. FK COMPOSTA: DEMANDA EM COLUNA/PROJETO DE OUTRO USUÁRIO
    // ==========================================

    @Test
    @Order(5)
    @DisplayName("Integridade: Usuário B não cria demanda vinculada a coluna de A")
    void bNaoCriaDemandaEmColunaDeA() {
        String urlPost = helper.getUrl() + "/rest/v1/demandas";
        Map<String, Object> body = Map.of(
                "projeto_id", projBId,
                "coluna_id", colA1Id, // Coluna pertencente a A
                "usuario_id", userB.getId(),
                "titulo", "Demanda em coluna alheia"
        );

        HttpStatusCodeException ex = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlPost, new HttpEntity<>(body, headersPara(userB.getAccessToken())), String.class));
        assertTrue(ex.getStatusCode().is4xxClientError(), "FK Composta deve bloquear criação de demanda em coluna alheia");
    }

    // ==========================================
    // 6. SUBENTIDADES: ETIQUETAS E CHECKLISTS EM DEMANDA ALHEIA
    // ==========================================

    @Test
    @Order(6)
    @DisplayName("Integridade: Usuário B não associa etiqueta nem checklist à demanda de A")
    void bNaoAssociaSubentidadeADemandaDeA() {
        // Tentativa 1: Etiqueta
        String urlEtiqueta = helper.getUrl() + "/rest/v1/demandas_etiquetas";
        Map<String, Object> bodyEtiqueta = Map.of(
                "demanda_id", demAId, // Demanda de A
                "usuario_id", userB.getId(),
                "codigo", "TAG_INTRUSA",
                "nome", "Tag Intrusa",
                "cor_hex", "#ff0000"
        );

        HttpStatusCodeException exEtiqueta = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlEtiqueta, new HttpEntity<>(bodyEtiqueta, headersPara(userB.getAccessToken())), String.class));
        assertTrue(exEtiqueta.getStatusCode().is4xxClientError());

        // Tentativa 2: Checklist
        String urlChecklist = helper.getUrl() + "/rest/v1/demandas_checklists";
        Map<String, Object> bodyChecklist = Map.of(
                "demanda_id", demAId, // Demanda de A
                "usuario_id", userB.getId(),
                "titulo", "Checklist Invasor"
        );

        HttpStatusCodeException exChecklist = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlChecklist, new HttpEntity<>(bodyChecklist, headersPara(userB.getAccessToken())), String.class));
        assertTrue(exChecklist.getStatusCode().is4xxClientError());
    }

    // ==========================================
    // 7. QUADROS RECENTES DE OUTRO USUÁRIO
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("Integridade: Usuário B não inclui quadro de A em seus recentes")
    void bNaoIncluiQuadroDeAEmSeusRecentes() {
        String urlRecentes = helper.getUrl() + "/rest/v1/quadros_visualizados_recentemente";
        Map<String, Object> body = Map.of(
                "quadro_id", projAId, // Projeto de A
                "usuario_id", userB.getId()
        );

        HttpStatusCodeException ex = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlRecentes, new HttpEntity<>(body, headersPara(userB.getAccessToken())), String.class));
        assertTrue(ex.getStatusCode().is4xxClientError(), "FK Composta de quadros_recentes deve barrar projeto alheio");
    }

    // ==========================================
    // 8. STORAGE: ANEXOS PRIVADOS NO BUCKET
    // ==========================================

    @Test
    @Order(8)
    @DisplayName("Storage: Usuário B não baixa nem sobrescreve anexo enviado por Usuário A")
    void bNaoAcessaNemSobrescreveAnexoDeA() {
        String pathArquivo = userA.getId() + "/" + demAId + "/documento_sigiloso_" + UUID.randomUUID() + ".txt";
        String urlUploadA = helper.getUrl() + "/storage/v1/object/nexioo-attachments/" + pathArquivo;

        // 1. A faz upload com seu próprio JWT
        HttpHeaders uploadHeadersA = headersPara(userA.getAccessToken());
        uploadHeadersA.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<byte[]> entityUploadA = new HttpEntity<>("Conteúdo Altamente Confidencial de A".getBytes(), uploadHeadersA);

        ResponseEntity<String> resUpload = restTemplate.postForEntity(urlUploadA, entityUploadA, String.class);
        assertTrue(resUpload.getStatusCode().is2xxSuccessful(), "Upload de anexo pelo Usuário A deve ser bem-sucedido");

        // 2. B tenta baixar com seu JWT_B
        String urlGet = helper.getUrl() + "/storage/v1/object/nexioo-attachments/" + pathArquivo;
        HttpStatusCodeException exDownload = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.exchange(urlGet, HttpMethod.GET, new HttpEntity<>(headersPara(userB.getAccessToken())), byte[].class));
        assertTrue(exDownload.getStatusCode().is4xxClientError(),
                "RLS do Storage deve bloquear download por outro usuário com HTTP 4xx");

        // 3. B tenta sobrescrever arquivo de A
        HttpHeaders uploadHeadersB = headersPara(userB.getAccessToken());
        uploadHeadersB.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<byte[]> entityUploadB = new HttpEntity<>("Conteúdo Invasor de B".getBytes(), uploadHeadersB);

        HttpStatusCodeException exOverwrite = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlUploadA, entityUploadB, String.class));
        assertTrue(exOverwrite.getStatusCode().is4xxClientError(), "RLS do Storage deve impedir gravação no caminho de outro usuário");
    }

    // ==========================================
    // 9. RLS WITH CHECK: ADULTERAÇÃO DE USUARIO_ID
    // ==========================================

    @Test
    @Order(9)
    @DisplayName("RLS WITH CHECK: Rejeição imediata se B tentar enviar usuario_id = A no payload")
    void payloadComUsuarioIdAdulteradoRejeitado() {
        String urlPost = helper.getUrl() + "/rest/v1/areas_trabalho";
        Map<String, Object> body = Map.of(
                "usuario_id", userA.getId(), // B tenta forjar a autoria de A
                "nome", "Área Falsa de A criada por B",
                "inicial", "AF"
        );

        HttpStatusCodeException ex = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlPost, new HttpEntity<>(body, headersPara(userB.getAccessToken())), String.class));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode(), "RLS WITH CHECK deve rejeitar usuario_id != auth.uid() com 403 Forbidden");
    }

    // ==========================================
    // 10. RPCs: REJEIÇÃO DE ENTIDADES ESTRANGEIRAS E DADOS INVÁLIDOS
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("RPCs: Rejeitam movimentação, reordenação e registro com entidades estrangeiras")
    void rpcsRejeitamEntidadesEstrangeiras() {
        // 1. rpc_mover_demanda: B tenta mover demanda de A
        String urlMover = helper.getUrl() + "/rest/v1/rpc/rpc_mover_demanda";
        Map<String, Object> paramsMover = Map.of(
                "p_demanda_id", demAId, // Demanda de A
                "p_coluna_origem_id", colA1Id,
                "p_coluna_destino_id", colA2Id,
                "p_nova_posicao", 0,
                "p_projeto_id", projAId
        );

        HttpStatusCodeException exMover = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlMover, new HttpEntity<>(paramsMover, headersPara(userB.getAccessToken())), Map.class));
        assertTrue(exMover.getStatusCode().is4xxClientError() || exMover.getStatusCode().is5xxServerError());

        // 2. rpc_reordenar_colunas: B tenta reordenar colunas pertencentes a A
        String urlReordenar = helper.getUrl() + "/rest/v1/rpc/rpc_reordenar_colunas";
        Map<String, Object> paramsReordenarAlheias = Map.of(
                "p_projeto_id", projAId,
                "p_coluna_ids", List.of(colA2Id, colA1Id)
        );

        HttpStatusCodeException exReordenar = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlReordenar, new HttpEntity<>(paramsReordenarAlheias, headersPara(userB.getAccessToken())), Map.class));
        assertTrue(exReordenar.getStatusCode().is4xxClientError() || exReordenar.getStatusCode().is5xxServerError());

        // 3. rpc_reordenar_colunas: B tenta passar lista duplicada de suas próprias colunas
        Map<String, Object> paramsDuplicadas = Map.of(
                "p_projeto_id", projBId,
                "p_coluna_ids", List.of(colB1Id, colB1Id) // IDs duplicados
        );

        HttpStatusCodeException exDuplicadas = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlReordenar, new HttpEntity<>(paramsDuplicadas, headersPara(userB.getAccessToken())), Map.class));
        assertTrue(exDuplicadas.getStatusCode().is4xxClientError() || exDuplicadas.getStatusCode().is5xxServerError());

        // 4. rpc_registrar_visualizacao_quadro: B tenta registrar visualização de projeto de A
        String urlVisualizar = helper.getUrl() + "/rest/v1/rpc/rpc_registrar_visualizacao_quadro";
        Map<String, Object> paramsVisualizar = Map.of("p_quadro_id", projAId);

        HttpStatusCodeException exVisualizar = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlVisualizar, new HttpEntity<>(paramsVisualizar, headersPara(userB.getAccessToken())), Map.class));
        assertTrue(exVisualizar.getStatusCode().is4xxClientError() || exVisualizar.getStatusCode().is5xxServerError());
    }

    // ==========================================
    // 11. BIDIRECIONALIDADE: USUÁRIO A NÃO ACESSA DADOS DE B
    // ==========================================

    @Test
    @Order(11)
    @DisplayName("Bidirecionalidade: Usuário A não visualiza, altera nem exclui registros de B")
    void bidirecionalidadeANaoAcessaB() {
        // A tenta ler projeto de B
        ResponseEntity<List> resProj = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/projetos?id=eq." + projBId,
                HttpMethod.GET,
                new HttpEntity<>(headersPara(userA.getAccessToken())),
                List.class
        );
        assertTrue(resProj.getBody().isEmpty(), "RLS VIOLADO: A visualizou dados de B!");

        // A tenta alterar projeto de B
        String urlPatch = helper.getUrl() + "/rest/v1/projetos?id=eq." + projBId;
        HttpHeaders headers = headersPara(userA.getAccessToken());
        headers.set("Prefer", "return=representation");
        ResponseEntity<List> resPatch = restTemplate.exchange(
                urlPatch,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nome", "ALTERADO_POR_A"), headers),
                List.class
        );
        assertTrue(resPatch.getBody().isEmpty(), "RLS VIOLADO: A alterou projeto de B!");

        // A tenta criar coluna em projeto de B
        String urlCol = helper.getUrl() + "/rest/v1/colunas";
        HttpStatusCodeException exCol = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlCol, new HttpEntity<>(Map.of(
                        "projeto_id", projBId,
                        "usuario_id", userA.getId(),
                        "codigo", "COL_A_IN_B",
                        "nome", "Coluna Invasora A",
                        "ordem", 99
                ), headersPara(userA.getAccessToken())), String.class));
        assertTrue(exCol.getStatusCode().is4xxClientError());
    }

    // ==========================================
    // 12. CONCORRÊNCIA EM RPCs
    // ==========================================

    @Test
    @Order(12)
    @DisplayName("Concorrência: Chamadas simultâneas de reordenação de colunas executam sem deadlock")
    void concorrenciaReordenacaoSemDeadlock() throws InterruptedException, ExecutionException {
        String urlReordenar = helper.getUrl() + "/rest/v1/rpc/rpc_reordenar_colunas";
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> task1 = () -> {
            Map<String, Object> params = Map.of(
                    "p_projeto_id", projAId,
                    "p_coluna_ids", List.of(colA1Id, colA2Id)
            );
            ResponseEntity<String> res = restTemplate.postForEntity(urlReordenar, new HttpEntity<>(params, headersPara(userA.getAccessToken())), String.class);
            return res.getStatusCode().value();
        };

        Callable<Integer> task2 = () -> {
            Map<String, Object> params = Map.of(
                    "p_projeto_id", projAId,
                    "p_coluna_ids", List.of(colA2Id, colA1Id)
            );
            ResponseEntity<String> res = restTemplate.postForEntity(urlReordenar, new HttpEntity<>(params, headersPara(userA.getAccessToken())), String.class);
            return res.getStatusCode().value();
        };

        Future<Integer> f1 = executor.submit(task1);
        Future<Integer> f2 = executor.submit(task2);

        int status1 = f1.get();
        int status2 = f2.get();

        executor.shutdown();
        assertTrue(status1 == 200 || status1 == 204);
        assertTrue(status2 == 200 || status2 == 204);
    }

    // ==========================================
    // 13. SALVAMENTO ATÔMICO E REMOÇÃO DE SUBENTIDADES
    // ==========================================

    @Test
    @Order(13)
    @DisplayName("RPC Atômica: Salva demanda com subentidades completas e remove itens desmarcados/excluídos")
    void salvamentoAtomicoCompletoEExclusaoDeSubentidadesRemovidas() {
        String urlRpc = helper.getUrl() + "/rest/v1/rpc/rpc_salvar_demanda_completa";
        UUID novaDemandaId = UUID.randomUUID();
        UUID chk1Id = UUID.randomUUID();
        UUID chk2Id = UUID.randomUUID();
        UUID item1Id = UUID.randomUUID();
        UUID item2Id = UUID.randomUUID();
        UUID anexo1Id = UUID.randomUUID();
        UUID anexo2Id = UUID.randomUUID();

        // 1. Salva demanda inicialmente com 2 checklists, 2 anexos, 2 etiquetas e 2 membros
        Map<String, Object> payloadInicial = Map.of(
                "demanda", Map.of(
                        "id", novaDemandaId,
                        "projeto_id", projAId,
                        "coluna_id", colA1Id,
                        "titulo", "Demanda Completa com Subentidades",
                        "posicao", 5
                ),
                "checklists", List.of(
                        Map.of("id", chk1Id, "titulo", "Checklist 1", "posicao", 0, "itens", List.of(Map.of("id", item1Id, "texto", "Item 1"))),
                        Map.of("id", chk2Id, "titulo", "Checklist 2", "posicao", 1, "itens", List.of(Map.of("id", item2Id, "texto", "Item 2")))
                ),
                "anexos", List.of(
                        Map.of("id", anexo1Id, "nome", "anexo1.txt", "url", "http://local/1.txt"),
                        Map.of("id", anexo2Id, "nome", "anexo2.txt", "url", "http://local/2.txt")
                ),
                "etiquetas", List.of(
                        Map.of("codigo", "TAG_1", "nome", "Tag 1", "cor_hex", "#ff0000"),
                        Map.of("codigo", "TAG_2", "nome", "Tag 2", "cor_hex", "#00ff00")
                ),
                "membros", List.of(
                        Map.of("nome", "Membro 1"),
                        Map.of("nome", "Membro 2")
                )
        );

        ResponseEntity<Map> res1 = restTemplate.postForEntity(urlRpc,
                new HttpEntity<>(Map.of("p_payload", payloadInicial), headersPara(userA.getAccessToken())), Map.class);
        assertTrue(res1.getStatusCode().is2xxSuccessful());

        // Confirma existência das subentidades no PostgREST
        String urlConsulta = helper.getUrl() + "/rest/v1/demandas?id=eq." + novaDemandaId
                + "&select=*,demandas_etiquetas(*),demandas_checklists(*,demandas_checklist_itens(*)),demandas_anexos(*),demandas_membros(*)";
        ResponseEntity<List> resConsulta1 = restTemplate.exchange(urlConsulta, HttpMethod.GET,
                new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
        assertEquals(1, resConsulta1.getBody().size());
        Map<?, ?> demGravada1 = (Map<?, ?>) resConsulta1.getBody().get(0);
        assertEquals(2, ((List<?>) demGravada1.get("demandas_checklists")).size());
        assertEquals(2, ((List<?>) demGravada1.get("demandas_anexos")).size());
        assertEquals(2, ((List<?>) demGravada1.get("demandas_etiquetas")).size());
        assertEquals(2, ((List<?>) demGravada1.get("demandas_membros")).size());

        // 2. Atualiza a demanda removendo 1 checklist, 1 anexo, 1 etiqueta e 1 membro
        Map<String, Object> payloadReduzido = Map.of(
                "demanda", Map.of(
                        "id", novaDemandaId,
                        "projeto_id", projAId,
                        "coluna_id", colA1Id,
                        "titulo", "Demanda com Subentidades Reduzidas",
                        "posicao", 5
                ),
                "checklists", List.of(
                        Map.of("id", chk1Id, "titulo", "Checklist 1 Mantido", "posicao", 0, "itens", List.of(Map.of("id", item1Id, "texto", "Item 1")))
                ),
                "anexos", List.of(
                        Map.of("id", anexo1Id, "nome", "anexo1.txt", "url", "http://local/1.txt")
                ),
                "etiquetas", List.of(
                        Map.of("codigo", "TAG_1", "nome", "Tag 1 Mantida", "cor_hex", "#ff0000")
                ),
                "membros", List.of(
                        Map.of("nome", "Membro 1")
                )
        );

        ResponseEntity<Map> res2 = restTemplate.postForEntity(urlRpc,
                new HttpEntity<>(Map.of("p_payload", payloadReduzido), headersPara(userA.getAccessToken())), Map.class);
        assertTrue(res2.getStatusCode().is2xxSuccessful());

        // 3. Comprova que os itens removidos foram efetivamente deletados e não reaparecem
        ResponseEntity<List> resConsulta2 = restTemplate.exchange(urlConsulta, HttpMethod.GET,
                new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
        assertEquals(1, resConsulta2.getBody().size());
        Map<?, ?> demGravada2 = (Map<?, ?>) resConsulta2.getBody().get(0);

        List<?> chksAtualizados = (List<?>) demGravada2.get("demandas_checklists");
        List<?> anexosAtualizados = (List<?>) demGravada2.get("demandas_anexos");
        List<?> etiqsAtualizadas = (List<?>) demGravada2.get("demandas_etiquetas");
        List<?> membrosAtualizados = (List<?>) demGravada2.get("demandas_membros");

        assertEquals(1, chksAtualizados.size(), "Checklist 2 deve ter sido excluído");
        assertEquals(1, anexosAtualizados.size(), "Anexo 2 deve ter sido excluído");
        assertEquals(1, etiqsAtualizadas.size(), "Etiqueta 2 deve ter sido excluída");
        assertEquals(1, membrosAtualizados.size(), "Membro 2 deve ter sido excluído");

        // Limpeza da demanda de teste
        restTemplate.exchange(helper.getUrl() + "/rest/v1/demandas?id=eq." + novaDemandaId,
                HttpMethod.DELETE, new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
    }

    // ==========================================
    // 14. ROLLBACK TOTAL ATÔMICO EM CASO DE FALHA NO SALVAMENTO
    // ==========================================

    @Test
    @Order(14)
    @DisplayName("Rollback Atômico Intermediário: Falha no salvamento do item aborta transação e desfaz demanda/checklist já inseridos")
    void falhaNoMeioDoSalvamentoCausaRollbackTotal() {
        String urlRpc = helper.getUrl() + "/rest/v1/rpc/rpc_salvar_demanda_completa";
        UUID demandaFalhaId = UUID.randomUUID();
        UUID anexoFalhaId = UUID.randomUUID();
        UUID chkFalhaId = UUID.randomUUID();
        UUID itemFalhaId = UUID.randomUUID();

        // Passa demanda, checklist e anexo VÁLIDOS, mas um item com texto excedendo o limite da coluna VARCHAR(240)
        String textoExcedente = "X".repeat(300);

        Map<String, Object> payloadComFalhaIntermediaria = Map.of(
                "demanda", Map.of(
                        "id", demandaFalhaId,
                        "projeto_id", projAId,
                        "coluna_id", colA1Id,
                        "titulo", "Demanda Valida com Item Invalido"
                ),
                "checklists", List.of(
                        Map.of(
                                "id", chkFalhaId,
                                "titulo", "Checklist Valido",
                                "itens", List.of(
                                        Map.of("id", itemFalhaId, "texto", textoExcedente)
                                )
                        )
                ),
                "anexos", List.of(
                        Map.of("id", anexoFalhaId, "nome", "anexo_valido.txt", "url", "http://local/valido.txt")
                )
        );

        HttpStatusCodeException ex = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlRpc, new HttpEntity<>(Map.of("p_payload", payloadComFalhaIntermediaria), headersPara(userA.getAccessToken())), Map.class));
        assertTrue(ex.getStatusCode().is4xxClientError() || ex.getStatusCode().is5xxServerError());

        // Comprova ausência total da demanda (desfeita pelo rollback)
        ResponseEntity<List> resDem = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/demandas?id=eq." + demandaFalhaId,
                HttpMethod.GET, new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
        assertTrue(resDem.getBody().isEmpty(), "ROLLBACK FALHOU: Demanda foi gravada parcialmente!");

        // Comprova ausência total do anexo (desfeito pelo rollback)
        ResponseEntity<List> resAnexo = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/demandas_anexos?id=eq." + anexoFalhaId,
                HttpMethod.GET, new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
        assertTrue(resAnexo.getBody().isEmpty(), "ROLLBACK FALHOU: Anexo foi gravado parcialmente!");

        // Comprova ausência total do checklist (desfeito pelo rollback)
        ResponseEntity<List> resChk = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/demandas_checklists?id=eq." + chkFalhaId,
                HttpMethod.GET, new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
        assertTrue(resChk.getBody().isEmpty(), "ROLLBACK FALHOU: Checklist foi gravado parcialmente!");
    }

    @Test
    @Order(15)
    @DisplayName("Segurança RPC: Tentativa de A utilizar ID de subentidade pertencente a B lança exceção e aborta")
    void subentidadesDeOutroUsuarioCausamExcecaoERollback() {
        String urlRpc = helper.getUrl() + "/rest/v1/rpc/rpc_salvar_demanda_completa";
        UUID chkBId = UUID.randomUUID();

        // 1. Usuário B cria um checklist em sua demanda
        Map<String, Object> payloadB = Map.of(
                "demanda", Map.of(
                        "id", demBId,
                        "projeto_id", projBId,
                        "coluna_id", colB1Id,
                        "titulo", "Demanda B com Checklist"
                ),
                "checklists", List.of(
                        Map.of("id", chkBId, "titulo", "Checklist Exclusivo de B")
                )
        );
        ResponseEntity<Map> resB = restTemplate.postForEntity(urlRpc,
                new HttpEntity<>(Map.of("p_payload", payloadB), headersPara(userB.getAccessToken())), Map.class);
        assertTrue(resB.getStatusCode().is2xxSuccessful());

        // 2. Usuário A tenta salvar sua demanda referenciando o ID do checklist de B (chkBId)
        UUID demandaTentativaAId = UUID.randomUUID();
        Map<String, Object> payloadInvasaoA = Map.of(
                "demanda", Map.of(
                        "id", demandaTentativaAId,
                        "projeto_id", projAId,
                        "coluna_id", colA1Id,
                        "titulo", "Tentativa de Invasão de Subentidade por A"
                ),
                "checklists", List.of(
                        Map.of("id", chkBId, "titulo", "Checklist Hackeado por A")
                )
        );

        HttpStatusCodeException ex = assertThrows(HttpStatusCodeException.class, () ->
                restTemplate.postForEntity(urlRpc, new HttpEntity<>(Map.of("p_payload", payloadInvasaoA), headersPara(userA.getAccessToken())), Map.class));
        assertTrue(ex.getStatusCode().is4xxClientError() || ex.getStatusCode().is5xxServerError());

        // Comprova que nada de A foi gravado
        ResponseEntity<List> resDemA = restTemplate.exchange(
                helper.getUrl() + "/rest/v1/demandas?id=eq." + demandaTentativaAId,
                HttpMethod.GET, new HttpEntity<>(headersPara(userA.getAccessToken())), List.class);
        assertTrue(resDemA.getBody().isEmpty(), "Segurança RPC falhou: Demanda invasora de A foi gravada!");
    }
}
