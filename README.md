# Customer Service

Serviço para **cadastrar clientes**, **consultar o score de crédito** deles e **trocar o status via mensagem** (RabbitMQ).

Feito com Java 25, Spring Boot, PostgreSQL e RabbitMQ. Vem com testes unitários e planos de teste do **JMeter**.

## O que ele faz

```
 Você (curl, Postman, JMeter)
        │
        ▼
 ┌───────────────────────── customer-service ──────────────────────────┐
 │  CustomerController  →  CustomerService  →  PostgreSQL              │
 │         │                      │                                    │
 │         │                      └─ cliente novo? avisa o RabbitMQ ──►│ customer.created.queue
 │         ▼                                                           │
 │    ScoreClient  ──────────────►  serviço de score (aqui: um mock)   │
 │                                                                     │
 │  StatusChangeListener  ◄──  customer.status-change.queue            │
 └─────────────────────────────────────────────────────────────────────┘
```

1. **Cadastro de clientes** (criar, consultar, listar, buscar por nome, atualizar e apagar).
2. **Score**: `GET /customers/{id}/score` pergunta a um serviço externo. Se ele falhar ou demorar mais de 2 s, respondemos `503` em vez de ficar pendurados.
3. **RabbitMQ**: ao cadastrar um cliente, publicamos o evento `CUSTOMER_CREATED`. Também escutamos uma fila onde chegam pedidos para mudar o status de um cliente.

## Como rodar

Você precisa de **Docker** e, para rodar fora do Docker, **JDK 25** e **Maven 3.9+**.

**Opção 1 — tudo no Docker (mais fácil):**

```bash
docker compose up --build
```

**Opção 2 — dependências no Docker, aplicação pelo Maven (melhor para programar):**

```bash
docker compose up -d postgres rabbitmq score-mock
mvn spring-boot:run
```

Pronto, a API está em http://localhost:8080. Para conferir:

```bash
curl http://localhost:8080/customers
```

| O quê | Onde |
|---|---|
| API | http://localhost:8080 |
| Painel do RabbitMQ | http://localhost:15672 (usuário `guest`, senha `guest`) |
| Mock do score | http://localhost:8089 |

## Testar a API no Postman

Os arquivos importáveis estão em `api-tests/postman/`:

1. No Postman, importe `Customer Service.postman_collection.json` e `Customer Service.postman_environment.json`.
2. Selecione o ambiente **Customer Service - Local**.
3. Inicie a aplicação e o PostgreSQL; pela opção Docker, use `docker compose up --build`.
4. Execute a coleção **Customer Service - API** em ordem pelo Collection Runner.

A coleção testa criação, validação, CPF duplicado, consulta, listagem, filtro por status, busca por nome, atualização, score e exclusão. Ela gera um CPF novo a cada execução e exclui o cliente criado no final.

## Endpoints

| Método | Rota | O que faz | Respostas |
|---|---|---|---|
| POST | `/customers` | Cadastra um cliente | 201, 400 (dados inválidos), 409 (CPF já existe) |
| GET | `/customers/{id}` | Busca um cliente | 200, 404 |
| GET | `/customers?status=ACTIVE&page=0&size=20` | Lista clientes (o `status` é opcional) | 200, 400 |
| GET | `/customers/search?name=joao` | Busca pelo nome (sem diferenciar maiúsculas) | 200 |
| PUT | `/customers/{id}` | Atualiza um cliente | 200, 400, 404, 409 |
| DELETE | `/customers/{id}` | Apaga um cliente | 204, 404 |
| GET | `/customers/{id}/score` | Consulta o score | 200, 404 (sem score), 503 (serviço de score com problema) |

Status possíveis: `ACTIVE`, `INACTIVE`, `BLOCKED`. Se você não informar, o cliente nasce `ACTIVE`.
A lista é paginada (padrão 20 por página, no máximo 100).

**Exemplos:**

```bash
# cadastrar
curl -X POST localhost:8080/customers -H 'Content-Type: application/json' \
  -d '{"name":"João da Silva","cpf":"12345678901","email":"joao@email.com"}'

# consultar o score do cliente 1
curl localhost:8080/customers/1/score
# {"cpf":"12345678901","score":750,"classification":"LOW_RISK"}
```

