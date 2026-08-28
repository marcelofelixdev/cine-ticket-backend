# 1. Resumo Executivo

O projeto tem uma base didática organizada, com separação entre controller/service/repository, DTOs, Flyway, autenticação JWT, RBAC administrativo, proteção de IDOR em ingressos e uma restrição PostgreSQL efetiva contra overbooking. A auditoria confirmou, porém, que o sistema original não estava apto a produção: aprovava pagamentos simulados, tinha corrida entre pagamento/cancelamento, Compose inviável, perfil/configuração inseguros, baixa cobertura dos fluxos críticos e nenhuma garantia operacional para eventos, rollback, backup ou incidentes.

Nesta auditoria foram implementadas correções locais: transições de ticket protegidas por lock/update condicional; reembolso apenas de pagamento aprovado; cancelamento emergencial idempotente; bloqueio de venda para sessão inativa/passada; serialização da criação de sessões por sala; simulador de pagamento desabilitado e vendas bloqueadas por padrão em produção; JWT endurecido; validações/limites; CORS externo; health probes; correlação de logs; métricas Prometheus; Compose corrigido; CI/Dependabot; atualização de dependências; migration V4 com constraints/índices; TTL/limites de filas; e ampliação de 4 para 13 testes.

Mesmo após essas correções, produção permanece bloqueada. Não existe gateway de pagamento real, outbox/reconciliação, consumidor de reembolso confirmado, TLS/secret manager/IAM, CD/rollback, backup/restore testado, observabilidade operacional completa ou testes PostgreSQL/RabbitMQ sob concorrência real.

Escopo comprovado: revisão estática de todo o repositório, histórico de rastreamento de `.env`, `mvn verify`, build do JAR e `docker-compose config --quiet`. Não foi possível confirmar execução integrada com PostgreSQL/RabbitMQ nem build/runtime da imagem porque o daemon Docker não estava ativo. Também não foi possível confirmar cloud, DNS, TLS, firewall, backups, dashboards, alertas ou runbooks com as evidências disponíveis.

# 2. Nota Geral do Projeto

Nota: **5,7/10**

Nível de maturidade: **FUNCIONAL, MAS ARRISCADO**

Veredito de produção: **❌ BLOQUEADO PARA PRODUÇÃO**

# 3. Principais Riscos

1. Não há cobrança real; o perfil de produção agora falha fechado e não vende ingressos.
2. Commit no banco e publicação RabbitMQ não são atômicos; pagamentos/reembolsos podem ser perdidos.
3. Reservas `PENDING` não expiram nem são reconciliadas, bloqueando assentos indefinidamente.
4. Token de cartão ainda integra o contrato da mensagem; AMQPS/mTLS e minimização PCI/LGPD não existem.
5. Não há consumidor de reembolso neste projeto nem contrato/SLO externo comprovado.
6. Migração inicial exige papel `postgres`; V4 precisa de preflight em dados existentes.
7. Não há CD, rollback, estratégia de migração compatível, backup/restore testado ou runbook.
8. Testes continuam sem PostgreSQL/RabbitMQ reais, segurança end-to-end e concorrência real.
9. Rate limiting é local por instância e não confiável atrás de proxy/escala horizontal.
10. TLS, IAM, secret manager, rede de produção, alertas e resposta a incidentes não foram implementados.

# 4. Achados Críticos

## AUD-001 — Pagamento real inexistente; produção falha fechada

**ID:** AUD-001  
**Título:** Não existe confirmação autenticada de pagamento  
**Área:** Segurança / pagamentos  
**Ambiente afetado:** Staging e Produção  
**Severidade:** CRÍTICO  
**Nota de risco:** 9,8/10  
**Status:** Confirmado; mitigado localmente, não resolvido funcionalmente  
**Descrição:** O consumidor original aprovava qualquer ticket `PENDING` sem consultar gateway. A correção condiciona o mock ao perfil de desenvolvimento e `payments.enabled=false` bloqueia vendas em produção.  
**Evidência encontrada:** `PaymentConsumer.java:13-35`, `TicketService.java:88-119`, `application-prod.properties:36-38`, `README.md:118-120`.  
**Causa raiz:** Integração de pagamento foi modelada como simulação, não como confirmação de provedor.  
**Impacto técnico:** Sem a mitigação, ingresso gratuito; com a mitigação, venda indisponível em produção.  
**Impacto operacional:** Receita e operação de bilheteria bloqueadas.  
**Impacto de segurança:** Fraude financeira.  
**Cenário de falha:** Ativar o mock em produção permite aprovação sem cobrança.  
**Probabilidade:** Alta  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Integrar gateway homologado; aceitar apenas webhook/resultado autenticado e idempotente, validando `paymentId`, ticket, valor, moeda e estado.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Ou fraude direta, ou impossibilidade de vender.

