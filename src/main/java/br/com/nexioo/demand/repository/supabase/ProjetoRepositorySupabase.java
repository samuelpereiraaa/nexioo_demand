package br.com.nexioo.demand.repository.supabase;

import br.com.nexioo.demand.client.SupabaseRestClient;
import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.ProjetoRepository;
import br.com.nexioo.demand.util.IdUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile({"prod", "supabase", "dev-supabase"})
public class ProjetoRepositorySupabase implements ProjetoRepository {

    private final SupabaseRestClient client;
    private final UserContext userContext;

    public ProjetoRepositorySupabase(SupabaseRestClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext;
    }

    @Override
    public Projeto salvar(Projeto projeto) {
        UUID uid = userContext.requireUsuarioId();
        if (projeto.getId() == null) {
            UUID newUuid = IdUtils.generateUuid();
            projeto.setId(newUuid);

            Map<String, Object> body = new HashMap<>();
            body.put("id", newUuid);
            body.put("nome", projeto.getNome());
            body.put("descricao", projeto.getDescricao() != null ? projeto.getDescricao() : "");
            body.put("gradiente", projeto.getGradiente());
            body.put("area_trabalho_id", projeto.getAreaTrabalhoUuid());
            body.put("usuario_id", uid);

            ProjetoEntity created = client.post("projetos", body, ProjetoEntity.class);
            if (created != null) {
                return toModel(created);
            }
            return projeto;
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("nome", projeto.getNome());
            body.put("descricao", projeto.getDescricao());
            body.put("gradiente", projeto.getGradiente());
            client.patch("projetos?id=eq." + projeto.getId() + "&usuario_id=eq." + uid, body);
            return projeto;
        }
    }

    @Override
    public Optional<Projeto> buscarPorId(UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return buscarPorIdEUsuario(uuid, userContext.requireUsuarioId());
    }

    @Override
    public Optional<Projeto> buscarPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null) {
            return Optional.empty();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        List<ProjetoEntity> list = client.getList("projetos?id=eq." + id + "&usuario_id=eq." + uid + "&select=*", ProjetoEntity.class);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(toModel(list.get(0)));
    }

    @Override
    public List<Projeto> listarPorUsuario(UUID usuarioId) {
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        List<ProjetoEntity> list = client.getList("projetos?usuario_id=eq." + uid + "&select=*&order=created_at.desc", ProjetoEntity.class);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<Projeto> listarPorAreaEUsuario(UUID areaTrabalhoId, UUID usuarioId) {
        if (areaTrabalhoId == null) {
            return Collections.emptyList();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        List<ProjetoEntity> list = client.getList("projetos?area_trabalho_id=eq." + areaTrabalhoId + "&usuario_id=eq." + uid + "&select=*&order=created_at.desc", ProjetoEntity.class);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<Projeto> buscarPorIdsEUsuario(List<UUID> ids, UUID usuarioId) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        String inClause = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        String query = "projetos?id=in.(" + inClause + ")&usuario_id=eq." + uid + "&select=*";
        List<ProjetoEntity> list = client.getList(query, ProjetoEntity.class);
        Map<UUID, Projeto> map = list.stream().map(this::toModel).collect(Collectors.toMap(Projeto::getUuid, p -> p, (p1, p2) -> p1));

        // Preserva rigorosamente a ordem da lista de entrada (ordem de visualizado_em DESC)
        List<Projeto> ordenados = new ArrayList<>();
        for (UUID id : ids) {
            Projeto p = map.get(id);
            if (p != null) {
                ordenados.add(p);
            }
        }
        return ordenados;
    }

    @Override
    public List<Projeto> listarTodos() {
        return listarPorUsuario(userContext.requireUsuarioId());
    }

    @Override
    public List<Projeto> listarPorArea(UUID areaTrabalhoUuid) {
        if (areaTrabalhoUuid == null) return listarTodos();
        return listarPorAreaEUsuario(areaTrabalhoUuid, userContext.requireUsuarioId());
    }

    @Override
    public List<Projeto> listarRecentes() {
        // Consulta relacional de recentes é centralizada em QuadroRecenteRepository
        return Collections.emptyList();
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
        client.delete("projetos?id=eq." + id + "&usuario_id=eq." + uid);
    }

    private Projeto toModel(ProjetoEntity entity) {
        Projeto model = new Projeto();
        model.setId(entity.id);
        model.setUsuarioId(entity.usuarioId);
        model.setAreaTrabalhoId(entity.areaTrabalhoId);
        model.setNome(entity.nome);
        model.setDescricao(entity.descricao);
        model.setGradiente(entity.gradiente);
        model.setDataCriacao(entity.createdAt != null ? entity.createdAt.toLocalDate() : LocalDate.now());
        if (userContext != null && userContext.isAutenticado()) {
            model.setUsuarioProprietario(userContext.getEmail());
        }
        return model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProjetoEntity {
        @JsonProperty("id")
        public UUID id;

        @JsonProperty("usuario_id")
        public UUID usuarioId;

        @JsonProperty("area_trabalho_id")
        public UUID areaTrabalhoId;

        @JsonProperty("nome")
        public String nome;

        @JsonProperty("descricao")
        public String descricao;

        @JsonProperty("gradiente")
        public String gradiente;

        @JsonProperty("created_at")
        public LocalDateTime createdAt;
    }
}
