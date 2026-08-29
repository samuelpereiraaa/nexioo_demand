package br.com.nexioo.demand;

import br.com.nexioo.demand.dto.DemandaForm;
import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.model.Prioridade;
import br.com.nexioo.demand.service.DemandaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Popula o repositório com demandas de demonstração na inicialização.
 * Contém dados representativos inspirados no prompt e na imagem de referência.
 */
@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final DemandaService demandaService;

    public DataLoader(DemandaService demandaService) {
        this.demandaService = demandaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Carregando dados de demonstração para o quadro Kanban...");

        // Coluna 1: Backlog
        criar("Definir identidade visual",
                "Construir guia de estilo, paleta dark premium com preto grafite e verde-esmeralda elétrico.",
                Coluna.BACKLOG, Prioridade.ALTA, "Samuel Oliveira", LocalDate.now().plusDays(10));

        criar("Elaborar documentação da arquitetura",
                "Mapear módulos, contratos de repositório e estratégias de persistência desacoplada.",
                Coluna.BACKLOG, Prioridade.BAIXA, "Ana Rodrigues", null);

        // Coluna 2: A fazer
        criar("Criar página inicial",
                "Implementar o quadro Kanban com visualização completa das colunas, cartões e barra superior translúcida.",
                Coluna.A_FAZER, Prioridade.ALTA, "Samuel Oliveira", LocalDate.now().plusDays(2));

        criar("Configurar testes automatizados",
                "Cobrir camadas web (MockMvc) e serviços com testes unitários JUnit 5 e Mockito.",
                Coluna.A_FAZER, Prioridade.MEDIA, "Pedro Costa", LocalDate.now().plusDays(5));

        // Coluna 3: Em andamento
        criar("Validar fluxo de cadastro",
                "Testar criação de demandas, validação de campos obrigatórios no servidor e feedback com mensagens amigáveis.",
                Coluna.EM_ANDAMENTO, Prioridade.ALTA, "Maria Santos", LocalDate.now().plusDays(1));

        criar("Ajustar contraste e acessibilidade",
                "Verificar navegação por teclado, foco visível e suporte a leitores de tela em conformidade com WCAG.",
                Coluna.EM_ANDAMENTO, Prioridade.MEDIA, "Maria Santos", LocalDate.now().plusDays(3));

        // Coluna 4: Concluído
        criar("Preparar apresentação",
                "Reunir capturas de tela do layout responsivo em desktop e mobile para demonstração ao cliente.",
                Coluna.CONCLUIDO, Prioridade.MEDIA, "Pedro Costa", LocalDate.now().minusDays(1));

        criar("Estruturar projeto Spring Boot",
                "Configurar Maven, dependências Web, Thymeleaf, Hibernate Validator e perfis de ambiente.",
                Coluna.CONCLUIDO, Prioridade.ALTA, "Samuel Oliveira", LocalDate.now().minusDays(3));

        log.info("Dados de demonstração carregados com sucesso.");
    }

    private void criar(String titulo, String descricao, Coluna coluna,
                       Prioridade prioridade, String responsavel, LocalDate prazo) {
        DemandaForm form = new DemandaForm();
        form.setTitulo(titulo);
        form.setDescricao(descricao);
        form.setColuna(coluna);
        form.setPrioridade(prioridade);
        form.setResponsavel(responsavel);
        form.setPrazo(prazo);
        demandaService.criar(form);
    }
}
