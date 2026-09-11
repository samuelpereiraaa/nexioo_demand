package br.com.nexioo.demand.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IdUtils — Testes de Geração e Parsing de UUIDs")
class IdUtilsTest {

    @Test
    @DisplayName("Geração de UUID nativo aleatório")
    void deveGerarUuidValido() {
        UUID novoUuid = IdUtils.generateUuid();
        assertNotNull(novoUuid);
        UUID outroUuid = IdUtils.generateUuid();
        assertNotEquals(novoUuid, outroUuid);
    }

    @Test
    @DisplayName("parseUuid deve tratar strings válidas e arbitrárias com robustez")
    void deveFazerParseDeUuidsVariados() {
        UUID uuidReal = UUID.randomUUID();
        assertEquals(uuidReal, IdUtils.parseUuid(uuidReal.toString()));
        assertEquals(uuidReal, IdUtils.parseUuid(uuidReal));

        assertNull(IdUtils.parseUuid(null));
        assertNull(IdUtils.parseUuid(""));

        assertNull(IdUtils.parseUuid("BACKLOG"));
    }
}
