-- ============================================================================
-- SCRIPT DE INVENTÁRIO, DIAGNÓSTICO E AUDITORIA COMPLETA (SUPABASE POSTGRESQL)
-- PROJETO: NEXIOO DEMAND
-- ============================================================================
-- Este script realiza a inspeção não-destrutiva do estado real do banco de dados,
-- verificando tabelas, tipos de dados, PKs, FKs compostas, constraints, índices,
-- triggers, privilégios (grants), políticas de RLS, migrações aplicadas,
-- detecção de registros órfãos e mapeamento de IDs legados não-UUID.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. TABELAS EXISTENTES NO SCHEMA PUBLIC E PRIVATE
-- ----------------------------------------------------------------------------
SELECT 
    table_schema, 
    table_name, 
    table_type
FROM information_schema.tables
WHERE table_schema IN ('public', 'private')
ORDER BY table_schema, table_name;

-- ----------------------------------------------------------------------------
-- 2. COLUNAS E TIPOS DE DADOS DE TODAS AS TABELAS DO DOMÍNIO
-- ----------------------------------------------------------------------------
SELECT 
    table_schema,
    table_name, 
    column_name, 
    ordinal_position,
    data_type, 
    udt_name,
    is_nullable, 
    column_default
FROM information_schema.columns
WHERE table_schema IN ('public', 'private')
ORDER BY table_schema, table_name, ordinal_position;

-- ----------------------------------------------------------------------------
-- 3. DETECÇÃO DE IDs LEGADOS (COLUNAS DE ID QUE NÃO SÃO UUID)
-- ----------------------------------------------------------------------------
SELECT 
    table_schema,
    table_name, 
    column_name, 
    data_type, 
    udt_name
FROM information_schema.columns
WHERE table_schema IN ('public', 'private')
  AND (column_name = 'id' OR column_name LIKE '%_id')
  AND udt_name NOT IN ('uuid')
ORDER BY table_schema, table_name, column_name;

-- ----------------------------------------------------------------------------
-- 4. VERIFICAÇÃO DE COLUNA OBSOLETA 'recentemente_visualizado'
-- ----------------------------------------------------------------------------
SELECT 
    table_schema, 
    table_name, 
    column_name, 
    data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND column_name = 'recentemente_visualizado';

-- ----------------------------------------------------------------------------
-- 5. CHAVES PRIMÁRIAS (PKs)
-- ----------------------------------------------------------------------------
SELECT 
    tc.table_schema,
    tc.table_name,
    tc.constraint_name,
    string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS pk_columns
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name 
  AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'PRIMARY KEY'
  AND tc.table_schema IN ('public', 'private')
GROUP BY tc.table_schema, tc.table_name, tc.constraint_name
ORDER BY tc.table_schema, tc.table_name;

-- ----------------------------------------------------------------------------
-- 6. CHAVES ESTRANGEIRAS (FKs) E INTEGRIDADE REFERENCIAL COMPOSTA
-- ----------------------------------------------------------------------------
SELECT 
    tc.table_schema AS schema_origem,
    tc.table_name AS tabela_origem,
    tc.constraint_name AS fk_nome,
    string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS colunas_origem,
    ccu.table_schema AS schema_destino,
    ccu.table_name AS tabela_destino,
    string_agg(ccu.column_name, ', ' ORDER BY kcu.ordinal_position) AS colunas_destino,
    rc.update_rule,
    rc.delete_rule
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name 
  AND tc.table_schema = kcu.table_schema
JOIN information_schema.referential_constraints rc 
  ON tc.constraint_name = rc.constraint_name 
  AND tc.table_schema = rc.table_schema
JOIN information_schema.constraint_column_usage ccu 
  ON rc.unique_constraint_name = ccu.constraint_name 
  AND rc.unique_constraint_schema = ccu.constraint_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema IN ('public', 'private')
