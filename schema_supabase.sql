-- ============================================================================
-- SCRIPT DE ESTRUTURA E PERSISTÊNCIA SUPABASE - NEXIOO DEMAND (CONSOLIDADO)
-- ============================================================================
-- Este script consolida exatamente as migrações versionadas de supabase/migrations/:
-- 20260910000001_profiles_security.sql
-- 20260910000002_business_schema.sql
-- 20260910000003_rls_and_grants.sql
-- 20260910000004_ordering_rpcs.sql
-- 20260910000005_storage_bucket_policies.sql
-- 20260910000006_quadros_recentes_e_isolamento.sql
-- 20260911000007_isolamento_multi_tenant_definitivo.sql
-- ============================================================================

-- 1. Extensões necessárias
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. Tabela pública de perfis de usuário sincronizada com auth.users
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email VARCHAR(255) UNIQUE NOT NULL,
    nome VARCHAR(120),
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- Triggers de timestamp
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated ON public.users;
CREATE TRIGGER trg_users_updated
BEFORE UPDATE ON public.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- Trigger seguro para sincronizar novos usuários de auth.users para public.users
CREATE OR REPLACE FUNCTION public.handle_new_user_sync()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    INSERT INTO public.users (id, email, nome, created_at, updated_at)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.raw_user_meta_data->>'name', split_part(NEW.email, '@', 1)),
        NEW.created_at,
        now()
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        nome = COALESCE(EXCLUDED.nome, public.users.nome),
        updated_at = now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_user_sync();

-- 3. Tabelas de Domínio de Negócio (Multi-tenant Nativo com Chaves Compostas)

-- 3.1. Áreas de Trabalho
CREATE TABLE IF NOT EXISTS public.areas_trabalho (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    nome VARCHAR(120) NOT NULL,
    inicial VARCHAR(4) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT uq_area_usuario_nome UNIQUE (usuario_id, nome),
    CONSTRAINT uq_area_id_usuario UNIQUE (id, usuario_id)
);

-- 3.2. Projetos (Quadros)
CREATE TABLE IF NOT EXISTS public.projetos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    area_trabalho_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao TEXT DEFAULT '',
    gradiente VARCHAR(100) DEFAULT 'linear-gradient(135deg, #a855f7, #ec4899)',
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_projeto_area FOREIGN KEY (area_trabalho_id, usuario_id)
        REFERENCES public.areas_trabalho(id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT uq_projeto_id_usuario UNIQUE (id, usuario_id)
);

-- 3.3. Colunas
CREATE TABLE IF NOT EXISTS public.colunas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    projeto_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    codigo VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    ordem INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_coluna_projeto FOREIGN KEY (projeto_id, usuario_id)
        REFERENCES public.projetos(id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT uq_coluna_projeto_codigo UNIQUE (projeto_id, codigo),
    CONSTRAINT uq_coluna_id_projeto UNIQUE (id, projeto_id)
);

-- 3.4. Demandas (Cartões)
CREATE TABLE IF NOT EXISTS public.demandas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    projeto_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    coluna_id UUID NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    descricao TEXT DEFAULT '',
    prioridade VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    responsavel_nome VARCHAR(80),
    responsavel_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    prazo DATE,
    posicao INT NOT NULL DEFAULT 0,
    concluido BOOLEAN NOT NULL DEFAULT false,
    acompanhando BOOLEAN NOT NULL DEFAULT false,
    imagem_url TEXT,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_demanda_projeto FOREIGN KEY (projeto_id, usuario_id)
        REFERENCES public.projetos(id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT fk_demanda_coluna FOREIGN KEY (coluna_id, projeto_id)
        REFERENCES public.colunas(id, projeto_id) ON DELETE CASCADE,
    CONSTRAINT uq_demanda_id_usuario UNIQUE (id, usuario_id)
);

-- 3.5. Sub-entidades de Demandas
CREATE TABLE IF NOT EXISTS public.demandas_etiquetas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    codigo VARCHAR(60) NOT NULL,
    nome VARCHAR(60) NOT NULL,
    cor_hex VARCHAR(10) NOT NULL,
    CONSTRAINT fk_etiqueta_demanda FOREIGN KEY (demanda_id, usuario_id)
        REFERENCES public.demandas(id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT uq_etiqueta_demanda_codigo UNIQUE (demanda_id, codigo)
);

