package br.com.nexioo.demand.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserContext — Testes de Ciclo de Vida e Proteção de Dados")
class UserContextTest {

    @Test
    @DisplayName("UserContext deve inicializar corretamente e mascarar o token no toString")
    void deveInicializarEMascararToken() {
        UserContext ctx = new UserContext();
        assertFalse(ctx.isAutenticado());

        UUID uid = UUID.randomUUID();
        ctx.inicializar(uid, "usuario@nexioo.com.br", "Usuário Teste", "secret-bearer-token-12345");

        assertTrue(ctx.isAutenticado());
        assertEquals(uid, ctx.getUsuarioId());
        assertEquals("usuario@nexioo.com.br", ctx.getEmail());
        assertEquals("Usuário Teste", ctx.getNome());
        assertEquals("secret-bearer-token-12345", ctx.getAccessToken());

        String representacao = ctx.toString();
        assertFalse(representacao.contains("secret-bearer-token-12345"), "O access token nunca deve vazar no toString()");
        assertTrue(representacao.contains("[PROTECTED]"));
    }

    @Test
    @DisplayName("UserContext não deve permitir reinicialização na mesma requisição")
    void deveImpedirReinicializacao() {
        UserContext ctx = new UserContext();
        UUID uid = UUID.randomUUID();
        ctx.inicializar(uid, "u1@nexioo.com", "U1", "token1");

        assertThrows(IllegalStateException.class, () ->
                ctx.inicializar(UUID.randomUUID(), "u2@nexioo.com", "U2", "token2")
        );
    }
}
