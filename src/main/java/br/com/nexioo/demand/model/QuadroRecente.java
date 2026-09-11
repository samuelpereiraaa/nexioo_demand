package br.com.nexioo.demand.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidade de domínio representando a visualização recente de um quadro por um usuário.
 */
public class QuadroRecente {
    private UUID usuarioId;
    private UUID quadroId;
    private OffsetDateTime visualizadoEm;

    public QuadroRecente() {}

    public QuadroRecente(UUID usuarioId, UUID quadroId, OffsetDateTime visualizadoEm) {
        this.usuarioId = usuarioId;
        this.quadroId = quadroId;
        this.visualizadoEm = visualizadoEm;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public UUID getQuadroId() {
        return quadroId;
    }

    public void setQuadroId(UUID quadroId) {
        this.quadroId = quadroId;
    }

    public OffsetDateTime getVisualizadoEm() {
        return visualizadoEm;
    }

    public void setVisualizadoEm(OffsetDateTime visualizadoEm) {
        this.visualizadoEm = visualizadoEm;
    }
}
