package br.com.nexioo.demand.util;

import java.util.UUID;

/**
 * Utilitário para validação e manipulação estrita de UUIDs.
 */
public final class IdUtils {

    private IdUtils() {
    }

    public static UUID generateUuid() {
        return UUID.randomUUID();
    }

    /**
     * Converte com segurança um objeto ou string em UUID.
     * Retorna null caso o valor seja nulo, vazio ou não represente um UUID válido.
     * Não utiliza gerações artificiais nem conversões destrutivas.
     */
    public static UUID parseUuid(Object valor) {
        if (valor == null) return null;
        if (valor instanceof UUID) return (UUID) valor;
        String str = valor.toString().trim();
        if (str.isEmpty()) return null;

        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
