package com.app.cineticket.service;

import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.dto.request.BoxOfficeSaleRequestDTO;
import com.app.cineticket.dto.response.BoxOfficeSaleResponseDTO;
import com.app.cineticket.exception.BusinessException;
import com.app.cineticket.exception.ConflictException;
import com.app.cineticket.exception.ForbiddenException;
import com.app.cineticket.exception.ResourceNotFoundException;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Venda no caixa do cinema: o funcionário (ou gerente) vende lugares de uma sessão do
 * próprio cinema, pagos na hora. Os ingressos já nascem aprovados, sem passar pelo Stripe.
 * <p>
 * A sessão fica travada durante a venda (lock pessimista): vendas simultâneas da mesma
 * sessão no caixa são feitas uma depois da outra, e a cota de meia é contada sem corrida.
 * Contra a compra online do mesmo lugar valem o lock no assento e o índice anti-overbooking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoxOfficeService {

    private final SessionRepository sessionRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketQrCodeService qrCodeService;
    private final PdfService pdfService;
    private final HalfPricePolicy halfPricePolicy;

    /** Até quanto tempo depois do início da sessão o caixa ainda vende. */
    @Value("${app.box-office.sales-close-after-start:30m}")
    private Duration salesCloseAfterStart = Duration.ofMinutes(30);

    @Transactional
    public BoxOfficeSaleResponseDTO sell(BoxOfficeSaleRequestDTO request, User seller) {
        Session session = sessionRepository.findByIdWithLock(request.sessionId())
                .filter(found -> Boolean.TRUE.equals(found.getAtivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
        CinemaScope.requireSameCinema(seller, session.getRoom().getCinema().getId(), "Esta sessão é de outro cinema.");
        if (LocalDateTime.now(AppTimeZone.ZONE).isAfter(session.getHorarioInicio().plus(salesCloseAfterStart))) {
            throw new BusinessException("As vendas desta sessão já foram encerradas.");
        }

        List<BoxOfficeSaleRequestDTO.Item> itens = request.itens().stream()
                .sorted(Comparator.comparing(BoxOfficeSaleRequestDTO.Item::seatId))
                .toList();
        if (new HashSet<>(itens.stream().map(BoxOfficeSaleRequestDTO.Item::seatId).toList()).size() != itens.size()) {
            throw new BusinessException("O mesmo lugar foi informado mais de uma vez.");
        }
        halfPricePolicy.requireQuota(session,
                itens.stream().filter(item -> HalfPricePolicy.isHalfPrice(item.ticketType())).count());

        String email = request.clienteEmail() == null || request.clienteEmail().isBlank()
                ? null : request.clienteEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> buyer = email == null ? Optional.empty() : userRepository.findByEmailIgnoreCase(email);
        User sellerRef = userRepository.getReferenceById(seller.getId());

        List<Ticket> tickets = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        // Em ordem de assento: duas vendas que disputam os mesmos lugares travam na mesma ordem.
        for (BoxOfficeSaleRequestDTO.Item item : itens) {
            Seat seat = seatRepository.findByIdWithLock(item.seatId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cadeira não encontrada. ID: " + item.seatId()));
            String assento = seat.label();
            if (!seat.getRoom().getId().equals(session.getRoom().getId())) {
                throw new BusinessException("A cadeira " + assento + " não é da sala desta sessão.");
            }
            if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(session.getId(), seat.getId(),
                    TicketStatus.OCCUPYING_SEAT)) {
                throw new ConflictException("A cadeira " + assento + " já está ocupada.");
            }

            Ticket ticket = new Ticket();
            ticket.setSession(session);
            ticket.setSeat(seat);
            ticket.setUser(buyer.orElse(null));
            ticket.setClienteEmail(email);
            ticket.setTicketType(item.ticketType());
            ticket.setDocumentoMeia(item.ticketType() == TicketType.MEIA_ESTUDANTE
                    ? HalfPricePolicy.studentDocument(item.documentoMeia()) : null);
            ticket.setValorPago(HalfPricePolicy.priceFor(session.getValorBase(), item.ticketType()));
            ticket.setStatus(TicketStatus.APPROVED);
            ticket.setCanal(TicketChannel.BALCAO);
            ticket.setFormaPagamento(request.formaPagamento());
            ticket.setVendidoPor(sellerRef);
            tickets.add(ticket);
            total = total.add(ticket.getValorPago());
        }
        // saveAllAndFlush: se o índice anti-overbooking barrar um lugar, a venda inteira volta.
        List<Ticket> saved = ticketRepository.saveAllAndFlush(tickets);
        log.info("[CAIXA] {} vendeu {} ingresso(s) da sessão {} ({}, R$ {}).", seller.getEmail(), saved.size(),
                session.getId(), request.formaPagamento(), total);

        return new BoxOfficeSaleResponseDTO(
                session.getId(),
                session.getMovie().getTitulo(),
                session.getRoom().getNome(),
                session.getHorarioInicio(),
                request.formaPagamento(),
                total,
                saved.stream()
                        .map(ticket -> new BoxOfficeSaleResponseDTO.Ingresso(
                                ticket.getId(),
                                ticket.getSeat().label(),
                                ticket.getTicketType(),
                                ticket.getValorPago(),
                                qrCodeService.payloadFor(ticket.getId())))
                        .toList());
    }

    /** PDF de um ingresso vendido no balcão do próprio cinema, para imprimir. */
    @Transactional(readOnly = true)
    public byte[] pdf(Long ticketId, User seller) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingresso não encontrado."));
        String notAllowed = "Só é possível imprimir ingressos vendidos no balcão deste cinema.";
        if (ticket.getCanal() != TicketChannel.BALCAO) {
            throw new ForbiddenException(notAllowed);
        }
        CinemaScope.requireSameCinema(seller, ticket.getSession().getRoom().getCinema().getId(), notAllowed);
        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new BusinessException("Ingresso cancelado não pode ser impresso.");
        }
        return pdfService.generateTicketPdf(ticket);
    }

}
