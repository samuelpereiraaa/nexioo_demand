-- ============================================================================
-- 006: HISTÓRICO DE QUADROS RECENTES E ISOLAMENTO MULTI-TENANT DEFINITIVO
-- ============================================================================

-- 1. TABELA DE HISTÓRICO DE QUADROS VISUALIZADOS RECENTEMENTE
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

CREATE INDEX IF NOT EXISTS idx_quadros_recentes_usuario_data
    ON public.quadros_visualizados_recentemente(usuario_id, visualizado_em DESC);

-- 2. MIGRAÇÃO SEGURA E POSTERIOR REMOÇÃO DA COLUNA OBSOLETA 'recentemente_visualizado' DE PROJETOS
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

-- 3. EXPURGO EXAUSTIVO DE TODAS AS POLÍTICAS LEGADAS VIA CATÁLOGO
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT schemaname, tablename, policyname 
        FROM pg_policies 
        WHERE schemaname = 'public' 
          AND tablename IN (
            'users', 'areas_trabalho', 'projetos', 'colunas', 'demandas',
            'demandas_etiquetas', 'demandas_membros', 'demandas_checklists',
            'demandas_checklist_itens', 'demandas_anexos', 'demandas_comentarios',
            'demandas_atividades', 'quadros_visualizados_recentemente'
          )
    ) LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I;', r.policyname, r.schemaname, r.tablename);
    END LOOP;
END;
$$;

-- 4. REVOGAÇÃO DE ACESSO PÚBLICO E ANÔNIMO
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

-- 5. CONCESSÃO ESTRITA INDIVIDUAL POR TABELA PARA USUÁRIOS AUTENTICADOS
REVOKE ALL ON TABLE public.users FROM authenticated;
GRANT SELECT, UPDATE (nome) ON TABLE public.users TO authenticated;

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

-- 6. HABILITAÇÃO DE ROW LEVEL SECURITY (RLS)
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

-- 7. POLÍTICAS RLS EXPLÍCITAS POR OPERAÇÃO ((select auth.uid()) = usuario_id)

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

-- HISTÓRICO DE RECENTES
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