Quando algo dá errado, a resposta explica o motivo, por exemplo `{"status":404,"message":"Cliente 99 não encontrado", ...}`.

### CPFs do mock de score

O mock (`mock-score/mappings/scores.json`) responde conforme o CPF consultado. Isso serve para testar os cenários de erro:

| CPF | O que o mock faz | O que a API devolve |
|---|---|---|
| `12345678901` | Score 750 | 200 |
| `00000000000` a `00000000009` | Não tem score | 404 |
| `99999999999` | Dá erro 500 | 503 |
| `88888888888` | Demora 10 s | 503 (estoura nosso limite de 2 s) |
| qualquer outro com 11 dígitos | Score 600 | 200 |

## RabbitMQ

**Publicamos** — ao cadastrar um cliente, uma mensagem vai para a exchange `customer.events` (routing key `customer.created`, fila `customer.created.queue`):

```json
{"eventType":"CUSTOMER_CREATED","customerId":123,"createdAt":"2026-08-11T15:30:00Z"}
```

Se o RabbitMQ estiver fora do ar, o cadastro **não falha**: o erro só é registrado no log.

**Consumimos** — a fila `customer.status-change.queue` (routing key `customer.status.change`) recebe pedidos assim:

```json
{"eventType":"CUSTOMER_STATUS_CHANGE","customerId":123,"status":"INACTIVE"}
```

Para testar: no painel do RabbitMQ vá em *Exchanges → customer.events → Publish message*, informe a routing key `customer.status.change` e cole o JSON acima (com o id de um cliente que exista). Depois consulte o cliente: o status mudou.

Alguns detalhes:
- Mensagem repetida não é problema: aplicar o mesmo status duas vezes dá o mesmo resultado.
- Mensagem inválida ou de cliente que não existe é ignorada, com um aviso no log.

## Testes

### Testes unitários (não precisam de Docker)

```bash
mvn test
```

| Arquivo | O que confere |
|---|---|
| `CustomerServiceTest` | Regras do cadastro: status padrão, CPF duplicado, 404, atualizar, apagar, listar |
| `CustomerControllerTest` | Rotas HTTP: códigos de status e JSON de resposta, validação dos dados |
| `ScoreClientTest` | Cliente do score contra um servidor falso: 200, 404, erro 500 e serviço fora do ar |
| `CustomerStatusChangeListenerTest` | Mensagens do RabbitMQ: válida, incompleta e de cliente inexistente |

### Testes com JMeter

Os planos ficam na pasta `jmeter/`. Eles testam a API **de fora**, como um usuário real, então a aplicação precisa estar rodando (veja "Como rodar").

| Arquivo | Para que serve |
|---|---|
| `jmeter/01-funcional.jmx` | **Funcional.** Percorre todos os endpoints e confere cada resposta (status e conteúdo). Inclui os cenários de erro: dados inválidos, 404, CPF duplicado, score 404 e score 503. |
| `jmeter/02-carga.jmx` | **Carga.** Simula vários usuários ao mesmo tempo por alguns minutos (listar, buscar e, de vez em quando, cadastrar e apagar) e falha se alguma resposta passar do limite de tempo. |
| `jmeter/dados/clientes.csv` | Nomes e e-mails usados para montar clientes realistas. O CPF é sorteado a cada execução. |
| `jmeter/rodar.sh` | Atalho para rodar tudo pelo terminal e gerar o relatório em HTML. |

**Instalação do JMeter:** baixe em https://jmeter.apache.org/download_jmeter.cgi, descompacte e (se quiser) coloque a pasta `bin` no PATH. É preciso ter Java instalado.

**Abrindo na tela (bom para começar):**

1. Abra o JMeter (`bin/jmeter.bat` no Windows) e use *File → Open* para carregar um `.jmx`.
2. Clique no botão verde ▶.
3. Veja os resultados em *Resultados (View Results Tree)* e *Resumo (Summary Report)*.

**Pelo terminal (recomendado para carga):**

```bash
./jmeter/rodar.sh funcional
./jmeter/rodar.sh carga -Jusers=50 -Jduracao=120
```

