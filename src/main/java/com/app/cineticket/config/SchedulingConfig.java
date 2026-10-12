package com.app.cineticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita as tarefas agendadas (ex.: expiração de ingressos pendentes). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
