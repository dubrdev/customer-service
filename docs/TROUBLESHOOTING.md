# Troubleshooting e análise de causa raiz (RCA)

## Método
1. **Sintoma e impacto**: o que o usuário vê, desde quando, quantos afetados, qual SLO quebrou.
2. **Mitigar primeiro** (rollback, escalar, desligar feature flag, abrir/forçar circuit breaker), investigar depois.
3. **Linha do tempo**: o que mudou? (deploy, config, tráfego, dependência, certificado, volume de dados).
4. **Sinais**: métricas (RED/USE) → traces (qual span é lento) → logs (por que) com o mesmo `traceId`.
5. **Hipóteses → testes** curtos para refutar; evitar confirmar a primeira ideia.
6. **Causa raiz** com os *5 porquês* (processo, não pessoa); corrigir a causa, não só o sintoma.
7. **Post-mortem sem culpados**: ação corretiva, preventiva, alerta/teste que teria detectado.

## Guia rápido por sintoma
| Sintoma | Onde olhar | Causas comuns |
|---|---|---|
| **503 em `/score`** | `resilience4j_circuitbreaker_state`, logs "Score provider unavailable", health do provedor | provedor fora/lento, timeout curto, breaker aberto (volta sozinho em 10 s) |
| **401 em massa** | log do resource server, JWKS acessível?, relógio dos nós | token expirado, JWKS inacessível, `iss`/chave rotacionada |
| **403** | claim `realm_access.roles` no token | role não atribuída, token de outro realm |
| **DLQ crescendo** | UI do RabbitMQ, payload da mensagem, log do consumidor | cliente inexistente, payload inválido, bug no processamento |
| **Fila cresce (não é DLQ)** | taxa de consumo × produção, `customer_status_change_events_total` | consumidor lento/parado, banco lento |
| **Latência alta** | trace (Dynatrace), `hikaricp_connections_pending`, GC | pool esgotado, query sem índice, GC, throttling de CPU |
| **Pod reiniciando** | `kubectl describe pod`, `kubectl logs --previous` | OOMKilled (limite de memória), probe falhando, dependência indisponível |
| **Evento não publicado** | métrica `customer_events_published_total{result="failed"}` | RabbitMQ indisponível no pós-commit (reprocessar; evoluir para outbox) |

## Comandos úteis
```bash
kubectl get pods; kubectl describe pod <pod>; kubectl logs <pod> --previous
kubectl top pod; kubectl get hpa; kubectl rollout undo deployment/customer-service
curl -s localhost:8080/actuator/health | jq
curl -s localhost:8080/actuator/prometheus | grep -E 'hikaricp|resilience4j|customer_'
jcmd <pid> Thread.print      # dump de threads;  jcmd <pid> GC.heap_dump /tmp/heap.hprof
```
