package com.app.cineticket.messaging;

import com.app.cineticket.domain.enums.RefundStatus;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.service.RefundService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundConsumerTest {

    private static final RefundEventDTO EVENT = new RefundEventDTO(5L, "pi_1", new BigDecimal("40.00"));

    @Mock private RefundService refundService;

    @InjectMocks private RefundConsumer refundConsumer;

    @Test
    void reembolsoConcluidoConfirmaAMensagem() {
        when(refundService.process(EVENT)).thenReturn(RefundStatus.REFUNDED);

        assertDoesNotThrow(() -> refundConsumer.onRefundRequested(EVENT));
    }

    @Test
    void falhaDoGatewayPedeNovaTentativa() {
        when(refundService.process(EVENT)).thenReturn(RefundStatus.FAILED);

        assertThrows(RefundConsumer.RefundFailedException.class, () -> refundConsumer.onRefundRequested(EVENT));
    }

    @Test
    void pedidoSemIngressoVaiDiretoParaADlq() {
        RefundEventDTO invalid = new RefundEventDTO(null, "pi_1", BigDecimal.TEN);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> refundConsumer.onRefundRequested(invalid));
        verifyNoInteractions(refundService);
    }
}