## AUD-002 — Dual write perde pagamentos e reembolsos

**ID:** AUD-002  
**Título:** Banco e RabbitMQ não participam da mesma garantia transacional  
**Área:** Confiabilidade / arquitetura  
**Ambiente afetado:** Staging e Produção  
**Severidade:** CRÍTICO  
**Nota de risco:** 9,4/10  
**Status:** Confirmado  
**Descrição:** Eventos são publicados por `TransactionalEventListener(AFTER_COMMIT)`. Se o processo ou broker falhar depois do commit, não há registro persistente para retomar a publicação.  
**Evidência encontrada:** `PaymentProducer.java:17-24`, `RefundProducer.java:18-21`.  
**Causa raiz:** Ausência de transactional outbox/inbox e reconciliação.  
**Impacto técnico:** Ticket preso em `PENDING` ou cancelado sem restituição.  
**Impacto operacional:** Suporte manual, divergência financeira e incidentes difíceis de reproduzir.  
**Impacto de segurança:** Possível cobrança sem entrega ou restituição duplicada em recuperações manuais.  
**Cenário de falha:** Commit conclui; Rabbit fica indisponível; callback pós-commit falha e o evento desaparece.  
**Probabilidade:** Média  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Outbox na mesma transação, relay com retry/backoff, publisher confirms/returns, inbox idempotente e reconciliação periódica.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Inconsistência financeira inevitável ao longo do tempo.

# 5. Achados de Segurança

## AUD-003 — Dado de pagamento em fila sem TLS comprovado

**ID:** AUD-003  
**Título:** `cartaoToken` trafega em mensagem durável  
**Área:** Segurança / privacidade  
**Ambiente afetado:** Desenvolvimento, Staging e Produção  
**Severidade:** ALTO  
**Nota de risco:** 8,6/10  
**Status:** Confirmado  
**Descrição:** O token integra `PaymentEventDTO` e é enviado ao RabbitMQ. Foram adicionados TTL e limites, mas o contrato ainda amplia o escopo sensível e não existe AMQPS/mTLS/ACL comprovado.  
**Evidência encontrada:** `PaymentEventDTO.java:5-9`, `TicketService.java:115-119`, `RabbitMQConfig.java:23-36`, `application-prod.properties:18-22`.  
**Causa raiz:** Tokenização/acoplamento ao consumidor interno em vez de referência opaca do provedor.  
**Impacto técnico:** Dado sensível em queue/DLQ.  
**Impacto operacional:** Maior escopo PCI/LGPD e rotação/forense complexos.  
**Impacto de segurança:** Leitura ou replay por identidade comprometida do broker.  
**Cenário de falha:** Credencial Rabbit vazada permite consumir mensagens persistidas.  
**Probabilidade:** Média  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Tokenizar diretamente no provedor, transportar somente `paymentIntentId`, usar AMQPS/mTLS, vhost/ACL mínima e retenção documentada.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Exposição financeira e regulatória.

## AUD-004 — Rate limit não é distribuído nem proxy-safe

**ID:** AUD-004  
**Título:** Limitação local diverge entre réplicas  
**Área:** Segurança / escalabilidade  
**Ambiente afetado:** Staging e Produção  
**Severidade:** ALTO  
**Nota de risco:** 7,8/10  
**Status:** Confirmado; parcialmente mitigado  
**Descrição:** O cache agora possui limite e há namespaces para login/cadastro/compra/PDF, mas os buckets continuam locais e usam `remoteAddr`.  
**Evidência encontrada:** `RateLimitService.java:14-26`, `AuthController.java:32-34`, `UserController.java:20-29`, `TicketController.java:31-40,45-53`.  
**Causa raiz:** Proteção implementada dentro da instância, sem gateway/Redis e sem política de proxy confiável.  
**Impacto técnico:** Restart e múltiplas réplicas resetam/multiplicam limites.  
**Impacto operacional:** Falso bloqueio atrás de proxy ou abuso por rotação de IP/contas.  
**Impacto de segurança:** Brute force, account farming e DoS.  
**Cenário de falha:** Em duas réplicas, o atacante recebe aproximadamente duas vezes a cota.  
**Probabilidade:** Alta  
**Prioridade:** P1 — Alta  
**Recomendação conceitual:** Rate limit no gateway ou storage distribuído, por rota+IP+conta, com proxies confiáveis, métricas e limite global.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Proteção inconsistente sob escala.

