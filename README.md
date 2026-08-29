# Nexio Demand

Sistema web de gerenciamento de demandas em quadro Kanban com design moderno, fundo gradiente violeta/rosa, topbar translúcida e cartões responsivos.  
Desenvolvido com Java 11, Spring Boot 2.7, Thymeleaf e CSS3 responsivo puro.

---

## Pré-requisitos

| Ferramenta | Versão utilizada |
|---|---|
| Java (JDK) | 11 (Temurin) |
| Maven | 3.9.16 (Homebrew) |

Verifique a instalação:

```bash
java -version   # deve mostrar 11.x.x
mvn -version    # deve mostrar 3.9.x
```

> **Dica para Mac**: caso o Maven aponte para outra versão do Java, garanta o Java 11 exportando `JAVA_HOME`:
>
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 11)
> mvn spring-boot:run
> ```

---

## Executar localmente

```bash
# Acesse o diretório do projeto
cd /Users/samuel/Documents/my_projects/nexioo_demand

# Compilar e iniciar a aplicação
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
mvn spring-boot:run

# A aplicação estará disponível em:
# http://localhost:8080 ou http://localhost:8080/quadro
```

Ao iniciar, o `DataLoader` popula automaticamente **8 demandas de demonstração** distribuídas pelas 4 colunas (`Backlog`, `A fazer`, `Em andamento`, `Concluído`).

---

## Executar os testes

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
mvn test
```

Os testes cobrem:
- **`DemandaServiceTest`**: 8 testes unitários (regras de negócio, filtros, mudança de coluna e exclusão com Mockito)
- **`DemandaControllerTest`**: 6 testes de camada web (MockMvc)
- **`QuadroControllerTest`**: 2 testes de renderização do quadro Kanban (rotas `/` e `/quadro`)

---

## Estrutura do projeto

```
src/
├── main/
│   ├── java/br/com/nexioo/demand/
│   │   ├── NexiooDemandApplication.java
│   │   ├── DataLoader.java                  ← dados de demonstração
│   │   ├── config/
│   │   │   └── AppConfig.java               ← registra o repositório em memória
│   │   ├── controller/
│   │   │   ├── QuadroController.java        ← GET / e /quadro (quadro Kanban)
│   │   │   ├── DemandaController.java       ← CRUD completo de demandas
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── dto/
│   │   │   └── DemandaForm.java             ← binding + validações do formulário
│   │   ├── exception/
│   │   │   └── DemandaNaoEncontradaException.java
│   │   ├── model/
│   │   │   ├── Demanda.java                 ← entidade POJO com helper de avatar
│   │   │   ├── Coluna.java                  ← enum das 4 colunas Kanban
│   │   │   └── Prioridade.java              ← enum (Baixa, Média, Alta, Urgente)
│   │   ├── repository/
│   │   │   ├── DemandaRepository.java       ← interface desacoplada
│   │   │   └── memory/
│   │   │       └── DemandaRepositoryMemory.java  ← implementação em memória
│   │   └── service/
│   │       ├── DemandaService.java          ← interface de casos de uso
│   │       └── DemandaServiceImpl.java
│   └── resources/
│       ├── application.properties
│       ├── application-dev.properties       ← perfil dev (preparado para BD)
│       ├── templates/
│       │   ├── fragments/
│       │   │   ├── head.html                ← meta tags, título e CSS
│       │   │   ├── header.html              ← topbar translúcida com avatar e busca
│       │   │   ├── coluna.html              ← componente reutilizável da coluna
│       │   │   ├── cartao.html              ← componente reutilizável do cartão
│       │   │   ├── modal-demanda.html       ← modal acessível de criação rápida
│       │   │   └── alerts.html              ← notificações toast
│       │   ├── quadro/index.html            ← tela principal do quadro Kanban
│       │   ├── demanda/
│       │   │   ├── form.html                ← formulário dedicado criar/editar
│       │   │   ├── detalhe.html             ← visualização detalhada
│       │   │   └── confirmar-exclusao.html  ← diálogo de exclusão
│       │   └── erro/nao-encontrado.html
│       └── static/
│           ├── css/
│           │   ├── base.css                 ← variáveis, gradiente, topbar, modais
│           │   ├── kanban.css               ← colunas, cartões, tags e grid
│           │   └── form.css                 ← formulários, inputs e subpáginas
│           └── js/app.js                    ← modal, foco, atalhos de teclado
└── test/
    └── java/br/com/nexioo/demand/
        ├── service/DemandaServiceTest.java
        └── controller/
            ├── DemandaControllerTest.java
            └── QuadroControllerTest.java
```

---

## Rotas disponíveis

| Método | Rota | Descrição |
|---|---|---|
| GET | `/` ou `/quadro` | Quadro Kanban com busca e colunas |
| GET | `/demandas/nova` | Formulário dedicado de criação |
| POST | `/demandas` | Criar nova demanda |
| GET | `/demandas/{id}` | Visualizar detalhes da demanda |
| GET | `/demandas/{id}/editar` | Formulário de edição |
| POST | `/demandas/{id}/editar` | Salvar alterações da demanda |
| POST | `/demandas/{id}/status` | Alterar status/coluna da demanda |
| GET | `/demandas/{id}/excluir` | Tela de confirmação de exclusão |
| POST | `/demandas/{id}/excluir` | Excluir demanda definitivamente |
