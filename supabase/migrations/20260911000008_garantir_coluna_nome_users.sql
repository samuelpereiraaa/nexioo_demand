-- ============================================================================
-- 008: GARANTIR COLUNA NOME EM PUBLIC.USERS PARA BANCOS EXISTENTES
-- ============================================================================

ALTER TABLE public.users ADD COLUMN IF NOT EXISTS nome VARCHAR(120);
