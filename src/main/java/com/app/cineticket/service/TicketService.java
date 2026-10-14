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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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

    @Value("${payments.enabled:false}")
    private boolean paymentsEnabled;

    /** Prazo para pagar um ingresso PENDING antes de ele expirar e liberar o assento. */
    @Value("${tickets.pending.ttl:10m}")
    private Duration pendingTtl = Duration.ofMinutes(10);

    /** Fração dos lugares de cada sessão que pode ser vendida como meia-entrada (Lei 12.933/2013: 40%). */
    @Value("${tickets.half-price.quota:0.40}")
    private double halfPriceQuota = 0.40;

    private static final int SENIOR_AGE = 60;
    private static final List<TicketType> HALF_PRICE_TYPES = List.of(TicketType.MEIA_ESTUDANTE, TicketType.MEIA_IDOSO);

    /** Máximo de ingressos aguardando pagamento por usuário (evita travar a sala inteira). */
    @Value("${tickets.pending.max-per-user:4}")
    private int maxPendingPerUser = 4;

    @Transactional(readOnly = true)
    public byte[] downloadPdf(Long ticketId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }

        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new BusinessException("Ingresso não aprovado ainda.");
        }

        return pdfService.generateTicketPdf(ticket);
    }

    @Transactional
    public TicketResponseDTO buyTicket(TicketRequestDTO request) {
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

        List<TicketStatus> statusAtivos = List.of(TicketStatus.APPROVED, TicketStatus.PENDING);
        if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(request.sessionId(), request.seatId(), statusAtivos)) {
            throw new ConflictException("Esta cadeira já está ocupada para esta sessão.");
        }

        if (!paymentsEnabled) {
            throw new BusinessException("Compra de ingressos indisponível até a configuração de um gateway de pagamento seguro");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        User loggedUser = (User) auth.getPrincipal();

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

        BigDecimal valorSessao = session.getValorBase();

        if (request.ticketType() == TicketType.INTEIRA) {
            ticket.setValorPago(valorSessao);
        } else {
            ticket.setValorPago(valorSessao.divide(new BigDecimal("2"),
                    RoundingMode.HALF_UP));
        }

        ticket.setTicketType(request.ticketType());

        ticket.setStatus(TicketStatus.PENDING);
        ticket.setExpiresAt(LocalDateTime.now().plus(pendingTtl));

        // Só reserva o assento: o pagamento é feito em POST /tickets/{id}/payment.
        Ticket savedTicket = ticketRepository.save(ticket);

        return ticketMapper.toResponseDTO(savedTicket);
    }

    /**
     * Regras da meia-entrada. Devolve o documento normalizado (estudante) ou null.
     * <ul>
     *   <li>Idoso: 60 anos ou mais na data da sessão, pela data de nascimento do perfil.</li>
     *   <li>Estudante: número da carteira de estudante, conferido na entrada.</li>
     *   <li>Cota: no máximo 40% dos lugares da sessão como meia (Lei 12.933/2013).</li>
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
            documento = request.documentoMeia() == null ? "" : request.documentoMeia().trim().toUpperCase(java.util.Locale.ROOT);
            if (documento.length() < 6) {
                throw new BusinessException("Para meia-entrada de estudante, informe o número da carteira de estudante "
                        + "(documentoMeia). Ele será conferido na entrada.");
            }
        }

        // Lock na sessão: duas compras de meia simultâneas não estouram a cota.
        sessionRepository.findByIdWithLock(session.getId());
        int capacity = session.getRoom().getCapacidade() == null ? 0 : session.getRoom().getCapacidade();
        long quota = (long) Math.floor(capacity * halfPriceQuota);
        long sold = ticketRepository.countBySessionIdAndTicketTypeInAndStatusIn(
                session.getId(), HALF_PRICE_TYPES, List.of(TicketStatus.PENDING, TicketStatus.APPROVED));
        if (sold >= quota) {
            throw new ConflictException("Cota de meia-entrada esgotada para esta sessão ("
                    + quota + " de " + capacity + " lugares). A inteira continua disponível.");
        }
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

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }

        return ticketMapper.toResponseDTO(ticket);
    }

    @Transactional
    public TicketResponseDTO cancelTicket(Long id, User loggedUser) {
        Ticket ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
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
