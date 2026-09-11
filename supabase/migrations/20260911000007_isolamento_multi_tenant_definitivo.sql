-- ============================================================================
-- 007: ISOLAMENTO MULTI-TENANT DEFINITIVO, PERSISTÊNCIA ATÔMICA E POLÍTICAS CORRETIVAS
-- ============================================================================

-- 1. ESTRUTURA PRELIMINAR E TABELA DE QUADROS VISUALIZADOS RECENTEMENTE
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'uq_projeto_id_usuario' AND t.relname = 'projetos' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.projetos ADD CONSTRAINT uq_projeto_id_usuario UNIQUE (id, usuario_id);
    END IF;
END $$;

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

        ALTER TABLE public.projetos DROP COLUMN IF EXISTS recentemente_visualizado;
    END IF;
END $$;

-- 2. INTEGRIDADE REFERENCIAL, BACKFILL E CONSTRAINTS OBRIGATÓRIAS
-- 2.1 Garantir existência de tabelas e colunas essenciais
CREATE TABLE IF NOT EXISTS public.areas_trabalho (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    nome VARCHAR(100) NOT NULL,
    inicial VARCHAR(4) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT uq_area_usuario_nome UNIQUE (usuario_id, nome),
    CONSTRAINT uq_area_id_usuario UNIQUE (id, usuario_id)
);

