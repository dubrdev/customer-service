# Testes de estresse e performance

## Tipos de teste (e a pergunta que cada um responde)
| Tipo | Pergunta | Perfil de carga |
|---|---|---|
| **Smoke** | O cenário funciona sob carga mínima? | 1 usuário, 30 s |
| **Load** | Atende o SLO na carga esperada de produção? | Rampa até o pico normal e platô |
| **Stress** | Onde e *como* quebra? Degrada de forma graciosa? | Rampa acima do pico até saturar |
| **Spike** | Sobrevive a picos súbitos (campanha, retry storm)? | Salto abrupto e retorno |
| **Soak (endurance)** | Há vazamento de memória/conexões em horas de uso? | Carga moderada por 2–24 h |
| **Breakpoint** | Qual a capacidade máxima? | Rampa contínua até violar o SLO |
| **Escalabilidade** | O HPA/instâncias adicionais aumentam a vazão proporcionalmente? | Repetir load com 1, 2, 4 réplicas |

## Método
1. **Defina SLOs antes**: ex. p95 < 500 ms, p99 < 1 s, erro < 1 % (estão como *thresholds* no k6).
2. **Modelo de carga realista**: mix de operações (aqui 70 % leitura / 20 % busca / 10 % escrita), dados e think time realistas; ambiente o mais parecido possível com produção.
3. **Modelo aberto vs. fechado**: VUs fixos (fechado) escondem a saturação porque o gerador desacelera junto com o sistema; para estresse use taxa de chegada constante (`ramping-arrival-rate`).
4. **Meça latência em percentis**, nunca só média; olhe vazão, taxa de erro e **saturação** (CPU, GC, heap, pool de conexões, threads, profundidade das filas).
5. **Mude uma variável por vez**, repita para confirmar, e compare com a *baseline*.
6. **Cuidado com o gerador de carga**: monitore a CPU dele; "coordinated omission" distorce latências.

## O que observar neste serviço
* **HikariCP**: `hikaricp_connections_pending` > 0 e tempo de aquisição crescendo ⇒ pool pequeno ou queries lentas.
* **JVM**: pausas de GC, uso de heap pós-GC (tendência de alta no soak = vazamento), threads.
* **RabbitMQ**: profundidade da fila, taxa de *ack*, tamanho da DLQ; consumidores mais lentos que produtores ⇒ escalar consumidores.
* **Score externo**: estado do circuit breaker (`resilience4j_circuitbreaker_state`), taxa de *cache hit* no Redis.
* **Kubernetes**: *throttling* de CPU (limits), OOMKilled, eventos do HPA, tempo de *readiness*.

## Gargalos típicos e tratamento
| Sintoma | Causa provável | Ação |
|---|---|---|
| p99 sobe, CPU baixa | espera em I/O: pool de conexões, provedor lento | ajustar pool/timeout, cache, threads virtuais, circuit breaker |
| CPU 100 % | serialização, regex, N+1, GC | profiling (Dynatrace/async-profiler), índices, paginação |
| Latência "dentada" | pausas de GC | ajustar heap/GC, reduzir alocação |
| Erros só em pico | filas/pools sem limite, *retry storm* | backpressure, limites, backoff com jitter, rate limiting |
| Degrada em horas | vazamento, crescimento de tabela/índice | soak + heap dump, manutenção do banco |

## Já aplicado no projeto
Threads virtuais · pool Hikari configurável · índices (`status`, `lower(name)`) · paginação com limite (máx. 100) · cache Redis do score · timeouts + retry com backoff + circuit breaker · transação sem HTTP externo dentro · consumidores idempotentes · *graceful shutdown* · HPA.

## Como executar
```bash
docker compose up -d --build
k6 run -e SCENARIO=smoke  perf/load-test.js
k6 run -e SCENARIO=load   perf/load-test.js
k6 run -e SCENARIO=stress perf/load-test.js      # também: spike, soak
```
Durante o teste, acompanhe `/actuator/prometheus` (ou o Dynatrace) e a UI do RabbitMQ. Ferramentas equivalentes: JMeter, Gatling, Locust.
