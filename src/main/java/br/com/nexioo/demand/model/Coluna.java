package br.com.nexioo.demand.model;

import br.com.nexioo.demand.util.IdUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa as colunas/listas do quadro Kanban.
 * Utiliza UUID como chave técnica primária e código único por projeto.
 */
public class Coluna {

    private String id;
    private UUID uuid;
    private UUID projetoId;
    private UUID usuarioId;
    private String codigo;
    private String descricao;
    private int ordem;

    // Colunas padrão do sistema
    public static final Coluna BACKLOG = new Coluna("BACKLOG", "Backlog", 1);
    public static final Coluna A_FAZER = new Coluna("A_FAZER", "A Fazer", 2);
    public static final Coluna EM_ANDAMENTO = new Coluna("EM_ANDAMENTO", "Em Andamento", 3);
    public static final Coluna CONCLUIDO = new Coluna("CONCLUIDO", "Concluído", 4);

    public Coluna() {
    }

    public Coluna(String id, String descricao, int ordem) {
        this.id = id;
        this.codigo = id;
        this.descricao = descricao;
        this.ordem = ordem;
        this.uuid = IdUtils.parseUuid(id);
    }

    public Coluna(UUID uuid, UUID projetoId, UUID usuarioId, String codigo, String descricao, int ordem) {
        this.uuid = uuid;
        this.id = (uuid != null) ? uuid.toString() : codigo;
        this.projetoId = projetoId;
        this.usuarioId = usuarioId;
        this.codigo = codigo;
        this.descricao = descricao;
        this.ordem = ordem;
    }

    public String getId() {
        if (id == null && uuid != null) {
            id = uuid.toString();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
        if (this.codigo == null) {
            this.codigo = id;
        }
        if (this.uuid == null) {
            this.uuid = IdUtils.parseUuid(id);
        }
    }

    public UUID getUuid() {
        if (uuid == null && id != null) {
            uuid = IdUtils.parseUuid(id);
        }
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
        if (uuid != null) {
            this.id = uuid.toString();
        }
    }

    public UUID getProjetoId() {
        return projetoId;
    }

    public void setProjetoId(UUID projetoId) {
        this.projetoId = projetoId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getCodigo() {
        return (codigo != null && !codigo.isBlank()) ? codigo : id;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getNome() {
        return descricao;
    }

    public void setNome(String nome) {
        this.descricao = nome;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    /**
     * Retorna o ID/código para manter compatibilidade com chamadas ${coluna.name()} nos templates Thymeleaf.
     */
    public String name() {
        return (id != null) ? id : (codigo != null ? codigo : "COLUNA");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coluna coluna = (Coluna) o;
        if (uuid != null && coluna.uuid != null && Objects.equals(uuid, coluna.uuid)) {
            return true;
        }
        String c1 = (codigo != null && !codigo.isBlank()) ? codigo.trim().toUpperCase() : (id != null ? id.trim().toUpperCase() : "");
        String c2 = (coluna.codigo != null && !coluna.codigo.isBlank()) ? coluna.codigo.trim().toUpperCase() : (coluna.id != null ? coluna.id.trim().toUpperCase() : "");
        return !c1.isEmpty() && c1.equals(c2);
    }

    @Override
    public int hashCode() {
        String c = (codigo != null && !codigo.isBlank()) ? codigo.trim().toUpperCase() : (id != null ? id.trim().toUpperCase() : "");
        return Objects.hash(c);
    }
}
