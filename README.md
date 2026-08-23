# Cine Ticket Backend

## Projeto
Backend para um sistema de bilheteria de cinema (Cine Ticket), responsável por gerenciar cinemas, salas, filmes, sessões e venda de ingressos.

## Objetivo de Estudo
Desenvolver uma API RESTful robusta abordando conceitos avançados do ecossistema Java e Spring, como segurança (JWT), cache, mensageria assíncrona, versionamento de banco de dados, rate limiting e regras de negócio complexas (evitar overbooking de assentos).

## Ferramentas e Tecnologias
- Java 21
- Spring Boot 3.3.4 (Web, Data JPA, Security, Validation, AMQP, Actuator)
- PostgreSQL (Banco de dados principal)
- H2 Database (Banco de dados em memória para testes)
- Flyway (Migrações e versionamento do banco)
- MapStruct & Lombok (Mapeamento de DTOs e boilerplate)
- Caffeine (Cache de dados em memória)
- OpenPDF & ZXing (Geração de ingressos em PDF e QR Codes)
- Bucket4j (Rate Limiting para proteção contra abuso de API)
- Auth0 Java JWT (Autenticação e autorização)
- JUnit 5 & Mockito (Testes unitários e de integração)
- Docker & Docker Compose

## Rotas

### 1. Autenticação (Auth)
- `POST /api/v1/auth/login`
  - Request: `LoginRequestDTO { email, senha }`
  - Response (200): `LoginResponseDTO { token }`

### 2. Cinemas
- `POST /api/v1/cinemas`
  - Request: `CinemaRequestDTO { nome, cnpj, endereco }`
  - Response (201): `CinemaResponseDTO { id, nome, cnpj, endereco }`
- `GET /api/v1/cinemas`
  - Request: Query params `page`, `size`, `sort`
  - Response (200): `Page<CinemaResponseDTO>`
- `GET /api/v1/cinemas/{id}`
  - Response (200): `CinemaResponseDTO { id, nome, cnpj, endereco }`
- `PUT /api/v1/cinemas/{id}`
  - Request: `CinemaRequestDTO { nome, cnpj, endereco }`
  - Response (200): `CinemaResponseDTO { id, nome, cnpj, endereco }`
- `DELETE /api/v1/cinemas/{id}`
  - Response (204): No Content

### 3. Filmes (Movies)
- `POST /api/v1/movies`
  - Request: `MovieRequestDTO { titulo, duracaoEmMinutos }`
  - Response (201): `MovieResponseDTO { id, titulo, sinopse, tmdbId, posterUrl, duracaoEmMinutos }`
- `GET /api/v1/movies`
  - Request: Query params `page`, `size`, `sort`
  - Response (200): `Page<MovieResponseDTO>`
- `GET /api/v1/movies/{id}`
  - Response (200): `MovieResponseDTO { id, titulo, sinopse, tmdbId, posterUrl, duracaoEmMinutos }`
- `PUT /api/v1/movies/{id}`
  - Request: `MovieRequestDTO { titulo, duracaoEmMinutos }`
  - Response (200): `MovieResponseDTO { id, titulo, sinopse, tmdbId, posterUrl, duracaoEmMinutos }`
- `DELETE /api/v1/movies/{id}`
  - Response (204): No Content

### 4. Salas (Rooms)
- `POST /api/v1/rooms`
  - Request: `RoomRequestDTO { nome, capacidade, cinemaId }`
  - Response (201): `RoomResponseDTO { id, nome, capacidade, cinemaId }`
- `GET /api/v1/rooms`
  - Request: Query params `page`, `size`, `sort`
  - Response (200): `Page<RoomResponseDTO>`
- `PUT /api/v1/rooms/{id}`
  - Request: `RoomRequestDTO { nome, capacidade, cinemaId }`
  - Response (200): `RoomResponseDTO { id, nome, capacidade, cinemaId }`
- `DELETE /api/v1/rooms/{id}`
  - Response (204): No Content

### 5. Sessões (Sessions)
- `POST /api/v1/sessions`
  - Request: `SessionRequestDTO { horarioInicio, valorBase, movieId, roomId }`
  - Response (201): `SessionResponseDTO { id, horarioInicio, valorBase, movieId, roomId }`
