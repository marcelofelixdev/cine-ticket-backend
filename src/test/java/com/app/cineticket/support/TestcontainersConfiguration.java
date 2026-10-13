package com.app.cineticket.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL e RabbitMQ reais em containers, nas mesmas versões do docker-compose.
 * <p>
 * O {@code @ServiceConnection} liga o Spring aos containers sem configurar URL/porta à mão.
 * Os containers sobem uma vez e são compartilhados por todas as classes que estendem
 * {@link AbstractIntegrationTest} (mesmo contexto Spring em cache).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        // Usuário "postgres": a migração V1 (pg_dump) faz ALTER ... OWNER TO postgres.
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("cineticket")
                .withUsername("postgres")
                .withPassword("postgres");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));
    }
}