CREATE TABLE IF NOT EXISTS public.demandas_membros (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    membro_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    nome VARCHAR(80) NOT NULL,
    CONSTRAINT fk_membro_demanda FOREIGN KEY (demanda_id, usuario_id)
        REFERENCES public.demandas(id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT uq_membro_demanda UNIQUE (demanda_id, nome)
);

CREATE TABLE IF NOT EXISTS public.demandas_checklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    titulo VARCHAR(120) NOT NULL,
    posicao INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_checklist_demanda FOREIGN KEY (demanda_id, usuario_id)
        REFERENCES public.demandas(id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT uq_checklist_id_usuario UNIQUE (id, usuario_id)
);

CREATE TABLE IF NOT EXISTS public.demandas_checklist_itens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    texto VARCHAR(240) NOT NULL,
    concluido BOOLEAN NOT NULL DEFAULT false,
    posicao INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_item_checklist FOREIGN KEY (checklist_id, usuario_id)
        REFERENCES public.demandas_checklists(id, usuario_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.demandas_anexos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    nome VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    capa BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_anexo_demanda FOREIGN KEY (demanda_id, usuario_id)
        REFERENCES public.demandas(id, usuario_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.demandas_comentarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    autor_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    autor_nome VARCHAR(80) NOT NULL,
    texto TEXT NOT NULL,
    criado_em TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_comentario_demanda FOREIGN KEY (demanda_id, usuario_id)
        REFERENCES public.demandas(id, usuario_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS public.demandas_atividades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    autor_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    autor_nome VARCHAR(80) NOT NULL,
    acao TEXT NOT NULL,
    data_hora TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT fk_atividade_demanda FOREIGN KEY (demanda_id, usuario_id)
        REFERENCES public.demandas(id, usuario_id) ON DELETE CASCADE
);

-- 3.6. Histórico de Quadros Visualizados Recentemente
CREATE TABLE IF NOT EXISTS public.quadros_visualizados_recentemente (
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    quadro_id UUID NOT NULL,
    visualizado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, quadro_id),
    CONSTRAINT fk_recente_usuario FOREIGN KEY (usuario_id)
        REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recente_projeto FOREIGN KEY (quadro_id, usuario_id)
        REFERENCES public.projetos(id, usuario_id) ON DELETE CASCADE
);

-- Migração segura e posterior remoção da coluna legada 'recentemente_visualizado' de projetos
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'projetos'
          AND column_name = 'recentemente_visualizado'
    ) THEN
        INSERT INTO public.quadros_visualizados_recentemente (usuario_id, quadro_id, visualizado_em)
        SELECT usuario_id, id, now()
        FROM public.projetos
        WHERE recentemente_visualizado = true AND usuario_id IS NOT NULL
        ON CONFLICT (usuario_id, quadro_id) DO NOTHING;
    END IF;
END $$;

ALTER TABLE public.projetos DROP COLUMN IF EXISTS recentemente_visualizado;

-- 4. Triggers de updated_at
DROP TRIGGER IF EXISTS trg_areas_updated ON public.areas_trabalho;
CREATE TRIGGER trg_areas_updated BEFORE UPDATE ON public.areas_trabalho FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_projetos_updated ON public.projetos;
CREATE TRIGGER trg_projetos_updated BEFORE UPDATE ON public.projetos FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_colunas_updated ON public.colunas;
CREATE TRIGGER trg_colunas_updated BEFORE UPDATE ON public.colunas FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_demandas_updated ON public.demandas;
CREATE TRIGGER trg_demandas_updated BEFORE UPDATE ON public.demandas FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- 5. Índices de performance e integridade de FKs
CREATE INDEX IF NOT EXISTS idx_areas_usuario ON public.areas_trabalho(usuario_id);

CREATE INDEX IF NOT EXISTS idx_projetos_usuario_area ON public.projetos(usuario_id, area_trabalho_id);
CREATE INDEX IF NOT EXISTS idx_projetos_area_trabalho ON public.projetos(area_trabalho_id);

CREATE INDEX IF NOT EXISTS idx_colunas_usuario ON public.colunas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_colunas_projeto_ordem ON public.colunas(projeto_id, ordem);
CREATE INDEX IF NOT EXISTS idx_colunas_projeto_usuario ON public.colunas(projeto_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_demandas_usuario ON public.demandas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_demandas_projeto ON public.demandas(projeto_id);
CREATE INDEX IF NOT EXISTS idx_demandas_coluna_posicao ON public.demandas(coluna_id, posicao);
CREATE INDEX IF NOT EXISTS idx_demandas_coluna_usuario ON public.demandas(coluna_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_etiquetas_usuario ON public.demandas_etiquetas(usuario_id);
CREATE INDEX IF NOT EXISTS idx_etiquetas_demanda_usuario ON public.demandas_etiquetas(demanda_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_membros_usuario ON public.demandas_membros(usuario_id);
CREATE INDEX IF NOT EXISTS idx_membros_demanda_usuario ON public.demandas_membros(demanda_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_checklists_usuario ON public.demandas_checklists(usuario_id);
CREATE INDEX IF NOT EXISTS idx_checklists_demanda ON public.demandas_checklists(demanda_id);
CREATE INDEX IF NOT EXISTS idx_checklists_demanda_usuario ON public.demandas_checklists(demanda_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_checklist_itens_usuario ON public.demandas_checklist_itens(usuario_id);
CREATE INDEX IF NOT EXISTS idx_checklist_itens_checklist ON public.demandas_checklist_itens(checklist_id);
CREATE INDEX IF NOT EXISTS idx_checklist_itens_checklist_usuario ON public.demandas_checklist_itens(checklist_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_anexos_usuario ON public.demandas_anexos(usuario_id);
CREATE INDEX IF NOT EXISTS idx_anexos_demanda ON public.demandas_anexos(demanda_id);
CREATE INDEX IF NOT EXISTS idx_anexos_demanda_usuario ON public.demandas_anexos(demanda_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_comentarios_usuario ON public.demandas_comentarios(usuario_id);
CREATE INDEX IF NOT EXISTS idx_comentarios_demanda ON public.demandas_comentarios(demanda_id);
CREATE INDEX IF NOT EXISTS idx_comentarios_demanda_usuario ON public.demandas_comentarios(demanda_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_atividades_usuario ON public.demandas_atividades(usuario_id);
CREATE INDEX IF NOT EXISTS idx_atividades_demanda ON public.demandas_atividades(demanda_id);
CREATE INDEX IF NOT EXISTS idx_atividades_demanda_usuario ON public.demandas_atividades(demanda_id, usuario_id);

CREATE INDEX IF NOT EXISTS idx_quadros_recentes_usuario_data ON public.quadros_visualizados_recentemente(usuario_id, visualizado_em DESC);
CREATE INDEX IF NOT EXISTS idx_quadros_recentes_quadro_usuario ON public.quadros_visualizados_recentemente(quadro_id, usuario_id);

-- 6. Privilégios (Grants) Estritos por Tabela
REVOKE ALL ON TABLE public.users FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.areas_trabalho FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.projetos FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.colunas FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_etiquetas FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_membros FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_checklists FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_checklist_itens FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_anexos FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_comentarios FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.demandas_atividades FROM PUBLIC, anon;
REVOKE ALL ON TABLE public.quadros_visualizados_recentemente FROM PUBLIC, anon;

-- public.users: apenas SELECT próprio e UPDATE de campos seguros (nome)
REVOKE ALL ON TABLE public.users FROM authenticated;
GRANT SELECT, UPDATE (nome) ON TABLE public.users TO authenticated;

-- Tabelas privadas com acesso controlado por RLS
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.areas_trabalho TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.projetos TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.colunas TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_etiquetas TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_membros TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_checklists TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_checklist_itens TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_anexos TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_comentarios TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.demandas_atividades TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.quadros_visualizados_recentemente TO authenticated;

-- 7. Habilitação de Row Level Security (RLS)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.areas_trabalho ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.projetos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.colunas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_etiquetas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_membros ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_checklists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_checklist_itens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_anexos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_comentarios ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.demandas_atividades ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quadros_visualizados_recentemente ENABLE ROW LEVEL SECURITY;

-- 8. Políticas RLS Explícitas por Operação ((select auth.uid()) = usuario_id)

-- USERS
CREATE POLICY "users_select_policy" ON public.users
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = id);

CREATE POLICY "users_update_policy" ON public.users
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = id)
    WITH CHECK ((select auth.uid()) = id);

-- AREAS DE TRABALHO
CREATE POLICY "areas_select_policy" ON public.areas_trabalho
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "areas_insert_policy" ON public.areas_trabalho
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "areas_update_policy" ON public.areas_trabalho
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "areas_delete_policy" ON public.areas_trabalho
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- PROJETOS
CREATE POLICY "projetos_select_policy" ON public.projetos
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "projetos_insert_policy" ON public.projetos
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "projetos_update_policy" ON public.projetos
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "projetos_delete_policy" ON public.projetos
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- COLUNAS
CREATE POLICY "colunas_select_policy" ON public.colunas
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "colunas_insert_policy" ON public.colunas
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "colunas_update_policy" ON public.colunas
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "colunas_delete_policy" ON public.colunas
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS
CREATE POLICY "demandas_select_policy" ON public.demandas
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "demandas_insert_policy" ON public.demandas
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "demandas_update_policy" ON public.demandas
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "demandas_delete_policy" ON public.demandas
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_ETIQUETAS
CREATE POLICY "etiquetas_select_policy" ON public.demandas_etiquetas
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "etiquetas_insert_policy" ON public.demandas_etiquetas
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "etiquetas_update_policy" ON public.demandas_etiquetas
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "etiquetas_delete_policy" ON public.demandas_etiquetas
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_MEMBROS
CREATE POLICY "membros_select_policy" ON public.demandas_membros
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "membros_insert_policy" ON public.demandas_membros
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "membros_update_policy" ON public.demandas_membros
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "membros_delete_policy" ON public.demandas_membros
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_CHECKLISTS
CREATE POLICY "checklists_select_policy" ON public.demandas_checklists
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "checklists_insert_policy" ON public.demandas_checklists
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "checklists_update_policy" ON public.demandas_checklists
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "checklists_delete_policy" ON public.demandas_checklists
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_CHECKLIST_ITENS
CREATE POLICY "checklist_itens_select_policy" ON public.demandas_checklist_itens
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "checklist_itens_insert_policy" ON public.demandas_checklist_itens
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "checklist_itens_update_policy" ON public.demandas_checklist_itens
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "checklist_itens_delete_policy" ON public.demandas_checklist_itens
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_ANEXOS
CREATE POLICY "anexos_select_policy" ON public.demandas_anexos
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "anexos_insert_policy" ON public.demandas_anexos
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "anexos_update_policy" ON public.demandas_anexos
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "anexos_delete_policy" ON public.demandas_anexos
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_COMENTARIOS
CREATE POLICY "comentarios_select_policy" ON public.demandas_comentarios
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "comentarios_insert_policy" ON public.demandas_comentarios
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "comentarios_update_policy" ON public.demandas_comentarios
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "comentarios_delete_policy" ON public.demandas_comentarios
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- DEMANDAS_ATIVIDADES
CREATE POLICY "atividades_select_policy" ON public.demandas_atividades
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "atividades_insert_policy" ON public.demandas_atividades
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "atividades_update_policy" ON public.demandas_atividades
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "atividades_delete_policy" ON public.demandas_atividades
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- QUADROS_VISUALIZADOS_RECENTEMENTE
CREATE POLICY "recentes_select_policy" ON public.quadros_visualizados_recentemente
    FOR SELECT TO authenticated
    USING ((select auth.uid()) = usuario_id);

CREATE POLICY "recentes_insert_policy" ON public.quadros_visualizados_recentemente
    FOR INSERT TO authenticated
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "recentes_update_policy" ON public.quadros_visualizados_recentemente
    FOR UPDATE TO authenticated
    USING ((select auth.uid()) = usuario_id)
    WITH CHECK ((select auth.uid()) = usuario_id);

CREATE POLICY "recentes_delete_policy" ON public.quadros_visualizados_recentemente
    FOR DELETE TO authenticated
    USING ((select auth.uid()) = usuario_id);

-- 9. RPCs Transacionais

-- 9.1. Movimentação atômica de demandas
CREATE OR REPLACE FUNCTION public.rpc_mover_demanda(
    p_demanda_id UUID,
    p_coluna_origem_id UUID,
    p_coluna_destino_id UUID,
    p_nova_posicao INT,
    p_projeto_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid UUID := (SELECT auth.uid());
    v_demanda RECORD;
    v_total_destino INT;
    v_rows INT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    IF p_demanda_id IS NULL OR p_coluna_origem_id IS NULL OR p_coluna_destino_id IS NULL
       OR p_projeto_id IS NULL OR p_nova_posicao IS NULL OR p_nova_posicao < 0 THEN
        RAISE EXCEPTION 'Parâmetros inválidos para movimentação de demanda' USING ERRCODE = '22023';
    END IF;

    PERFORM 1 FROM public.projetos
    WHERE id = p_projeto_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Projeto não encontrado ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    SELECT * INTO v_demanda FROM public.demandas
    WHERE id = p_demanda_id AND usuario_id = v_uid AND projeto_id = p_projeto_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Demanda não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    IF v_demanda.coluna_id <> p_coluna_origem_id THEN
        RAISE EXCEPTION 'Coluna de origem não coincide com a coluna atual da demanda' USING ERRCODE = '22000';
    END IF;

    PERFORM 1 FROM public.colunas
    WHERE id = p_coluna_destino_id AND projeto_id = p_projeto_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Coluna de destino não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    SELECT count(*) INTO v_total_destino
    FROM public.demandas
    WHERE coluna_id = p_coluna_destino_id AND usuario_id = v_uid;

    IF p_coluna_origem_id = p_coluna_destino_id THEN
        IF p_nova_posicao >= v_total_destino THEN
            RAISE EXCEPTION 'Posição % excede o limite da coluna (total: %)', p_nova_posicao, v_total_destino USING ERRCODE = '22023';
        END IF;
    ELSE
        IF p_nova_posicao > v_total_destino THEN
            RAISE EXCEPTION 'Posição % excede o limite da coluna destino (total: %)', p_nova_posicao, v_total_destino USING ERRCODE = '22023';
        END IF;
    END IF;

    IF p_coluna_origem_id = p_coluna_destino_id THEN
        IF v_demanda.posicao < p_nova_posicao THEN
            UPDATE public.demandas
            SET posicao = posicao - 1
            WHERE coluna_id = p_coluna_origem_id
              AND usuario_id = v_uid
              AND posicao > v_demanda.posicao
              AND posicao <= p_nova_posicao;
        ELSIF v_demanda.posicao > p_nova_posicao THEN
            UPDATE public.demandas
            SET posicao = posicao + 1
            WHERE coluna_id = p_coluna_origem_id
              AND usuario_id = v_uid
              AND posicao >= p_nova_posicao
              AND posicao < v_demanda.posicao;
        END IF;
    ELSE
        UPDATE public.demandas
        SET posicao = posicao - 1
        WHERE coluna_id = p_coluna_origem_id
          AND usuario_id = v_uid
          AND posicao > v_demanda.posicao;

        UPDATE public.demandas
        SET posicao = posicao + 1
        WHERE coluna_id = p_coluna_destino_id
          AND usuario_id = v_uid
          AND posicao >= p_nova_posicao;
    END IF;

    UPDATE public.demandas
    SET coluna_id = p_coluna_destino_id,
        posicao = p_nova_posicao,
        updated_at = now()
    WHERE id = p_demanda_id AND usuario_id = v_uid;

    GET DIAGNOSTICS v_rows = ROW_COUNT;
    IF v_rows = 0 THEN
        RAISE EXCEPTION 'Falha ao mover demanda: nenhuma linha foi alterada' USING ERRCODE = 'P0001';
    END IF;

    RETURN jsonb_build_object(
        'status', 'ok',
        'demanda_id', p_demanda_id,
        'coluna_destino', p_coluna_destino_id,
        'nova_posicao', p_nova_posicao
    );
END;
$$;

-- 9.2. Reordenação de colunas
CREATE OR REPLACE FUNCTION public.rpc_reordenar_colunas(
    p_projeto_id UUID,
    p_coluna_ids UUID[]
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid UUID := (SELECT auth.uid());
    v_total_colunas INT;
    v_distintos INT;
    v_rows INT;
    i INT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    IF p_coluna_ids IS NULL OR array_length(p_coluna_ids, 1) IS NULL OR array_length(p_coluna_ids, 1) = 0 THEN
        RAISE EXCEPTION 'Lista de colunas não pode ser nula ou vazia' USING ERRCODE = '22023';
    END IF;

    PERFORM 1 FROM public.projetos
    WHERE id = p_projeto_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Projeto não encontrado ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    -- Prevenção de deadlock: bloqueio ordenado de todas as colunas do projeto
    PERFORM 1 FROM public.colunas
    WHERE projeto_id = p_projeto_id AND usuario_id = v_uid
    ORDER BY id
    FOR UPDATE;

    SELECT count(DISTINCT c_id) INTO v_distintos FROM unnest(p_coluna_ids) AS c_id;
    IF v_distintos <> array_length(p_coluna_ids, 1) THEN
        RAISE EXCEPTION 'Lista de colunas contém identificadores duplicados' USING ERRCODE = '22023';
    END IF;

    SELECT count(*) INTO v_total_colunas
    FROM public.colunas
    WHERE projeto_id = p_projeto_id AND usuario_id = v_uid;

    IF array_length(p_coluna_ids, 1) <> v_total_colunas THEN
        RAISE EXCEPTION 'Lista de colunas incompleta ou divergente do total de colunas do projeto' USING ERRCODE = '22023';
    END IF;

    FOR i IN 1 .. array_length(p_coluna_ids, 1) LOOP
        UPDATE public.colunas
        SET ordem = i - 1, updated_at = now()
        WHERE id = p_coluna_ids[i] AND projeto_id = p_projeto_id AND usuario_id = v_uid;

        GET DIAGNOSTICS v_rows = ROW_COUNT;
        IF v_rows = 0 THEN
            RAISE EXCEPTION 'Coluna % não encontrada no projeto especificado' , p_coluna_ids[i] USING ERRCODE = 'P0002';
        END IF;
    END LOOP;

    RETURN jsonb_build_object('status', 'ok');
END;
$$;

-- 9.3. Registro atômico de visualização de quadros recentes
CREATE OR REPLACE FUNCTION public.rpc_registrar_visualizacao_quadro(
    p_quadro_id UUID
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid UUID := (SELECT auth.uid());
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    PERFORM 1 FROM public.projetos
    WHERE id = p_quadro_id AND usuario_id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Projeto não encontrado ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    INSERT INTO public.quadros_visualizados_recentemente (usuario_id, quadro_id, visualizado_em)
    VALUES (v_uid, p_quadro_id, now())
    ON CONFLICT (usuario_id, quadro_id) DO UPDATE
    SET visualizado_em = now();

    RETURN jsonb_build_object('status', 'ok', 'quadro_id', p_quadro_id);
END;
$$;

-- 9.4. Salvamento atômico transacional de demanda com subentidades completas e exclusão de removidas
CREATE OR REPLACE FUNCTION public.rpc_salvar_demanda_completa(
    p_payload JSONB
) RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_uid UUID := (SELECT auth.uid());
    v_demanda JSONB;
    v_demanda_id UUID;
    v_projeto_id UUID;
    v_coluna_id UUID;
    v_titulo VARCHAR(120);
    v_descricao TEXT;
    v_prioridade VARCHAR(20);
    v_responsavel_nome VARCHAR(80);
    v_prazo DATE;
    v_posicao INT;
    v_concluido BOOLEAN;
    v_acompanhando BOOLEAN;
    v_imagem_url TEXT;

    v_chk_ids UUID[] := ARRAY[]::UUID[];
    v_item_ids UUID[] := ARRAY[]::UUID[];
    v_anexo_ids UUID[] := ARRAY[]::UUID[];
    v_etiqueta_cods TEXT[] := ARRAY[]::TEXT[];
    v_membro_nomes TEXT[] := ARRAY[]::TEXT[];

    v_chk JSONB;
    v_item JSONB;
    v_anexo JSONB;
    v_etiq JSONB;
    v_membro JSONB;
    v_chk_id UUID;
    v_item_id UUID;
    v_anexo_id UUID;
    v_etiq_cod VARCHAR(60);
    v_membro_nome VARCHAR(80);
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    IF p_payload IS NULL OR p_payload->'demanda' IS NULL THEN
        RAISE EXCEPTION 'Payload inválido: objeto demanda é obrigatório' USING ERRCODE = '22023';
    END IF;

    v_demanda := p_payload->'demanda';

    IF v_demanda->>'id' IS NOT NULL AND (v_demanda->>'id') != '' THEN
        v_demanda_id := (v_demanda->>'id')::UUID;
    ELSE
        v_demanda_id := gen_random_uuid();
    END IF;

    v_projeto_id := (v_demanda->>'projeto_id')::UUID;
    v_coluna_id := (v_demanda->>'coluna_id')::UUID;

    IF v_projeto_id IS NULL THEN
        RAISE EXCEPTION 'projeto_id é obrigatório na demanda' USING ERRCODE = '22023';
    END IF;
    IF v_coluna_id IS NULL THEN
        RAISE EXCEPTION 'coluna_id é obrigatório na demanda' USING ERRCODE = '22023';
    END IF;

    PERFORM 1 FROM public.colunas c
    JOIN public.projetos p ON c.projeto_id = p.id AND p.usuario_id = v_uid
    WHERE c.id = v_coluna_id AND c.projeto_id = v_projeto_id AND c.usuario_id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Coluna ou projeto não encontrados ou não pertencem ao usuário' USING ERRCODE = '42501';
    END IF;

    v_titulo := COALESCE(NULLIF(trim(v_demanda->>'titulo'), ''), 'Nova Demanda');
    v_descricao := COALESCE(v_demanda->>'descricao', '');
    v_prioridade := COALESCE(v_demanda->>'prioridade', 'MEDIA');
    v_responsavel_nome := v_demanda->>'responsavel_nome';
    IF v_demanda->>'prazo' IS NOT NULL AND (v_demanda->>'prazo') != '' THEN
        v_prazo := (v_demanda->>'prazo')::DATE;
    ELSE
        v_prazo := NULL;
    END IF;
    v_posicao := COALESCE((v_demanda->>'posicao')::INT, 0);
    v_concluido := COALESCE((v_demanda->>'concluido')::BOOLEAN, false);
    v_acompanhando := COALESCE((v_demanda->>'acompanhando')::BOOLEAN, false);
    v_imagem_url := v_demanda->>'imagem_url';

    INSERT INTO public.demandas (
        id, projeto_id, usuario_id, coluna_id, titulo, descricao, prioridade,
        responsavel_nome, prazo, posicao, concluido, acompanhando, imagem_url, updated_at
    ) VALUES (
        v_demanda_id, v_projeto_id, v_uid, v_coluna_id, v_titulo, v_descricao, v_prioridade,
        v_responsavel_nome, v_prazo, v_posicao, v_concluido, v_acompanhando, v_imagem_url, now()
    )
    ON CONFLICT (id) DO UPDATE SET
        projeto_id = EXCLUDED.projeto_id,
        coluna_id = EXCLUDED.coluna_id,
        titulo = EXCLUDED.titulo,
        descricao = EXCLUDED.descricao,
        prioridade = EXCLUDED.prioridade,
        responsavel_nome = EXCLUDED.responsavel_nome,
        prazo = EXCLUDED.prazo,
        posicao = EXCLUDED.posicao,
        concluido = EXCLUDED.concluido,
        acompanhando = EXCLUDED.acompanhando,
        imagem_url = EXCLUDED.imagem_url,
        updated_at = now()
    WHERE public.demandas.usuario_id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Acesso negado à demanda: registro pertence a outro usuário' USING ERRCODE = '42501';
    END IF;

    -- Checklists e Itens
    IF p_payload ? 'checklists' AND jsonb_typeof(p_payload->'checklists') = 'array' THEN
        FOR v_chk IN SELECT * FROM jsonb_array_elements(p_payload->'checklists') LOOP
            IF v_chk->>'id' IS NOT NULL AND (v_chk->>'id') != '' THEN
                v_chk_ids := array_append(v_chk_ids, (v_chk->>'id')::UUID);
            END IF;
        END LOOP;

        DELETE FROM public.demandas_checklists
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid
          AND (cardinality(v_chk_ids) = 0 OR id != ALL(v_chk_ids));

        FOR v_chk IN SELECT * FROM jsonb_array_elements(p_payload->'checklists') LOOP
            IF v_chk->>'id' IS NOT NULL AND (v_chk->>'id') != '' THEN
                v_chk_id := (v_chk->>'id')::UUID;
                IF EXISTS (SELECT 1 FROM public.demandas_checklists WHERE id = v_chk_id AND usuario_id != v_uid) THEN
                    RAISE EXCEPTION 'Acesso negado ao checklist: registro pertence a outro usuário' USING ERRCODE = '42501';
                END IF;
            ELSE
                v_chk_id := gen_random_uuid();
            END IF;

            INSERT INTO public.demandas_checklists (id, demanda_id, usuario_id, titulo, posicao)
            VALUES (
                v_chk_id,
                v_demanda_id,
                v_uid,
                COALESCE(NULLIF(trim(v_chk->>'titulo'), ''), 'Checklist'),
                COALESCE((v_chk->>'posicao')::INT, 0)
            )
            ON CONFLICT (id) DO UPDATE SET
                titulo = EXCLUDED.titulo,
                posicao = EXCLUDED.posicao
            WHERE public.demandas_checklists.usuario_id = v_uid;

            IF v_chk ? 'itens' AND jsonb_typeof(v_chk->'itens') = 'array' THEN
                v_item_ids := ARRAY[]::UUID[];
                FOR v_item IN SELECT * FROM jsonb_array_elements(v_chk->'itens') LOOP
                    IF v_item->>'id' IS NOT NULL AND (v_item->>'id') != '' THEN
                        v_item_ids := array_append(v_item_ids, (v_item->>'id')::UUID);
                    END IF;
                END LOOP;

                DELETE FROM public.demandas_checklist_itens
                WHERE checklist_id = v_chk_id AND usuario_id = v_uid
                  AND (cardinality(v_item_ids) = 0 OR id != ALL(v_item_ids));

                FOR v_item IN SELECT * FROM jsonb_array_elements(v_chk->'itens') LOOP
                    IF v_item->>'id' IS NOT NULL AND (v_item->>'id') != '' THEN
                        v_item_id := (v_item->>'id')::UUID;
                        IF EXISTS (SELECT 1 FROM public.demandas_checklist_itens WHERE id = v_item_id AND usuario_id != v_uid) THEN
                            RAISE EXCEPTION 'Acesso negado ao item de checklist: registro pertence a outro usuário' USING ERRCODE = '42501';
                        END IF;
                    ELSE
                        v_item_id := gen_random_uuid();
                    END IF;

                    INSERT INTO public.demandas_checklist_itens (id, checklist_id, usuario_id, texto, concluido, posicao)
                    VALUES (
                        v_item_id,
                        v_chk_id,
                        v_uid,
                        COALESCE(v_item->>'texto', ''),
                        COALESCE((v_item->>'concluido')::BOOLEAN, false),
                        COALESCE((v_item->>'posicao')::INT, 0)
                    )
                    ON CONFLICT (id) DO UPDATE SET
                        texto = EXCLUDED.texto,
                        concluido = EXCLUDED.concluido,
                        posicao = EXCLUDED.posicao
                    WHERE public.demandas_checklist_itens.usuario_id = v_uid;
                END LOOP;
            ELSE
                DELETE FROM public.demandas_checklist_itens
                WHERE checklist_id = v_chk_id AND usuario_id = v_uid;
            END IF;
        END LOOP;
    ELSE
        DELETE FROM public.demandas_checklists
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid;
    END IF;

    -- Anexos
    IF p_payload ? 'anexos' AND jsonb_typeof(p_payload->'anexos') = 'array' THEN
        FOR v_anexo IN SELECT * FROM jsonb_array_elements(p_payload->'anexos') LOOP
            IF v_anexo->>'id' IS NOT NULL AND (v_anexo->>'id') != '' THEN
                v_anexo_ids := array_append(v_anexo_ids, (v_anexo->>'id')::UUID);
            END IF;
        END LOOP;

        DELETE FROM public.demandas_anexos
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid
          AND (cardinality(v_anexo_ids) = 0 OR id != ALL(v_anexo_ids));

        FOR v_anexo IN SELECT * FROM jsonb_array_elements(p_payload->'anexos') LOOP
            IF v_anexo->>'id' IS NOT NULL AND (v_anexo->>'id') != '' THEN
                v_anexo_id := (v_anexo->>'id')::UUID;
                IF EXISTS (SELECT 1 FROM public.demandas_anexos WHERE id = v_anexo_id AND usuario_id != v_uid) THEN
                    RAISE EXCEPTION 'Acesso negado ao anexo: registro pertence a outro usuário' USING ERRCODE = '42501';
                END IF;
            ELSE
                v_anexo_id := gen_random_uuid();
            END IF;

            INSERT INTO public.demandas_anexos (id, demanda_id, usuario_id, nome, url, storage_path, capa)
            VALUES (
                v_anexo_id,
                v_demanda_id,
                v_uid,
                COALESCE(v_anexo->>'nome', 'arquivo'),
                COALESCE(v_anexo->>'url', ''),
                COALESCE(v_anexo->>'storage_path', 'local'),
                COALESCE((v_anexo->>'capa')::BOOLEAN, false)
            )
            ON CONFLICT (id) DO UPDATE SET
                nome = EXCLUDED.nome,
                url = EXCLUDED.url,
                storage_path = EXCLUDED.storage_path,
                capa = EXCLUDED.capa
            WHERE public.demandas_anexos.usuario_id = v_uid;
        END LOOP;
    ELSE
        DELETE FROM public.demandas_anexos
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid;
    END IF;

    -- Etiquetas
    IF p_payload ? 'etiquetas' AND jsonb_typeof(p_payload->'etiquetas') = 'array' THEN
        FOR v_etiq IN SELECT * FROM jsonb_array_elements(p_payload->'etiquetas') LOOP
            IF v_etiq->>'codigo' IS NOT NULL AND (v_etiq->>'codigo') != '' THEN
                v_etiqueta_cods := array_append(v_etiqueta_cods, (v_etiq->>'codigo')::TEXT);
            END IF;
        END LOOP;

        DELETE FROM public.demandas_etiquetas
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid
          AND (cardinality(v_etiqueta_cods) = 0 OR codigo != ALL(v_etiqueta_cods));

        FOR v_etiq IN SELECT * FROM jsonb_array_elements(p_payload->'etiquetas') LOOP
            v_etiq_cod := trim(v_etiq->>'codigo');
            IF v_etiq_cod IS NOT NULL AND v_etiq_cod != '' THEN
                IF EXISTS (SELECT 1 FROM public.demandas_etiquetas WHERE demanda_id = v_demanda_id AND codigo = v_etiq_cod AND usuario_id != v_uid) THEN
                    RAISE EXCEPTION 'Acesso negado à etiqueta: registro pertence a outro usuário' USING ERRCODE = '42501';
                END IF;

                INSERT INTO public.demandas_etiquetas (demanda_id, usuario_id, codigo, nome, cor_hex)
                VALUES (
                    v_demanda_id,
                    v_uid,
                    v_etiq_cod,
                    COALESCE(v_etiq->>'nome', v_etiq_cod),
                    COALESCE(v_etiq->>'cor_hex', '#3b82f6')
                )
                ON CONFLICT (demanda_id, codigo) DO UPDATE SET
                    nome = EXCLUDED.nome,
                    cor_hex = EXCLUDED.cor_hex
                WHERE public.demandas_etiquetas.usuario_id = v_uid;
            END IF;
        END LOOP;
    ELSE
        DELETE FROM public.demandas_etiquetas
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid;
    END IF;

    -- Membros
    IF p_payload ? 'membros' AND jsonb_typeof(p_payload->'membros') = 'array' THEN
        FOR v_membro IN SELECT * FROM jsonb_array_elements(p_payload->'membros') LOOP
            IF v_membro->>'nome' IS NOT NULL AND (v_membro->>'nome') != '' THEN
                v_membro_nomes := array_append(v_membro_nomes, trim(v_membro->>'nome'));
            END IF;
        END LOOP;

        DELETE FROM public.demandas_membros
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid
          AND (cardinality(v_membro_nomes) = 0 OR nome != ALL(v_membro_nomes));

        FOR v_membro IN SELECT * FROM jsonb_array_elements(p_payload->'membros') LOOP
            v_membro_nome := trim(v_membro->>'nome');
            IF v_membro_nome IS NOT NULL AND v_membro_nome != '' THEN
                IF EXISTS (SELECT 1 FROM public.demandas_membros WHERE demanda_id = v_demanda_id AND nome = v_membro_nome AND usuario_id != v_uid) THEN
                    RAISE EXCEPTION 'Acesso negado ao membro: registro pertence a outro usuário' USING ERRCODE = '42501';
                END IF;

                INSERT INTO public.demandas_membros (demanda_id, usuario_id, nome)
                VALUES (v_demanda_id, v_uid, v_membro_nome)
                ON CONFLICT (demanda_id, nome) DO NOTHING;
            END IF;
        END LOOP;
    ELSE
        DELETE FROM public.demandas_membros
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid;
    END IF;

    RETURN jsonb_build_object('status', 'ok', 'id', v_demanda_id);
END;
$$;

REVOKE ALL ON FUNCTION public.rpc_mover_demanda(UUID, UUID, UUID, INT, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.rpc_reordenar_colunas(UUID, UUID[]) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.rpc_registrar_visualizacao_quadro(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.rpc_salvar_demanda_completa(JSONB) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rpc_mover_demanda(UUID, UUID, UUID, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_reordenar_colunas(UUID, UUID[]) TO authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_registrar_visualizacao_quadro(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_salvar_demanda_completa(JSONB) TO authenticated;

-- 10. Storage Bucket e Políticas Multi-tenant
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'nexioo-attachments',
    'nexioo-attachments',
    false,
    20971520,
    ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'application/pdf', 'text/plain']
)
ON CONFLICT (id) DO UPDATE
SET public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "storage_select_self" ON storage.objects;
CREATE POLICY "storage_select_self" ON storage.objects
    FOR SELECT TO authenticated
    USING (
        bucket_id = 'nexioo-attachments'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
        AND (storage.foldername(name))[2] IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM public.demandas d
            WHERE d.id::text = (storage.foldername(name))[2]
              AND d.usuario_id = (select auth.uid())
        )
    );

DROP POLICY IF EXISTS "storage_insert_self" ON storage.objects;
CREATE POLICY "storage_insert_self" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'nexioo-attachments'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
        AND (storage.foldername(name))[2] IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM public.demandas d
            WHERE d.id::text = (storage.foldername(name))[2]
              AND d.usuario_id = (select auth.uid())
        )
    );

DROP POLICY IF EXISTS "storage_update_self" ON storage.objects;
CREATE POLICY "storage_update_self" ON storage.objects
    FOR UPDATE TO authenticated
    USING (
        bucket_id = 'nexioo-attachments'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
        AND (storage.foldername(name))[2] IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM public.demandas d
            WHERE d.id::text = (storage.foldername(name))[2]
              AND d.usuario_id = (select auth.uid())
        )
    )
    WITH CHECK (
        bucket_id = 'nexioo-attachments'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
        AND (storage.foldername(name))[2] IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM public.demandas d
            WHERE d.id::text = (storage.foldername(name))[2]
              AND d.usuario_id = (select auth.uid())
        )
    );

DROP POLICY IF EXISTS "storage_delete_self" ON storage.objects;
CREATE POLICY "storage_delete_self" ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'nexioo-attachments'
        AND (storage.foldername(name))[1] = (select auth.uid())::text
        AND (storage.foldername(name))[2] IS NOT NULL
        AND EXISTS (
            SELECT 1 FROM public.demandas d
            WHERE d.id::text = (storage.foldername(name))[2]
              AND d.usuario_id = (select auth.uid())
        )
    );