## AUD-005 — TLS, IAM e rotação não comprovados

**ID:** AUD-005  
**Título:** Fronteira de rede de produção ausente  
**Área:** Infraestrutura / segurança  
**Ambiente afetado:** Produção  
**Severidade:** ALTO  
**Nota de risco:** 8,0/10  
**Status:** Não foi possível confirmar com as evidências disponíveis  
**Descrição:** O Compose agora liga a API a `127.0.0.1` por padrão, mas não há proxy TLS, certificados, firewall, IAM, secret manager ou política de rotação no repositório.  
**Evidência encontrada:** `docker-compose.yml:45-61`, ausência de IaC/proxy/TLS. `.env` está ignorado e não apareceu rastreado no histórico; seus valores devem ser rotacionados se forem reais.  
**Causa raiz:** Ambiente de produção ainda não foi desenhado.  
**Impacto técnico:** Deploy direto pode expor bearer tokens/AMQP em texto claro.  
**Impacto operacional:** Rotação e resposta a comprometimento não são reproduzíveis.  
**Impacto de segurança:** Roubo de sessão/credencial e movimento lateral.  
**Cenário de falha:** Porta 8080 publicada sem terminador HTTPS.  
**Probabilidade:** Média  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Rede privada, ingress HTTPS/HSTS, AMQPS, secret manager, identidades distintas e menor privilégio.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Comprometimento de credenciais e dados.

## AUD-006 — QR previsível contém PII

**ID:** AUD-006  
**Título:** QR não assinado inclui e-mail  
**Área:** Segurança / privacidade  
**Ambiente afetado:** Todos  
**Severidade:** MÉDIO  
**Nota de risco:** 6,2/10  
**Status:** Confirmado  
**Descrição:** O QR concatena ID sequencial e e-mail, sem assinatura, nonce, expiração ou anti-replay.  
**Evidência encontrada:** `PdfService.java:44-48`.  
**Causa raiz:** QR concebido como texto, não como credencial verificável.  
**Impacto técnico:** Falsificação futura se o leitor confiar no payload.  
**Impacto operacional:** Fraude na entrada e suporte.  
**Impacto de segurança:** Exposição de PII.  
**Cenário de falha:** Terceiro fabrica QR com ID/e-mail conhecidos.  
**Probabilidade:** Média  
**Prioridade:** P1 — Alta  
**Recomendação conceitual:** Identificador aleatório/assinado, payload mínimo sem PII, validação online, uso único e auditoria.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Fraude e vazamento de identidade.

# 6. Achados de Performance

## AUD-007 — Operações em lote não escalam

**ID:** AUD-007  
**Título:** Emergência carrega e bloqueia todos os ingressos da sessão  
**Área:** Performance / banco  
**Ambiente afetado:** Staging e Produção  
**Severidade:** MÉDIO  
**Nota de risco:** 6,5/10  
**Status:** Confirmado  
**Descrição:** O lock garante consistência, mas o cancelamento emergencial materializa todos os tickets numa transação.  
**Evidência encontrada:** `SessionService.java:66-90`, `TicketRepository.java:30-32`.  
**Causa raiz:** Operação síncrona sem paginação/outbox.  
**Impacto técnico:** Locks longos, memória e muitos eventos pós-commit.  
**Impacto operacional:** Timeout justamente durante incidente.  
**Impacto de segurança:** DoS administrativo indireto.  
**Cenário de falha:** Sessão grande é cancelada sob pico e bloqueia consumidores/DB.  
**Probabilidade:** Média  
**Prioridade:** P2 — Planejar  
**Recomendação conceitual:** Update em lote controlado, outbox paginada e worker monitorado.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Degradação sob maior volume.

## AUD-008 — TMDB sem circuit breaker/cache

