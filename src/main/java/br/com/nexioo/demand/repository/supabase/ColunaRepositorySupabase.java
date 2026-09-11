package br.com.nexioo.demand.repository.supabase;

import br.com.nexioo.demand.client.SupabaseRestClient;
import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.repository.ColunaRepository;
import br.com.nexioo.demand.util.IdUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile({"prod", "supabase", "dev-supabase"})
public class ColunaRepositorySupabase implements ColunaRepository {

    private final SupabaseRestClient client;
    private final UserContext userContext;

    public ColunaRepositorySupabase(SupabaseRestClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext;
    }

    @Override
    public Coluna salvar(Coluna coluna) {
        UUID uid = userContext.requireUsuarioId();
        if (coluna.getUuid() == null) {
            UUID newUuid = IdUtils.parseUuid(coluna.getId());
            if (newUuid == null) {
                newUuid = IdUtils.generateUuid();
            }
            coluna.setUuid(newUuid);

            Map<String, Object> body = new HashMap<>();
            body.put("id", newUuid);
            body.put("projeto_id", coluna.getProjetoId());
            body.put("usuario_id", uid);
            body.put("codigo", coluna.getCodigo() != null ? coluna.getCodigo() : "COLUNA");
            body.put("nome", coluna.getDescricao() != null ? coluna.getDescricao() : "Lista");
            body.put("ordem", coluna.getOrdem());

            ColunaEntity created = client.post("colunas", body, ColunaEntity.class);
            if (created != null) {
                return toModel(created);
            }
            return coluna;
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("nome", coluna.getDescricao());
            body.put("ordem", coluna.getOrdem());
            client.patch("colunas?id=eq." + coluna.getUuid() + "&usuario_id=eq." + uid, body);
            return coluna;
        }
    }

    @Override
    public Optional<Coluna> buscarPorId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return buscarPorIdEUsuario(id, userContext.requireUsuarioId());
    }

    @Override
    public Optional<Coluna> buscarPorUuid(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return buscarPorIdEUsuario(uuid.toString(), userContext.requireUsuarioId());
    }

    @Override
    public List<Coluna> listarTodas() {
        return listarPorProjetoEUsuario(null, userContext.requireUsuarioId());
    }

    @Override
    public List<Coluna> listarPorProjeto(UUID projetoId) {
        return listarPorProjetoEUsuario(projetoId, userContext.requireUsuarioId());
    }

    @Override
    public boolean existePorId(String id) {
        return buscarPorId(id).isPresent();
    }

    @Override
    public void excluir(String id) {
        if (id == null) return;
        UUID uid = userContext.requireUsuarioId();
        UUID uuid = IdUtils.parseUuid(id);
        if (uuid != null) {
            client.delete("colunas?id=eq." + uuid + "&usuario_id=eq." + uid);
        } else {
            client.delete("colunas?codigo=eq." + id + "&usuario_id=eq." + uid);
        }
    }

    @Override
    public Optional<Coluna> buscarPorIdEUsuario(String id, UUID usuarioId) {
        if (id == null) {
            return Optional.empty();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        UUID uuid = IdUtils.parseUuid(id);
        List<ColunaEntity> list;
        if (uuid != null && uuid.getMostSignificantBits() != 0) {
            list = client.getList("colunas?id=eq." + uuid + "&usuario_id=eq." + uid + "&select=*", ColunaEntity.class);
        } else {
            list = client.getList("colunas?codigo=eq." + id + "&usuario_id=eq." + uid + "&select=*", ColunaEntity.class);
        }

        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toModel(list.get(0)));
    }

    @Override
    public Optional<Coluna> buscarPorIdEProjetoEUsuario(String id, UUID projetoId, UUID usuarioId) {
        if (id == null) {
            return Optional.empty();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        UUID uuid = IdUtils.parseUuid(id);
        String projFilter = (projetoId != null) ? "&projeto_id=eq." + projetoId : "";
        List<ColunaEntity> list;
        if (uuid != null && uuid.getMostSignificantBits() != 0) {
            list = client.getList("colunas?id=eq." + uuid + "&usuario_id=eq." + uid + projFilter + "&select=*", ColunaEntity.class);
        } else {
            list = client.getList("colunas?codigo=eq." + id + "&usuario_id=eq." + uid + projFilter + "&select=*", ColunaEntity.class);
        }

        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toModel(list.get(0)));
    }

    @Override
    public List<Coluna> listarPorProjetoEUsuario(UUID projetoId, UUID usuarioId) {
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        String query = (projetoId != null)
                ? "colunas?projeto_id=eq." + projetoId + "&usuario_id=eq." + uid + "&select=*&order=ordem.asc"
                : "colunas?usuario_id=eq." + uid + "&select=*&order=ordem.asc";
        List<ColunaEntity> list = client.getList(query, ColunaEntity.class);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public void excluirPorIdEProjetoEUsuario(String id, UUID projetoId, UUID usuarioId) {
        if (id == null) return;
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        UUID uuid = IdUtils.parseUuid(id);
        String projFilter = (projetoId != null) ? "&projeto_id=eq." + projetoId : "";
        if (uuid != null && uuid.getMostSignificantBits() != 0) {
            client.delete("colunas?id=eq." + uuid + "&usuario_id=eq." + uid + projFilter);
        } else {
            client.delete("colunas?codigo=eq." + id + "&usuario_id=eq." + uid + projFilter);
        }
    }

    private Coluna toModel(ColunaEntity entity) {
        Coluna model = new Coluna();
        model.setUuid(entity.id);
        model.setId(entity.id.toString());
        model.setProjetoId(entity.projetoId);
        model.setUsuarioId(entity.usuarioId);
        model.setCodigo(entity.codigo);
        model.setDescricao(entity.nome);
        model.setOrdem(entity.ordem);
        return model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ColunaEntity {
        @JsonProperty("id")
        public UUID id;

        @JsonProperty("projeto_id")
        public UUID projetoId;

        @JsonProperty("usuario_id")
        public UUID usuarioId;

        @JsonProperty("codigo")
        public String codigo;

        @JsonProperty("nome")
        public String nome;

        @JsonProperty("ordem")
        public int ordem;
    }
}
