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
    public static final String DLQ_PAGAMENTOS = "pagamentos.v1.queue.dlq";
    public static final String EXCHANGE_CINETICKET = "cineticket.direct.change";
    public static final String DLX_CINETICKET = "cineticket.dlx";
    public static final String ROUTING_KEY_PAGAMENTO = "pagamento.routingKey";
    public static final String ROUTING_KEY_DLQ = "pagamento.dlq.routingKey";
    public static final String FILA_REEMBOLSOS = "reembolsos.v1.queue";
    public static final String ROUTING_KEY_REEMBOLSO = "reembolso.routingKey";

    @Bean
    public Queue pagamentosQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(FILA_PAGAMENTOS)
                .withArgument("x-dead-letter-exchange", DLX_CINETICKET)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                .build();
    }

    @Bean
    public Queue pagamentosDlq() {
        return org.springframework.amqp.core.QueueBuilder.durable(DLQ_PAGAMENTOS).build();
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_CINETICKET);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_CINETICKET);
    }

    @Bean
    public Binding binding(Queue pagamentosQueue, DirectExchange exchange) {
        return BindingBuilder.bind(pagamentosQueue).to(exchange).with(ROUTING_KEY_PAGAMENTO);
    }

    @Bean
    public Queue reembolsosQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(FILA_REEMBOLSOS).build();
    }

    @Bean
    public Binding reembolsoBinding(Queue reembolsosQueue, DirectExchange exchange) {
        return BindingBuilder.bind(reembolsosQueue).to(exchange).with(ROUTING_KEY_REEMBOLSO);
    }

    @Bean
    public Binding dlqBinding(Queue pagamentosDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(pagamentosDlq).to(deadLetterExchange).with(ROUTING_KEY_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