**ID:** AUD-008  
**Título:** Dependência externa degrada criação de filmes  
**Área:** Performance / resiliência  
**Ambiente afetado:** Staging e Produção  
**Severidade:** MÉDIO  
**Nota de risco:** 5,8/10  
**Status:** Confirmado; parcialmente mitigado  
**Descrição:** Há timeout de 3s e fallback; a chamada foi retirada da transação de banco, mas ainda não há cache, bulkhead, circuit breaker ou métrica específica.  
**Evidência encontrada:** `TmdbClient.java:20-39`, `MovieService.java:29-50`.  
**Causa raiz:** Integração síncrona simples.  
**Impacto técnico:** Threads ficam ocupadas e dados podem ser salvos sem enriquecimento.  
**Impacto operacional:** Latência e falha parcial silenciosa para o usuário.  
**Impacto de segurança:** Limitado.  
**Cenário de falha:** TMDB lento por minutos degrada todo POST de filmes.  
**Probabilidade:** Média  
**Prioridade:** P2 — Planejar  
**Recomendação conceitual:** Cache, circuit breaker/bulkhead, métricas e enriquecimento assíncrono/reconciliável.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Degradação e dados incompletos.

# 7. Achados de Arquitetura

## AUD-009 — Serviço de reembolso não comprovado

**ID:** AUD-009  
**Título:** Há producer/fila, mas nenhum consumidor no projeto  
**Área:** Arquitetura / integração  
**Ambiente afetado:** Staging e Produção  
**Severidade:** ALTO  
**Nota de risco:** 8,4/10  
**Status:** Confirmado no repositório; integração externa não confirmada  
**Descrição:** Reembolsos são publicados, porém o único listener é de pagamento. Não há contrato, owner, idempotency key ou SLO do eventual serviço externo.  
**Evidência encontrada:** `RefundProducer.java:18-21`, `RabbitMQConfig.java:20-21,56-64`, `PaymentConsumer.java:21`.  
**Causa raiz:** Limite entre serviços não foi formalizado.  
**Impacto técnico:** Fila acumula ou evento é consumido sem garantia verificável.  
**Impacto operacional:** Cliente pode não receber restituição.  
**Impacto de segurança:** Duplicidade financeira em redrive manual.  
**Cenário de falha:** Cancelamento conclui e nenhuma aplicação consome `reembolsos.v1.queue`.  
**Probabilidade:** Alta se não houver serviço externo  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Consumidor idempotente ou contrato externo versionado com ownership, autenticação, DLQ, dashboards e reconciliação.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Perda financeira e reputacional.

# 8. Achados de Banco de Dados

## AUD-010 — Reserva pendente não expira

**ID:** AUD-010  
**Título:** `PENDING` bloqueia assento indefinidamente  
**Área:** Banco / confiabilidade  
**Ambiente afetado:** Todos  
**Severidade:** ALTO  
**Nota de risco:** 8,8/10  
**Status:** Confirmado  
**Descrição:** O índice parcial protege overbooking, mas inclui `PENDING`; não há `expires_at`, job ou reconciliação.  
**Evidência encontrada:** `V2__Add_Data_And_Indices.sql:4-6`, `TicketService.java:111-119`.  
**Causa raiz:** Reserva e pagamento não possuem lifecycle temporal.  
**Impacto técnico:** Assento fica indisponível após mensagem perdida/DLQ.  
**Impacto operacional:** Inventário fantasma e perda de venda.  
**Impacto de segurança:** Abuso para bloquear sessões.  
**Cenário de falha:** Bot cria reservas e eventos nunca concluem.  
**Probabilidade:** Alta  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** `reservation_expires_at`, expiração atômica, reconciliação e limites de reservas por usuário/sessão.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Indisponibilidade lógica do inventário.

## AUD-011 — Migrations exigem preflight e usuário privilegiado

**ID:** AUD-011  
**Título:** V1 fixa owner `postgres`; V4 pode detectar dados inválidos  
**Área:** Banco / deploy  
**Ambiente afetado:** Staging e Produção  
**Severidade:** ALTO  
**Nota de risco:** 8,2/10  
**Status:** Confirmado  
**Descrição:** V1 contém `OWNER TO postgres` e timeouts ilimitados. V4 corretamente adiciona checks/índices, mas falhará de forma segura se já houver duplicidade case-insensitive ou valores inválidos.  
**Evidência encontrada:** `V1__Create_Tables.sql:10-12,37,67,93,121,149,179,212,240,266`; `V4__Add_Integrity_Constraints_And_Query_Indexes.sql:1-17`.  
**Causa raiz:** V1 foi gerada por dump; não existe processo expand/contract/preflight.  
**Impacto técnico:** Bootstrap em banco gerenciado pode falhar; migration pode aguardar lock.  
**Impacto operacional:** Deploy interrompido sem rollback de schema.  
**Impacto de segurança:** Usuário de migration excessivamente privilegiado.  
**Cenário de falha:** Staging usa role restrita e V1 tenta transferir ownership.  
**Probabilidade:** Média  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Baseline declarativa sem owner, role exclusiva de migration, timeouts finitos, preflight/backup e ensaio em cópia real. Não alterar checksum de V1 já aplicada sem estratégia.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Deploy bloqueado ou lock prolongado.

