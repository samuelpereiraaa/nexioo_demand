-- ============================================================================
-- 005: STORAGE BUCKET & MULTI-TENANT ACCESS POLICIES (NEXIOO ATTACHMENTS)
-- ============================================================================

-- 1. Criação/configuração do bucket privado nexioo-attachments
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'nexioo-attachments',
    'nexioo-attachments',
    false,
    20971520, -- Limite máximo de 20 MB por arquivo
    ARRAY['image/png', 'image/jpeg', 'image/webp', 'image/gif', 'application/pdf', 'text/plain']
)
ON CONFLICT (id) DO UPDATE
SET public = false,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- 2. Limpeza de políticas prévias em storage.objects para nexioo-attachments
DROP POLICY IF EXISTS "anexos_storage_insert" ON storage.objects;
DROP POLICY IF EXISTS "anexos_storage_select" ON storage.objects;
DROP POLICY IF EXISTS "anexos_storage_update" ON storage.objects;
DROP POLICY IF EXISTS "anexos_storage_delete" ON storage.objects;
DROP POLICY IF EXISTS "storage_select_self" ON storage.objects;
DROP POLICY IF EXISTS "storage_insert_self" ON storage.objects;
DROP POLICY IF EXISTS "storage_update_self" ON storage.objects;
DROP POLICY IF EXISTS "storage_delete_self" ON storage.objects;

-- 3. Políticas RLS estritas: caminho {(select auth.uid())}/{demandaId}/{arquivo} com validação de propriedade da demanda
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
