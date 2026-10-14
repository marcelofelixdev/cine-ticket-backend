package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.mapper.TicketMapper;
import com.app.cineticket.messaging.OutboxService;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final SessionRepository sessionRepository;
    private final SeatRepository seatRepository;
    private final OutboxService outboxService;
    private final TicketMapper ticketMapper;
    private final com.app.cineticket.service.PdfService pdfService;
    private final HalfPricePolicy halfPricePolicy;

    @Value("${payments.enabled:false}")
    private boolean paymentsEnabled;

    /** Prazo para pagar um ingresso PENDING antes de ele expirar e liberar o assento. */
    @Value("${tickets.pending.ttl:10m}")
    private Duration pendingTtl = Duration.ofMinutes(10);

    private static final int SENIOR_AGE = 60;

    /** Máximo de ingressos aguardando pagamento por usuário (evita travar a sala inteira). */
    @Value("${tickets.pending.max-per-user:4}")
    private int maxPendingPerUser = 4;

    @Transactional(readOnly = true)
    public byte[] downloadPdf(Long ticketId, User loggedUser) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (!ticket.isOwnedBy(loggedUser)) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }

        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new BusinessException("Ingresso não aprovado ainda.");
        }

        return pdfService.generateTicketPdf(ticket);
    }

    @Transactional
    public TicketResponseDTO buyTicket(TicketRequestDTO request, User loggedUser) {
        var seat = seatRepository.findByIdWithLock(request.seatId())
                .orElseThrow(() -> new ResourceNotFoundException("Cadeira não encontrada."));

        var session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));

        if (!Boolean.TRUE.equals(session.getAtivo())) {
            throw new BusinessException("Esta sessão não está ativa");
        }

        if (!session.getHorarioInicio().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Não é possível comprar ingresso para uma sessão iniciada ou encerrada");
        }

        if (!seat.getRoom().getId().equals(session.getRoom().getId())) {
            throw new BusinessException("A cadeira solicitada não pertence à sala desta sessão.");
        }

        if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(request.sessionId(), request.seatId(),
                TicketStatus.OCCUPYING_SEAT)) {
            throw new ConflictException("Esta cadeira já está ocupada para esta sessão.");
        }

        if (!paymentsEnabled) {
            throw new BusinessException("Compra de ingressos indisponível até a configuração de um gateway de pagamento seguro");
        }

        long pendingTickets = ticketRepository.countByUserIdAndStatus(loggedUser.getId(), TicketStatus.PENDING);
        if (pendingTickets >= maxPendingPerUser) {
            throw new BusinessException("Você já tem " + pendingTickets + " ingresso(s) aguardando pagamento. "
                    + "Conclua o pagamento ou aguarde a expiração antes de reservar outro.");
        }

        String documentoMeia = validateHalfPrice(request, session, loggedUser);

        Ticket ticket = ticketMapper.toEntity(request);
        ticket.setDocumentoMeia(documentoMeia);
        ticket.setUser(loggedUser);
        ticket.setSession(session);
        ticket.setSeat(seat);

        ticket.setValorPago(HalfPricePolicy.priceFor(session.getValorBase(), request.ticketType()));
        ticket.setTicketType(request.ticketType());

        ticket.setStatus(TicketStatus.PENDING);
        ticket.setExpiresAt(LocalDateTime.now().plus(pendingTtl));

        // Só reserva o assento: o pagamento é feito em POST /tickets/{id}/payment.
        Ticket savedTicket = ticketRepository.save(ticket);

        return ticketMapper.toResponseDTO(savedTicket);
    }

    /**
     * Regras da meia-entrada na compra online. Devolve o documento normalizado (estudante) ou null.
     * <ul>
     *   <li>Idoso: 60 anos ou mais na data da sessão, pela data de nascimento do perfil.</li>
     *   <li>Estudante e cota: {@link HalfPricePolicy}, as mesmas regras do caixa.</li>
     * </ul>
     */
    private String validateHalfPrice(TicketRequestDTO request, com.app.cineticket.domain.entity.Session session,
                                     User buyer) {
        if (request.ticketType() == TicketType.INTEIRA) {
            return null;
        }

        String documento = null;
        if (request.ticketType() == TicketType.MEIA_IDOSO) {
            if (buyer.getDataNascimento() == null) {
                throw new BusinessException("Para meia-entrada de idoso, informe sua data de nascimento no perfil "
                        + "(PATCH /api/v1/users/me).");
            }
            int age = java.time.Period.between(buyer.getDataNascimento(),
                    session.getHorarioInicio().toLocalDate()).getYears();
            if (age < SENIOR_AGE) {
                throw new BusinessException("Meia-entrada de idoso é para quem tem " + SENIOR_AGE
                        + " anos ou mais na data da sessão.");
            }
        } else {
            documento = HalfPricePolicy.studentDocument(request.documentoMeia());
        }

        // Lock na sessão: duas compras de meia simultâneas não estouram a cota.
        sessionRepository.findByIdWithLock(session.getId());
        halfPricePolicy.requireQuota(session, 1);
        return documento;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TicketResponseDTO> findMyTickets(User loggedUser, org.springframework.data.domain.Pageable pageable) {
        return ticketRepository.findByUserId(loggedUser.getId(), pageable)
                .map(ticketMapper::toResponseDTO);
    }

    /** Detalhe de um ingresso do próprio usuário (ex.: acompanhar a aprovação do pagamento). */
    @Transactional(readOnly = true)
    public TicketResponseDTO findById(Long id, User loggedUser) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (!ticket.isOwnedBy(loggedUser)) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }

        return ticketMapper.toResponseDTO(ticket);
    }

    @Transactional
    public TicketResponseDTO cancelTicket(Long id, User loggedUser) {
        Ticket ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (!ticket.isOwnedBy(loggedUser)) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }

        TicketStatus previousStatus = ticket.getStatus();
        if (previousStatus == TicketStatus.EXPIRED) {
            throw new ConflictException("O ingresso expirou por falta de pagamento.");
        }
        if (previousStatus != TicketStatus.PENDING && previousStatus != TicketStatus.APPROVED) {
            throw new ConflictException("O ingresso já está cancelado.");
        }
        if (ticket.getUsedAt() != null) {
            throw new ConflictException("Ingresso já utilizado na entrada não pode ser cancelado.");
        }
        if (ticket.getCanal() == com.app.cineticket.domain.enums.TicketChannel.BALCAO) {
            throw new BusinessException("Ingresso comprado no balcão: o cancelamento e a devolução do valor "
                    + "são feitos no caixa do cinema.");
        }

        LocalDateTime deadlineCancelamento = ticket.getSession().getHorarioInicio().minusMinutes(30);

        if (LocalDateTime.now().isAfter(deadlineCancelamento)) {
            throw new BusinessException("Cancelamento negado! " +
                    "Só é possível cancelar ingressos com no mínimo 30 minutos de antecedência.");
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        ticketRepository.save(ticket);

        if (previousStatus == TicketStatus.APPROVED) {
            log.info("Ticket {} cancelado. Preparando reembolso...", ticket.getId());
            outboxService.refundRequested(new com.app.cineticket.dto.request.RefundEventDTO(
                    ticket.getId(), ticket.getPaymentIntentId(), ticket.getValorPago()));
        }

        return ticketMapper.toResponseDTO(ticket);
    }
}
