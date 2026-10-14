package com.app.cineticket.metrics;

import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O Prometheus local consegue ler as métricas de negócio (perfil dev: endpoint liberado). */
class MetricsEndpointIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BusinessMetrics businessMetrics;

    @Test
    void endpointDoPrometheusExpoeAsMetricasDeNegocio() throws Exception {
        businessMetrics.refresh();

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cineticket_tickets{")))
                .andExpect(content().string(containsString("status=\"APPROVED\"")))
                .andExpect(content().string(containsString("cineticket_refunds{")))
                .andExpect(content().string(containsString("canal=\"BALCAO\"")))
                .andExpect(content().string(containsString("cineticket_outbox_pending")))
                .andExpect(content().string(containsString("cineticket_outbox_oldest_pending_seconds")))
                .andExpect(content().string(containsString("application=\"cineticket\"")));
    }

    @Test
    void latenciaHttpTemHistogramaParaOp95() throws Exception {
        mockMvc.perform(get("/api/v1/movies")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")));
    }
}
