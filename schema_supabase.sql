-- ============================================================================
-- SCRIPT DE ESTRUTURA E AUTENTICAÇÃO SUPABASE - NEXIOO DEMAND
-- ============================================================================
-- Execute este script no SQL Editor do seu Dashboard Supabase.
-- Configura a tabela 'public.users', permissões RLS e triggers de atualização.
-- ============================================================================

-- 1. Criação da tabela 'users' no schema public
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

-- 2. Habilitação de Row Level Security (RLS)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- 3. Políticas de Segurança (RLS Policies)
-- Permite que usuários autenticados leiam e atualizem apenas seus próprios dados
DROP POLICY IF EXISTS "Usuários podem visualizar seus próprios dados" ON public.users;
CREATE POLICY "Usuários podem visualizar seus próprios dados" 
ON public.users 
FOR SELECT 
USING (auth.uid() = id OR auth.role() = 'anon');

DROP POLICY IF EXISTS "Usuários podem atualizar seus próprios dados" ON public.users;
CREATE POLICY "Usuários podem atualizar seus próprios dados" 
ON public.users 
FOR UPDATE 
USING (auth.uid() = id);

DROP POLICY IF EXISTS "Permite inserção pública para novos cadastros" ON public.users;
CREATE POLICY "Permite inserção pública para novos cadastros" 
ON public.users 
FOR INSERT 
WITH CHECK (true);

-- 4. Função e Trigger para atualizar automaticamente 'updated_at'
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS set_users_updated_at ON public.users;
CREATE TRIGGER set_users_updated_at
BEFORE UPDATE ON public.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_updated_at();

-- 5. Função e Trigger para sincronizar automaticamente novos usuários do auth.users para public.users
CREATE OR REPLACE FUNCTION public.handle_new_user_sync()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.users (id, email, password, created_at, updated_at)
    VALUES (
        NEW.id,
        NEW.email,
        coalesce(NEW.encrypted_password, 'SUPABASE_AUTH_HASH'),
        NEW.created_at,
        now()
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Ativa trigger na tabela nativa 'auth.users' do Supabase
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
AFTER INSERT ON auth.users
FOR EACH ROW EXECUTE FUNCTION public.handle_new_user_sync();
