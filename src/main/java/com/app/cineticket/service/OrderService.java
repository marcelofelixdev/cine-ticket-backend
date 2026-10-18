package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.TicketOrder;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.OrderStatus;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.BoxOfficeSaleRequestDTO;
import com.app.cineticket.dto.request.OrderRequestDTO;
import com.app.cineticket.dto.request.PaymentEventDTO;
import com.app.cineticket.dto.request.RefundEventDTO;
import com.app.cineticket.dto.response.OrderResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.PaymentDeclinedException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.payment.Charge;
import com.app.cineticket.payment.PaymentGateway;
import com.app.cineticket.payment.PaymentOutcome;
import com.app.cineticket.payment.PaymentResult;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketOrderRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Compra online por pedido: os lugares de uma sessão são reservados juntos (todos ou nenhum)
 * e pagos numa cobrança só. A aprovação é assíncrona, como no ingresso avulso: o pagamento
 * passa pelo outbox e pelo RabbitMQ antes de aprovar os ingressos.
 */
@Slf4j
@Service
public class OrderService {

    private final TicketOrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SeatAllocator seatAllocator;
    private final HalfPricePolicy halfPricePolicy;
    private final PaymentGateway paymentGateway;
    private final OutboxService outboxService;
    private final boolean paymentsEnabled;
    private final Duration pendingTtl;
    private final int maxSeatsPerOrder;

    public OrderService(TicketOrderRepository orderRepository, TicketRepository ticketRepository,
                        SessionRepository sessionRepository, UserRepository userRepository,
                        SeatAllocator seatAllocator, HalfPricePolicy halfPricePolicy,
                        PaymentGateway paymentGateway, OutboxService outboxService,
                        @Value("${payments.enabled:false}") boolean paymentsEnabled,
                        @Value("${tickets.pending.ttl:10m}") Duration pendingTtl,
                        @Value("${tickets.pending.max-per-user:4}") int maxSeatsPerOrder) {
        this.orderRepository = orderRepository;
        this.ticketRepository = ticketRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.seatAllocator = seatAllocator;
        this.halfPricePolicy = halfPricePolicy;
        this.paymentGateway = paymentGateway;
        this.outboxService = outboxService;
        this.paymentsEnabled = paymentsEnabled;
        this.pendingTtl = pendingTtl;
        this.maxSeatsPerOrder = maxSeatsPerOrder;
    }

    /**
     * Máximo de lugares por pedido. É o mesmo limite de ingressos aguardando pagamento por
     * pessoa, para ninguém travar uma sala inteira sem pagar. O mapa da sala informa ao site.
     */
    public int maxSeatsPerOrder() {
        return maxSeatsPerOrder;
    }

