package com.app.cineticket.service;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.response.TicketQrCodeDTO;
import com.app.cineticket.dto.response.TicketValidationResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Entrada na sala: valida o QR Code e marca o ingresso como usado, uma única vez.
 * O lock pessimista no ingresso garante que duas leituras simultâneas do mesmo QR
 * (dois funcionários, ou um print compartilhado) não deixam entrar duas pessoas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketValidationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketQrCodeService qrCodeService;

    /** Quanto tempo antes do início da sessão a entrada é liberada. */
    @Value("${app.tickets.validation.opens-before:60m}")
    private Duration opensBefore = Duration.ofMinutes(60);

    @Transactional
    public TicketValidationResponseDTO validate(String qr, User staff) {
        long ticketId = qrCodeService.verify(qr);

        Ticket ticket = ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));

        if (ticket.getUsedAt() != null) {
            throw new ConflictException("Ingresso já utilizado em " + ticket.getUsedAt().format(TIME) + ".");
        }
        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new ConflictException("Ingresso não é válido para entrada (status: " + ticket.getStatus() + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = ticket.getSession().getHorarioInicio();
        LocalDateTime opensAt = start.minus(opensBefore);
        LocalDateTime endsAt = start.plusMinutes(ticket.getSession().getMovie().getDuracaoEmMinutos());
        if (now.isBefore(opensAt)) {
            throw new BusinessException("Entrada ainda não liberada: abre em " + opensAt.format(TIME) + ".");
        }
        if (now.isAfter(endsAt)) {
            throw new BusinessException("Esta sessão já terminou.");
        }

        ticket.setUsedAt(now);
        ticket.setUsedBy(userRepository.getReferenceById(staff.getId()));
        ticketRepository.save(ticket);
        log.info("[ENTRADA] Ingresso {} validado por {}.", ticket.getId(), staff.getEmail());

        return new TicketValidationResponseDTO(
                ticket.getId(),
                ticket.getSession().getMovie().getTitulo(),
                ticket.getSession().getRoom().getCinema().getNome(),
                ticket.getSession().getRoom().getNome(),
                ticket.getSeat().getFila() + "-" + ticket.getSeat().getNumero(),
                start,
                ticket.getTicketType(),
                alertFor(ticket.getTicketType()),
                now);
    }

    /** Conteúdo do QR do próprio ingresso (somente aprovado). */
    @Transactional(readOnly = true)
    public TicketQrCodeDTO qrCodeFor(Long ticketId, User loggedUser) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));
        if (!ticket.getUser().getId().equals(loggedUser.getId())) {
            throw new ForbiddenException("Você não tem permissão para acessar este ingresso.");
        }
        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new BusinessException("Ingresso não aprovado ainda.");
        }
        return new TicketQrCodeDTO(ticket.getId(), qrCodeService.payloadFor(ticket.getId()));
    }

    private static String alertFor(TicketType type) {
        return switch (type) {
            case MEIA_ESTUDANTE -> "Meia-entrada (estudante): conferir a carteira de estudante.";
            case MEIA_IDOSO -> "Meia-entrada (idoso): conferir documento com foto.";
            case INTEIRA -> null;
        };
    }
}
