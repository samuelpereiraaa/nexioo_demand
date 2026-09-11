package br.com.nexioo.demand.repository;

import java.util.List;
import java.util.UUID;

public interface QuadroRecenteRepository {
    void registrarVisualizacao(UUID usuarioId, UUID quadroId);
    List<UUID> listarQuadrosRecentesIds(UUID usuarioId, int limite);
    void removerRecente(UUID usuarioId, UUID quadroId);
}
