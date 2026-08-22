package com.app.cineticket.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FILA_PAGAMENTOS = "pagamentos.v1.queue";
    public static final String EXCHANGE_CINETICKET = "cineticket.direct.change";
    public static final String ROUTING_KEY_PAGAMENTO = "pagamento.routingKey";

    @Bean
    public Queue pagamentosQueue() {
        return new Queue(FILA_PAGAMENTOS, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_CINETICKET);
    }

    @Bean
    public Binding binding(Queue pagamentosQueue, DirectExchange exchange) {
        return BindingBuilder.bind(pagamentosQueue).to(exchange).with(ROUTING_KEY_PAGAMENTO);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