    @Transactional
    public OrderResponseDTO create(OrderRequestDTO request, User buyer) {
        TicketService.requirePaymentsEnabled(paymentsEnabled);
        // Lock na sessão: duas compras simultâneas não estouram a cota de meia.
        Session session = sessionRepository.findByIdWithLock(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
        TicketService.requireOnlineSaleOpen(session);

        List<BoxOfficeSaleRequestDTO.Item> itens = request.itens();
        if (itens.size() > maxSeatsPerOrder) {
            throw new BusinessException("Escolha no máximo " + maxSeatsPerOrder + " lugares por compra.");
        }
        long pending = ticketRepository.countByUserIdAndStatus(buyer.getId(), TicketStatus.PENDING);
        if (pending + itens.size() > maxSeatsPerOrder) {
            throw new BusinessException("Você já tem " + pending + " ingresso(s) aguardando pagamento. "
                    + "Conclua o pagamento ou aguarde a expiração antes de reservar mais lugares.");
        }

        User owner = userRepository.findById(buyer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        List<String> documentos = itens.stream().map(item -> validateItem(item, session, owner)).toList();
        halfPricePolicy.requireQuota(session,
                itens.stream().filter(item -> HalfPricePolicy.isHalfPrice(item.ticketType())).count());
        Map<Long, Seat> seats = seatAllocator.lockFreeSeats(session,
                itens.stream().map(BoxOfficeSaleRequestDTO.Item::seatId).toList());

        LocalDateTime expiresAt = LocalDateTime.now(AppTimeZone.ZONE).plus(pendingTtl);
        TicketOrder order = new TicketOrder();
        order.setUser(owner);
        order.setSession(session);
        order.setStatus(OrderStatus.PENDING);
        order.setExpiresAt(expiresAt);
        order.setTotal(BigDecimal.ZERO);
        orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            BoxOfficeSaleRequestDTO.Item item = itens.get(i);
            Ticket ticket = new Ticket();
            ticket.setTicketOrder(order);
            ticket.setUser(owner);
            ticket.setSession(session);
            ticket.setSeat(seats.get(item.seatId()));
            ticket.setTicketType(item.ticketType());
            ticket.setDocumentoMeia(documentos.get(i));
            ticket.setValorPago(HalfPricePolicy.priceFor(session.getValorBase(), item.ticketType()));
            ticket.setStatus(TicketStatus.PENDING);
            ticket.setExpiresAt(expiresAt);
            order.getTickets().add(ticket);
            total = total.add(ticket.getValorPago());
        }
        order.setTotal(total);
        // saveAllAndFlush: se o índice anti-overbooking barrar um lugar, o pedido inteiro volta.
        ticketRepository.saveAllAndFlush(order.getTickets());
        orderRepository.save(order);
        log.info("[ORDER] Pedido {} de {}: {} lugar(es) na sessão {}, R$ {}.",
                order.getId(), owner.getEmail(), itens.size(), session.getId(), total);
        return toResponse(order);
    }

    /** Meia estudante: carteira; meia 60+: idade pelo perfil. Devolve o documento normalizado ou null. */
    private static String validateItem(BoxOfficeSaleRequestDTO.Item item, Session session, User buyer) {
        if (item.ticketType() == TicketType.MEIA_IDOSO) {
            HalfPricePolicy.requireSenior(buyer, session);
            return null;
        }
        if (item.ticketType() == TicketType.MEIA_ESTUDANTE) {
            return HalfPricePolicy.studentDocument(item.documentoMeia());
        }
        return null;
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO findById(Long id, User loggedUser) {
        return toResponse(ownedOrder(id, loggedUser));
    }

    /** Uma cobrança pelo total do pedido. Responde com o pedido ainda PENDING; a aprovação é assíncrona. */
    @Transactional
    public OrderResponseDTO pay(Long id, String paymentMethodId, User loggedUser) {
        if (!paymentsEnabled) {
            throw new BusinessException("Pagamentos indisponíveis neste ambiente.");
        }
        // Lock no pedido: dois pagamentos simultâneos do mesmo pedido são serializados.
        TicketOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        if (!order.isOwnedBy(loggedUser)) {
            throw new ForbiddenException("Você não tem permissão para acessar este pedido.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ConflictException("Este pedido não está aguardando pagamento.");
        }
        if (!order.getExpiresAt().isAfter(LocalDateTime.now(AppTimeZone.ZONE))) {
            throw new ConflictException("O prazo de pagamento deste pedido expirou. Escolha os lugares de novo.");
        }
        if (order.getPaymentIntentId() != null) {
            throw new ConflictException("O pagamento deste pedido já foi enviado. Aguarde a confirmação.");
        }

        PaymentResult result = paymentGateway.charge(Charge.of(order), paymentMethodId);
        switch (result.outcome()) {
            case DECLINED -> throw new PaymentDeclinedException(result.message()
                    + " Os lugares continuam reservados até o fim do prazo; tente outro cartão.");
            case REQUIRES_ACTION -> throw new PaymentDeclinedException(
                    "Este cartão exige autenticação adicional (3-D Secure), que ainda não é suportada. Use outro cartão.");
            case SUCCEEDED, PROCESSING -> {
                order.setPaymentIntentId(result.paymentIntentId());
                orderRepository.save(order);
                if (!paymentGateway.confirmsViaWebhook() && result.outcome() == PaymentOutcome.SUCCEEDED) {
                    outboxService.paymentConfirmed(
                            PaymentEventDTO.ofOrder(order.getId(), result.paymentIntentId(), order.getTotal()));
                }
                log.info("[ORDER] Pedido {} cobrado ({}), aguardando confirmação.", order.getId(), result.outcome());
            }
        }
        return toResponse(order);
    }

    /**
     * Confirmação do pagamento (consumidor do RabbitMQ). Pedido pendente com o valor certo:
     * aprova os ingressos. Pedido expirado, já pago por outro pagamento ou valor diferente:
     * o dinheiro volta, ingresso por ingresso.
     */
    @Transactional
    public void confirmPayment(PaymentEventDTO event) {
        TicketOrder order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
        if (order == null) {
            // Pedido apagado (ex.: reset diário do demo). Tentar de novo não resolve.
            log.warn("[ORDER] Pagamento {} para pedido inexistente {}; ignorado.", event.paymentIntentId(), event.orderId());
            return;
        }
        String paymentIntentId = event.paymentIntentId();
        if (order.getStatus() == OrderStatus.PAID && paymentIntentId.equals(order.getPaymentIntentId())) {
            log.info("[ORDER] Pagamento {} do pedido {} já processado; ignorado.", paymentIntentId, order.getId());
            return;
        }
        boolean samePayment = order.getPaymentIntentId() == null || paymentIntentId.equals(order.getPaymentIntentId());
        if (order.getStatus() == OrderStatus.PENDING && samePayment && order.getTotal().compareTo(event.valor()) == 0) {
            ticketRepository.approvePendingOrderTickets(order.getId(), paymentIntentId);
            order.setStatus(OrderStatus.PAID);
            order.setPaymentIntentId(paymentIntentId);
            orderRepository.save(order);
            log.info("[ORDER] Pagamento {} aprovado. Pedido {} pago.", paymentIntentId, order.getId());
            return;
        }

        log.warn("[ORDER] Pagamento {} recebido para o pedido {} em estado {}: pedindo reembolso.",
                paymentIntentId, order.getId(), order.getStatus());
        for (Ticket ticket : order.getTickets()) {
            outboxService.refundRequested(new RefundEventDTO(ticket.getId(), paymentIntentId, ticket.getValorPago()));
        }
    }

    /** Pedidos pendentes vencidos viram EXPIRED (os ingressos deles expiram na mesma rotina). */
    @Transactional
    public int expirePendingOrders(LocalDateTime now) {
        return orderRepository.expirePendingOrders(now);
    }

    private TicketOrder ownedOrder(Long id, User loggedUser) {
        TicketOrder order = orderRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        if (!order.isOwnedBy(loggedUser)) {
            throw new ForbiddenException("Você não tem permissão para acessar este pedido.");
        }
        return order;
    }

    private static OrderResponseDTO toResponse(TicketOrder order) {
        Session session = order.getSession();
        return new OrderResponseDTO(
                order.getId(),
                order.getStatus(),
                session.getId(),
                session.getMovie().getTitulo(),
                session.getRoom().getNome(),
                session.getHorarioInicio(),
                order.getTotal(),
                order.getExpiresAt(),
                order.getTickets().stream()
                        .map(ticket -> new OrderResponseDTO.Ingresso(ticket.getId(), ticket.getSeat().label(),
                                ticket.getTicketType(), ticket.getValorPago(), ticket.getStatus()))
                        .toList());
    }
}