## AUD-012 — Tempo e duração não são snapshots estáveis

**ID:** AUD-012  
**Título:** `LocalDateTime` e duração mutável alteram regras retroativamente  
**Área:** Modelagem  
**Ambiente afetado:** Todos  
**Severidade:** ALTO  
**Nota de risco:** 7,4/10  
**Status:** Confirmado  
**Descrição:** Sessão usa timestamp sem fuso e calcula fim com a duração atual do filme. Editar o catálogo pode criar conflito retroativo.  
**Evidência encontrada:** `V1__Create_Tables.sql:172`, `Session.java:22-26`, `SessionService.java:47-54`, `MovieService.java:67-75`.  
**Causa raiz:** Catálogo e programação compartilham atributo mutável.  
**Impacto técnico:** Agenda muda semanticamente sem migration explícita.  
**Impacto operacional:** Sessões sobrepostas e cancelamentos no horário errado em regiões distintas.  
**Impacto de segurança:** Limitado.  
**Cenário de falha:** Duração do filme aumenta após criação de sessões adjacentes.  
**Probabilidade:** Média  
**Prioridade:** P1 — Alta  
**Recomendação conceitual:** Persistir `starts_at`/`ends_at` como `TIMESTAMPTZ` e snapshot de duração por sessão.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Corrupção semântica da grade.

# 9. Achados de Infraestrutura e DevOps

## AUD-013 — Deploy, rollback e continuidade ausentes

**ID:** AUD-013  
**Título:** CI existe, mas não há CD/rollback/backup/restore  
**Área:** DevOps / continuidade  
**Ambiente afetado:** Staging e Produção  
**Severidade:** ALTO  
**Nota de risco:** 8,3/10  
**Status:** Confirmado  
**Descrição:** CI agora usa wrapper, permissões mínimas e testes; Docker roda não-root e possui probes. Não existem promoção de artefato, ambientes protegidos, canary/rollback, backup, restore testado ou runbook.  
**Evidência encontrada:** `.github/workflows/maven.yml:1-27`, `Dockerfile:1-23`, ausência de CD/IaC/runbooks.  
**Causa raiz:** Projeto ainda orientado a desenvolvimento local.  
**Impacto técnico:** Mudança não pode ser revertida de forma comprovada.  
**Impacto operacional:** MTTR alto e risco de perda de dados.  
**Impacto de segurança:** Deploy e segredos sem governança.  
**Cenário de falha:** Migration falha parcialmente durante release e não há procedimento ensaiado.  
**Probabilidade:** Alta ao iniciar produção  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Artefato imutável, staging fiel, gates, smoke/canary, rollback, PITR e teste periódico de restauração.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Incidente prolongado e perda irrecuperável.

## AUD-014 — Supply chain parcialmente controlada