O script gera `jmeter/relatorio/<teste>/index.html`, avisa se alguma requisição falhou e sai com erro nesse caso. Sem o script, o comando direto é:

```bash
cd jmeter
jmeter -n -t 02-carga.jmx -l resultados/carga.jtl -e -o relatorio/carga -Jusers=50
```

**Parâmetros** (use `-Jnome=valor`):

| Parâmetro | Padrão | Vale para | Significado |
|---|---|---|---|
| `host` | `localhost` | os dois | Onde a API está |
| `port` | `8080` | os dois | Porta da API |
| `users` | `1` funcional / `20` carga | os dois | Quantos usuários simultâneos |
| `ramp` | `1` funcional / `10` carga | os dois | Segundos para todos os usuários entrarem |
| `loops` | `1` | funcional | Quantas vezes cada usuário repete o fluxo |
| `duracao` | `60` | carga | Duração do teste, em segundos |
| `limite_ms` | `1000` | carga | Tempo máximo aceitável por resposta |
| `pausa_ms` | `200` | carga | Pausa entre requisições de cada usuário |

**Dicas:**
- Comece pelo funcional. Se ele não passar, não adianta medir carga.
- Rode a carga em etapas (`-Jusers=10`, depois `50`, depois `100`) e observe quando o tempo de resposta começa a piorar.
- O teste de erros usa os CPFs `00000000001` e `99999999999` (ver tabela do mock). Se uma execução for interrompida no meio, esses clientes podem ficar no banco e a próxima rodada dará `409`. Para limpar tudo: `docker compose down -v`.
- O cenário do score lento (`88888888888`, 10 s) ficou de fora de propósito, porque deixaria o teste funcional demorado. Teste-o à mão com `curl`.
- O teste de carga cria clientes de verdade no banco, mas apaga os que cria.

## Como o projeto está organizado

```
customer-service/
├── pom.xml                      dependências do Maven
├── docker-compose.yml           sobe PostgreSQL, RabbitMQ, mock do score e a aplicação
├── Dockerfile                   empacota a aplicação
├── mock-score/mappings/         respostas do score de mentira
├── jmeter/                      planos de teste do JMeter (+ dados e script)
└── src/
    ├── main/java/com/ceg/customers/
    │   ├── CustomersApplication.java
    │   ├── customer/            cadastro: Controller, Service, Repository, entidade e DTOs
    │   ├── score/               ScoreClient: consulta o serviço externo
    │   └── messaging/           RabbitMQ: configuração, publicador e listener
    ├── main/resources/application.yml    configurações
    └── test/                    testes unitários
```

Para entender o código, leia nesta ordem: `CustomerController` → `CustomerService` → `ScoreClient` → `messaging/`.

### Configuração

Tudo tem valor padrão em `src/main/resources/application.yml`. Para mudar sem editar o arquivo, use variáveis de ambiente:
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`, `SCORE_BASE_URL`.

## O que foi simplificado

Esta versão tira o que não é essencial para entender e usar o serviço:

| Saiu | Por quê / como era |
|---|---|
| Login com Keycloak e JWT (perfis USER/ADMIN) | A API agora é aberta, sem token. Mais simples de testar com curl e JMeter. |
| Redis (cache do score), Resilience4j (retry e circuit breaker) | O `ScoreClient` só usa timeout e responde 503 se algo falhar. |
| Prometheus, Dynatrace, Actuator, métricas | Observabilidade avançada. |
| Kubernetes (`k8s/`), testes k6 e documentos de performance/troubleshooting | Substituídos pelos planos do JMeter. |
| Flyway (migrações do banco) | O Hibernate cria as tabelas sozinho (`ddl-auto: update`). |
| Fila de mensagens com falha (DLQ), consumidor idempotente com tabela `processed_events`, `@Version` | Trocar o status é naturalmente repetível, então não precisa de tudo isso. |
| Testes de integração com Testcontainers | Exigiam Docker para rodar `mvn verify`. Os planos do JMeter cobrem esse papel. |
| Threads virtuais, páginas em formato RFC 9457 (`ProblemDetail`), pasta `.idea/` | Detalhes que não mudam o comportamento essencial. |
| Java 25 (LTS), que roda em qualquer JDK 21 ou mais novo. |
