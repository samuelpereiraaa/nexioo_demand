package br.com.nexioo.demand.config;

import br.com.nexioo.demand.repository.DemandaRepository;
import br.com.nexioo.demand.repository.memory.DemandaRepositoryMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurações de infraestrutura da aplicação.
 * <p>
 * <strong>Fase 1:</strong> registra o repositório em memória como bean Spring.
 * <p>
 * <strong>Fase 2 (migração para JPA):</strong>
 * <ol>
 *   <li>Adicione {@code spring-boot-starter-data-jpa} ao pom.xml.</li>
 *   <li>Crie {@code DemandaRepositoryJpa extends JpaRepository<Demanda, Long>}.</li>
 *   <li>Anote-a com {@code @Repository}.</li>
 *   <li>Remova o {@code @Bean demandaRepository()} abaixo — o Spring Data
 *       registrará a implementação automaticamente.</li>
 * </ol>
 */
@Configuration
public class AppConfig {

    @Bean
    public DemandaRepository demandaRepository() {
        return new DemandaRepositoryMemory();
    }
}
