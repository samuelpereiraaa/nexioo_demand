package br.com.nexioo.demand.repository.supabase;

import br.com.nexioo.demand.client.SupabaseRestClient;
import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.repository.QuadroRecenteRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile({"prod", "supabase", "dev-supabase"})
public class QuadroRecenteRepositorySupabase implements QuadroRecenteRepository {

    private final SupabaseRestClient client;
    private final UserContext userContext;

    public QuadroRecenteRepositorySupabase(SupabaseRestClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext;
    }

    @Override
    public void registrarVisualizacao(UUID usuarioId, UUID quadroId) {
        if (quadroId == null) return;
        UUID authUid = userContext.requireUsuarioId();
        if (usuarioId != null && !authUid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("usuario_id", authUid);
        body.put("quadro_id", quadroId);
        body.put("visualizado_em", OffsetDateTime.now().toString());

        client.upsert("quadros_visualizados_recentemente", body);
    }

    @Override
    public List<UUID> listarQuadrosRecentesIds(UUID usuarioId, int limite) {
        UUID authUid = userContext.requireUsuarioId();
        if (usuarioId != null && !authUid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }

        int lim = (limite > 0) ? limite : 10;
        String query = "quadros_visualizados_recentemente?usuario_id=eq." + authUid
                + "&select=quadro_id&order=visualizado_em.desc&limit=" + lim;

        List<QuadroRecenteEntity> list = client.getList(query, QuadroRecenteEntity.class);
        return list.stream()
                .map(e -> e.quadroId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void removerRecente(UUID usuarioId, UUID quadroId) {
        if (quadroId == null) return;
        UUID authUid = userContext.requireUsuarioId();
        if (usuarioId != null && !authUid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }

        client.deleteOptional("quadros_visualizados_recentemente?usuario_id=eq." + authUid + "&quadro_id=eq." + quadroId);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuadroRecenteEntity {
        @JsonProperty("quadro_id")
        public UUID quadroId;

        @JsonProperty("usuario_id")
        public UUID usuarioId;

        @JsonProperty("visualizado_em")
        public String visualizadoEm;
    }
}