- `GET /api/v1/sessions`
  - Request: Query params `page`, `size`, `sort`
  - Response (200): `Page<SessionResponseDTO>`
- `DELETE /api/v1/sessions/{id}` (Deleção de emergência)
  - Response (204): No Content

### 6. Ingressos (Tickets)
- `POST /api/v1/tickets`
  - Request: `TicketRequestDTO { sessionId, seatId, tipo (Ex: INTEIRA/MEIA), cartaoToken }`
  - Response (200): `TicketResponseDTO { id, sessionId, seatId, status, ... }`
- `GET /api/v1/tickets/my-tickets`
  - Request: Contexto de Segurança do Usuário Autenticado + Query Params Pageable
  - Response (200): `Page<TicketResponseDTO>`
- `GET /api/v1/tickets/{id}/pdf`
  - Response (200): Arquivo Binário (application/pdf) - Download do ingresso
- `PUT /api/v1/tickets/{id}/cancel`
  - Response (200): `TicketResponseDTO` (Atualizado com status cancelado)
- `DELETE /api/v1/tickets/{id}`
  - Response (204): No Content

### 7. Usuários (Users)
- `POST /api/v1/users`
  - Request: `UserRequestDTO { nome, email, senha }`
  - Response (201): `UserResponseDTO { id, nome, email }`

## Tratamentos de Erros
A aplicação utiliza a classe `ResourceExceptionHandler` (`@RestControllerAdvice`) para centralizar exceções e padronizar as respostas de erro da API:

- `BusinessException` (HTTP 400 - Bad Request): Retornado para violações de regras de negócio estritas. Exemplos identificados:
  - OVERBOOKING: Ocorre ao tentar realizar a compra para uma cadeira já ocupada naquela mesma sessão.
  - CANCELAMENTO TARDIO: Ocorre caso o usuário tente cancelar um ingresso faltando menos de 30 minutos para o início do filme.
- `MethodArgumentNotValidException` (HTTP 422 - Unprocessable Entity): Interceptado sempre que os DTOs falham na validação de atributos (`@NotBlank`, `@NotNull`, `@Future`). A API processa a lista de erros de campo e retorna uma mensagem amigável com todos os atributos inválidos concatenados.
- `ResponseStatusException` (HTTP 429 - Too Many Requests): Utilizado na lógica de Rate Limiter (via Bucket4j). É disparado quando um único IP excede o número permitido de tentativas de login, ou um único usuário excede o número permitido de compras de ingresso em um curto período, retornando a razão exata no corpo.
- `Exception` genérica (HTTP 500 - Internal Server Error): Captura comportamentos não esperados na aplicação, registrando o stacktrace no log interno, sem vazar detalhes críticos da infraestrutura para o cliente da API.

## Testes
A aplicação faz uso de JUnit 5 e Mockito para garantir a resiliência das regras da plataforma:

- Testes de Serviço (Unitários): Como visto no `TicketServiceTest`, o foco é isolar regras cruciais simulando os repositórios para testar comportamentos onde cadeiras vendidas bloqueiam novas vendas ou simulações da barreira temporal de cancelamentos pré-sessão.
- Testes de Controladores (Integração): Estruturados (como no `TicketControllerTest`) para levantar o contexto web do Spring, certificando que os retornos HTTP, formatações de dados JSON e mecanismos de bloqueio pelo Spring Security operem de forma harmônica.

## Planos para o Futuro
1. Integração Direta com Gateways de Pagamento: Substituição de mocks e envio de tokens (`cartaoToken`) via RabbitMQ para integração homologada via webhooks transacionais com provedores (Stripe / Mercado Pago / Pagar.me).
2. Notificações Ativas via E-mail: Processar a emissão do ingresso consumindo as filas do AMQP (RabbitMQ) e enviar o OpenPDF em anexo automaticamente utilizando o Spring Boot Mail.
3. Integração Oficial e Rotinas (Cron): Explorar o cliente implementado para a API externa do TMDB para sincronização diária de novos filmes em cartaz e seus respectivos trailers e posteres sem a necessidade de inclusão manual via API.
4. Deploy em Nuvem (CI/CD): Implementação do Github Actions para build e test, além do deploy automatizado utilizando os arquivos Docker gerados (`Dockerfile` e `docker-compose.yml`) em provedores em nuvem modernos como AWS ECS ou Google Cloud Run.
