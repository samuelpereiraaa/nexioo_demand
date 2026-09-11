-- ============================================================================
-- 002: BUSINESS SCHEMA (AREAS, PROJETOS, COLUNAS, DEMANDAS E SUB-ENTIDADES)
-- ============================================================================

-- 1. Áreas de Trabalho
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

-- 2. Projetos (Quadros)
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

-- 3. Colunas
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

-- 4. Demandas (Cartões)
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

-- 5. Sub-entidades de Demandas
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

-- 6. Histórico de Quadros Visualizados Recentemente por Usuário
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

-- 7. Triggers para updated_at nas tabelas principais
DROP TRIGGER IF EXISTS trg_areas_updated ON public.areas_trabalho;
CREATE TRIGGER trg_areas_updated BEFORE UPDATE ON public.areas_trabalho FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_projetos_updated ON public.projetos;
CREATE TRIGGER trg_projetos_updated BEFORE UPDATE ON public.projetos FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_colunas_updated ON public.colunas;
CREATE TRIGGER trg_colunas_updated BEFORE UPDATE ON public.colunas FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS trg_demandas_updated ON public.demandas;
CREATE TRIGGER trg_demandas_updated BEFORE UPDATE ON public.demandas FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- 8. Índices de performance e integridade de FKs
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