CREATE TABLE IF NOT EXISTS public.demandas_etiquetas (
    demanda_id UUID NOT NULL REFERENCES public.demandas(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    codigo VARCHAR(60) NOT NULL,
    nome VARCHAR(60) NOT NULL,
    cor_hex VARCHAR(10) DEFAULT '#3b82f6',
    PRIMARY KEY (demanda_id, codigo)
);

CREATE TABLE IF NOT EXISTS public.demandas_membros (
    demanda_id UUID NOT NULL REFERENCES public.demandas(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    nome VARCHAR(80) NOT NULL,
    PRIMARY KEY (demanda_id, nome)
);

CREATE TABLE IF NOT EXISTS public.demandas_checklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL REFERENCES public.demandas(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    titulo VARCHAR(100) NOT NULL,
    posicao INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.demandas_checklist_itens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_id UUID NOT NULL REFERENCES public.demandas_checklists(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    texto VARCHAR(240) NOT NULL,
    concluido BOOLEAN DEFAULT false,
    posicao INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS public.demandas_anexos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL REFERENCES public.demandas(id) ON DELETE CASCADE,
    usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    nome VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    storage_path TEXT DEFAULT 'local',
    capa BOOLEAN DEFAULT false
);

CREATE TABLE IF NOT EXISTS public.demandas_comentarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL REFERENCES public.demandas(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    autor_nome VARCHAR(80) NOT NULL,
    texto TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE TABLE IF NOT EXISTS public.demandas_atividades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demanda_id UUID NOT NULL REFERENCES public.demandas(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    descricao TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

ALTER TABLE public.projetos ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.projetos ADD COLUMN IF NOT EXISTS area_trabalho_id UUID;

ALTER TABLE public.colunas ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.colunas ADD COLUMN IF NOT EXISTS projeto_id UUID;
ALTER TABLE public.colunas ADD COLUMN IF NOT EXISTS ordem INT DEFAULT 0;

ALTER TABLE public.demandas ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.demandas ADD COLUMN IF NOT EXISTS projeto_id UUID;
ALTER TABLE public.demandas ADD COLUMN IF NOT EXISTS coluna_id UUID;
ALTER TABLE public.demandas ADD COLUMN IF NOT EXISTS posicao INT DEFAULT 0;

ALTER TABLE public.demandas_etiquetas ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.demandas_membros ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.demandas_checklists ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.demandas_checklist_itens ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.demandas_anexos ADD COLUMN IF NOT EXISTS usuario_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

-- 2.2 Backfill encadeado de usuario_id e relacionamentos a partir da hierarquia
UPDATE public.colunas c
SET usuario_id = p.usuario_id
FROM public.projetos p
WHERE c.projeto_id = p.id AND c.usuario_id IS NULL AND p.usuario_id IS NOT NULL;

UPDATE public.demandas d
SET projeto_id = c.projeto_id
FROM public.colunas c
WHERE d.coluna_id = c.id AND d.projeto_id IS NULL;

UPDATE public.demandas d
SET usuario_id = p.usuario_id
FROM public.projetos p
WHERE d.projeto_id = p.id AND d.usuario_id IS NULL AND p.usuario_id IS NOT NULL;

UPDATE public.demandas_etiquetas e
SET usuario_id = d.usuario_id
FROM public.demandas d
WHERE e.demanda_id = d.id AND e.usuario_id IS NULL AND d.usuario_id IS NOT NULL;

UPDATE public.demandas_membros m
SET usuario_id = d.usuario_id
FROM public.demandas d
WHERE m.demanda_id = d.id AND m.usuario_id IS NULL AND d.usuario_id IS NOT NULL;

UPDATE public.demandas_checklists chk
SET usuario_id = d.usuario_id
FROM public.demandas d
WHERE chk.demanda_id = d.id AND chk.usuario_id IS NULL AND d.usuario_id IS NOT NULL;

UPDATE public.demandas_checklist_itens item
SET usuario_id = chk.usuario_id
FROM public.demandas_checklists chk
WHERE item.checklist_id = chk.id AND item.usuario_id IS NULL AND chk.usuario_id IS NOT NULL;

UPDATE public.demandas_anexos a
SET usuario_id = d.usuario_id
FROM public.demandas d
WHERE a.demanda_id = d.id AND a.usuario_id IS NULL AND d.usuario_id IS NOT NULL;

-- 2.3 Auditoria estrita de registros órfãos: aborta imediatamente sem mascaramento
DO $$
DECLARE
    v_orfaos INT;
BEGIN
    SELECT count(*) INTO v_orfaos FROM public.colunas WHERE usuario_id IS NULL OR projeto_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % colunas órfãs sem proprietário ou sem projeto', v_orfaos;
    END IF;

    SELECT count(*) INTO v_orfaos FROM public.demandas WHERE usuario_id IS NULL OR projeto_id IS NULL OR coluna_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % demandas órfãs sem proprietário, projeto ou coluna', v_orfaos;
    END IF;

    SELECT count(*) INTO v_orfaos FROM public.demandas_etiquetas WHERE usuario_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % etiquetas órfãs sem proprietário', v_orfaos;
    END IF;

    SELECT count(*) INTO v_orfaos FROM public.demandas_membros WHERE usuario_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % membros órfãos sem proprietário', v_orfaos;
    END IF;

    SELECT count(*) INTO v_orfaos FROM public.demandas_checklists WHERE usuario_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % checklists órfãos sem proprietário', v_orfaos;
    END IF;

    SELECT count(*) INTO v_orfaos FROM public.demandas_checklist_itens WHERE usuario_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % itens de checklist órfãos sem proprietário', v_orfaos;
    END IF;

    SELECT count(*) INTO v_orfaos FROM public.demandas_anexos WHERE usuario_id IS NULL;
    IF v_orfaos > 0 THEN
        RAISE EXCEPTION 'Abortando migração: existem % anexos órfãos sem proprietário', v_orfaos;
    END IF;
END $$;

-- 2.4 Aplicação estrita de NOT NULL (sem mascaramento por WHEN OTHERS)
ALTER TABLE public.colunas ALTER COLUMN projeto_id SET NOT NULL;
ALTER TABLE public.colunas ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE public.demandas ALTER COLUMN projeto_id SET NOT NULL;
ALTER TABLE public.demandas ALTER COLUMN coluna_id SET NOT NULL;
ALTER TABLE public.demandas ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE public.demandas_etiquetas ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE public.demandas_membros ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE public.demandas_checklists ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE public.demandas_checklist_itens ALTER COLUMN usuario_id SET NOT NULL;
ALTER TABLE public.demandas_anexos ALTER COLUMN usuario_id SET NOT NULL;

-- 2.5 Constraints e Foreign Keys Compostas (expand/contract seguro)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'uq_projeto_id_usuario' AND t.relname = 'projetos' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.projetos ADD CONSTRAINT uq_projeto_id_usuario UNIQUE (id, usuario_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'fk_coluna_projeto' AND t.relname = 'colunas' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.colunas ADD CONSTRAINT fk_coluna_projeto 
            FOREIGN KEY (projeto_id, usuario_id) REFERENCES public.projetos(id, usuario_id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'uq_coluna_id_projeto' AND t.relname = 'colunas' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.colunas ADD CONSTRAINT uq_coluna_id_projeto UNIQUE (id, projeto_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'uq_demanda_id_usuario' AND t.relname = 'demandas' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.demandas ADD CONSTRAINT uq_demanda_id_usuario UNIQUE (id, usuario_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'fk_demanda_projeto' AND t.relname = 'demandas' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.demandas ADD CONSTRAINT fk_demanda_projeto 
            FOREIGN KEY (projeto_id, usuario_id) REFERENCES public.projetos(id, usuario_id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'fk_demanda_coluna' AND t.relname = 'demandas' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.demandas ADD CONSTRAINT fk_demanda_coluna 
            FOREIGN KEY (coluna_id, projeto_id) REFERENCES public.colunas(id, projeto_id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'uq_checklist_id_usuario' AND t.relname = 'demandas_checklists' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.demandas_checklists ADD CONSTRAINT uq_checklist_id_usuario UNIQUE (id, usuario_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON c.conrelid = t.oid JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE c.conname = 'fk_item_checklist' AND t.relname = 'demandas_checklist_itens' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE public.demandas_checklist_itens ADD CONSTRAINT fk_item_checklist 
            FOREIGN KEY (checklist_id, usuario_id) REFERENCES public.demandas_checklists(id, usuario_id) ON DELETE CASCADE;
    END IF;
END $$;

-- 3. COBERTURA TOTAL DE ÍNDICES PARA FOREIGN KEYS E RLS (skill: schema-foreign-key-indexes)
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

-- 4. PRIVILÉGIOS (GRANTS) ESTRITOS
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

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.users TO authenticated;
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

-- 5. POLÍTICAS RLS OTIMIZADAS (skill: rls-performance-and-best-practices)
-- Utiliza ((select auth.uid()) = usuario_id) para evitar re-avaliação por linha

-- 5.1. users
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "users_select_self" ON public.users;
CREATE POLICY "users_select_self" ON public.users FOR SELECT TO authenticated USING (((select auth.uid()) = id));
DROP POLICY IF EXISTS "users_update_self" ON public.users;
CREATE POLICY "users_update_self" ON public.users FOR UPDATE TO authenticated USING (((select auth.uid()) = id)) WITH CHECK (((select auth.uid()) = id));

-- 5.2. areas_trabalho
ALTER TABLE public.areas_trabalho ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "areas_select_owner" ON public.areas_trabalho;
CREATE POLICY "areas_select_owner" ON public.areas_trabalho FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "areas_insert_owner" ON public.areas_trabalho;
CREATE POLICY "areas_insert_owner" ON public.areas_trabalho FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "areas_update_owner" ON public.areas_trabalho;
CREATE POLICY "areas_update_owner" ON public.areas_trabalho FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "areas_delete_owner" ON public.areas_trabalho;
CREATE POLICY "areas_delete_owner" ON public.areas_trabalho FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.3. projetos
ALTER TABLE public.projetos ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "projetos_select_owner" ON public.projetos;
CREATE POLICY "projetos_select_owner" ON public.projetos FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "projetos_insert_owner" ON public.projetos;
CREATE POLICY "projetos_insert_owner" ON public.projetos FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "projetos_update_owner" ON public.projetos;
CREATE POLICY "projetos_update_owner" ON public.projetos FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "projetos_delete_owner" ON public.projetos;
CREATE POLICY "projetos_delete_owner" ON public.projetos FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.4. colunas
ALTER TABLE public.colunas ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "colunas_select_owner" ON public.colunas;
CREATE POLICY "colunas_select_owner" ON public.colunas FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "colunas_insert_owner" ON public.colunas;
CREATE POLICY "colunas_insert_owner" ON public.colunas FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "colunas_update_owner" ON public.colunas;
CREATE POLICY "colunas_update_owner" ON public.colunas FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "colunas_delete_owner" ON public.colunas;
CREATE POLICY "colunas_delete_owner" ON public.colunas FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.5. demandas
ALTER TABLE public.demandas ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "demandas_select_owner" ON public.demandas;
CREATE POLICY "demandas_select_owner" ON public.demandas FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "demandas_insert_owner" ON public.demandas;
CREATE POLICY "demandas_insert_owner" ON public.demandas FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "demandas_update_owner" ON public.demandas;
CREATE POLICY "demandas_update_owner" ON public.demandas FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "demandas_delete_owner" ON public.demandas;
CREATE POLICY "demandas_delete_owner" ON public.demandas FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.6. demandas_etiquetas
ALTER TABLE public.demandas_etiquetas ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "etiquetas_select_owner" ON public.demandas_etiquetas;
CREATE POLICY "etiquetas_select_owner" ON public.demandas_etiquetas FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "etiquetas_insert_owner" ON public.demandas_etiquetas;
CREATE POLICY "etiquetas_insert_owner" ON public.demandas_etiquetas FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "etiquetas_update_owner" ON public.demandas_etiquetas;
CREATE POLICY "etiquetas_update_owner" ON public.demandas_etiquetas FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "etiquetas_delete_owner" ON public.demandas_etiquetas;
CREATE POLICY "etiquetas_delete_owner" ON public.demandas_etiquetas FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.7. demandas_membros
ALTER TABLE public.demandas_membros ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "membros_select_owner" ON public.demandas_membros;
CREATE POLICY "membros_select_owner" ON public.demandas_membros FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "membros_insert_owner" ON public.demandas_membros;
CREATE POLICY "membros_insert_owner" ON public.demandas_membros FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "membros_update_owner" ON public.demandas_membros;
CREATE POLICY "membros_update_owner" ON public.demandas_membros FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "membros_delete_owner" ON public.demandas_membros;
CREATE POLICY "membros_delete_owner" ON public.demandas_membros FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.8. demandas_checklists
ALTER TABLE public.demandas_checklists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "checklists_select_owner" ON public.demandas_checklists;
CREATE POLICY "checklists_select_owner" ON public.demandas_checklists FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "checklists_insert_owner" ON public.demandas_checklists;
CREATE POLICY "checklists_insert_owner" ON public.demandas_checklists FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "checklists_update_owner" ON public.demandas_checklists;
CREATE POLICY "checklists_update_owner" ON public.demandas_checklists FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "checklists_delete_owner" ON public.demandas_checklists;
CREATE POLICY "checklists_delete_owner" ON public.demandas_checklists FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.9. demandas_checklist_itens
ALTER TABLE public.demandas_checklist_itens ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "itens_select_owner" ON public.demandas_checklist_itens;
CREATE POLICY "itens_select_owner" ON public.demandas_checklist_itens FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "itens_insert_owner" ON public.demandas_checklist_itens;
CREATE POLICY "itens_insert_owner" ON public.demandas_checklist_itens FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "itens_update_owner" ON public.demandas_checklist_itens;
CREATE POLICY "itens_update_owner" ON public.demandas_checklist_itens FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "itens_delete_owner" ON public.demandas_checklist_itens;
CREATE POLICY "itens_delete_owner" ON public.demandas_checklist_itens FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.10. demandas_anexos
ALTER TABLE public.demandas_anexos ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "anexos_select_owner" ON public.demandas_anexos;
CREATE POLICY "anexos_select_owner" ON public.demandas_anexos FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "anexos_insert_owner" ON public.demandas_anexos;
CREATE POLICY "anexos_insert_owner" ON public.demandas_anexos FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "anexos_update_owner" ON public.demandas_anexos;
CREATE POLICY "anexos_update_owner" ON public.demandas_anexos FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "anexos_delete_owner" ON public.demandas_anexos;
CREATE POLICY "anexos_delete_owner" ON public.demandas_anexos FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.11. demandas_comentarios
ALTER TABLE public.demandas_comentarios ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "comentarios_select_owner" ON public.demandas_comentarios;
CREATE POLICY "comentarios_select_owner" ON public.demandas_comentarios FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "comentarios_insert_owner" ON public.demandas_comentarios;
CREATE POLICY "comentarios_insert_owner" ON public.demandas_comentarios FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "comentarios_update_owner" ON public.demandas_comentarios;
CREATE POLICY "comentarios_update_owner" ON public.demandas_comentarios FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "comentarios_delete_owner" ON public.demandas_comentarios;
CREATE POLICY "comentarios_delete_owner" ON public.demandas_comentarios FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.12. demandas_atividades
ALTER TABLE public.demandas_atividades ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "atividades_select_owner" ON public.demandas_atividades;
CREATE POLICY "atividades_select_owner" ON public.demandas_atividades FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "atividades_insert_owner" ON public.demandas_atividades;
CREATE POLICY "atividades_insert_owner" ON public.demandas_atividades FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "atividades_delete_owner" ON public.demandas_atividades;
CREATE POLICY "atividades_delete_owner" ON public.demandas_atividades FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 5.13. quadros_visualizados_recentemente
ALTER TABLE public.quadros_visualizados_recentemente ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "recentes_select_owner" ON public.quadros_visualizados_recentemente;
CREATE POLICY "recentes_select_owner" ON public.quadros_visualizados_recentemente FOR SELECT TO authenticated USING (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "recentes_insert_owner" ON public.quadros_visualizados_recentemente;
CREATE POLICY "recentes_insert_owner" ON public.quadros_visualizados_recentemente FOR INSERT TO authenticated WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "recentes_update_owner" ON public.quadros_visualizados_recentemente;
CREATE POLICY "recentes_update_owner" ON public.quadros_visualizados_recentemente FOR UPDATE TO authenticated USING (((select auth.uid()) = usuario_id)) WITH CHECK (((select auth.uid()) = usuario_id));
DROP POLICY IF EXISTS "recentes_delete_owner" ON public.quadros_visualizados_recentemente;
CREATE POLICY "recentes_delete_owner" ON public.quadros_visualizados_recentemente FOR DELETE TO authenticated USING (((select auth.uid()) = usuario_id));

-- 6. RPCs TRANSACIONAIS COM PREVENÇÃO RIGOROSA DE DEADLOCK E AUDITORIA DE TENANT
-- 6.1. Movimentação de Demanda
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
    v_pos_atual INT;
    v_col_atual UUID;
    v_proj_origem UUID;
    v_proj_destino UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    IF p_demanda_id IS NULL OR p_coluna_origem_id IS NULL OR p_coluna_destino_id IS NULL OR p_nova_posicao IS NULL THEN
        RAISE EXCEPTION 'Parâmetros obrigatórios não informados' USING ERRCODE = '22023';
    END IF;

    IF p_nova_posicao < 0 THEN
        RAISE EXCEPTION 'Posição não pode ser negativa' USING ERRCODE = '22023';
    END IF;

    -- Validação de tenant das colunas
    SELECT projeto_id INTO v_proj_origem
    FROM public.colunas
    WHERE id = p_coluna_origem_id AND usuario_id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Coluna de origem não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    SELECT projeto_id INTO v_proj_destino
    FROM public.colunas
    WHERE id = p_coluna_destino_id AND usuario_id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Coluna de destino não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    IF p_projeto_id IS NOT NULL AND (v_proj_origem != p_projeto_id OR v_proj_destino != p_projeto_id) THEN
        RAISE EXCEPTION 'Colunas pertencem a projeto distinto do informado' USING ERRCODE = '22023';
    END IF;

    IF v_proj_origem != v_proj_destino THEN
        RAISE EXCEPTION 'Não é permitido mover demanda entre projetos distintos' USING ERRCODE = '22023';
    END IF;

    -- Bloqueio exclusivo da demanda
    SELECT coluna_id, posicao INTO v_col_atual, v_pos_atual
    FROM public.demandas
    WHERE id = p_demanda_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Demanda não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    IF v_col_atual != p_coluna_origem_id THEN
        RAISE EXCEPTION 'Coluna de origem divergente da coluna atual da demanda' USING ERRCODE = '22023';
    END IF;

    -- 1. Mesma coluna
    IF p_coluna_origem_id = p_coluna_destino_id THEN
        IF v_pos_atual < p_nova_posicao THEN
            UPDATE public.demandas
            SET posicao = posicao - 1
            WHERE coluna_id = p_coluna_origem_id
              AND usuario_id = v_uid
              AND posicao > v_pos_atual
              AND posicao <= p_nova_posicao
              AND id != p_demanda_id;
        ELSIF v_pos_atual > p_nova_posicao THEN
            UPDATE public.demandas
            SET posicao = posicao + 1
            WHERE coluna_id = p_coluna_origem_id
              AND usuario_id = v_uid
              AND posicao >= p_nova_posicao
              AND posicao < v_pos_atual
              AND id != p_demanda_id;
        END IF;

        UPDATE public.demandas
        SET posicao = p_nova_posicao,
            updated_at = now()
        WHERE id = p_demanda_id AND usuario_id = v_uid;
    ELSE
        -- 2. Colunas diferentes
        UPDATE public.demandas
        SET posicao = posicao - 1
        WHERE coluna_id = p_coluna_origem_id
          AND usuario_id = v_uid
          AND posicao > v_pos_atual
          AND id != p_demanda_id;

        UPDATE public.demandas
        SET posicao = posicao + 1
        WHERE coluna_id = p_coluna_destino_id
          AND usuario_id = v_uid
          AND posicao >= p_nova_posicao;

        UPDATE public.demandas
        SET coluna_id = p_coluna_destino_id,
            posicao = p_nova_posicao,
            updated_at = now()
        WHERE id = p_demanda_id AND usuario_id = v_uid;
    END IF;

    RETURN jsonb_build_object(
        'status', 'ok',
        'demanda_id', p_demanda_id,
        'coluna_id', p_coluna_destino_id,
        'posicao', p_nova_posicao
    );
END;
$$;

-- 6.2. Reordenação de colunas (com ordenação for update anti-deadlock)
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

    -- Prevenção de deadlock: bloqueio ordenado por ID crescente
    PERFORM 1 FROM public.colunas
    WHERE projeto_id = p_projeto_id AND usuario_id = v_uid
    ORDER BY id
    FOR UPDATE;

    SELECT count(*), count(DISTINCT elem)
    INTO v_total_colunas, v_distintos
    FROM unnest(p_coluna_ids) AS elem;

    IF v_total_colunas != v_distintos THEN
        RAISE EXCEPTION 'A lista contém IDs de coluna duplicados' USING ERRCODE = '22023';
    END IF;

    SELECT count(*) INTO v_rows
    FROM public.colunas
    WHERE id = ANY(p_coluna_ids)
      AND projeto_id = p_projeto_id
      AND usuario_id = v_uid;

    IF v_rows != v_total_colunas THEN
        RAISE EXCEPTION 'Uma ou mais colunas não pertencem ao projeto ou ao usuário' USING ERRCODE = 'P0002';
    END IF;

    FOR i IN 1 .. array_length(p_coluna_ids, 1) LOOP
        UPDATE public.colunas
        SET ordem = i - 1,
            updated_at = now()
        WHERE id = p_coluna_ids[i]
          AND projeto_id = p_projeto_id
          AND usuario_id = v_uid;
    END LOOP;

    RETURN jsonb_build_object('status', 'ok', 'projeto_id', p_projeto_id, 'colunas_reordenadas', v_total_colunas);
END;
$$;

-- 6.3. Registro atômico de visualização de quadros recentes
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

-- 6.4. Salvamento atômico transacional de demanda com subentidades completas e exclusão de removidas
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

    -- Arrays para rastrear IDs mantidos (para exclusão dos removidos)
    v_chk_ids UUID[] := ARRAY[]::UUID[];
    v_item_ids UUID[] := ARRAY[]::UUID[];
    v_anexo_ids UUID[] := ARRAY[]::UUID[];
    v_etiqueta_cods TEXT[] := ARRAY[]::TEXT[];
    v_membro_nomes TEXT[] := ARRAY[]::TEXT[];

    -- Iteradores JSON
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
    
    -- Extrai e valida IDs da demanda
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

    -- Validação estrita de isolamento: coluna e projeto pertencem ao tenant auth.uid()
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

    -- 1. Salva ou atualiza a Demanda principal
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

    -- 2. Sincronização de Checklists e Itens
    IF p_payload ? 'checklists' AND jsonb_typeof(p_payload->'checklists') = 'array' THEN
        FOR v_chk IN SELECT * FROM jsonb_array_elements(p_payload->'checklists') LOOP
            IF v_chk->>'id' IS NOT NULL AND (v_chk->>'id') != '' THEN
                v_chk_ids := array_append(v_chk_ids, (v_chk->>'id')::UUID);
            END IF;
        END LOOP;

        -- Exclui checklists removidos pelo usuário
        DELETE FROM public.demandas_checklists
        WHERE demanda_id = v_demanda_id AND usuario_id = v_uid
          AND (cardinality(v_chk_ids) = 0 OR id != ALL(v_chk_ids));

        -- Insere/atualiza cada checklist
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

            -- Sincroniza itens deste checklist
            IF v_chk ? 'itens' AND jsonb_typeof(v_chk->'itens') = 'array' THEN
                v_item_ids := ARRAY[]::UUID[];
                FOR v_item IN SELECT * FROM jsonb_array_elements(v_chk->'itens') LOOP
                    IF v_item->>'id' IS NOT NULL AND (v_item->>'id') != '' THEN
                        v_item_ids := array_append(v_item_ids, (v_item->>'id')::UUID);
                    END IF;
                END LOOP;

                -- Exclui itens removidos deste checklist
                DELETE FROM public.demandas_checklist_itens
                WHERE checklist_id = v_chk_id AND usuario_id = v_uid
                  AND (cardinality(v_item_ids) = 0 OR id != ALL(v_item_ids));

                -- Insere/atualiza itens
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

    -- 3. Sincronização de Anexos
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

    -- 4. Sincronização de Etiquetas
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

    -- 5. Sincronização de Membros
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

GRANT EXECUTE ON FUNCTION public.rpc_mover_demanda(UUID, UUID, UUID, INT, UUID) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.rpc_mover_demanda(UUID, UUID, UUID, INT, UUID) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.rpc_reordenar_colunas(UUID, UUID[]) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.rpc_reordenar_colunas(UUID, UUID[]) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.rpc_registrar_visualizacao_quadro(UUID) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.rpc_registrar_visualizacao_quadro(UUID) FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION public.rpc_salvar_demanda_completa(JSONB) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.rpc_salvar_demanda_completa(JSONB) FROM PUBLIC, anon;

-- 7. CONFIGURAÇÃO SEGURA DO BUCKET PRIVADO DE STORAGE
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'nexioo-attachments',
    'nexioo-attachments',
    false,
    20971520, -- 20 MB
    ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'application/pdf', 'text/plain']::text[]
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 20971520,
    allowed_mime_types = ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'application/pdf', 'text/plain']::text[];

-- 8. POLÍTICAS RLS DE STORAGE PRIVADO POR TENANT E DEMANDA
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
