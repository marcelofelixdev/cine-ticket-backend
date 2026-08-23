# RE-AUDITORIA #3 — Estado Atual do CineTicket Backend

**Data:** 2026-08-23 01:19  
**Referência:** [Auditoria original](file:///C:/Users/Marcelo/.gemini/antigravity/brain/c80e06aa-2408-467d-b696-8ef00be3ae32/audit_report.md) | [Re-auditoria #2](file:///C:/Users/Marcelo/.gemini/antigravity/brain/c80e06aa-2408-467d-b696-8ef00be3ae32/re_audit_report.md)

---

## Evolução Geral

| Métrica | Auditoria #1 | Re-auditoria #2 | **Agora (#3)** |
|---|---|---|---|
| **Nota Geral** | 2.8/10 | 4.5/10 | **5.2/10** |
| **Achados Corrigidos** | 0/43 | 17/43 | **22/43 + 6 novos resolvidos** |
| **Achados P0 (Crítico)** | 8 | 2 | **1** |
| **Achados P1 (Alto)** | 16 | 10 | **7** |

---

## ✅ Novas Correções Desde a Re-auditoria #2

### AUD-005 — Consumer Catch Genérico ✅ AGORA TOTALMENTE CORRIGIDO

**O que mudou:**
- [`PaymentConsumer.java:44`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L44) — O catch genérico agora lança `AmqpRejectAndDontRequeueException` em vez de `RuntimeException`.

**Avaliação:** Agora **todos os caminhos de erro** no consumer enviam a mensagem para a DLQ em vez de requeue infinito. ✅

---

### AUD-009 — Information Disclosure no ResourceExceptionHandler ✅ CORRIGIDO

**O que mudou:**
- [`ResourceExceptionHandler.java:72`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/exception/ResourceExceptionHandler.java#L72) — `e.printStackTrace()` substituído por `log.error("Erro interno não tratado: {}", e.getMessage(), e)` usando SLF4J.
- [`ResourceExceptionHandler.java:78-79`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/exception/ResourceExceptionHandler.java#L78-L79) — Mensagem genérica retornada: `"Ocorreu um erro inesperado. Tente novamente mais tarde."` em vez de `e.getMessage()`.

**Avaliação:** Correção completa. Stack traces, nomes de tabelas SQL e classes internas não são mais expostos ao cliente.

---

### AUD-040 — ResponseStatusException Capturada como HTTP 500 ✅ CORRIGIDO

**O que mudou:**
- [`ResourceExceptionHandler.java:56-67`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/exception/ResourceExceptionHandler.java#L56-L67) — Novo handler `@ExceptionHandler(ResponseStatusException.class)` que respeita o status code original (ex: HTTP 429 do rate limiter).

**Avaliação:** Correção completa. O rate limiter agora retorna 429 corretamente.

---

### NOVO-001 — Imports Residuais no TicketController ✅ CORRIGIDO

**O que mudou:**
- [`TicketController.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java) — Imports de `Ticket`, `TicketStatus`, `TicketRepository`, `PdfService` removidos. Controller agora só depende de `TicketService` e `RateLimitService`.

---

### NOVO-005 — Partial Unique Index de Overbooking ✅ CORRIGIDO

**O que mudou:**
- [`V2__Add_Data_And_Indices.sql:4-6`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/db/migration/V2__Add_Data_And_Indices.sql#L4-L6):
```sql
CREATE UNIQUE INDEX idx_ticket_no_overbooking 
ON tb_ticket(session_id, seat_id) 
WHERE status_pagamento IN ('APPROVED', 'PENDING');
```

**Avaliação:** Perfeito. Agora o overbooking tem **dupla proteção**: lock pessimista na aplicação + unique index condicional no banco. Defense-in-depth implementado.

---

### NOVO-006 — Roles Default na Migration ✅ CORRIGIDO

**O que mudou:**
- [`V2__Add_Data_And_Indices.sql:1-2`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/db/migration/V2__Add_Data_And_Indices.sql#L1-L2):
```sql
INSERT INTO tb_role (nome) VALUES ('ROLE_USER') ON CONFLICT DO NOTHING;
INSERT INTO tb_role (nome) VALUES ('ROLE_ADMIN') ON CONFLICT DO NOTHING;
```

**Avaliação:** Uso correto de `ON CONFLICT DO NOTHING` para idempotência. Registro de novos usuários vai funcionar em banco limpo.

---

## ⚠️ Achados Restantes

### P0 — Imediato (1 restante)

| ID | Achado | Observação |
|---|---|---|
| **AUD-004** | Credenciais `guest:guest` do RabbitMQ hardcoded em [`docker-compose.yml:30-31`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/docker-compose.yml#L30-L31) e fallback `guest` em [`application-prod.properties:18-19`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-prod.properties#L18-L19) | Para ambiente dev local, é aceitável. Para produção, trocar por variáveis de ambiente sem fallback default |

### P1 — Alta (7 restantes)

| ID | Achado | Complexidade |
|---|---|---|
| AUD-008 | Rate limiting no endpoint de login | Pequena |
| AUD-017 | Paginação em endpoints de listagem | Média |
| AUD-022 | Validação de sobreposição de sessões (mesma sala + horário) | Média |
| AUD-033 | `SecurityFilter` lança `NoSuchElementException` para usuários deletados com token válido | Pequena |
| AUD-034 | Cancelamento de emergência sem trigger de reembolso | Média |
| AUD-038 | Cobertura de testes — agora ~4 testes; 8/9 services sem teste | Grande |
| AUD-039 | `MovieService.findById()` [linha 61](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L61) ainda usa `RuntimeException` → HTTP 500 | Pequena |

### P2 — Planejar (13 restantes)

| ID | Achado |
|---|---|
| AUD-011 | CORS / Security headers (CSP, HSTS, X-Frame-Options) |
| AUD-012 | Refresh tokens e revogação |
| AUD-013 | Capacidade de bloquear/desativar contas |
| AUD-024 | Timestamps `created_at`/`updated_at` nas entidades |
| AUD-025 | Overflow ASCII na geração de assentos (salas > 260) |
| AUD-028 | `SPRING_PROFILES_ACTIVE=dev` hardcoded no docker-compose |
| AUD-029 | Ausência de CI/CD pipeline |
| AUD-030 | Limites de recursos nos containers |
| AUD-037 | Métricas, traces distribuídos, correlation IDs |
| AUD-041 | `application.properties` com encoding corrompido na linha 2 |
| AUD-042 | `tmdbId` nunca populado no MovieService |
| AUD-043 | `TicketResponseDTO` omite `valorPago` e `ticketType` |
| NOVO-004 | `CineTicketApplicationTests` pode falhar sem RabbitMQ rodando |

### P3 — Melhoria (2 restantes)

| ID | Achado |
|---|---|
| AUD-020 | `@PathVariable` e `@Valid` no `RoomService` (anotações de controller no service) |
| NOVO-RES | Imports não utilizados no `ResourceExceptionHandler`: `HttpServlet` (L3), `Response` (L5), `RestController` (L9) |

---

## 📊 Notas por Categoria (Atualizadas)

| Categoria | Antes | Agora | Δ |
|---|---|---|---|
| **Segurança** | 1.5 | **5.0** | +3.5 ✅ |
| **Confiabilidade** | 2.0 | **5.5** | +3.5 ✅ |
| **Resiliência** | 1.5 | **4.5** | +3.0 ✅ |
| **Banco de Dados** | 3.0 | **6.0** | +3.0 ✅ |
| **Infraestrutura** | 2.5 | **5.5** | +3.0 ✅ |
| **Dependências** | 3.0 | **6.5** | +3.5 ✅ |
| **Tratamento de Erros** | 2.5 | **5.5** | +3.0 ✅ |
| **Gestão de Segredos** | 2.0 | **4.0** | +2.0 ✅ |
| **Observabilidade** | 1.5 | **3.0** | +1.5 |
| **Performance** | 3.0 | **4.0** | +1.0 |
| **Testes** | 1.0 | **2.0** | +1.0 |
| **Manutenibilidade** | 4.0 | **5.5** | +1.5 ✅ |

---

## 🎯 Próximos 5 Passos Recomendados (Quick Wins)

1. **AUD-039** — Trocar `RuntimeException` por `BusinessException` no `MovieService.findById()` [linha 61](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L61) *(30 segundos)*
2. **AUD-033** — No `SecurityFilter`, trocar `.orElseThrow()` por tratamento que retorna sem autenticar (chain.doFilter continua sem authentication, Spring Security devolve 401 automaticamente) *(5 minutos)*
3. **NOVO-RES** — Remover imports não utilizados no `ResourceExceptionHandler` (linhas 3, 5, 9) *(30 segundos)*
4. **AUD-008** — Adicionar `rateLimitService.getUserBucket()` no `AuthController.login()` para proteger contra brute force *(10 minutos)*
5. **AUD-028** — Trocar `SPRING_PROFILES_ACTIVE=dev` por `SPRING_PROFILES_ACTIVE=${PROFILE:dev}` no docker-compose *(1 minuto)*

---

## Veredito Atualizado

### ⚠️ CAUTELOSAMENTE PRÓXIMO — 5.2/10

O projeto evoluiu significativamente. Os **5 achados CRÍTICOS de segurança** originais foram **todos corrigidos**. O sistema de mensageria agora é resiliente com DLQ + idempotência + AFTER_COMMIT. O banco tem migrations versionadas com Flyway + partial unique index.

**Para considerar produção**, os itens mínimos restantes são:
- Paginação nos endpoints de listagem (AUD-017)
- Cobertura mínima de testes nos fluxos críticos (AUD-038)
- CI/CD pipeline (AUD-029)
- CORS configurado para o frontend (AUD-011)

O projeto saiu de um estado **perigoso** para um estado **funcional com gaps operacionais conhecidos**. Boa evolução! 👏
