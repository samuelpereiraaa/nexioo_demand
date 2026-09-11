-- ============================================================================
-- 004: ORDERING RPCS (TRANSACTIONAL FUNCTIONS FOR DRAG & DROP)
-- ============================================================================

-- 1. Função atômica e segura para mover demandas entre colunas e posições
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
    -- 1. Validação de autenticação
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    -- 2. Validação de argumentos obrigatórios
    IF p_demanda_id IS NULL OR p_coluna_origem_id IS NULL OR p_coluna_destino_id IS NULL 
       OR p_projeto_id IS NULL OR p_nova_posicao IS NULL OR p_nova_posicao < 0 THEN
        RAISE EXCEPTION 'Parâmetros inválidos para movimentação de demanda' USING ERRCODE = '22023';
    END IF;

    -- 3. Validação e bloqueio pessimista do projeto
    PERFORM 1 FROM public.projetos
    WHERE id = p_projeto_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Projeto não encontrado ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    -- 4. Validação e bloqueio pessimista da demanda
    SELECT * INTO v_demanda FROM public.demandas
    WHERE id = p_demanda_id AND usuario_id = v_uid AND projeto_id = p_projeto_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Demanda não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    -- 5. Validação de que a coluna de origem coincide com a coluna atual da demanda
    IF v_demanda.coluna_id <> p_coluna_origem_id THEN
        RAISE EXCEPTION 'Coluna de origem não coincide com a coluna atual da demanda' USING ERRCODE = '22000';
    END IF;

    -- 6. Validação e bloqueio da coluna destino
    PERFORM 1 FROM public.colunas
    WHERE id = p_coluna_destino_id AND projeto_id = p_projeto_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Coluna de destino não encontrada ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    -- 7. Validação do limite de posição na coluna destino
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

    -- 8. Reorganização das posições concorrentes
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
        -- Ajuste na coluna de origem (fecha lacuna)
        UPDATE public.demandas 
        SET posicao = posicao - 1
        WHERE coluna_id = p_coluna_origem_id 
          AND usuario_id = v_uid
          AND posicao > v_demanda.posicao;

        -- Ajuste na coluna de destino (abre espaço)
        UPDATE public.demandas 
        SET posicao = posicao + 1
        WHERE coluna_id = p_coluna_destino_id 
          AND usuario_id = v_uid
          AND posicao >= p_nova_posicao;
    END IF;

    -- 9. Atualização efetiva da demanda movida
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

-- 2. Função atômica e segura para reordenar colunas
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
    -- 1. Validação de autenticação
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Não autenticado' USING ERRCODE = '42501';
    END IF;

    -- 2. Validação do array de colunas
    IF p_coluna_ids IS NULL OR array_length(p_coluna_ids, 1) IS NULL OR array_length(p_coluna_ids, 1) = 0 THEN
        RAISE EXCEPTION 'Lista de colunas não pode ser nula ou vazia' USING ERRCODE = '22023';
    END IF;

    -- 3. Bloqueio pessimista do projeto
    PERFORM 1 FROM public.projetos
    WHERE id = p_projeto_id AND usuario_id = v_uid
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Projeto não encontrado ou acesso negado' USING ERRCODE = 'P0002';
    END IF;

    -- 3.1. Prevenção de deadlock: bloqueio ordenado de todas as colunas do projeto
    PERFORM 1 FROM public.colunas
    WHERE projeto_id = p_projeto_id AND usuario_id = v_uid
    ORDER BY id
    FOR UPDATE;

    -- 4. Rejeição de identificadores duplicados na lista
    SELECT count(DISTINCT c_id) INTO v_distintos FROM unnest(p_coluna_ids) AS c_id;
    IF v_distintos <> array_length(p_coluna_ids, 1) THEN
        RAISE EXCEPTION 'Lista de colunas contém identificadores duplicados' USING ERRCODE = '22023';
    END IF;

    -- 5. Rejeição de lista incompleta ou divergente do total de colunas do projeto
    SELECT count(*) INTO v_total_colunas 
    FROM public.colunas 
    WHERE projeto_id = p_projeto_id AND usuario_id = v_uid;

    IF array_length(p_coluna_ids, 1) <> v_total_colunas THEN
        RAISE EXCEPTION 'Lista de colunas incompleta ou divergente do total de colunas do projeto' USING ERRCODE = '22023';
    END IF;

    -- 6. Atualização ordenada com validação de linhas alteradas
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

-- Concessão estrita de execução: revoga de PUBLIC e anon, concede exclusivamente a authenticated
REVOKE ALL ON FUNCTION public.rpc_mover_demanda(UUID, UUID, UUID, INT, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.rpc_reordenar_colunas(UUID, UUID[]) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.rpc_mover_demanda(UUID, UUID, UUID, INT, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.rpc_reordenar_colunas(UUID, UUID[]) TO authenticated;
