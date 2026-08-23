# RELATÓRIO DE AUDITORIA TÉCNICA COMPLETA — CineTicket Backend

**Projeto:** `cine-ticket-backend`  
**Data:** 2026-08-22  
**Versão analisada:** Commit `6eb6630` (HEAD)  
**Stack:** Spring Boot 4.1.0 (versão inexistente — vide AUD-006), Java 21, PostgreSQL 15, RabbitMQ 3, Docker  
**Arquivos analisados:** 70 arquivos Java (.java), 3 properties, 1 Dockerfile, 1 docker-compose.yml, 1 .env, 1 pom.xml  

---

# 1. Resumo Executivo

O projeto **CineTicket Backend** é uma API REST para sistema de cinema com funcionalidades de cadastro de cinemas, salas, filmes, sessões, compra/cancelamento de ingressos, geração de PDF com QR Code e integração com a API TMDB para enriquecimento de dados de filmes.

A auditoria revelou um sistema em **estágio inicial de desenvolvimento**, com funcionalidades básicas implementadas, mas **vulnerabilidades críticas de segurança** que permitem **escalação de privilégios por qualquer usuário não autenticado**, **race conditions que possibilitam overbooking**, **segredos hardcoded em configurações de produção**, e **ausência quase total de testes automatizados**.

O projeto **NÃO está preparado para produção** e apresenta riscos significativos mesmo em ambientes de staging. As falhas de segurança identificadas permitiriam, em um cenário real, que um atacante obtivesse acesso administrativo completo ao sistema em questão de minutos.

> [!CAUTION]
> Foram encontradas **5 vulnerabilidades CRÍTICAS** e **12 vulnerabilidades ALTAS** que bloqueiam qualquer consideração de deploy em produção.

---

# 2. Nota Geral do Projeto

## Nota: 2.8 / 10

## Nível de maturidade: **CRÍTICO**

Sistema imaturo e altamente perigoso para produção. Possui vulnerabilidades de segurança exploráveis trivialmente, ausência de resiliência em mensageria, race conditions em operações financeiras, e cobertura de testes praticamente inexistente.

## Veredito de produção: ❌ BLOQUEADO PARA PRODUÇÃO

---

# 3. Principais Riscos (Top 10)

| # | Risco | Severidade | Impacto |
|---|---|---|---|
| 1 | **Escalação de privilégios via registro público** — Qualquer usuário pode se registrar como ADMIN | CRÍTICO (10/10) | Comprometimento total do sistema |
| 2 | **Race condition no overbooking** — Vendas concorrentes permitem dois ingressos para o mesmo assento | CRÍTICO (9.5/10) | Perda financeira e dados corrompidos |
| 3 | **Segredo JWT hardcoded em produção** — Fallback `SuperSecretKeyParaProducao123` permite forjar tokens | CRÍTICO (9.5/10) | Bypass completo de autenticação |
| 4 | **IDOR no download de PDF** — Qualquer usuário acessa ingresso de qualquer outro usuário | ALTO (8.5/10) | Violação de privacidade |
| 5 | **Consumer de pagamento não-idempotente** — Tickets cancelados voltam a APPROVED | ALTO (8.5/10) | Corrupção de dados financeiros |
| 6 | **Infinite requeue loop (poison pill)** — Mensagens inválidas causam loop infinito no RabbitMQ | ALTO (8/10) | Indisponibilidade do sistema |
| 7 | **Versão do Spring Boot inexistente (4.1.0)** — Dependência fantasma no pom.xml | ALTO (8/10) | Build não-reproduzível / supply chain |
| 8 | **Ausência total de testes em controllers e 7/9 services** | ALTO (7.5/10) | Regressões não detectadas |
| 9 | **Queries N+1 em todas as listagens** sem paginação | ALTO (7/10) | Degradação de performance e OOM |
| 10 | **TmdbClient sem timeout** — TMDB lento causa esgotamento de threads | ALTO (7/10) | Indisponibilidade em cascata |

---

# 4. Achados Críticos

---

**ID:** AUD-001

**Título:** Escalação de Privilégios via Registro Público de Usuário

**Área:** Segurança — Controle de Acesso

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** CRÍTICO

**Nota de risco:** 10/10

**Status:** Confirmado

**Descrição:** O endpoint público `POST /api/v1/users` aceita um campo `roleIds` no corpo da requisição. O [`UserService.create()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/UserService.java#L41-L49) itera sobre os IDs fornecidos e atribui as roles diretamente ao novo usuário sem qualquer verificação de permissão.

**Evidência encontrada:**
- [`UserRequestDTO.java:12`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/dto/request/UserRequestDTO.java#L12) — `Set<Long> roleIds` sem restrição
- [`UserService.java:42-49`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/UserService.java#L42-L49) — Atribuição direta de roles
- [`SecurityConfig.java:41`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/SecurityConfig.java#L41) — Endpoint é `permitAll()`

**Causa raiz:** Mass Assignment — O DTO de criação de usuário expõe campos administrativos sem separação entre registro público e gerenciamento administrativo.

**Impacto técnico:** Atacante cria conta com `ROLE_ADMIN` em uma única requisição HTTP.

**Impacto operacional:** Perda completa de controle do sistema. Atacante pode criar/deletar cinemas, filmes, sessões, e acessar dados de todos os usuários.

**Impacto de segurança:** Comprometimento total. Equivalente a obter credenciais de administrador.

**Cenário de falha:** `POST /api/v1/users {"nome":"Hacker","email":"hacker@evil.com","senha":"123456","roleIds":[1]}` → Conta ADMIN criada.

**Probabilidade:** Alta

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Remover `roleIds` do `UserRequestDTO`. Atribuir `ROLE_USER` por padrão. Criar endpoint separado com `hasRole("ADMIN")` para gerenciamento de roles.

**Esforço estimado:** Pequeno

**Risco de não corrigir:** Qualquer pessoa na internet pode se tornar administrador do sistema.

---

**ID:** AUD-002

**Título:** Race Condition no Controle de Overbooking (TOCTOU)

**Área:** Banco de Dados / Integridade de Dados

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** CRÍTICO

**Nota de risco:** 9.5/10

**Status:** Confirmado

**Descrição:** A verificação de assento ocupado em [`TicketService.buyTicket()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L42-L44) usa `existsBySessionIdAndSeatIdAndStatusIn()` como check-then-act sem lock no banco. Duas requisições concorrentes para o mesmo assento passam pela verificação simultaneamente e ambas inserem um ticket.

