package br.com.nexioo.demand.repository.supabase;

import br.com.nexioo.demand.client.SupabaseRestClient;
import br.com.nexioo.demand.config.UserContext;
import br.com.nexioo.demand.model.*;
import br.com.nexioo.demand.repository.DemandaRepository;
import br.com.nexioo.demand.util.IdUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Profile({"prod", "supabase", "dev-supabase"})
public class DemandaRepositorySupabase implements DemandaRepository {

    private static final Logger log = LoggerFactory.getLogger(DemandaRepositorySupabase.class);

    private final SupabaseRestClient client;
    private final UserContext userContext;

    public DemandaRepositorySupabase(SupabaseRestClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext;
    }

    @Override
    public Demanda salvar(Demanda demanda) {
        if (demanda == null) {
            throw new IllegalArgumentException("Demanda não pode ser nula.");
        }
        UUID uid = userContext.requireUsuarioId();

        UUID demandaId = demanda.getId() != null ? demanda.getId() : IdUtils.generateUuid();
        demanda.setId(demandaId);

        // 1. Monta o objeto Demanda
        Map<String, Object> demandaMap = new HashMap<>();
        demandaMap.put("id", demandaId);
        demandaMap.put("projeto_id", demanda.getProjetoId());
        demandaMap.put("usuario_id", uid);
        demandaMap.put("coluna_id", demanda.getColuna() != null ? demanda.getColuna().getUuid() : null);
        demandaMap.put("titulo", demanda.getTitulo());
        demandaMap.put("descricao", demanda.getDescricao() != null ? demanda.getDescricao() : "");
        demandaMap.put("prioridade", demanda.getPrioridade() != null ? demanda.getPrioridade().name() : "MEDIA");
        demandaMap.put("responsavel_nome", demanda.getResponsavel());
        demandaMap.put("prazo", demanda.getPrazo());
        demandaMap.put("posicao", demanda.getPosicao());
        demandaMap.put("concluido", demanda.isConcluido());
        demandaMap.put("acompanhando", demanda.isAcompanhando());
        demandaMap.put("imagem_url", demanda.getImagemUrl());

        // 2. Monta listas de subentidades
        List<Map<String, Object>> checklistsList = new ArrayList<>();
        if (demanda.getChecklists() != null) {
            int posChk = 0;
            for (Checklist c : demanda.getChecklists()) {
                UUID cId = c.getId() != null ? c.getId() : IdUtils.generateUuid();
                c.setId(cId);

                Map<String, Object> cMap = new HashMap<>();
                cMap.put("id", cId);
                cMap.put("titulo", c.getTitulo() != null ? c.getTitulo() : "Checklist");
                cMap.put("posicao", posChk++);

                List<Map<String, Object>> itensList = new ArrayList<>();
                if (c.getItens() != null) {
                    int posItem = 0;
                    for (ChecklistItem item : c.getItens()) {
                        UUID itemId = item.getId() != null ? item.getId() : IdUtils.generateUuid();
                        item.setId(itemId);

                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("id", itemId);
                        itemMap.put("texto", item.getTexto() != null ? item.getTexto() : "");
                        itemMap.put("concluido", item.isConcluido());
                        itemMap.put("posicao", posItem++);
                        itensList.add(itemMap);
                    }
                }
                cMap.put("itens", itensList);
                checklistsList.add(cMap);
            }
        }

        List<Map<String, Object>> anexosList = new ArrayList<>();
        if (demanda.getAnexos() != null) {
            for (Anexo a : demanda.getAnexos()) {
                UUID aId = IdUtils.parseUuid(a.getId());
                if (aId == null) {
                    aId = IdUtils.generateUuid();
                    a.setId(aId.toString());
                }
                Map<String, Object> aMap = new HashMap<>();
                aMap.put("id", aId);
                aMap.put("nome", a.getNome() != null ? a.getNome() : "arquivo");
                aMap.put("url", a.getUrl() != null ? a.getUrl() : "");
                aMap.put("storage_path", a.getStoragePath() != null ? a.getStoragePath() : "local");
                aMap.put("capa", a.isCapa());
                anexosList.add(aMap);
            }
        }

        List<Map<String, Object>> etiquetasList = new ArrayList<>();
        if (demanda.getEtiquetas() != null) {
            for (Etiqueta et : demanda.getEtiquetas()) {
                String codigo = et.getId() != null && !et.getId().isBlank()
                        ? et.getId()
                        : (et.getNome() != null ? et.getNome().toLowerCase().replace(" ", "_") : "tag");
                Map<String, Object> etMap = new HashMap<>();
                etMap.put("codigo", codigo);
                etMap.put("nome", et.getNome() != null ? et.getNome() : codigo);
                etMap.put("cor_hex", et.getCorHex() != null ? et.getCorHex() : "#3b82f6");
                etiquetasList.add(etMap);
            }
        }

        List<Map<String, Object>> membrosList = new ArrayList<>();
        if (demanda.getMembros() != null) {
            for (String membroNome : demanda.getMembros()) {
                if (membroNome == null || membroNome.isBlank()) continue;
                Map<String, Object> mMap = new HashMap<>();
                mMap.put("nome", membroNome.trim());
                membrosList.add(mMap);
            }
        }

        // 3. Monta payload unificado da RPC
        Map<String, Object> payload = new HashMap<>();
        payload.put("demanda", demandaMap);
        payload.put("checklists", checklistsList);
        payload.put("anexos", anexosList);
        payload.put("etiquetas", etiquetasList);
        payload.put("membros", membrosList);

        // 4. Invoca a RPC transacional atômica
        try {
            Map<String, Object> params = Map.of("p_payload", payload);
            Map<?, ?> res = client.rpc("rpc_salvar_demanda_completa", params, Map.class);
            if (res != null && res.containsKey("id")) {
                demanda.setId(IdUtils.parseUuid(res.get("id")));
            }
            return demanda;
        } catch (Exception e) {
            log.error("Falha ao salvar demanda completa via RPC [id={}]: {}", demandaId, e.getMessage());
            throw new IllegalStateException("Falha ao salvar demanda no Supabase: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Demanda> buscarPorUuid(UUID uuid) {
        if (uuid == null || userContext == null || !userContext.isAutenticado()) {
            return Optional.empty();
        }
        return buscarPorIdEUsuario(uuid, userContext.requireUsuarioId());
    }

    @Override
    public List<Demanda> listarTodas() {
        return listarPorUsuario(userContext.requireUsuarioId());
    }

    @Override
    public List<Demanda> listarPorProjeto(UUID projetoId) {
        return listarPorProjetoEUsuario(projetoId, userContext.requireUsuarioId());
    }

    @Override
    public Optional<Demanda> buscarPorIdEUsuario(UUID id, UUID usuarioId) {
        if (id == null) {
            return Optional.empty();
        }
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        String endpoint = "demandas?id=eq." + id + "&usuario_id=eq." + uid + "&select=*,demandas_etiquetas(*),demandas_checklists(*,demandas_checklist_itens(*)),demandas_anexos(*),demandas_comentarios(*),demandas_atividades(*),demandas_membros(*)";
        List<DemandaEntity> list = client.getList(endpoint, DemandaEntity.class);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(toModel(list.get(0)));
    }

    @Override
    public List<Demanda> listarPorUsuario(UUID usuarioId) {
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        String query = "demandas?usuario_id=eq." + uid + "&select=*,demandas_etiquetas(*),demandas_checklists(*,demandas_checklist_itens(*)),demandas_anexos(*),demandas_comentarios(*),demandas_atividades(*),demandas_membros(*)&order=posicao.asc";
        List<DemandaEntity> list = client.getList(query, DemandaEntity.class);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<Demanda> listarPorProjetoEUsuario(UUID projetoId, UUID usuarioId) {
        UUID uid = userContext.requireUsuarioId();
        if (usuarioId != null && !uid.equals(usuarioId)) {
            throw new SecurityException("Tentativa de acesso com usuarioId divergente do usuário autenticado");
        }
        String query = (projetoId != null)
                ? "demandas?projeto_id=eq." + projetoId + "&usuario_id=eq." + uid + "&select=*,demandas_etiquetas(*),demandas_checklists(*,demandas_checklist_itens(*)),demandas_anexos(*),demandas_comentarios(*),demandas_atividades(*),demandas_membros(*)&order=posicao.asc"
                : "demandas?usuario_id=eq." + uid + "&select=*,demandas_etiquetas(*),demandas_checklists(*,demandas_checklist_itens(*)),demandas_anexos(*),demandas_comentarios(*),demandas_atividades(*),demandas_membros(*)&order=posicao.asc";
        List<DemandaEntity> list = client.getList(query, DemandaEntity.class);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public boolean existePorIdEUsuario(UUID id, UUID usuarioId) {
        return buscarPorIdEUsuario(id, usuarioId).isPresent();
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
        client.delete("demandas?id=eq." + id + "&usuario_id=eq." + uid);
    }

    @Override
    public boolean existePorUuid(UUID uuid) {
        if (uuid == null) return false;
        return buscarPorUuid(uuid).isPresent();
    }

    private Demanda toModel(DemandaEntity entity) {
        Demanda model = new Demanda();
        model.setId(entity.id);
        model.setProjetoId(entity.projetoId);
        model.setUsuarioId(entity.usuarioId);
        model.setTitulo(entity.titulo);
        model.setDescricao(entity.descricao);
        try {
            model.setPrioridade(entity.prioridade != null ? Prioridade.valueOf(entity.prioridade) : Prioridade.MEDIA);
        } catch (Exception e) {
            model.setPrioridade(Prioridade.MEDIA);
        }
        model.setResponsavel(entity.responsavelNome);
        model.setPrazo(entity.prazo);
        model.setPosicao(entity.posicao != null ? entity.posicao : 0);
        model.setConcluido(entity.concluido != null && entity.concluido);
        model.setAcompanhando(entity.acompanhando != null && entity.acompanhando);
        model.setImagemUrl(entity.imagemUrl);
        model.setCriadoEm(entity.createdAt != null ? entity.createdAt : LocalDateTime.now());
        model.setAtualizadoEm(entity.updatedAt != null ? entity.updatedAt : LocalDateTime.now());

        if (entity.colunaId != null) {
            Coluna c = new Coluna();
            c.setUuid(entity.colunaId);
            c.setId(entity.colunaId.toString());
            model.setColuna(c);
        }

        // Sub-entidades
        if (entity.anexos != null) {
            List<Anexo> anexos = entity.anexos.stream().map(a -> {
                Anexo anexo = new Anexo(a.id != null ? a.id.toString() : UUID.randomUUID().toString(), a.nome, a.url, a.createdAt != null ? a.createdAt : LocalDateTime.now(), a.capa != null && a.capa);
                anexo.setStoragePath(a.storagePath);
                return anexo;
            }).collect(Collectors.toList());
            model.setAnexos(anexos);
        }

        if (entity.etiquetas != null) {
            List<Etiqueta> etiquetas = entity.etiquetas.stream()
                    .map(e -> new Etiqueta(e.codigo, e.nome, e.corHex))
                    .collect(Collectors.toList());
            model.setEtiquetas(etiquetas);
        }

        if (entity.membros != null) {
            List<String> membros = entity.membros.stream().map(m -> m.nome).collect(Collectors.toList());
            model.setMembros(membros);
        }

        if (entity.checklists != null) {
            List<Checklist> checklists = entity.checklists.stream().map(c -> {
                Checklist chk = new Checklist(c.id, c.titulo);
                if (c.itens != null) {
                    List<ChecklistItem> items = c.itens.stream()
                            .map(i -> new ChecklistItem(i.id, i.texto, i.concluido != null && i.concluido))
                            .collect(Collectors.toList());
                    chk.setItens(items);
                }
                return chk;
            }).collect(Collectors.toList());
            model.setChecklists(checklists);
        }

        return model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DemandaEntity {
        @JsonProperty("id")
        public UUID id;
        @JsonProperty("projeto_id")
        public UUID projetoId;
        @JsonProperty("usuario_id")
        public UUID usuarioId;
        @JsonProperty("coluna_id")
        public UUID colunaId;
        @JsonProperty("titulo")
        public String titulo;
        @JsonProperty("descricao")
        public String descricao;
        @JsonProperty("prioridade")
        public String prioridade;
        @JsonProperty("responsavel_nome")
        public String responsavelNome;
        @JsonProperty("prazo")
        public LocalDate prazo;
        @JsonProperty("posicao")
        public Integer posicao;
        @JsonProperty("concluido")
        public Boolean concluido;
        @JsonProperty("acompanhando")
        public Boolean acompanhando;
        @JsonProperty("imagem_url")
        public String imagemUrl;
        @JsonProperty("created_at")
        public LocalDateTime createdAt;
        @JsonProperty("updated_at")
        public LocalDateTime updatedAt;

        @JsonProperty("demandas_anexos")
        public List<AnexoEntity> anexos;
        @JsonProperty("demandas_etiquetas")
        public List<EtiquetaEntity> etiquetas;
        @JsonProperty("demandas_membros")
        public List<MembroEntity> membros;
        @JsonProperty("demandas_checklists")
        public List<ChecklistEntity> checklists;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnexoEntity {
        @JsonProperty("id")
        public UUID id;
        @JsonProperty("nome")
        public String nome;
        @JsonProperty("url")
        public String url;
        @JsonProperty("storage_path")
        public String storagePath;
        @JsonProperty("capa")
        public Boolean capa;
        @JsonProperty("created_at")
        public LocalDateTime createdAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EtiquetaEntity {
        @JsonProperty("codigo")
        public String codigo;
        @JsonProperty("nome")
        public String nome;
        @JsonProperty("cor_hex")
        public String corHex;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MembroEntity {
        @JsonProperty("nome")
        public String nome;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChecklistEntity {
        @JsonProperty("id")
        public UUID id;
        @JsonProperty("titulo")
        public String titulo;
        @JsonProperty("posicao")
        public Integer posicao;
        @JsonProperty("demandas_checklist_itens")
        public List<ChecklistItemEntity> itens;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChecklistItemEntity {
        @JsonProperty("id")
        public UUID id;
        @JsonProperty("texto")
        public String texto;
        @JsonProperty("concluido")
        public Boolean concluido;
        @JsonProperty("posicao")
        public Integer posicao;
    }
}