**ID:** AUD-014  
**Título:** Sem SBOM/SCA/assinatura e pins imutáveis  
**Área:** Dependências / supply chain  
**Ambiente afetado:** Todos  
**Severidade:** MÉDIO  
**Nota de risco:** 6,6/10  
**Status:** Confirmado; parcialmente mitigado  
**Descrição:** Spring Boot foi atualizado de 3.3.4 (fora de suporte OSS) para 3.5.16; java-jwt para 4.6.0 e OpenPDF para 2.2.5. Dependabot foi adicionado, mas imagens/Actions usam tags mutáveis e não há SBOM, SCA, assinatura ou attestation.  
**Evidência encontrada:** `pom.xml`, `.github/dependabot.yml`, `Dockerfile:2,9`, `docker-compose.yml:3,22`. A linha 3.3 encerrou suporte OSS em 3.3.13 segundo [Spring](https://spring.io/blog/2025/06/19/spring-boot-3-3-13-available-now/); versões estáveis atuais constam na [documentação Spring Boot](https://docs.spring.io/spring-boot/cli/).  
**Causa raiz:** Pipeline de supply chain mínimo.  
**Impacto técnico:** Vulnerabilidade/artefato pode entrar sem gate ou reprodução.  
**Impacto operacional:** Patch e investigação lentos.  
**Impacto de segurança:** Comprometimento de build/dependência.  
**Cenário de falha:** Tag de imagem muda entre staging e produção.  
**Probabilidade:** Média  
**Prioridade:** P1 — Alta  
**Recomendação conceitual:** Pin por digest/SHA, checksum do wrapper, CycloneDX, SCA/container/secret scan e assinatura/attestation.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Build não reproduzível e exposição desconhecida.

# 10. Achados de Confiabilidade e Resiliência

As transições concorrentes mais perigosas foram corrigidas: cancelamento obtém lock do ticket; aprovação é `UPDATE ... WHERE PENDING AND valor`; emergência bloqueia tickets e é idempotente; reembolso só nasce de `APPROVED`; criação de sessões serializa por sala. Permanecem **AUD-002**, **AUD-009** e **AUD-010**, que exigem outbox, consumidor/reconciliação e expiração de reserva.

# 11. Achados de Observabilidade

## AUD-015 — Métricas existem, operação ainda não

**ID:** AUD-015  
**Título:** Sem dashboards, alertas, tracing e métricas financeiras  
**Área:** Observabilidade  
**Ambiente afetado:** Staging e Produção  
**Severidade:** MÉDIO  
**Nota de risco:** 6,7/10  
**Status:** Confirmado; parcialmente mitigado  
**Descrição:** Foram adicionados Prometheus, health probes e correlation ID. Não existem collector, dashboard, alertas, tracing, retenção ou SLO.  
**Evidência encontrada:** `application.properties:5-13`, `CorrelationIdFilter.java`, ausência de configuração externa de observabilidade.  
**Causa raiz:** Instrumentação sem plataforma operacional.  
**Impacto técnico:** Diagnóstico ainda depende de logs locais.  
**Impacto operacional:** Incidente às 3h não tem alerta, contexto ou procedimento.  
**Impacto de segurança:** Detecção tardia de abuso.  
**Cenário de falha:** DLQ/tickets pendentes crescem sem alarme.  
**Probabilidade:** Alta  
**Prioridade:** P1 — Alta  
**Recomendação conceitual:** Métricas de fila/DLQ, `PENDING` por idade, falhas de pagamento/reembolso, Hikari/TMDB; dashboards, alertas, OTel e SLO/runbook.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Alto MTTR e falhas silenciosas.

# 12. Achados de Testes

## AUD-016 — Suíte não representa produção

**ID:** AUD-016  
**Título:** H2 sem Flyway/Rabbit e filtros desligados  
**Área:** QA / regressão  
**Ambiente afetado:** Todos  
**Severidade:** ALTO  
**Nota de risco:** 7,9/10  
**Status:** Confirmado; parcialmente mitigado  
**Descrição:** A suíte passou com 13 testes e agora cobre JWT, mock de pagamento, cancelamento/reembolso e emergência. O contexto ainda usa H2, desliga Flyway/Rabbit e o controller test remove filtros.  
**Evidência encontrada:** `CineTicketApplicationTests.java:6-16`, `TicketControllerTest.java:18-19`, testes adicionados em `messaging`, `security` e `service`.  
**Causa raiz:** Ausência de Testcontainers e estratégia de integração/E2E.  
**Impacto técnico:** SQL PostgreSQL, migrations, locks e contratos Rabbit podem quebrar com CI verde.  
**Impacto operacional:** Regressões aparecem após deploy.  
**Impacto de segurança:** RBAC/JWT/IDOR não são testados de ponta a ponta.  
**Cenário de falha:** V4 falha em PostgreSQL real e H2 não detecta.  
**Probabilidade:** Alta  
**Prioridade:** P0 — Imediata  
**Recomendação conceitual:** Testcontainers PostgreSQL/Rabbit, Flyway real, concorrência, segurança com filtros, contrato/webhook, Docker smoke, carga e gate de cobertura relevante.  
**Esforço estimado:** Alto  
**Risco de não corrigir:** Falsa confiança no pipeline.

# 13. Achados de Manutenibilidade e Dívida Técnica

## AUD-017 — Contratos HTTP e erros ainda inconsistentes

**ID:** AUD-017  
**Título:** `BusinessException` concentra 400 e PageImpl não é contrato estável  
**Área:** APIs / manutenibilidade  
**Ambiente afetado:** Todos  
**Severidade:** MÉDIO  
**Nota de risco:** 5,9/10  
**Status:** Confirmado; parcialmente mitigado  
**Descrição:** 401 e conflito de integridade passaram a ter handlers, mas ausência, autorização e regra de domínio continuam pouco tipadas; páginas serializam `PageImpl`.  
**Evidência encontrada:** `ResourceExceptionHandler.java`, `BusinessException.java`, controllers paginados.  
**Causa raiz:** Modelo único de exceção e exposição direta de tipo do framework.  
**Impacto técnico:** Clientes dependem de semântica/JSON instáveis.  
**Impacto operacional:** Alertas e retry de clientes classificam erros incorretamente.  
**Impacto de segurança:** 403/404 inconsistentes podem facilitar enumeração ou mascarar abuso.  
**Cenário de falha:** Recurso inexistente retorna 400; upgrade altera JSON de página.  
**Probabilidade:** Alta  
**Prioridade:** P2 — Planejar  
**Recomendação conceitual:** Problem Details, exceções 404/409/422/403 tipadas e DTO de paginação versionado.  
**Esforço estimado:** Médio  
**Risco de não corrigir:** Breaking changes e diagnóstico ruim.

# 14. Riscos de Mudanças Futuras

- Alterar `TicketStatus` exige atualizar enum Java, check de V1 e índice parcial de V2.
- Alterar capacidade da sala agora é bloqueado; uma futura reconciliação deve preservar assentos já referenciados.
- Alterar duração do filme afeta a grade existente até haver snapshot `ends_at`.
- Trocar JWT para cookie exige reavaliar CSRF, CORS credentials e SameSite.
- Ativar `PAYMENTS_ENABLED` antes do gateway/outbox reabre risco financeiro.
- Alterar V1 já aplicada muda checksum Flyway; usar baseline/nova migration.
- Mudar formato de `PageImpl` quebra consumidores sem contrato explícito.
- Escalar horizontalmente sem rate limiter/outbox distribuídos cria divergência entre réplicas.

# 15. Problemas que Podem Bloquear Produção

- Gateway real e confirmação autenticada/idempotente inexistentes.
- Outbox/inbox, reconciliação e expiração de reservas inexistentes.
- Serviço/contrato de reembolso não comprovado.
- TLS, secret manager, IAM, rede e rotação não definidos.
- V1/V4 não ensaiadas em PostgreSQL real com cópia de dados.
- Backup/restore, rollback e runbooks inexistentes.
- Testes de integração/concorrência reais inexistentes.
- Alertas e SLOs operacionais inexistentes.

# 16. Melhorias Recomendadas

## Curto prazo

- Integrar gateway real em sandbox; manter produção fail-closed até concluir.
- Implementar outbox/inbox, idempotência e expiração de `PENDING`.
- Criar Testcontainers PostgreSQL/Rabbit e ensaiar Flyway V1–V4.
- Corrigir baseline/owner/timeouts de migration sem quebrar checksums existentes.
- Definir consumidor de reembolso, DLQ/redrive e reconciliação.
- Rotacionar segredos locais se forem reais e configurar TLS/secret manager.

## Médio prazo

- Pipeline CD com staging fiel, smoke/canary/rollback e artefato imutável.
- Dashboards/alertas/SLO para DB, Rabbit, DLQ, pagamentos e reservas antigas.
- Rate limiter distribuído e gateway proxy-safe.
- Snapshot temporal da sessão com `TIMESTAMPTZ` e QR assinado sem PII.
- Problem Details e contrato de paginação estável.

## Longo prazo

- PITR e exercícios regulares de restore/DR.
- OTel/tracing e capacidade/custo testados em 10x/100x.
- SBOM, assinatura, attestation e políticas de supply chain.
- Arquivamento/retenção/exclusão LGPD documentados.

# 17. Prioridades P0, P1, P2 e P3

- **P0:** AUD-001, AUD-002, AUD-003, AUD-005, AUD-009, AUD-010, AUD-011, AUD-013, AUD-016.
- **P1:** AUD-004, AUD-006, AUD-012, AUD-014, AUD-015.
- **P2:** AUD-007, AUD-008, AUD-017.
- **P3:** limpeza de warnings MapStruct/Lombok, padronização de estilo/imports e documentação adicional de setup.

# 18. Notas por Categoria

| Categoria | Nota | Justificativa resumida |
|---|---:|---|
| Arquitetura | 6,3 | Camadas claras e locks corrigidos; falta outbox e contratos externos. |
| Segurança | 6,2 | RBAC/IDOR/JWT melhorados; pagamento, TLS/IAM e QR pendentes. |
| Performance | 6,4 | Paginação limitada e TMDB fora da transação; lotes/caches pendentes. |
| Escalabilidade | 5,0 | Rate limit local, operações em lote e dependência de instância. |
| Confiabilidade | 5,5 | Corridas locais corrigidas; dual write e reserva eterna permanecem. |
| Resiliência | 4,6 | Timeouts básicos; sem outbox, reconciliação, circuit breaker ou DR. |
| Banco de dados | 6,5 | FKs, overbooking, checks/índices; migrations/timezone precisam trabalho. |
| APIs | 6,1 | DTOs/validação; erros e paginação ainda pouco estáveis. |
| Tratamento de erros | 5,7 | 401/409 melhorados; faltam tipos 403/404/422 e Problem Details. |
| Testes | 5,2 | 13 testes verdes; baixa fidelidade a PostgreSQL/Rabbit/segurança real. |
| Observabilidade | 5,4 | Health, Prometheus e correlation ID; sem plataforma/alertas/SLO. |
| CI/CD | 5,0 | CI/Dependabot presentes; CD/rollback/gates de segurança ausentes. |
| Infraestrutura | 4,8 | Compose corrigido e não-root; produção/IaC/TLS/backup ausentes. |
| Gestão de segredos | 5,8 | `.env` ignorado e injeção reduzida; sem secret manager/rotação. |
| Dependências | 7,0 | Stack atualizada e automatizada; falta SCA/SBOM/pins imutáveis. |
| Manutenibilidade | 6,5 | Estrutura simples; contratos implícitos e warnings técnicos persistem. |
| Capacidade de mudança | 5,9 | Flyway e testes ajudam; estados/schema/filas são acoplados. |
| Preparação para produção | 4,5 | Bloqueada pelos fluxos financeiro e operacional. |

# 19. Cenários de Falha Mais Prováveis

1. **Pico 10x/100x:** pool/threads/Rabbit e rate limit por instância saturam; emergência em lote amplia locks.
2. **Banco lento:** timeout de aquisição é curto, mas não há circuit breaker/bulkhead; erros crescem em cascata.
3. **Rabbit indisponível:** commit conclui e evento pós-commit é perdido; ticket fica `PENDING`.
4. **Deploy com falha:** CI detecta compilação/testes, mas não há canary, rollback ou smoke integrado.
5. **Migration falha:** V1 pode exigir owner inexistente; V4 pode encontrar dado inválido; falta preflight/restore.
6. **Credencial comprometida:** rotação não é automatizada; JWT vale até duas horas e não há revogação.
7. **Usuário malicioso:** RBAC/IDOR resistem melhor, mas account farming e reservas pendentes podem bloquear inventário.
8. **Processo interrompido:** locks preservam transação local; dual write continua vulnerável após commit.
9. **Falha silenciosa:** DLQ/fila/reembolso podem parar sem alertas configurados.
10. **Incidente às 3h:** probes e correlation ID ajudam, mas faltam alertas, dashboards, ownership e runbooks.

# 20. Veredito Final

**❌ BLOQUEADO PARA PRODUÇÃO.**

O projeto ficou substancialmente mais seguro e coerente após as correções implementadas e verificadas, mas ainda não é um sistema financeiro-operacional de produção. A barreira principal é objetiva: pagamentos/reembolsos não possuem integração real e garantia de entrega/reconciliação; reservas podem ficar eternamente pendentes; migrations não foram ensaiadas em PostgreSQL real; e não existe envelope operacional de TLS, deploy/rollback, backup/restore, alertas e resposta a incidentes.

Desenvolvimento local: utilizável, com o simulador explicitamente limitado ao perfil dev.  
Staging: somente após Testcontainers/ambiente real, gateway sandbox, outbox e observabilidade.  
Produção: não ativar `PAYMENTS_ENABLED` até fechar todos os P0 e realizar teste de carga, falha, migration e restore.

Validação final executada: `mvn verify` — **13 testes, 0 falhas, BUILD SUCCESS**; `docker-compose config --quiet` — **válido**. Build/runtime Docker integrado: **não foi possível confirmar com as evidências disponíveis**, pois o daemon não estava ativo.
