# RE-AUDITORIA — Estado Atual do CineTicket Backend

**Data:** 2026-08-23  
**Referência:** [Auditoria original](file:///C:/Users/Marcelo/.gemini/antigravity/brain/c80e06aa-2408-467d-b696-8ef00be3ae32/audit_report.md) (2026-08-22)  
**Arquivos modificados:** 21 | **Arquivos novos:** 3

---

## Resumo da Evolução

| Métrica | Antes | Agora | Δ |
|---|---|---|---|
| **Nota Geral** | 2.8/10 | **4.5/10** | +1.7 ✅ |
| **Achados Corrigidos** | 0 | **13 totalmente + 4 parcialmente** | — |
| **Achados Críticos (P0)** | 8 | **2 restantes** | -6 ✅ |
| **Achados Altos (P1)** | 16 | **10 restantes** | -6 ✅ |
| **Spring Boot** | 4.1.0 (inexistente) | **3.3.4** (estável) | ✅ |
| **Migrations** | Nenhuma | **Flyway + V1** | ✅ |
| **Testes** | 2 (0 controller) | **4 (1 controller)** | ✅ |

> [!TIP]
> Evolução significativa! Os 5 achados CRÍTICOS de segurança mais graves foram endereçados. O projeto agora tem uma base mais sólida para continuar evoluindo.

---

## ✅ Achados CORRIGIDOS

### AUD-001 — Escalação de Privilégios via Registro Público ✅ CORRIGIDO

**O que mudou:**
- [`UserRequestDTO.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/dto/request/UserRequestDTO.java) — `roleIds` **removido** do record. Adicionou `@Email` no campo email.
- [`UserService.java:42-44`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/UserService.java#L42-L44) — Atribui `ROLE_USER` por padrão via `roleRepository.findByNome("ROLE_USER")`.
- [`RoleRepository.java:9`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/RoleRepository.java#L9) — Adicionou `findByNome(String nome)`.

**Avaliação:** Correção completa e bem implementada. A vulnerabilidade mais grave do sistema foi eliminada.

---

### AUD-002 — Race Condition de Overbooking ✅ CORRIGIDO

**O que mudou:**
- [`SeatRepository.java:9-11`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/SeatRepository.java#L9-L11) — Novo método `findByIdWithLock()` com `@Lock(PESSIMISTIC_WRITE)`.
- [`TicketService.java:59-76`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L59-L76) — Agora primeiro trava a cadeira com lock pessimista, depois verifica se já foi vendida.

**Avaliação:** Correção sólida via lock pessimista. O check-then-act agora é feito dentro de uma transação com lock, eliminando a race condition.

> [!NOTE]
> Para completar a proteção, ainda seria recomendável adicionar um partial unique index no banco: `CREATE UNIQUE INDEX ON tb_ticket(session_id, seat_id) WHERE status_pagamento IN ('APPROVED', 'PENDING')`. Isso daria uma segunda camada de proteção (defense-in-depth). A migration V1 não inclui este index.

---

### AUD-003 — JWT Secret Hardcoded em Produção ✅ CORRIGIDO

**O que mudou:**
- [`application-prod.properties:22`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-prod.properties#L22) — Agora é `api.security.token.secret=${JWT_SECRET}` **sem fallback**.

**Avaliação:** Correção completa. Se `JWT_SECRET` não for definido, a aplicação falhará na inicialização ao invés de usar uma chave pública.

---

### AUD-005 — Consumer Não-Idempotente ✅ PARCIALMENTE CORRIGIDO

**O que mudou:**
- [`PaymentConsumer.java:27-30`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L27-L30) — Verifica `ticket.getStatus() != TicketStatus.PENDING` antes de processar. Se o ticket já foi cancelado, o processamento é ignorado com log.
- [`PaymentConsumer.java:25`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L25) — Usa `AmqpRejectAndDontRequeueException` para ticket inexistente → vai direto para DLQ.
- [`PaymentConsumer.java:22`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L22) — Token de cartão **não é mais logado** ✅ (AUD-010 também corrigido).
- Usa `@Slf4j` em vez de `System.out.println`.

**Problema residual:** Linha 44 faz `throw new RuntimeException("Erro ao processar pagamento", e)` no catch genérico. Sem `AmqpRejectAndDontRequeueException`, o RabbitMQ **fará requeue infinito** para falhas genéricas. Deveria ser `AmqpRejectAndDontRequeueException` também.

---

### AUD-006 — IDOR no Download de PDF ✅ CORRIGIDO

**O que mudou:**
- [`TicketService.java:39-54`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L39-L54) — Método `downloadPdf()` movido do controller para o service. Verifica `ticket.getUser().getId().equals(loggedUser.getId())` antes de gerar o PDF.
- [`TicketController.java:48-59`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java#L48-L59) — Controller agora delega para `ticketService.downloadPdf(id)`.

**Avaliação:** Correção completa e bem estruturada. Violação de camadas (AUD-019) do download de PDF também resolvida.

---

### AUD-007 — RBAC Faltando em Sessões ✅ CORRIGIDO

**O que mudou:**
- [`SecurityConfig.java:44-45`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/SecurityConfig.java#L44-L45) — `POST /api/v1/sessions` e `PUT /api/v1/sessions/**` agora requerem `ROLE_ADMIN`.

**Avaliação:** Correção completa.

---

### AUD-010 — Log de Dados Sensíveis de Cartão ✅ CORRIGIDO

**O que mudou:**
- [`PaymentConsumer.java:22`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L22) — Loga apenas `evento.ticketId()`, sem `cartaoToken`.

---

### AUD-015 — TmdbClient sem Timeout ✅ CORRIGIDO

**O que mudou:**
- [`TmdbClient.java:20-22`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/client/TmdbClient.java#L20-L22) — `SimpleClientHttpRequestFactory` com `connectTimeout(3s)` e `readTimeout(3s)`.

**Avaliação:** Correção completa. Threads não ficarão mais bloqueadas indefinidamente.

---

### AUD-016 — Memory Leak no RateLimitService ✅ CORRIGIDO

**O que mudou:**
- [`RateLimitService.java:14-16`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/RateLimitService.java#L14-L16) — Migrou de `ConcurrentHashMap` para `Caffeine` cache com `expireAfterAccess(1, TimeUnit.HOURS)`.

**Avaliação:** Correção completa. Entradas agora são evictadas automaticamente após 1 hora de inatividade.

---

### AUD-018 — Spring Boot 4.1.0 (Inexistente) ✅ CORRIGIDO

**O que mudou:**
- [`pom.xml:8`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L8) — `spring-boot-starter-parent:3.3.4` (versão estável e real).

**Avaliação:** Correção completa.

---

### AUD-021 — Ausência de Migrations ✅ CORRIGIDO

**O que mudou:**
- [`pom.xml:92-99`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L92-L99) — Flyway core + flyway-database-postgresql adicionados.
- [`V1__Create_Tables.sql`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/db/migration/V1__Create_Tables.sql) — Migration baseline com DDL completo (dump do PostgreSQL).
- [`application-dev.properties:23`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-dev.properties#L23) — Mudou de `ddl-auto=update` para `ddl-auto=validate` ✅.

**Avaliação:** Excelente implementação. Schema agora é versionado e reproduzível.

---

### AUD-026 — Dockerfile Inseguro ✅ CORRIGIDO

**O que mudou:**
- [`Dockerfile`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/Dockerfile) — Multi-stage build (Maven → JRE). Usuário não-root `spring:spring`. Usa `eclipse-temurin:21-jre-alpine`.

**Avaliação:** Correção completa e bem feita.

---

### AUD-027 — Portas Expostas ao Host ✅ PARCIALMENTE CORRIGIDO

**O que mudou:**
- [`docker-compose.yml:14,27-28`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/docker-compose.yml#L14) — Portas agora em `127.0.0.1:5433:5432`, `127.0.0.1:5672:5672`, `127.0.0.1:15672:15672`.

**Avaliação:** Boa melhoria — portas restritas ao localhost. Nota: PostgreSQL mudou para porta 5433 no host — certifique-se de que a variável `DB_PORT` no `.env` foi atualizada para 5433.

---

### AUD-031 — Infinite Requeue Loop (Poison Pill) ✅ CORRIGIDO

**O que mudou:**
- [`RabbitMQConfig.java:15-19`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/RabbitMQConfig.java#L15-L19) — DLQ (`pagamentos.v1.queue.dlq`) e DLX (`cineticket.dlx`) configurados.
- [`RabbitMQConfig.java:22-27`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/RabbitMQConfig.java#L22-L27) — Queue principal com `x-dead-letter-exchange` e `x-dead-letter-routing-key`.
- [`PaymentConsumer.java:25`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L25) — Usa `AmqpRejectAndDontRequeueException` para enviar mensagens inválidas direto para a DLQ.

**Avaliação:** Correção completa para ticket inexistente. Mas o catch genérico na linha 44 (`RuntimeException`) ainda pode causar requeue — vide observação no AUD-005.

---

### AUD-032 — Dual-Write ✅ CORRIGIDO

**O que mudou:**
- [`TicketService.java:101-105`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L101-L105) — Usa `eventPublisher.publishEvent()` (Spring ApplicationEvent) em vez de chamar `PaymentProducer` diretamente.
- [`PaymentProducer.java:17`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentProducer.java#L17) — `@TransactionalEventListener(phase = AFTER_COMMIT)` — mensagem só é enviada **após o commit** da transação.

**Avaliação:** Excelente correção! Implementação elegante usando Spring Events com `AFTER_COMMIT`. A mensagem nunca será enviada se a transação do banco falhar.

---

### AUD-035 — System.out.println ✅ PARCIALMENTE CORRIGIDO

**O que mudou:**
- `PaymentConsumer` e `PaymentProducer` agora usam `@Slf4j` + `log.info()`/`log.error()`.
- `MovieService` agora usa `@Slf4j` + `log.warn()`.

**Restante:** `ResourceExceptionHandler` ainda usa `e.printStackTrace()` (linha 57).

---

### Novas dependências adicionadas ✅

| Dependência | Arquivo | Finalidade |
|---|---|---|
| `flyway-core` | [`pom.xml:93-95`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L93-L95) | Migrations de banco |
| `flyway-database-postgresql` | [`pom.xml:97-99`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L97-L99) | Suporte PostgreSQL para Flyway |
| `caffeine` | [`pom.xml:102-104`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L102-L104) | Cache com eviction para rate limiter |
| `spring-boot-starter-actuator` | [`pom.xml:107-109`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L107-L109) | Health checks e métricas |
| `h2` (test) | [`pom.xml:118-121`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L118-L121) | Banco em memória para testes |
| `spring-security-test` (test) | [`pom.xml:124-127`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L124-L127) | Testes de segurança |

---

### AUD-023 — Validação Seat-Room ✅ CORRIGIDO

**O que mudou:**
- [`TicketService.java:67-70`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L67-L70) — Valida se `seat.getRoom().getId().equals(session.getRoom().getId())` antes de prosseguir com a compra.

---

### AUD-014 — N+1 Queries ✅ PARCIALMENTE CORRIGIDO

**O que mudou:**
- [`SessionRepository.java:14-15`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/SessionRepository.java#L14-L15) — `@EntityGraph(attributePaths = {"movie", "room", "room.cinema"})` em `findAll()`.
- [`TicketRepository.java:17-18`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/TicketRepository.java#L17-L18) — `@EntityGraph(attributePaths = {"session.movie", "session.room", "seat"})` em `findByUserId()`.

**Restante:** `RoomRepository.findAll()` e `CinemaRepository.findAll()` ainda sem EntityGraph.

---

## ⚠️ Achados AINDA ABERTOS

### P0 — Imediato (2 restantes)

| ID | Achado | Status |
|---|---|---|
| **AUD-004** | Credenciais expostas em docker-compose (`guest:guest` hardcoded nas linhas 30-31) e `.env` com valores reais no workspace | **ABERTO** — As credenciais RabbitMQ `guest:guest` continuam hardcoded em [`docker-compose.yml:30-31`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/docker-compose.yml#L30-L31) |
| **AUD-005** | Consumer catch genérico pode causar requeue infinito | **PARCIAL** — Falta trocar `RuntimeException` por `AmqpRejectAndDontRequeueException` no catch da [linha 44](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L44) |

### P1 — Alta (10 restantes)

| ID | Achado | Status |
|---|---|---|
| AUD-008 | Rate limiting no login | ABERTO |
| AUD-009 | Information disclosure no `ResourceExceptionHandler` — `e.getMessage()` e `e.printStackTrace()` | ABERTO |
| AUD-014 | N+1 em Room e Cinema | PARCIAL |
| AUD-017 | Paginação nas listagens | ABERTO |
| AUD-019 | Controller acessa Repository — `TicketController` ainda importa `TicketRepository` e `PdfService` (linhas 7 e 10) apesar de não usá-los mais | **NOVO** — imports residuais |
| AUD-022 | Sobreposição de sessões (mesmo horário + sala) | ABERTO |
| AUD-033 | SecurityFilter lança exceção para usuários deletados | ABERTO |
| AUD-034 | Cancelamento de emergência sem reembolso | ABERTO |
| AUD-038 | Cobertura de testes — agora 4 testes, mas 8 services sem teste | MELHORIA PARCIAL |

### P2 — Planejar (16 restantes)

| ID | Status |
|---|---|
| AUD-011 (CORS/Headers) | ABERTO |
| AUD-012 (Refresh tokens) | ABERTO |
| AUD-013 (Bloqueio de contas) | ABERTO |
| AUD-024 (Timestamps de auditoria) | ABERTO |
| AUD-025 (Overflow ASCII assentos) | ABERTO |
| AUD-028 (Profile hardcoded compose) | ABERTO |
| AUD-029 (CI/CD) | ABERTO |
| AUD-030 (Limites de recursos containers) | ABERTO |
| AUD-035 (`e.printStackTrace` no ResourceExceptionHandler) | PARCIAL |
| AUD-036 (Health check estático) | PARCIAL — Actuator adicionado ao pom.xml mas sem configuração de endpoints |
| AUD-037 (Métricas e traces) | ABERTO |
| AUD-039 (RuntimeException inconsistente) | ABERTO — `MovieService.findById()` ainda usa `RuntimeException` na [linha 61](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L61) |
| AUD-040 (ResponseStatusException como 500) | ABERTO |
| AUD-041 (Properties corrompido) | ABERTO |
| AUD-042 (tmdbId nunca populado) | ABERTO |
| AUD-043 (Campos omitidos no TicketResponseDTO) | ABERTO |

---

## 🔍 Novos Problemas Identificados

### NOVO-001 — Imports Residuais no TicketController

**Arquivo:** [`TicketController.java:3-4, 7, 10, 15`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java#L3-L10)

Os imports `Ticket`, `TicketStatus`, `TicketRepository`, `PdfService`, `HttpStatus` não são mais usados após a refatoração do `downloadPdf` para o service layer. Devem ser removidos para manter o código limpo.

**Severidade:** BAIXO

---

### NOVO-002 — RabbitMQ: Queue Existente Precisa Ser Deletada/Recriada

Se o RabbitMQ já estava rodando antes da alteração de DLQ, a queue `pagamentos.v1.queue` já existe **sem** os argumentos `x-dead-letter-exchange` e `x-dead-letter-routing-key`. O RabbitMQ **não permite alterar argumentos** de uma queue existente — a aplicação vai lançar uma exceção na inicialização.

**Ação necessária:** Deletar a queue `pagamentos.v1.queue` no management UI (`http://localhost:15672`) antes de reiniciar a aplicação.

**Severidade:** MÉDIO (bloqueante se não tratado)

---

### NOVO-003 — TicketServiceTest Precisa de Atualização no Mock

O [`TicketServiceTest.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/test/java/com/app/cineticket/service/TicketServiceTest.java#L51) declara `@Mock ApplicationEventPublisher eventPublisher` e `@Mock PdfService pdfService` está faltando. O `TicketService` agora depende de `PdfService` (via `this.pdfService`), mas o mock não é declarado no teste. A `@InjectMocks` vai falhar silenciosamente ou injetar null.

**Severidade:** MÉDIO

---

### NOVO-004 — CineTicketApplicationTests Pode Falhar com RabbitMQ

O [`CineTicketApplicationTests.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/test/java/com/app/cineticket/CineTicketApplicationTests.java) desabilita Flyway e configura H2 para o `contextLoads()`, mas **não** desabilita RabbitMQ autoconfiguration. Se o RabbitMQ não estiver rodando durante os testes, o contexto vai falhar ao tentar conectar.

**Ação sugerida:** Adicionar `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration` ou usar `@MockBean RabbitTemplate` / `@MockBean ConnectionFactory`.

**Severidade:** MÉDIO

---

### NOVO-005 — V1 Migration Falta o Partial Unique Index de Overbooking

A migration [`V1__Create_Tables.sql`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/db/migration/V1__Create_Tables.sql) é um dump direto do `pg_dump` sem o partial unique index que protege contra overbooking no nível do banco. O lock pessimista na aplicação é a única proteção. Um `V2__Add_Overbooking_Index.sql` complementaria a defesa:

```sql
CREATE UNIQUE INDEX idx_ticket_no_overbooking 
ON tb_ticket(session_id, seat_id) 
WHERE status_pagamento IN ('APPROVED', 'PENDING');
```

**Severidade:** MÉDIO (recomendação defense-in-depth)

---

### NOVO-006 — V1 Migration Falta INSERT de Roles Default

A migration V1 cria a tabela `tb_role` mas **não insere as roles padrão** (`ROLE_USER`, `ROLE_ADMIN`). O `UserService.create()` faz `roleRepository.findByNome("ROLE_USER").orElseThrow()`, que vai lançar `BusinessException` se a role não existir no banco. Precisa de um `V2` ou `data.sql`:

```sql
INSERT INTO tb_role (nome) VALUES ('ROLE_USER');
INSERT INTO tb_role (nome) VALUES ('ROLE_ADMIN');
```

**Severidade:** ALTO — Sem isso, o registro de novos usuários vai falhar em um banco limpo.

---

## Próximos Passos Recomendados (Top 5)

1. **Criar V2 migration** com INSERT das roles default e partial unique index de overbooking (NOVO-005 + NOVO-006)
2. **Trocar `RuntimeException` por `AmqpRejectAndDontRequeueException`** no catch genérico do PaymentConsumer (AUD-005 residual)
3. **Sanitizar o ResourceExceptionHandler** — remover `e.printStackTrace()` e `e.getMessage()` da resposta (AUD-009)
4. **Adicionar handler para `ResponseStatusException`** no `ResourceExceptionHandler` (AUD-040)
5. **Remover imports não utilizados** do `TicketController` (NOVO-001)
