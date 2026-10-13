package com.app.cineticket.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Popula o demo quando a aplicação termina de subir (depois do Flyway e do admin)
 * e faz o reset diário. Uma falha aqui é registrada, mas não derruba a API.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoLifecycle {

    private final DemoDataService demoDataService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        try {
            demoDataService.seed();
        } catch (Exception e) {
            log.error("[DEMO] Falha ao popular o ambiente de demonstração.", e);
        }
    }

    @Scheduled(cron = "${app.demo.reset-cron:0 0 4 * * *}")
    public void dailyReset() {
        try {
            demoDataService.reset();
        } catch (Exception e) {
            log.error("[DEMO] Falha no reset diário.", e);
        }
    }
}