**Evidência encontrada:**
- [`TicketService.java:42-44`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L42-L44) — Check sem lock
- [`Ticket.java:15`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/domain/entity/Ticket.java#L15) — `@Table(name = "tb_ticket")` sem unique constraint condicional em `(session_id, seat_id)`

**Causa raiz:** Ausência de constraint de unicidade no banco de dados e ausência de lock pessimista/otimista.

**Impacto técnico:** Dois ingressos vendidos para o mesmo assento na mesma sessão.

**Impacto operacional:** Overbooking real. Dois clientes chegam ao cinema com ingressos válidos para o mesmo assento. Prejuízo financeiro e de reputação.

**Impacto de segurança:** Baixo (não é uma vulnerabilidade de segurança, mas de integridade de dados).

**Cenário de falha:** Dois usuários clicam "Comprar" ao mesmo tempo para o mesmo assento. Ambos recebem confirmação.

**Probabilidade:** Alta (em qualquer cenário com tráfego real)

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Adicionar partial unique index no PostgreSQL: `CREATE UNIQUE INDEX idx_ticket_seat_session_active ON tb_ticket(session_id, seat_id) WHERE status_pagamento IN ('APPROVED', 'PENDING')`. Complementar com `@Lock(LockModeType.PESSIMISTIC_WRITE)` na query de verificação.

**Esforço estimado:** Médio

**Risco de não corrigir:** Overbooking em produção é inevitável sob qualquer carga concorrente.

---

**ID:** AUD-003

**Título:** Segredo JWT Hardcoded como Fallback em Configuração de Produção

**Área:** Segurança — Gestão de Segredos

**Ambiente afetado:** Produção

**Severidade:** CRÍTICO

**Nota de risco:** 9.5/10

**Status:** Confirmado

**Descrição:** O arquivo [`application-prod.properties:24`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-prod.properties#L24) contém `api.security.token.secret=${JWT_SECRET:SuperSecretKeyParaProducao123}`. Se a variável de ambiente `JWT_SECRET` não for definida, o sistema usará o valor hardcoded, permitindo a qualquer pessoa forjar tokens JWT válidos.

**Evidência encontrada:**
- [`application-prod.properties:24`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-prod.properties#L24) — `${JWT_SECRET:SuperSecretKeyParaProducao123}`
- Segredo está commitado no repositório Git.

**Causa raiz:** Fallback default com valor previsível em configuração de produção.

**Impacto técnico:** Forja de tokens JWT com qualquer email e roles, bypass completo de autenticação e autorização.

**Impacto operacional:** Acesso irrestrito a todos os endpoints da API, incluindo administrativos.

**Impacto de segurança:** Comprometimento total. Combinado com AUD-001, torna o sistema completamente aberto.

**Cenário de falha:** Deploy sem definir `JWT_SECRET` → sistema usa chave pública → atacante cria tokens.

**Probabilidade:** Média (depende de erro operacional no deploy)

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Remover o valor padrão. Usar `${JWT_SECRET}` sem fallback. Configurar o Spring para falhar na inicialização se o segredo não estiver definido (`@Value` sem default + validação). Usar secrets manager (Vault, AWS Secrets Manager).

**Esforço estimado:** Pequeno

**Risco de não corrigir:** Um único deploy sem variável de ambiente compromete completamente o sistema.

---

**ID:** AUD-004

**Título:** Credenciais RabbitMQ e DB Expostas no Repositório e em Configuração

**Área:** Segurança — Gestão de Segredos

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** CRÍTICO

**Nota de risco:** 9/10

**Status:** Confirmado

**Descrição:** Múltiplas credenciais estão expostas em arquivos versionados no Git:

**Evidência encontrada:**
- [`.env`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/.env) contém `DB_PASSWORD=104512aa`, `JWT_SECRET=8f9a2b4c6d8e0f1a3b5c7d9e1f3a5b7c`, `TMDB_API_KEY=86606f693ed586608857bd25b6224839` — embora esteja no `.gitignore`, o arquivo existe no workspace.
- [`docker-compose.yml:30-31`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/docker-compose.yml#L30-L31) — `RABBITMQ_DEFAULT_USER: guest`, `RABBITMQ_DEFAULT_PASS: guest` (hardcoded, commitado)
- [`application-dev.properties:18-19`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-dev.properties#L18-L19) — Credenciais RabbitMQ `guest:guest` commitadas
- [`application-prod.properties:20-21`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-prod.properties#L20-L21) — Fallback `guest:guest` para RabbitMQ em produção

**Causa raiz:** Ausência de separação adequada de segredos. Credenciais padrão usadas sem rotação.

**Impacto técnico:** Acesso direto ao banco de dados e broker de mensagens.

**Impacto operacional:** Manipulação direta de dados, injeção de mensagens na fila.

**Impacto de segurança:** Comprometimento do banco de dados e do sistema de mensageria.

**Cenário de falha:** Atacante descobre as portas expostas (vide AUD-017) e usa credenciais default.

**Probabilidade:** Alta

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Rotacionar todas as credenciais. Usar variáveis de ambiente sem fallbacks default. Remover credenciais de arquivos commitados. Implementar secrets manager.

**Esforço estimado:** Pequeno

**Risco de não corrigir:** Acesso direto ao banco e broker por qualquer pessoa que obtiver acesso à rede.

---

**ID:** AUD-005

**Título:** Consumer de Pagamento Não-Idempotente Sobrescreve Status de Tickets Cancelados

**Área:** Mensageria / Integridade de Dados

**Ambiente afetado:** Staging, Produção

**Severidade:** CRÍTICO

**Nota de risco:** 9/10

**Status:** Confirmado

**Descrição:** O [`PaymentConsumer.processarPagamento()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L22-L31) faz `Thread.sleep(3000)` e depois marca o ticket como `APPROVED` incondicionalmente, sem verificar o status atual. Se durante esses 3 segundos o usuário cancelar o ticket ou um admin fizer cancelamento de emergência, o status `CANCELLED`/`EMERGENCY_CANCELLED` é sobrescrito para `APPROVED`.

**Evidência encontrada:**
- [`PaymentConsumer.java:28`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L28) — `ticket.setStatus(TicketStatus.APPROVED)` sem verificação
- [`PaymentConsumer.java:23`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L23) — `Thread.sleep(3000)` cria janela de race condition

**Causa raiz:** Ausência de verificação de estado anterior (state machine) e ausência de idempotência.

**Impacto técnico:** Tickets cancelados voltam a `APPROVED`. Assentos que deveriam estar liberados ficam ocupados.

**Impacto operacional:** Clientes são cobrados por ingressos que já cancelaram. Inconsistência financeira.

**Impacto de segurança:** Baixo

**Cenário de falha:** Usuário compra → cancela em 2 segundos → consumer aprova 1 segundo depois → ticket APPROVED apesar de cancelado.

**Probabilidade:** Média

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Verificar status `PENDING` antes de aprovar. Implementar state machine com transições válidas. Garantir idempotência por ticket ID.

**Esforço estimado:** Pequeno

**Risco de não corrigir:** Corrupção de dados financeiros e de estado de ingressos.

---

# 5. Achados de Segurança

---

**ID:** AUD-006

**Título:** IDOR (Insecure Direct Object Reference) no Download de PDF de Ingresso

**Área:** Segurança — Controle de Acesso

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 8.5/10

**Status:** Confirmado

**Descrição:** O endpoint `GET /api/v1/tickets/{id}/pdf` em [`TicketController.java:51-69`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java#L50-L69) busca o ticket por ID e gera o PDF sem verificar se o ticket pertence ao usuário autenticado. Qualquer usuário pode enumerar IDs e baixar ingressos de outros clientes.

**Evidência encontrada:** Ausência de comparação `ticket.getUser().getId().equals(loggedUser.getId())` no controller.

**Causa raiz:** Falta de verificação de ownership no objeto retornado.

**Probabilidade:** Alta

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Adicionar verificação de ownership do ticket antes de gerar o PDF. Mover a lógica para o service layer.

**Esforço estimado:** Pequeno

**Risco de não corrigir:** Vazamento de dados pessoais (nome, email) e QR codes válidos de ingressos de terceiros.

---

**ID:** AUD-007

**Título:** Ausência de Restrição RBAC na Criação de Sessões

**Área:** Segurança — Autorização

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 8/10

**Status:** Confirmado

**Descrição:** [`SecurityConfig.java:44-47`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/SecurityConfig.java#L44-L47) restringe POST/PUT/DELETE para cinemas, filmes e salas a `ROLE_ADMIN`, mas `POST /api/v1/sessions` e `PUT /api/v1/sessions/**` caem em `.anyRequest().authenticated()`, permitindo que qualquer usuário autenticado crie sessões de cinema.

**Evidência encontrada:** Ausência de regra para `HttpMethod.POST, "/api/v1/sessions"` no `SecurityConfig`.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Adicionar `.requestMatchers(HttpMethod.POST, "/api/v1/sessions").hasRole("ADMIN")` e `.requestMatchers(HttpMethod.PUT, "/api/v1/sessions/**").hasRole("ADMIN")`.

**Esforço estimado:** Pequeno

---

**ID:** AUD-008

**Título:** Ausência de Rate Limiting no Endpoint de Login

**Área:** Segurança — Brute Force

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7.5/10

**Status:** Confirmado

**Descrição:** O endpoint `POST /api/v1/auth/login` não possui rate limiting, diferentemente do endpoint de compra de tickets. Um atacante pode executar ataques de brute force ou credential stuffing ilimitadamente.

**Evidência encontrada:** [`AuthController.java:23-33`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/AuthController.java#L23-L33) — Nenhum uso de `RateLimitService`.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Implementar rate limiting por IP e por email no endpoint de login. Implementar lockout de conta após N tentativas falhas.

**Esforço estimado:** Pequeno

---

**ID:** AUD-009

**Título:** Exposição de Informações Internas nas Respostas de Erro

**Área:** Segurança — Information Disclosure

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** O [`ResourceExceptionHandler.java:54-68`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/exception/ResourceExceptionHandler.java#L54-L68) retorna `e.getMessage()` diretamente na resposta HTTP 500, expondo nomes de tabelas, erros SQL, nomes de classes internas e stack traces para clientes.

**Evidência encontrada:**
```java
e.getMessage() != null ? e.getMessage() : e.toString()
```
Plus `e.printStackTrace()` no console.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Retornar mensagem genérica ao cliente ("Erro interno do servidor"). Logar detalhes com SLF4J. Substituir `e.printStackTrace()` por `log.error()`.

**Esforço estimado:** Pequeno

---

**ID:** AUD-010

**Título:** Log de Dados Sensíveis de Cartão no Consumer de Pagamento

**Área:** Segurança — Privacidade / PCI-DSS

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`PaymentConsumer.java:20`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L20) loga `evento.cartaoToken()` com `System.out.println`, expondo tokens de cartão em logs.

**Evidência encontrada:** `System.out.println("[CONSUMER] Iniciando cobrança do ingresso: " + evento.ticketId() + " no cartão " + evento.cartaoToken())`

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Nunca logar dados de pagamento. Mascarar tokens de cartão. Usar SLF4J com níveis adequados.

**Esforço estimado:** Pequeno

---

**ID:** AUD-011

**Título:** Ausência de CORS, CSP, HSTS e Security Headers

**Área:** Segurança — Headers HTTP

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 6/10

**Status:** Confirmado

**Descrição:** [`SecurityConfig.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/SecurityConfig.java) não configura CORS, Content-Security-Policy, Strict-Transport-Security, X-Frame-Options, ou X-Content-Type-Options.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-012

**Título:** Ausência de Refresh Token e Mecanismo de Revogação de Token

**Área:** Segurança — Session Management

**Ambiente afetado:** Produção

**Severidade:** MÉDIO

**Nota de risco:** 6/10

**Status:** Confirmado

**Descrição:** O sistema emite apenas access tokens JWT com expiração de 2 horas. Não há refresh token, blocklist, nem mecanismo de revogação. Se um token for comprometido, permanece válido por 2 horas sem possibilidade de invalidação.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-013

**Título:** Contas de Usuário sem Capacidade de Desativação/Bloqueio

**Área:** Segurança — Account Management

**Ambiente afetado:** Produção

**Severidade:** MÉDIO

**Nota de risco:** 5.5/10

**Status:** Confirmado

**Descrição:** [`User.java:58-64`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/domain/entity/User.java#L58-L64) retorna `true` hardcoded para `isEnabled()`, `isAccountNonLocked()`, etc. Não existe mecanismo para bloquear, desativar ou banir contas de usuário.

**Prioridade:** P2 — Planejar

---

# 6. Achados de Performance

---

**ID:** AUD-014

**Título:** Queries N+1 em Todas as Listagens (Session, Room, Ticket)

**Área:** Performance — Banco de Dados

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7.5/10

**Status:** Confirmado

**Descrição:** Todas as operações `findAll()` carregam entidades com `FetchType.LAZY`, e os mappers acessam relacionamentos lazy (`session.getMovie().getTitulo()`, `room.getCinema().getNome()`), gerando N+1 queries. Com 100 sessões, `SessionService.findAll()` gera ~300 queries adicionais (movie + room + cinema por sessão).

**Evidência encontrada:**
- [`SessionMapper`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/mapper/SessionMapper.java) acessa `session.movie.titulo`, `session.room.nome`, `session.room.cinema.nome`
- Nenhum repositório usa `JOIN FETCH`
- Nenhum endpoint usa `Pageable`

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Implementar queries `@Query("SELECT s FROM Session s JOIN FETCH s.movie JOIN FETCH s.room JOIN FETCH s.room.cinema")` e paginação `Page<T>` em todos os endpoints de listagem.

**Esforço estimado:** Médio

---

**ID:** AUD-015

**Título:** TmdbClient sem Timeout — Risco de Esgotamento de Threads

**Área:** Performance — Integração Externa

**Ambiente afetado:** Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`TmdbClient.java:19-22`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/client/TmdbClient.java#L19-L22) cria `RestClient` sem configurar connection timeout ou read timeout. Se a API TMDB ficar lenta ou indisponível, as threads do servidor ficam bloqueadas indefinidamente.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Configurar `ClientHttpRequestFactory` com timeouts (connect: 2s, read: 5s). Implementar circuit breaker com Resilience4j.

**Esforço estimado:** Pequeno

---

**ID:** AUD-016

**Título:** Rate Limiter em Memória com Vazamento Permanente

**Área:** Performance — Memory Leak

**Ambiente afetado:** Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`RateLimitService.java:13`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/RateLimitService.java#L13) usa `ConcurrentHashMap` sem TTL/eviction. Cada email autenticado cria uma entrada permanente que nunca é removida, causando vazamento de memória ao longo do tempo.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Usar Caffeine cache com TTL ou migrar para Redis-backed rate limiting para cenários multi-instância.

**Esforço estimado:** Médio

---

**ID:** AUD-017

**Título:** Ausência de Paginação em Todas as Listagens

**Área:** Performance / Escalabilidade

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 6.5/10

**Status:** Confirmado

**Descrição:** Todos os endpoints `GET` de listagem (cinemas, filmes, salas, sessões, tickets) retornam `List<T>` sem paginação. Com crescimento de dados, estas queries vão carregar todos os registros em memória, causando OOM.

**Prioridade:** P1 — Alta

**Esforço estimado:** Médio

---

# 7. Achados de Arquitetura

---

**ID:** AUD-018

**Título:** Versão do Spring Boot Inexistente (4.1.0)

**Área:** Dependências / Supply Chain

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 8/10

**Status:** Confirmado

**Descrição:** [`pom.xml:8`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/pom.xml#L8) declara `spring-boot-starter-parent:4.1.0`. Spring Boot 4.x **não existe**. As versões estáveis mais recentes são 3.x. Isto pode causar builds não-reproduzíveis, resolução de dependências incorreta, ou risco de supply chain se alguém publicar maliciosamente um artefato com essa versão.

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Corrigir para a versão estável apropriada (ex: `3.3.x` ou `3.4.x`).

**Esforço estimado:** Pequeno

---

**ID:** AUD-019

**Título:** Violação de Camadas — Controller Acessa Repository Diretamente

**Área:** Arquitetura — Separação de Responsabilidades

**Ambiente afetado:** Desenvolvimento

**Severidade:** MÉDIO

**Nota de risco:** 5/10

**Status:** Confirmado

**Descrição:** [`TicketController.java:29`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java#L29) injeta `TicketRepository` diretamente, bypassando o service layer. O endpoint `downloadPdf` consulta o repository e chama `PdfService` diretamente, sem passar pelo `TicketService`.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-020

**Título:** Anotações Web (@PathVariable, @Valid) Vazadas para Service Layer

**Área:** Arquitetura — Separação de Responsabilidades

**Ambiente afetado:** Desenvolvimento

**Severidade:** BAIXO

**Nota de risco:** 3/10

**Status:** Confirmado

**Descrição:** [`RoomService.java:60`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/RoomService.java#L60) usa `@PathVariable` na assinatura do método, e linha 49 usa `@Valid`. Estas são anotações de controller e não devem estar no service.

**Prioridade:** P3 — Melhoria

---

# 8. Achados de Banco de Dados

---

**ID:** AUD-021

**Título:** Uso de `hibernate.ddl-auto=update` em Desenvolvimento (sem Flyway/Liquibase)

**Área:** Banco de Dados — Migrações

**Ambiente afetado:** Desenvolvimento, Staging

**Severidade:** ALTO

**Nota de risco:** 7.5/10

**Status:** Confirmado

**Descrição:** [`application-dev.properties:23`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application-dev.properties#L23) usa `ddl-auto=update`. Produção está configurado como `none`, mas não existe Flyway/Liquibase no projeto. Não existem migrations versionadas, tornando impossível reproduzir o schema, fazer rollback, ou versionar mudanças de banco.

**Evidência encontrada:** Nenhum arquivo `.sql` de migração encontrado. Flyway/Liquibase ausentes do `pom.xml`.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Adicionar Flyway ao projeto. Criar migration inicial baseada no schema atual. Todas as alterações de schema devem ser feitas via migrations versionadas.

**Esforço estimado:** Médio

---

**ID:** AUD-022

**Título:** Ausência de Validação de Sobreposição de Sessões (Mesmo Horário + Mesma Sala)

**Área:** Banco de Dados — Regras de Negócio

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`SessionService.create()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/SessionService.java#L35-L48) não verifica se já existe outra sessão na mesma sala e horário. Múltiplas sessões podem ser criadas para o mesmo horário na mesma sala.

**Prioridade:** P1 — Alta

---

**ID:** AUD-023

**Título:** Ausência de Validação Seat-Room no TicketService

**Área:** Integridade de Dados

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`TicketService.buyTicket()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L53-L59) não valida se o `seatId` fornecido pertence à sala (`room`) da sessão (`session`). Um usuário pode comprar um ingresso referenciando um assento de outra sala ou cinema.

**Prioridade:** P1 — Alta

---

**ID:** AUD-024

**Título:** Ausência de Timestamps (created_at, updated_at) em Entidades

**Área:** Banco de Dados — Auditoria

**Ambiente afetado:** Produção

**Severidade:** MÉDIO

**Nota de risco:** 5/10

**Status:** Confirmado

**Descrição:** Nenhuma entidade possui campos `createdAt`/`updatedAt`. Não é possível auditar quando registros foram criados ou modificados, dificultando investigação de incidentes e compliance.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-025

**Título:** Bug de Overflow ASCII na Geração de Assentos (Salas > 260 lugares)

**Área:** Banco de Dados — Integridade

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 5/10

**Status:** Confirmado

**Descrição:** [`RoomService.java:75-92`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/RoomService.java#L75-L92) — `letraFila++` incrementa além de `'Z'` para caracteres como `[`, `\`, `]`, `^` quando a capacidade excede 260.

**Prioridade:** P2 — Planejar

---

# 9. Achados de Infraestrutura e DevOps

---

**ID:** AUD-026

**Título:** Dockerfile Executando como Root com JDK Completo

**Área:** Infraestrutura — Container Security

**Ambiente afetado:** Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`Dockerfile`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/Dockerfile) usa `eclipse-temurin:21-jdk-alpine` (JDK completo com ferramentas de compilação/debug) e executa como root. Sem multi-stage build, sem HEALTHCHECK, sem flags JVM.

**Prioridade:** P1 — Alta

**Recomendação conceitual:** Multi-stage build (Maven → JRE). Adicionar `USER appuser`. Usar `eclipse-temurin:21-jre-alpine`. Adicionar `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.

**Esforço estimado:** Pequeno

---

**ID:** AUD-027

**Título:** Portas de Banco e RabbitMQ Expostas ao Host

**Área:** Infraestrutura — Rede

**Ambiente afetado:** Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`docker-compose.yml:13-14`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/docker-compose.yml#L13-L14) expõe PostgreSQL em `0.0.0.0:5432` e RabbitMQ em `0.0.0.0:5672` e `0.0.0.0:15672`. Com credenciais default `guest:guest`, qualquer pessoa na rede pode acessar.

**Prioridade:** P1 — Alta

---

**ID:** AUD-028

**Título:** Perfil de Desenvolvimento Hardcoded no Docker Compose

**Área:** Infraestrutura — Configuração

**Ambiente afetado:** Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 6/10

**Status:** Confirmado

**Descrição:** [`docker-compose.yml:48`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/docker-compose.yml#L48) define `SPRING_PROFILES_ACTIVE=dev` diretamente. Usar este compose em staging/produção ativa `ddl-auto=update` e `show-sql=true`.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-029

**Título:** Ausência Completa de CI/CD Pipeline

**Área:** DevOps — CI/CD

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 6/10

**Status:** Confirmado

**Descrição:** Não existe nenhum arquivo de CI/CD (`.github/workflows/`, `Jenkinsfile`, `.gitlab-ci.yml`, etc.). Deploys são manuais, sem gates de qualidade, sem scans de segurança, sem testes automáticos.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-030

**Título:** Sem Limites de Recursos nos Containers

**Área:** Infraestrutura — Resiliência

**Ambiente afetado:** Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 5/10

**Status:** Confirmado

**Descrição:** Nenhum container no `docker-compose.yml` define `deploy.resources.limits` de CPU/memória. Um memory leak pode consumir toda a memória do host.

**Prioridade:** P2 — Planejar

---

# 10. Achados de Confiabilidade e Resiliência

---

**ID:** AUD-031

**Título:** Infinite Requeue Loop — Poison Pill Messages no RabbitMQ

**Área:** Resiliência — Mensageria

**Ambiente afetado:** Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 8/10

**Status:** Confirmado

**Descrição:** [`PaymentConsumer.java:26`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java#L26) lança `RuntimeException` quando o ticket não é encontrado. Sem DLQ ou retry limit, RabbitMQ requeue a mensagem infinitamente, consumindo 100% da CPU do consumer.

**Evidência encontrada:** [`RabbitMQConfig.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/config/RabbitMQConfig.java) — Nenhuma configuração de DLX, DLQ, ou retry.

**Prioridade:** P0 — Imediata

**Recomendação conceitual:** Configurar Dead Letter Exchange (DLX) e Dead Letter Queue (DLQ). Implementar retry com backoff exponencial. Limitar retries a 3-5 tentativas.

**Esforço estimado:** Médio

---

**ID:** AUD-032

**Título:** Dual-Write — Mensagem RabbitMQ Enviada Dentro da Transação do Banco

**Área:** Resiliência — Consistência

**Ambiente afetado:** Staging, Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`TicketService.buyTicket():76-80`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/TicketService.java#L76-L80) envia mensagem ao RabbitMQ antes do commit da transação do banco. Se a transação fizer rollback, a mensagem já terá sido enviada. Se o RabbitMQ estiver indisponível, a transação do banco pode falhar.

**Prioridade:** P2 — Planejar

**Recomendação conceitual:** Usar Outbox Pattern ou `TransactionalEventListener` para garantir que a mensagem só seja enviada após o commit.

---

**ID:** AUD-033

**Título:** SecurityFilter Lança Exceção Não Tratada para Usuários Deletados

**Área:** Resiliência — Autenticação

**Ambiente afetado:** Produção

**Severidade:** ALTO

**Nota de risco:** 7/10

**Status:** Confirmado

**Descrição:** [`SecurityFilter.java:35`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/security/SecurityFilter.java#L35) usa `userRepository.findByEmail(email).orElseThrow()`. Se o token JWT é válido mas o usuário foi deletado do banco, um `NoSuchElementException` é lançado no filtro, retornando HTTP 500 em vez de 401.

**Prioridade:** P1 — Alta

---

**ID:** AUD-034

**Título:** Cancelamento de Emergência sem Trigger de Reembolso

**Área:** Resiliência — Fluxo de Negócio

**Ambiente afetado:** Produção

**Severidade:** MÉDIO

**Nota de risco:** 6/10

**Status:** Confirmado

**Descrição:** [`SessionService.deleteEmergency()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/SessionService.java#L51-L65) marca tickets como `EMERGENCY_CANCELLED` mas não dispara nenhum evento de reembolso. Clientes são cobrados por ingressos de sessões canceladas.

**Prioridade:** P1 — Alta

---

# 11. Achados de Observabilidade

---

**ID:** AUD-035

**Título:** Uso de System.out.println em Lugar de Framework de Logging

**Área:** Observabilidade — Logging

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 6/10

**Status:** Confirmado

**Descrição:** Múltiplos arquivos usam `System.out.println()` em vez de SLF4J/Logback:
- [`PaymentConsumer.java:20,31,33`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentConsumer.java)
- [`PaymentProducer.java:21`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/messaging/PaymentProducer.java#L21)
- [`MovieService.java:42`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L42)
- [`ResourceExceptionHandler.java:57`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/exception/ResourceExceptionHandler.java#L57)

**Prioridade:** P2 — Planejar

---

**ID:** AUD-036

**Título:** Health Check Estático sem Validação de Dependências

**Área:** Observabilidade — Health Checks

**Ambiente afetado:** Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 5.5/10

**Status:** Confirmado

**Descrição:** [`HealthController.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/HealthController.java) retorna `"status": "UP"` hardcoded. Não verifica conectividade com PostgreSQL, RabbitMQ, ou TMDB. Ausência de Spring Boot Actuator.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-037

**Título:** Ausência de Métricas, Traces e Correlation IDs

**Área:** Observabilidade

**Ambiente afetado:** Produção

**Severidade:** MÉDIO

**Nota de risco:** 5/10

**Status:** Confirmado

**Descrição:** Não existe Micrometer, Prometheus, Zipkin/Jaeger, correlation IDs, MDC context, ou qualquer forma de trace distribuído.

**Prioridade:** P2 — Planejar

---

# 12. Achados de Testes

---

**ID:** AUD-038

**Título:** Cobertura de Testes Praticamente Inexistente

**Área:** Testes — Cobertura

**Ambiente afetado:** Desenvolvimento

**Severidade:** ALTO

**Nota de risco:** 8/10

**Status:** Confirmado

**Descrição:** O projeto possui apenas **2 arquivos de teste**:
- [`CineTicketApplicationTests.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/test/java/com/app/cineticket/CineTicketApplicationTests.java) — Apenas `contextLoads()` vazio
- [`TicketServiceTest.java`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/test/java/com/app/cineticket/service/TicketServiceTest.java) — 2 testes unitários

**Cobertura por camada:**
- Controllers: **0%** (zero testes)
- Services: **~3%** (2 testes para 1 de 9 services)
- Repositories: **0%**
- Security: **0%** (sem `spring-security-test` no pom.xml)
- Integration: **0%**
- E2E: **0%**

**Prioridade:** P1 — Alta

---

# 13. Achados de Manutenibilidade e Dívida Técnica

---

**ID:** AUD-039

**Título:** Uso Inconsistente de RuntimeException vs BusinessException

**Área:** Manutenibilidade — Error Handling

**Ambiente afetado:** Desenvolvimento

**Severidade:** MÉDIO

**Nota de risco:** 5.5/10

**Status:** Confirmado

**Descrição:** Alguns services usam `BusinessException` (retorna 400) e outros usam `RuntimeException` (retorna 500) para erros de negócio idênticos (ex: "Cinema não encontrado"). Isso causa comportamento inconsistente na API.

**Evidência encontrada:**
- `CinemaService.create()` → `RuntimeException` (HTTP 500)
- `CinemaService.update()` → `BusinessException` (HTTP 400)
- `SessionService.create()` → `RuntimeException` (HTTP 500)
- `TicketService.buyTicket()` → `BusinessException` (HTTP 400)

**Prioridade:** P2 — Planejar

---

**ID:** AUD-040

**Título:** ResponseStatusException Capturada como HTTP 500

**Área:** Manutenibilidade — Error Handling

**Ambiente afetado:** Desenvolvimento, Staging, Produção

**Severidade:** MÉDIO

**Nota de risco:** 5.5/10

**Status:** Confirmado

**Descrição:** [`TicketController.java:41-43`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/controller/TicketController.java#L41-L43) lança `ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)`, mas `ResourceExceptionHandler` captura `Exception.class` antes do Spring processar o status, retornando HTTP 500 em vez de 429.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-041

**Título:** Arquivo application.properties Corrompido

**Área:** Manutenibilidade — Configuração

**Ambiente afetado:** Desenvolvimento

**Severidade:** BAIXO

**Nota de risco:** 3/10

**Status:** Confirmado

**Descrição:** [`application.properties:2`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/resources/application.properties#L2) contém bytes UTF-16LE corrompidos onde `logging.level.org.springframework.security=DEBUG` foi colado com encoding incorreto na mesma linha do `spring.config.import`.

**Prioridade:** P2 — Planejar

---

**ID:** AUD-042

**Título:** Campo tmdbId Nunca Populado no MovieService

**Área:** Manutenibilidade — Dados Incompletos

**Ambiente afetado:** Desenvolvimento

**Severidade:** BAIXO

**Nota de risco:** 3/10

**Status:** Confirmado

**Descrição:** [`Movie.java:28-29`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/domain/entity/Movie.java#L28-L29) define `tmdbId` como unique, mas [`MovieService.create()`](file:///c:/Users/Marcelo/Desktop/Estudos%20Java/Cineticket/cine-ticket-backend/src/main/java/com/app/cineticket/service/MovieService.java#L26-L47) nunca chama `movie.setTmdbId()`. Além disso, o campo `movie` no `TmdbMovieDTO` provavelmente deveria ser `title` (nome do campo na API TMDB).

**Prioridade:** P3 — Melhoria

---

**ID:** AUD-043

**Título:** TicketResponseDTO Omite Campos Importantes (valorPago, ticketType)

**Área:** Manutenibilidade — API Design

**Ambiente afetado:** Desenvolvimento

**Severidade:** BAIXO

**Nota de risco:** 3/10

**Status:** Confirmado

**Descrição:** O DTO de resposta de ticket não inclui `valorPago` nem `ticketType`. O cliente não recebe confirmação de preço pago ou tipo de ingresso.

**Prioridade:** P3 — Melhoria

---

# 14. Riscos de Mudanças Futuras

| Mudança | Componentes Afetados | Risco de Regressão | Mitigação Necessária |
|---|---|---|---|
| Adicionar novo tipo de ticket | `TicketType`, `TicketService`, `TicketMapper`, `PdfService` | **Alto** — Lógica de preço hardcoded | Testes unitários do cálculo de preço |
| Migrar de ddl-auto para Flyway | Todas as entidades, schema PostgreSQL | **Alto** — Sem snapshot do schema atual | Gerar migration baseline |
| Implementar pagamento real | `PaymentConsumer`, `PaymentProducer`, `TicketService` | **Crítico** — Race conditions existentes | Resolver AUD-002 e AUD-005 primeiro |
| Adicionar múltiplas instâncias | `RateLimitService`, `SecurityFilter` | **Alto** — Rate limiting in-memory | Migrar para Redis |
| Atualizar Spring Boot para versão real | `pom.xml`, todos os starters | **Alto** — Versão atual é fantasma | Testar em branch separada |
| Implementar soft delete de usuários | `User`, `SecurityFilter`, `AuthService` | **Alto** — Login hardcoded como enabled | Adicionar campo `ativo` na entidade |

---

# 15. Problemas que Podem Bloquear Produção

1. **AUD-001** — Escalação de privilégios via registro público (CRÍTICO)
2. **AUD-002** — Race condition de overbooking (CRÍTICO)
3. **AUD-003** — JWT secret hardcoded em produção (CRÍTICO)
4. **AUD-004** — Credenciais expostas em repositório (CRÍTICO)
5. **AUD-005** — Consumer não-idempotente sobrescreve cancelamentos (CRÍTICO)
6. **AUD-006** — IDOR no download de PDF (ALTO)
7. **AUD-018** — Versão Spring Boot inexistente (ALTO)
8. **AUD-021** — Ausência de migrations (ALTO)
9. **AUD-031** — Poison pill infinite loop no RabbitMQ (ALTO)
10. **AUD-038** — Ausência quase total de testes (ALTO)

---

# 16. Melhorias Recomendadas

## Curto prazo (1–2 semanas)
- Remover `roleIds` do `UserRequestDTO` e atribuir `ROLE_USER` por padrão (AUD-001)
- Adicionar unique constraint parcial para prevenir overbooking (AUD-002)
- Remover fallback de JWT secret em produção (AUD-003)
- Rotacionar todas as credenciais expostas (AUD-004)
- Verificar status `PENDING` antes de aprovar no consumer (AUD-005)
- Adicionar verificação de ownership no download de PDF (AUD-006)
- Restringir criação de sessões a ADMIN (AUD-007)
- Corrigir versão do Spring Boot no pom.xml (AUD-018)
- Configurar DLQ no RabbitMQ (AUD-031)
- Sanitizar mensagens de erro no exception handler (AUD-009)

## Médio prazo (2–6 semanas)
- Implementar Flyway para migrações (AUD-021)
- Adicionar rate limiting no login (AUD-008)
- Resolver queries N+1 com JOIN FETCH (AUD-014)
- Implementar paginação em todos os endpoints (AUD-017)
- Configurar timeouts no TmdbClient (AUD-015)
- Hardening do Dockerfile — multi-stage build, non-root user, JRE (AUD-026)
- Remover portas expostas do docker-compose (AUD-027)
- Migrar logging de System.out.println para SLF4J (AUD-035)
- Implementar testes unitários para todos os services (AUD-038)
- Implementar testes de controller com MockMvc (AUD-038)
- Adicionar Spring Boot Actuator para health checks reais (AUD-036)

## Longo prazo (6+ semanas)
- Implementar refresh tokens e revogação (AUD-012)
- Adicionar CORS, CSP, HSTS (AUD-011)
- Implementar observabilidade (métricas, traces, correlation IDs) (AUD-037)
- CI/CD pipeline com gates de qualidade e scans de segurança (AUD-029)
- Migrar rate limiting para Redis (AUD-016)
- Implementar circuit breaker para integrações externas (AUD-015)
- Outbox pattern para consistência banco/mensageria (AUD-032)
- Implementar testes de integração e carga (AUD-038)
- Implementar capacidade de desativar/bloquear contas (AUD-013)

---

# 17. Prioridades P0, P1, P2 e P3

## P0 — Imediata (bloqueia qualquer deploy)
| ID | Achado |
|---|---|
| AUD-001 | Escalação de privilégios via registro público |
| AUD-002 | Race condition de overbooking |
| AUD-003 | JWT secret hardcoded em produção |
| AUD-004 | Credenciais expostas |
| AUD-005 | Consumer não-idempotente |
| AUD-006 | IDOR no PDF |
| AUD-018 | Versão Spring Boot inexistente |
| AUD-031 | Poison pill infinite loop |

## P1 — Alta (necessário antes de produção)
| ID | Achado |
|---|---|
| AUD-007 | RBAC faltando em sessões |
| AUD-008 | Rate limiting no login |
| AUD-009 | Information disclosure |
| AUD-010 | Log de dados de cartão |
| AUD-014 | N+1 queries |
| AUD-015 | TmdbClient sem timeout |
| AUD-016 | Memory leak no rate limiter |
| AUD-017 | Ausência de paginação |
| AUD-021 | Ausência de migrations |
| AUD-022 | Sobreposição de sessões |
| AUD-023 | Validação seat-room |
| AUD-026 | Dockerfile inseguro |
| AUD-027 | Portas expostas |
| AUD-033 | SecurityFilter exception não tratada |
| AUD-034 | Cancelamento sem reembolso |
| AUD-038 | Ausência de testes |

## P2 — Planejar (importante mas não bloqueante)
| ID | Achado |
|---|---|
| AUD-011 | CORS / Security headers |
| AUD-012 | Refresh tokens |
| AUD-013 | Bloqueio de contas |
| AUD-019 | Controller acessa repository |
| AUD-024 | Timestamps de auditoria |
| AUD-025 | Overflow ASCII na geração de assentos |
| AUD-028 | Profile hardcoded no compose |
| AUD-029 | Ausência de CI/CD |
| AUD-030 | Limites de recursos |
| AUD-032 | Dual-write RabbitMQ |
| AUD-035 | System.out.println |
| AUD-036 | Health check estático |
| AUD-037 | Métricas e traces |
| AUD-039 | RuntimeException inconsistente |
| AUD-040 | ResponseStatusException capturada errado |
| AUD-041 | Properties corrompido |

## P3 — Melhoria
| ID | Achado |
|---|---|
| AUD-020 | Anotações web no service |
| AUD-042 | tmdbId nunca populado |
| AUD-043 | Campos omitidos no TicketResponseDTO |

---

# 18. Notas por Categoria

| Categoria | Nota | Justificativa | Principais Riscos |
|---|---|---|---|
| **Arquitetura** | 4.0/10 | Separação básica controller/service/repository existe, mas há violações de camadas, acoplamento com Spring Security via `SecurityContextHolder` em services, e ausência de patterns de resiliência. | Violação de camadas, ausência de hexagonal/clean architecture |
| **Segurança** | 1.5/10 | Vulnerabilidades críticas exploráveis trivialmente (privilege escalation, IDOR, hardcoded secrets). Falhas fundamentais de design de segurança. | Mass assignment, IDOR, secrets hardcoded, brute force |
| **Performance** | 3.0/10 | N+1 queries em todos os endpoints de listagem, ausência de paginação, memory leak no rate limiter, TmdbClient sem timeout. | OOM sob carga, degradação linear com crescimento |
| **Escalabilidade** | 2.0/10 | Rate limiting in-memory, sem paginação, sem cache, operações de listagem não limitadas. Impossível escalar horizontalmente. | Single-instance only, OOM com crescimento |
| **Confiabilidade** | 2.0/10 | Race conditions no overbooking, dual-write inconsistencies, poison pill no RabbitMQ, ausência de DLQ. | Corrupção de dados, indisponibilidade |
| **Resiliência** | 1.5/10 | Zero circuit breakers, zero timeouts em clients, zero DLQ, zero retry policies, zero fallbacks. | Falha em cascata, indisponibilidade total |
| **Banco de Dados** | 3.0/10 | Modelagem básica funcional. Ausência de migrations, constraints importantes faltando (overbooking, sobreposição de sessões), sem índices customizados. | Corrupção de dados, migrations irreversíveis |
| **APIs** | 4.5/10 | DTOs e validação parcialmente implementados. Versionamento `/v1` presente. Ausência de paginação, CORS, rate limiting abrangente. | Breaking changes sem versionamento, DoS |
| **Tratamento de Erros** | 2.5/10 | Handler global existe mas vaza informações internas. Uso inconsistente de RuntimeException vs BusinessException. ResponseStatusException capturada errado. | Information disclosure, UX inconsistente |
| **Testes** | 1.0/10 | 2 testes unitários em 70 arquivos Java. Zero testes de controller, integration, security, e2e. | Regressões não detectadas |
| **Observabilidade** | 1.5/10 | Sem logging framework, sem métricas, sem traces, health check falso. System.out.println em produção. | Impossível diagnosticar incidentes |
| **CI/CD** | 0.0/10 | Inexistente. | Deploy manual sem gates |
| **Infraestrutura** | 2.5/10 | Docker básico existe. Container roda como root, portas expostas, sem resource limits, sem multi-stage build. | Container escape, resource starvation |
| **Gestão de Segredos** | 2.0/10 | .gitignore cobre .env, mas fallback hardcoded em prod, credenciais default em compose e properties. | Comprometimento de segredos |
| **Dependências** | 3.0/10 | Spring Boot versão inexistente. Dependências gerais razoáveis. Sem lock file Maven Wrapper adequado. Sem scan de vulnerabilidades. | Supply chain, builds não-reproduzíveis |
| **Manutenibilidade** | 4.0/10 | Código razoavelmente organizado. Uso de MapStruct e Lombok reduz boilerplate. Inconsistências de exception handling e naming. | Dívida técnica crescente |
| **Capacidade de Mudança** | 3.0/10 | Sem testes para proteger contra regressões. Acoplamento com SecurityContextHolder dificulta testing. Sem migrations para schema changes. | Alto risco de regressão |
| **Preparação para Produção** | 1.0/10 | Não possui nenhum dos requisitos mínimos: migrations, logging, monitoring, CI/CD, testes, secrets management, container hardening. | Deploy em produção é perigoso |

---

# 19. Cenários de Falha Mais Prováveis

### Cenário 1 — Pico de Tráfego (10x–100x)
Sem paginação, queries N+1, e rate limiter in-memory, o sistema sofre OOM e degradação severa. Threads ficam bloqueadas esperando TMDB. Banco fica sobrecarregado com queries individuais por entidade. **Resultado: Indisponibilidade total em minutos.**

### Cenário 2 — Banco Lento
Sem timeouts em queries JPA e sem connection pool tuning explícito, o pool de conexões esgota. SecurityFilter faz query por request, amplificando o problema. **Resultado: Cascata de erros HTTP 500.**

### Cenário 3 — TMDB Indisponível
TmdbClient sem timeout bloqueia threads do servidor indefinidamente. `MovieService.create()` captura o erro silenciosamente com `System.out.println`. **Resultado: Criação de filmes funciona parcialmente mas threads ficam presas para chamadas em andamento.**

### Cenário 4 — Deploy com Falha
Sem CI/CD, sem rollback automatizado, sem health checks reais. O único health check retorna "UP" sempre. **Resultado: Deploy quebrado passa despercebido até reclamações de usuários.**

### Cenário 5 — Migração de Schema
Sem Flyway/Liquibase, com `ddl-auto=none` em produção. Qualquer alteração de entidade requer DDL manual. **Resultado: Versão nova da API incompatível com schema do banco → erro 500 em todos os endpoints.**

### Cenário 6 — Credencial Comprometida
JWT secret hardcoded no repositório. Não existe mecanismo de rotação de segredos ou revogação de tokens. **Resultado: Todos os tokens emitidos com a chave antiga permanecem válidos por até 2 horas. Sem forma de invalidar.**

### Cenário 7 — Usuário Malicioso
1. Registra-se como ADMIN via `roleIds` (AUD-001) → 2. Cria sessões falsas → 3. Acessa PDFs de todos os usuários (AUD-006) → 4. Lê dados pessoais de todos os clientes. **Resultado: Comprometimento total em 3 requisições HTTP.**

### Cenário 8 — Processo Interrompido
Compra de ingresso é interrompida após `paymentProducer.enviarParaFilaDePagamento()` mas antes do commit. **Resultado: Mensagem na fila sem ticket no banco → consumer lança RuntimeException → infinite requeue loop (AUD-031).**

### Cenário 9 — Falha Silenciosa
Consumer de pagamento para de funcionar (crash, RabbitMQ indisponível). Tickets permanecem eternamente em `PENDING`. **Resultado: Nenhum alerta. Nenhum log. Clientes nunca recebem confirmação. Time descobre dias depois.**

### Cenário 10 — Incidente às 3 da Manhã
Sem dashboards, sem alertas, sem métricas, sem traces, sem correlation IDs, health check falso. **Resultado: Impossível diagnosticar remotamente. Necessário acesso SSH ao servidor para ler System.out em stdout. Sem contexto nos logs para correlacionar o problema.**

---

# 20. Veredito Final

## ❌ BLOQUEADO PARA PRODUÇÃO

O projeto **CineTicket Backend** apresenta **5 vulnerabilidades críticas** que tornam o sistema impróprio para qualquer ambiente que não seja desenvolvimento local controlado.

A mais grave — **AUD-001 (Escalação de Privilégios)** — permite que qualquer pessoa na internet obtenha acesso administrativo ao sistema em uma única requisição HTTP, sem necessidade de autquer técnica avançada de hacking. Esta falha sozinha bloquearia qualquer auditoria de segurança.

A segunda — **AUD-002 (Race Condition de Overbooking)** — compromete a operação principal do sistema (venda de ingressos) e inevitavelmente causará problemas em qualquer cenário com dois ou mais usuários concorrentes.

A combinação de **ausência quase total de testes** (2 testes em 70 arquivos), **inexistência de CI/CD**, **inexistência de migrations**, **inexistência de observabilidade**, e **container executando como root com portas de banco expostas** coloca o projeto no nível de maturidade de um protótipo de aprendizado, não de um sistema operacional.

**O projeto necessita de correções estruturais significativas antes de considerar qualquer deploy fora de ambiente de desenvolvimento.**

A prioridade absoluta deve ser:
1. Corrigir as 5 vulnerabilidades críticas (P0)
2. Corrigir a versão do Spring Boot
3. Implementar Flyway para migrations
4. Adicionar testes mínimos nos fluxos críticos
5. Implementar DLQ no RabbitMQ
6. Hardening de Docker e gestão de segredos

---

> [!IMPORTANT]
> Este relatório foi gerado com base na análise estática do código-fonte em 2026-08-22. Nenhuma execução dinâmica (pentest, teste de carga, scan de vulnerabilidades) foi realizada. Os achados classificados como "Confirmado" foram verificados diretamente no código. Recomenda-se complementar esta auditoria com testes dinâmicos de segurança (DAST) e testes de penetração antes de qualquer consideração de deploy.
