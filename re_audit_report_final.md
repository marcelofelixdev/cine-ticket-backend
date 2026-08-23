# RE-AUDITORIA #4 — Estado Atual do CineTicket Backend

**Data:** 2026-08-23 15:47  
**Referência:** [Auditoria original](file:///C:/Users/Marcelo/.gemini/antigravity/brain/c80e06aa-2408-467d-b696-8ef00be3ae32/audit_report.md) | [Re-auditoria #3](file:///C:/Users/Marcelo/.gemini/antigravity/brain/c80e06aa-2408-467d-b696-8ef00be3ae32/re_audit_report.md)

---

## Evolução Geral

| Métrica | #1 | #2 | #3 | **#4 (Agora)** |
|---|---|---|---|---|
| **Nota Geral** | 2.8 | 4.5 | 5.2 | **6.5** |
| **Achados Originais Corrigidos** | 0/43 | 17/43 | 22/43 | **34/43** |
| **P0 (Crítico)** | 8 | 2 | 1 | **0** ✅ |
| **P1 (Alto)** | 16 | 10 | 7 | **2** |
| **Arquivos Modificados** | — | 21 | 23 | **44** |
| **Arquivos Novos** | — | 3 | 6 | **10** |

> [!TIP]
> **Todos os achados P0 (Críticos) foram eliminados.** O projeto agora está sem vulnerabilidades críticas de segurança. Excelente evolução de 2.8 → 6.5 em 24h!

---

## ✅ Novas Correções Desde a Re-auditoria #3

### AUD-008 — Rate Limiting no Login ✅ CORRIGIDO

**O que mudou:**
- [`AuthController.java:30-35`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/AuthController.java#L30-L35) — Rate limiting por IP usando `rateLimitService.getUserBucket(ip).tryConsume(1)`. Retorna HTTP 429 quando excedido.

**Avaliação:** Boa implementação. Proteção básica contra brute-force.

> [!NOTE]
> Quando a aplicação estiver atrás de um proxy reverso (Nginx/Load Balancer), `request.getRemoteAddr()` retornará o IP do proxy. Será necessário usar `X-Forwarded-For` header. Por agora, para dev/MVP, está correto.

---

### AUD-011 — CORS ✅ PARCIALMENTE CORRIGIDO

**O que mudou:**
- [`CorsConfig.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/CorsConfig.java) — Nova classe configurando CORS para `localhost:3000` (React) e `localhost:4200` (Angular).

**Problema residual:** A [`SecurityConfig.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/SecurityConfig.java) não inclui `.cors(Customizer.withDefaults())` no `SecurityFilterChain`. Sem isso, preflight `OPTIONS` requests serão bloqueados pelo Spring Security antes de chegar ao `CorsConfig`. Para funcionar, adicione na SecurityConfig:

```java
http.cors(Customizer.withDefaults())  // <-- adicionar antes de .csrf()
```

---

### AUD-017 — Paginação ✅ CORRIGIDO

**O que mudou:**
Todos os endpoints de listagem agora usam `Pageable` com `@PageableDefault`:

| Endpoint | Arquivo | Sort |
|---|---|---|
| `GET /api/v1/cinemas` | [`CinemaController:29-31`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/CinemaController.java#L29-L31) | `nome` |
| `GET /api/v1/movies` | [`MovieController:30`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/MovieController.java#L30) | `titulo` |
| `GET /api/v1/rooms` | [`RoomController:28-30`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/RoomController.java#L28-L30) | `nome` |
| `GET /api/v1/sessions` | [`SessionController:27-28`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/SessionController.java#L27-L28) | `horarioInicio` |
| `GET /api/v1/tickets/my-tickets` | [`TicketController:61-68`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java#L61-L68) | `id` |

**Avaliação:** Completa. Ótima adição de `@EntityGraph` nos repositories para evitar N+1.

---

### AUD-022 — Sobreposição de Sessões ✅ CORRIGIDO

**O que mudou:**
- [`SessionService.java:46-58`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/SessionService.java#L46-L58) — Ao criar uma sessão, carrega todas as sessões da sala (`findByRoomId`) e valida se há conflito de horário considerando `duração + 30 minutos` de intervalo.
- [`SessionRepository.java:17`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/SessionRepository.java#L17) — Novo `findByRoomId(Long roomId)`.

**Avaliação:** Lógica de overlap correta. A margem de 30 minutos para limpeza é um bom touch.

> [!NOTE]
> A validação carrega TODAS as sessões da sala (incluindo inativas/passadas). Uma otimização futura seria filtrar: `findByRoomIdAndAtivoTrueAndHorarioInicioAfter(roomId, LocalDateTime.now().minusHours(6))`.

---

### AUD-024 — Timestamps de Auditoria ✅ CORRIGIDO

**O que mudou:**
- [`AuditableEntity.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/domain/entity/AuditableEntity.java) — Nova classe `@MappedSuperclass` com `@CreationTimestamp` e `@UpdateTimestamp`.
- [`V3__Add_Audit_Timestamps.sql`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/db/migration/V3__Add_Audit_Timestamps.sql) — Adiciona `created_at` e `updated_at` em TODAS as 8 tabelas.
- **Todas as entidades** agora extendem `AuditableEntity`: Cinema, Movie, Role, Room, Seat, Session, Ticket, User.

**Avaliação:** Excelente implementação. Approach com Hibernate annotations (`@CreationTimestamp`/`@UpdateTimestamp`) é direto e funcional.

---

### AUD-029 — CI/CD Pipeline ✅ CORRIGIDO

**O que mudou:**
- [`.github/workflows/maven.yml`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/.github/workflows/maven.yml) — GitHub Actions com `actions/checkout@v3`, `setup-java@v3` (Temurin 21), `mvn -B package`.

**Avaliação:** Pipeline básica funcional. Compila e executa testes em push/PR para `main`/`master`.

---

### AUD-033 — SecurityFilter NoSuchElementException ✅ CORRIGIDO

**O que mudou:**
- [`SecurityFilter.java:35-42`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/security/SecurityFilter.java#L35-L42) — Trocou `.orElseThrow()` por `.orElse(null)` com `if (user != null)`. Usuários deletados com token válido são tratados gracefully sem exceção.

---

### AUD-034 — Cancelamento com Reembolso ✅ PARCIALMENTE CORRIGIDO

**O que mudou:**
- [`RefundEventDTO.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/dto/request/RefundEventDTO.java) — Novo DTO para evento de reembolso.
- [`RefundProducer.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/RefundProducer.java) — `@TransactionalEventListener(AFTER_COMMIT)` que publica evento para RabbitMQ.
- [`TicketService.cancelTicket():159-162`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L159-L162) — Novo método `cancelTicket` publica `RefundEventDTO`.

**Problemas identificados — veja NOVO-001 e NOVO-002 abaixo.**

---

### AUD-039 — RuntimeException no MovieService.findById ✅ CORRIGIDO

- [`MovieService.java:63`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L63) — Agora usa `BusinessException` em vez de `RuntimeException`.

---

### AUD-042 — tmdbId Nunca Populado ✅ CORRIGIDO

- [`MovieService.java:40`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L40) — `movie.setTmdbId(String.valueOf(filmeGringo.id()))` agora popula o campo.
- [`TmdbSearchResponseDTO.java:8`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/dto/client/TmdbSearchResponseDTO.java#L8) — Campo corrigido para `title` (era `movie`).

---

### AUD-043 — TicketResponseDTO Incompleto ✅ CORRIGIDO

- [`TicketResponseDTO.java:9-10`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/dto/response/TicketResponseDTO.java#L9-L10) — Adicionou `valorPago` (`BigDecimal`) e `ticketType` (`TicketType`).

---

### NOVO-RES — Imports no ResourceExceptionHandler ✅ CORRIGIDO

- [`ResourceExceptionHandler.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/exception/ResourceExceptionHandler.java) — Imports `HttpServlet`, `Response`, `RestController` removidos.

---

### N+1 Queries — EntityGraph Expandido ✅ CORRIGIDO

| Repository | EntityGraph |
|---|---|
| [`SessionRepository.findAll(Pageable)`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/SessionRepository.java#L14-L15) | `movie, room, room.cinema` |
| [`TicketRepository.findByUserId(Pageable)`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/TicketRepository.java#L17-L18) | `session.movie, session.room, seat` |
| [`RoomRepository.findAll(Pageable)`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/repository/RoomRepository.java) | `cinema` |

---

## 🔍 Novos Problemas Identificados

### NOVO-001 ⚠️ Duplicação de Métodos de Cancelamento (ALTO)

**Arquivo:** [`TicketService.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java)

Existem **dois métodos de cancelamento** com regras divergentes:

| Método | Valida 30 min? | Publica Reembolso? | Valida já cancelado? |
|---|---|---|---|
| `cancelMyTicket(Long)` L118-139 | ✅ Sim | ❌ Não | ❌ Não |
| `cancelTicket(Long, User)` L141-165 | ❌ Não | ✅ Sim | ✅ Sim |

**Risco:** Um usuário poderia burlar a regra de 30 minutos usando o endpoint `PUT /{id}/cancel` (que chama `cancelTicket`) e ainda receber reembolso. Os dois métodos devem ser **unificados** com todas as validações.

---

### NOVO-002 ⚠️ RefundProducer Usa Exchange/Routing Key Diferentes do RabbitMQConfig (ALTO)

**Arquivo:** [`RefundProducer.java:21`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/RefundProducer.java#L21)

```java
rabbitTemplate.convertAndSend("pagamentos.v1.exchange", "reembolso.rota", event);
```

Mas o [`RabbitMQConfig.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/RabbitMQConfig.java) declara:
- Exchange: `cineticket.direct.change` (não `pagamentos.v1.exchange`)
- Nenhuma queue/binding para `reembolso.rota`

**Resultado:** Mensagens de reembolso serão **silenciosamente descartadas** pelo RabbitMQ (exchange inexistente = mensagem perdida sem erro).

---

### NOVO-003 — CORS Não Integrado com Spring Security (MÉDIO)

**Arquivo:** [`SecurityConfig.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/SecurityConfig.java)

Falta `http.cors(Customizer.withDefaults())`. Sem isso, preflight `OPTIONS` serão bloqueados pelo Security Filter antes de atingir o CORS handler do Spring MVC.

---

### NOVO-004 — SessionService.deleteEmergency Não Publica Reembolso (MÉDIO)

**Arquivo:** [`SessionService.java:67-82`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/SessionService.java#L67-L82)

O `deleteEmergency` muda status para `EMERGENCY_CANCELLED` mas **não publica `RefundEventDTO`** para nenhum dos tickets. O `RefundProducer` já existe mas não é usado aqui.

---

### NOVO-005 — SessionService.findAll Retorna Sessões Inativas (BAIXO)

**Arquivo:** [`SessionService.java:84-88`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/SessionService.java#L84-L88)

Chama `sessionRepository.findAll(pageable)` que retorna ALL sessões, incluindo as com `ativo = false` (canceladas emergencialmente). Deveria usar `findByAtivoTrue(pageable)`.

---

### NOVO-006 — Coluna `ticketType` em camelCase (BAIXO)

**Arquivo:** [`Ticket.java:42`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/domain/entity/Ticket.java#L42)

```java
@Column(name = "ticketType", nullable = false)
```

Todas as outras colunas usam `snake_case` (`status_pagamento`, `valor_pago`, `session_id`). Deveria ser `ticket_type` para consistência. A migration V1 usa `ticket_type` como nome da coluna no DDL.

---

## ⚠️ Achados Restantes da Auditoria Original

### P1 — Alto (2 restantes)

| ID | Achado | Complexidade |
|---|---|---|
| **AUD-020** | `@PathVariable` e `@Valid` no [`RoomService`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/RoomService.java#L49-L60) (linhas 49, 60) — Anotações de controller na camada de serviço | Trivial (30s) |
| **AUD-039-B** | `RuntimeException` inconsistente: [`CinemaService.create:27`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/CinemaService.java#L27), [`CinemaService.findById:45`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/CinemaService.java#L45), [`RoomService.create:36`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/RoomService.java#L36), [`SessionService.create:39,42`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/SessionService.java#L39-L42) — Todos devem usar `BusinessException` | Trivial (2min) |

### P2 — Planejar (7 restantes)

| ID | Achado |
|---|---|
| AUD-004 | Credenciais `guest:guest` RabbitMQ no docker-compose (aceitável para dev local) |
| AUD-012 | Refresh tokens / revogação |
| AUD-013 | Desativação de contas (User.isEnabled hardcoded) |
| AUD-025 | Overflow ASCII na geração de assentos (salas > 260 cadeiras) |
| AUD-028 | `SPRING_PROFILES_ACTIVE=dev` hardcoded no docker-compose |
| AUD-030 | Limites de recursos nos containers Docker |
| AUD-037 | Métricas, traces, correlation IDs |

---

## 📊 Notas por Categoria (Atualizadas)

| Categoria | #1 | #3 | **#4** | Δ total |
|---|---|---|---|---|
| **Segurança** | 1.5 | 5.0 | **6.5** | +5.0 ✅ |
| **Confiabilidade** | 2.0 | 5.5 | **7.0** | +5.0 ✅ |
| **Resiliência** | 1.5 | 4.5 | **5.5** | +4.0 ✅ |
| **Banco de Dados** | 3.0 | 6.0 | **7.5** | +4.5 ✅ |
| **Infraestrutura** | 2.5 | 5.5 | **7.0** | +4.5 ✅ |
| **Performance** | 3.0 | 4.0 | **6.5** | +3.5 ✅ |
| **Tratamento de Erros** | 2.5 | 5.5 | **6.0** | +3.5 ✅ |
| **Manutenibilidade** | 4.0 | 5.5 | **6.5** | +2.5 ✅ |
| **Observabilidade** | 1.5 | 3.0 | **4.5** | +3.0 ✅ |
| **Testes** | 1.0 | 2.0 | **2.5** | +1.5 |

---

## 🎯 Top 5 Correções Prioritárias

1. **NOVO-001** — Unificar `cancelMyTicket` e `cancelTicket` em um único método com TODAS as validações (30 min + reembolso + idempotência) *(15 minutos)*
2. **NOVO-002** — Corrigir exchange/routing-key no `RefundProducer` para usar constantes do `RabbitMQConfig` e criar a queue/binding de reembolso *(10 minutos)*
3. **NOVO-003** — Adicionar `.cors(Customizer.withDefaults())` no `SecurityConfig` *(30 segundos)*
4. **AUD-039-B** — Trocar `RuntimeException` → `BusinessException` nos 5 locais restantes *(2 minutos)*
5. **AUD-020** — Remover `@PathVariable` e `@Valid` do `RoomService` *(30 segundos)*

---

## Veredito Atualizado

### ✅ VIÁVEL PARA MVP — 6.5/10

O projeto evoluiu enormemente. **Zero achados P0 críticos restam.** As vulnerabilidades de segurança, race conditions, information disclosure e falhas de infraestrutura foram todas resolvidas.

**Para produção real**, os itens mínimos restantes são:
- Unificar o fluxo de cancelamento (NOVO-001 + NOVO-002)
- Integrar CORS com Spring Security (NOVO-003)
- Expandir cobertura de testes

O projeto saiu de **"perigosamente vulnerável" (2.8)** para **"MVP funcional e seguro" (6.5)** 🎉
