package br.com.nexioo.demand.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("GlobalModelAttributes — Testes de Iniciais e Nomes do Usuário")
class GlobalModelAttributesTest {

    @Test
    @DisplayName("Deve gerar iniciais corretas para nomes com uma ou várias palavras")
    void deveGerarIniciaisCorretamente() {
        assertEquals("SP", GlobalModelAttributes.gerarIniciais("Samuel Pereira"));
        assertEquals("AS", GlobalModelAttributes.gerarIniciais("Ana Silva"));
        assertEquals("J", GlobalModelAttributes.gerarIniciais("João"));
        assertEquals("S", GlobalModelAttributes.gerarIniciais("Samuel"));
        assertEquals("SP", GlobalModelAttributes.gerarIniciais("Samuel de Pereira"));
    }

    @Test
    @DisplayName("Deve gerar iniciais para e-mails sanitizados")
    void deveGerarIniciaisDeEmail() {
        String nome1 = GlobalModelAttributes.extrairNomeDeEmail("samuel.pereira@nexioo.com.br");
        assertEquals("SP", GlobalModelAttributes.gerarIniciais(nome1));

        String nome2 = GlobalModelAttributes.extrairNomeDeEmail("ana_silva@empresa.com");
        assertEquals("AS", GlobalModelAttributes.gerarIniciais(nome2));

        String nome3 = GlobalModelAttributes.extrairNomeDeEmail("joao@nexioo.com.br");
        assertEquals("J", GlobalModelAttributes.gerarIniciais(nome3));
    }
}
