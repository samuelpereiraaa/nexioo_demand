package br.com.nexioo.demand.repository.supabase;

import br.com.nexioo.demand.client.SupabaseRestClient;
import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.model.AreaTrabalho;
import br.com.nexioo.demand.repository.AreaTrabalhoRepository;
import br.com.nexioo.demand.util.IdUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile({"prod", "supabase", "dev-supabase"})
public class AreaTrabalhoRepositorySupabase implements AreaTrabalhoRepository {

    private final SupabaseRestClient client;
    private final UserContext userContext;

    public AreaTrabalhoRepositorySupabase(SupabaseRestClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext;
    }

    @Override
    public List<AreaTrabalho> listarTodas() {
        UUID uid = userContext.requireUsuarioId();
        List<AreaTrabalhoEntity> entities = client.getList("areas_trabalho?usuario_id=eq." + uid + "&select=*&order=created_at.asc", AreaTrabalhoEntity.class);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(String usuario) {
        return listarTodas();
    }

    @Override
    public List<AreaTrabalho> listarPorUsuario(UUID usuarioId) {
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        List<AreaTrabalhoEntity> entities = client.getList("areas_trabalho?usuario_id=eq." + uid + "&select=*&order=created_at.asc", AreaTrabalhoEntity.class);
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public Optional<AreaTrabalho> buscarPorId(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return buscarPorIdEUsuario(uuid, userContext.requireUsuarioId());
    }

    @Override
    public Optional<AreaTrabalho> buscarPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null) {
            return Optional.empty();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        List<AreaTrabalhoEntity> list = client.getList("areas_trabalho?id=eq." + id + "&usuario_id=eq." + uid + "&select=*", AreaTrabalhoEntity.class);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(toModel(list.get(0)));
    }

    @Override
    public AreaTrabalho salvar(AreaTrabalho areaTrabalho) {
        UUID uid = userContext.requireUsuarioId();
        if (areaTrabalho.getId() == null) {
            UUID newUuid = IdUtils.generateUuid();
            areaTrabalho.setId(newUuid);

            Map<String, Object> body = new HashMap<>();
            body.put("id", newUuid);
            body.put("nome", areaTrabalho.getNome());
            body.put("inicial", areaTrabalho.getInicial());
            body.put("usuario_id", uid);

            AreaTrabalhoEntity created = client.post("areas_trabalho", body, AreaTrabalhoEntity.class);
            if (created != null) {
                return toModel(created);
            }
            return areaTrabalho;
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("nome", areaTrabalho.getNome());
            body.put("inicial", areaTrabalho.getInicial());
            client.patch("areas_trabalho?id=eq." + areaTrabalho.getId() + "&usuario_id=eq." + uid, body);
            return areaTrabalho;
        }
    }

    @Override
    public void excluir(UUID uuid) {
        if (uuid != null) {
            excluirPorIdEUsuario(uuid, userContext.requireUsuarioId());
        }
    }

    @Override
    public void excluirPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null) return;
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        client.delete("areas_trabalho?id=eq." + id + "&usuario_id=eq." + uid);
    }

    private AreaTrabalho toModel(AreaTrabalhoEntity entity) {
        AreaTrabalho model = new AreaTrabalho();
        model.setId(entity.id);
        model.setUsuarioId(entity.usuarioId);
        model.setNome(entity.nome);
        model.setInicial(entity.inicial);
        model.setUsuarioProprietario(userContext.getEmail());
        model.setCriadoEm(entity.createdAt != null ? entity.createdAt : LocalDateTime.now());
        return model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AreaTrabalhoEntity {
        @JsonProperty("id")
        public UUID id;

        @JsonProperty("usuario_id")
        public UUID usuarioId;

        @JsonProperty("nome")
        public String nome;

        @JsonProperty("inicial")
        public String inicial;

        @JsonProperty("created_at")
        public LocalDateTime createdAt;
    }
}