GROUP BY tc.table_schema, tc.table_name, tc.constraint_name, ccu.table_schema, ccu.table_name, rc.update_rule, rc.delete_rule
ORDER BY tc.table_schema, tc.table_name, tc.constraint_name;

-- ----------------------------------------------------------------------------
-- 7. CONSTRAINTS UNIQUE (CHAVES ALTERNATIVAS / CANDIDATAS)
-- ----------------------------------------------------------------------------
SELECT 
    tc.table_schema,
    tc.table_name,
    tc.constraint_name,
    string_agg(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS unique_columns
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu 
  ON tc.constraint_name = kcu.constraint_name 
  AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'UNIQUE'
  AND tc.table_schema IN ('public', 'private')
GROUP BY tc.table_schema, tc.table_name, tc.constraint_name
ORDER BY tc.table_schema, tc.table_name;

-- ----------------------------------------------------------------------------
-- 8. ÍNDICES DE PERFORMANCE E UNICIDADE
-- ----------------------------------------------------------------------------
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname IN ('public', 'private')
ORDER BY schemaname, tablename, indexname;

-- ----------------------------------------------------------------------------
-- 9. TRIGGERS E FUNÇÕES ASSOCIADAS
-- ----------------------------------------------------------------------------
SELECT 
    event_object_schema AS schema_nome,
    event_object_table AS tabela_nome,
    trigger_name,
    action_timing,
    event_manipulation AS evento,
    action_statement AS acao
FROM information_schema.triggers
WHERE event_object_schema IN ('public', 'auth')
ORDER BY event_object_schema, event_object_table, trigger_name;

-- ----------------------------------------------------------------------------
-- 10. PRIVILÉGIOS CONCEDIDOS EM TABELAS (GRANTS)
-- ----------------------------------------------------------------------------
SELECT 
    grantee,
    table_schema,
    table_name,
    string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privileges
FROM information_schema.table_privileges
WHERE table_schema IN ('public', 'private')
  AND grantee IN ('anon', 'authenticated', 'public')
GROUP BY grantee, table_schema, table_name
ORDER BY grantee, table_schema, table_name;

-- ----------------------------------------------------------------------------
-- 11. PRIVILÉGIOS CONCEDIDOS EM ROTINAS / FUNÇÕES RPC (GRANTS EM FUNÇÕES)
-- ----------------------------------------------------------------------------
SELECT 
    routine_schema,
    routine_name,
    grantee,
    privilege_type
FROM information_schema.routine_privileges
WHERE routine_schema = 'public'
  AND routine_name IN ('rpc_mover_demanda', 'rpc_reordenar_colunas', 'handle_new_user_sync', 'handle_updated_at')
  AND grantee IN ('anon', 'authenticated', 'public')
ORDER BY routine_name, grantee;

-- ----------------------------------------------------------------------------
-- 12. STATUS DO ROW LEVEL SECURITY (RLS) POR TABELA
-- ----------------------------------------------------------------------------
SELECT 
    schemaname,
    tablename,
    rowsecurity AS rls_habilitado
FROM pg_tables
WHERE schemaname IN ('public', 'private')
ORDER BY schemaname, tablename;

-- ----------------------------------------------------------------------------
-- 13. POLÍTICAS DE RLS REGISTRADAS NO CATÁLOGO
-- ----------------------------------------------------------------------------
SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd AS operacao,
    qual AS expressao_using,
    with_check AS expressao_with_check
FROM pg_policies
WHERE schemaname IN ('public', 'storage')
ORDER BY schemaname, tablename, policyname;

-- ----------------------------------------------------------------------------
-- 14. MIGRAÇÕES APLICADAS (HISTÓRICO SUPABASE CLI)
-- ----------------------------------------------------------------------------
SELECT 
    version,
    inserted_at
FROM supabase_migrations.schema_migrations
ORDER BY version ASC;

-- ----------------------------------------------------------------------------
-- 15. AUDITORIA DE REGISTROS ÓRFÃOS E VIOLAÇÕES DE TENANCY ENTRE CONTAS
-- ----------------------------------------------------------------------------

-- 15.1. Projetos com usuario_id diferente da Área de Trabalho
SELECT 
    p.id AS projeto_id,
    p.nome AS projeto_nome,
    p.usuario_id AS projeto_usuario,
    a.id AS area_id,
    a.usuario_id AS area_usuario
FROM public.projetos p
JOIN public.areas_trabalho a ON p.area_trabalho_id = a.id
WHERE p.usuario_id <> a.usuario_id;

-- 15.2. Colunas com usuario_id diferente do Projeto
SELECT 
    c.id AS coluna_id,
    c.nome AS coluna_nome,
    c.usuario_id AS coluna_usuario,
    p.id AS projeto_id,
    p.usuario_id AS projeto_usuario
FROM public.colunas c
JOIN public.projetos p ON c.projeto_id = p.id
WHERE c.usuario_id <> p.usuario_id;

-- 15.3. Demandas com usuario_id diferente do Projeto ou Coluna pertencente a outro projeto
SELECT 
    d.id AS demanda_id,
    d.titulo AS demanda_titulo,
    d.usuario_id AS demanda_usuario,
    p.id AS projeto_id,
    p.usuario_id AS projeto_usuario,
    c.id AS coluna_id,
    c.projeto_id AS coluna_projeto_id
FROM public.demandas d
JOIN public.projetos p ON d.projeto_id = p.id
JOIN public.colunas c ON d.coluna_id = c.id
WHERE d.usuario_id <> p.usuario_id 
   OR c.projeto_id <> d.projeto_id;

-- 15.4. Sub-entidades de Demanda com usuario_id divergente da Demanda
-- Etiquetas:
SELECT e.id, e.nome, e.usuario_id, d.usuario_id AS demanda_usuario
FROM public.demandas_etiquetas e
JOIN public.demandas d ON e.demanda_id = d.id
WHERE e.usuario_id <> d.usuario_id;

-- Membros:
SELECT m.id, m.nome, m.usuario_id, d.usuario_id AS demanda_usuario
FROM public.demandas_membros m
JOIN public.demandas d ON m.demanda_id = d.id
WHERE m.usuario_id <> d.usuario_id;

-- Checklists:
SELECT ch.id, ch.titulo, ch.usuario_id, d.usuario_id AS demanda_usuario
FROM public.demandas_checklists ch
JOIN public.demandas d ON ch.demanda_id = d.id
WHERE ch.usuario_id <> d.usuario_id;

-- Itens de Checklist com checklist pai de outro usuário:
SELECT ci.id, ci.texto, ci.usuario_id, ch.usuario_id AS checklist_usuario
FROM public.demandas_checklist_itens ci
JOIN public.demandas_checklists ch ON ci.checklist_id = ch.id
WHERE ci.usuario_id <> ch.usuario_id;

-- Anexos:
SELECT a.id, a.nome, a.usuario_id, d.usuario_id AS demanda_usuario
FROM public.demandas_anexos a
JOIN public.demandas d ON a.demanda_id = d.id
WHERE a.usuario_id <> d.usuario_id;

-- Comentários:
SELECT cm.id, cm.usuario_id, d.usuario_id AS demanda_usuario
FROM public.demandas_comentarios cm
JOIN public.demandas d ON cm.demanda_id = d.id
WHERE cm.usuario_id <> d.usuario_id;

-- Atividades:
SELECT at.id, at.usuario_id, d.usuario_id AS demanda_usuario
FROM public.demandas_atividades at
JOIN public.demandas d ON at.demanda_id = d.id
WHERE at.usuario_id <> d.usuario_id;

-- Quadros recentes apontando para projeto de outro usuário:
SELECT r.usuario_id AS recente_usuario, r.quadro_id, p.usuario_id AS projeto_usuario
FROM public.quadros_visualizados_recentemente r
JOIN public.projetos p ON r.quadro_id = p.id
WHERE r.usuario_id <> p.usuario_id;
