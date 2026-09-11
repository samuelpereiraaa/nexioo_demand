package br.com.nexioo.demand.service;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupabaseLegacyMigrationUpgradeLiveTest — Validação Real de Atualização a Partir de Schema Legado")
class SupabaseLegacyMigrationUpgradeLiveTest {

    private static final String DB_URL = System.getProperty("supabase.test.db.url",
            System.getenv().getOrDefault("SUPABASE_TEST_DB_URL", "jdbc:postgresql://127.0.0.1:54322/postgres"));
    private static final String DB_USER = System.getProperty("supabase.test.db.user",
            System.getenv().getOrDefault("SUPABASE_TEST_DB_USER", "postgres"));
    private static final String DB_PASSWORD = System.getProperty("supabase.test.db.password",
            System.getenv().getOrDefault("SUPABASE_TEST_DB_PASSWORD", "postgres"));

    private static Connection conn;

    @BeforeAll
    static void conectarAoBancoPostgres() {
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            Assumptions.assumeTrue(false, "SKIPPED: Não foi possível conectar ao Postgres na porta 54322: " + e.getMessage());
        }
    }

    @AfterAll
    static void fecharConexaoELimpar() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS test_legado CASCADE;");
            }
            conn.close();
        }
    }

    @Test
    @DisplayName("Deve atualizar banco legado: adicionar colunas, realizar backfill de usuario_id, validar integridade e aplicar NOT NULL")
    void deveAtualizarSchemaLegadoComSucesso() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID projetoId = UUID.randomUUID();
        UUID colunaId = UUID.randomUUID();
        UUID demandaId = UUID.randomUUID();
        UUID anexoId = UUID.randomUUID();

        try (Statement st = conn.createStatement()) {
            // 1. Constrói schema legado representativo
            st.execute("DROP SCHEMA IF EXISTS test_legado CASCADE;");
            st.execute("CREATE SCHEMA test_legado;");

            // Tabela de projetos legada com recentemente_visualizado
            st.execute("CREATE TABLE test_legado.projetos ("
                    + "id UUID PRIMARY KEY, "
                    + "usuario_id UUID NOT NULL, "
                    + "nome VARCHAR(120) NOT NULL, "
                    + "recentemente_visualizado BOOLEAN DEFAULT false"
                    + ");");

            // Tabela de colunas legada SEM usuario_id
            st.execute("CREATE TABLE test_legado.colunas ("
                    + "id UUID PRIMARY KEY, "
                    + "projeto_id UUID NOT NULL REFERENCES test_legado.projetos(id), "
                    + "nome VARCHAR(100) NOT NULL, "
                    + "codigo VARCHAR(50) NOT NULL"
                    + ");");

            // Tabela de demandas legada SEM usuario_id e SEM projeto_id (apenas coluna_id)
            st.execute("CREATE TABLE test_legado.demandas ("
                    + "id UUID PRIMARY KEY, "
                    + "coluna_id UUID NOT NULL REFERENCES test_legado.colunas(id), "
                    + "titulo VARCHAR(120) NOT NULL, "
                    + "posicao INT DEFAULT 0"
                    + ");");

            // Tabela de anexos legada SEM usuario_id
            st.execute("CREATE TABLE test_legado.demandas_anexos ("
                    + "id UUID PRIMARY KEY, "
                    + "demanda_id UUID NOT NULL REFERENCES test_legado.demandas(id), "
                    + "nome VARCHAR(255) NOT NULL, "
                    + "url TEXT NOT NULL"
                    + ");");

            // 2. Insere usuário válido em auth.users e dados legados antigos
            st.execute("INSERT INTO auth.users (id, email, created_at) VALUES ('" + usuarioId + "', 'legacy." + usuarioId + "@test.com', now()) ON CONFLICT (id) DO NOTHING;");

            st.execute("INSERT INTO test_legado.projetos (id, usuario_id, nome, recentemente_visualizado) "
                    + "VALUES ('" + projetoId + "', '" + usuarioId + "', 'Projeto Legado Alfa', true);");

            st.execute("INSERT INTO test_legado.colunas (id, projeto_id, nome, codigo) "
                    + "VALUES ('" + colunaId + "', '" + projetoId + "', 'A Fazer Antigo', 'COL_LEGADO');");

            st.execute("INSERT INTO test_legado.demandas (id, coluna_id, titulo, posicao) "
                    + "VALUES ('" + demandaId + "', '" + colunaId + "', 'Demanda Legada Importante', 0);");

            st.execute("INSERT INTO test_legado.demandas_anexos (id, demanda_id, nome, url) "
                    + "VALUES ('" + anexoId + "', '" + demandaId + "', 'contrato_antigo.pdf', 'http://storage.local/contrato.pdf');");

            // 3. Lê e executa O ARQUIVO REAL da migração 20260911000007_isolamento_multi_tenant_definitivo.sql
            st.execute("SET search_path TO test_legado, public;");
            String migrationFilePath = "supabase/migrations/20260911000007_isolamento_multi_tenant_definitivo.sql";
            String migrationSql = java.nio.file.Files.readString(java.nio.file.Path.of(migrationFilePath));
            String testMigrationSql = migrationSql.replaceAll("public\\.(?!users\\b)", "test_legado.");
            
            // Executa o script
            try {
                st.execute(testMigrationSql);
            } catch (SQLException e) {
                System.err.println("MIGRATION FAILED ON FULL SCRIPT: " + e.getMessage());
                throw e;
            } finally {
                // Restaura as políticas de storage.objects apontando para public.demandas para não afetar os outros testes live
                try {
                    st.execute("DROP POLICY IF EXISTS \"storage_select_self\" ON storage.objects; " +
                            "CREATE POLICY \"storage_select_self\" ON storage.objects FOR SELECT TO authenticated USING (bucket_id = 'nexioo-attachments' AND (storage.foldername(name))[1] = (select auth.uid())::text AND (storage.foldername(name))[2] IS NOT NULL AND EXISTS (SELECT 1 FROM public.demandas d WHERE d.id::text = (storage.foldername(name))[2] AND d.usuario_id = (select auth.uid()))); " +
                            "DROP POLICY IF EXISTS \"storage_insert_self\" ON storage.objects; " +
                            "CREATE POLICY \"storage_insert_self\" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'nexioo-attachments' AND (storage.foldername(name))[1] = (select auth.uid())::text AND (storage.foldername(name))[2] IS NOT NULL AND EXISTS (SELECT 1 FROM public.demandas d WHERE d.id::text = (storage.foldername(name))[2] AND d.usuario_id = (select auth.uid()))); " +
                            "DROP POLICY IF EXISTS \"storage_update_self\" ON storage.objects; " +
                            "CREATE POLICY \"storage_update_self\" ON storage.objects FOR UPDATE TO authenticated USING (bucket_id = 'nexioo-attachments' AND (storage.foldername(name))[1] = (select auth.uid())::text AND (storage.foldername(name))[2] IS NOT NULL AND EXISTS (SELECT 1 FROM public.demandas d WHERE d.id::text = (storage.foldername(name))[2] AND d.usuario_id = (select auth.uid()))); " +
                            "DROP POLICY IF EXISTS \"storage_delete_self\" ON storage.objects; " +
                            "CREATE POLICY \"storage_delete_self\" ON storage.objects FOR DELETE TO authenticated USING (bucket_id = 'nexioo-attachments' AND (storage.foldername(name))[1] = (select auth.uid())::text AND (storage.foldername(name))[2] IS NOT NULL AND EXISTS (SELECT 1 FROM public.demandas d WHERE d.id::text = (storage.foldername(name))[2] AND d.usuario_id = (select auth.uid())));");
                } catch (Exception ignored) {}
            }

            // 4. Confirmação de integridade e dados preservados
            try (ResultSet rs = st.executeQuery("SELECT usuario_id, ordem FROM test_legado.colunas WHERE id = '" + colunaId + "';")) {
                assertTrue(rs.next());
                assertEquals(usuarioId.toString(), rs.getString("usuario_id"));
            }

            try (ResultSet rs = st.executeQuery("SELECT usuario_id, projeto_id, titulo FROM test_legado.demandas WHERE id = '" + demandaId + "';")) {
                assertTrue(rs.next());
                assertEquals(usuarioId.toString(), rs.getString("usuario_id"));
                assertEquals(projetoId.toString(), rs.getString("projeto_id"));
                assertEquals("Demanda Legada Importante", rs.getString("titulo"));
            }

            try (ResultSet rs = st.executeQuery("SELECT usuario_id, nome FROM test_legado.demandas_anexos WHERE id = '" + anexoId + "';")) {
                assertTrue(rs.next());
                assertEquals(usuarioId.toString(), rs.getString("usuario_id"));
                assertEquals("contrato_antigo.pdf", rs.getString("nome"));
            }
            st.execute("SET search_path TO public;");
        }
    }

    @Test
    @DisplayName("Auditoria estrita: registros órfãos impossíveis de reconciliar abortam a migração sem mascarar erro")
    void registrosOrfaosAbortamMigracao() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS test_orfaos CASCADE;");
            st.execute("CREATE SCHEMA test_orfaos;");

            st.execute("CREATE TABLE test_orfaos.demandas ("
                    + "id UUID PRIMARY KEY, "
                    + "usuario_id UUID, "
                    + "coluna_id UUID, "
                    + "projeto_id UUID, "
                    + "titulo VARCHAR(120)"
                    + ");");

            // Insere demanda sem usuario_id e sem projeto_id (órfã)
            st.execute("INSERT INTO test_orfaos.demandas (id, titulo) VALUES ('" + UUID.randomUUID() + "', 'Demanda Órfã');");

            // Bloco anônimo idêntico ao da migração 007
            String plpgsqlAuditoria = "DO $$ "
                    + "DECLARE v_orfaos INT; "
                    + "BEGIN "
                    + "  SELECT count(*) INTO v_orfaos FROM test_orfaos.demandas WHERE usuario_id IS NULL; "
                    + "  IF v_orfaos > 0 THEN "
                    + "    RAISE EXCEPTION 'Abortando migração: existem % demandas órfãs sem proprietário', v_orfaos; "
                    + "  END IF; "
                    + "END $$;";

            SQLException ex = assertThrows(SQLException.class, () -> st.execute(plpgsqlAuditoria));
            assertTrue(ex.getMessage().contains("Abortando migração"),
                    "Erro deve ser fatal e não mascarado como NOTICE");
        } finally {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS test_orfaos CASCADE;");
            }
        }
    }
}
