package com.app.cineticket.support;

import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base dos testes de integração: aplicação completa (perfil dev) contra PostgreSQL e
 * RabbitMQ reais, com as migrações do Flyway aplicadas e {@code ddl-auto=validate}.
 * <p>
 * Assim os testes cobrem o que o H2 não cobria: CHECKs e índices das migrações
 * (inclusive o índice parcial anti-overbooking), locks pessimistas do PostgreSQL e o
 * fluxo de mensageria de verdade.
 * <p>
 * Todas as subclasses compartilham o mesmo contexto: não adicione propriedades ou
 * {@code @MockitoBean} nas subclasses, senão um novo contexto (e novos containers) sobe.
 */
@SpringBootTest(properties = {
        "api.security.token.secret=test_secret_for_ci_with_at_least_32_chars",
        "TMDB_API_KEY=test_tmdb_key",
        "app.bootstrap.admin.enabled=false",
        "app.bootstrap.staff.enabled=false",
        "app.bootstrap.manager.enabled=false",
        "payments.stripe.webhook-secret=" + AbstractIntegrationTest.WEBHOOK_SECRET,
        // Fixos aqui porque o .env local é importado pela aplicação: com PAYMENTS_PROVIDER=stripe
        // no .env, os testes cobrariam no Stripe de verdade e esperariam um webhook que não vem.
        "payments.provider=mock",
        "payments.stripe.secret-key="
})
@AutoConfigureMockMvc
// Por padrão os testes do Spring Boot desligam a exportação de métricas (o /actuator/prometheus
// responderia 404). Ligado aqui, na base, para não criar um segundo contexto só para isso.
@AutoConfigureObservability(tracing = false)
@Import({TestcontainersConfiguration.class, TestData.class})
public abstract class AbstractIntegrationTest {

    /** Signing secret do webhook do Stripe usado nos testes (o gateway continua simulado). */
    protected static final String WEBHOOK_SECRET = "whsec_test_cineticket";
}
