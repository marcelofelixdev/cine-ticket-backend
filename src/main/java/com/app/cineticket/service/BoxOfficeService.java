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
import java.math.RoundingMode;
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

    private static final List<TicketStatus> OCCUPYING = List.of(TicketStatus.PENDING, TicketStatus.APPROVED);
    private static final List<TicketType> HALF_PRICE = List.of(TicketType.MEIA_ESTUDANTE, TicketType.MEIA_IDOSO);
    private static final int MIN_STUDENT_DOCUMENT = 6;

    private final SessionRepository sessionRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketQrCodeService qrCodeService;
    private final PdfService pdfService;

    /** Fração dos lugares que pode ser meia-entrada (mesma propriedade da compra online). */
    @Value("${tickets.half-price.quota:0.40}")
    private double halfPriceQuota = 0.40;

    /** Até quanto tempo depois do início da sessão o caixa ainda vende. */
    @Value("${app.box-office.sales-close-after-start:30m}")
    private Duration salesCloseAfterStart = Duration.ofMinutes(30);

    @Transactional
    public BoxOfficeSaleResponseDTO sell(BoxOfficeSaleRequestDTO request, User seller) {
        Session session = sessionRepository.findByIdWithLock(request.sessionId())
                .filter(found -> Boolean.TRUE.equals(found.getAtivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada."));
        if (!ProgramacaoService.cinemaIdOf(seller).equals(session.getRoom().getCinema().getId())) {
            throw new ForbiddenException("Esta sessão é de outro cinema.");
        }
        if (LocalDateTime.now(AppTimeZone.ZONE).isAfter(session.getHorarioInicio().plus(salesCloseAfterStart))) {
            throw new BusinessException("As vendas desta sessão já foram encerradas.");
        }

        List<BoxOfficeSaleRequestDTO.Item> itens = request.itens().stream()
                .sorted(Comparator.comparing(BoxOfficeSaleRequestDTO.Item::seatId))
                .toList();
        if (new HashSet<>(itens.stream().map(BoxOfficeSaleRequestDTO.Item::seatId).toList()).size() != itens.size()) {
            throw new BusinessException("O mesmo lugar foi informado mais de uma vez.");
        }
        checkHalfPriceQuota(session, itens);

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
            String assento = seat.getFila() + "-" + seat.getNumero();
            if (!seat.getRoom().getId().equals(session.getRoom().getId())) {
                throw new BusinessException("A cadeira " + assento + " não é da sala desta sessão.");
            }
            if (ticketRepository.existsBySessionIdAndSeatIdAndStatusIn(session.getId(), seat.getId(), OCCUPYING)) {
                throw new ConflictException("A cadeira " + assento + " já está ocupada.");
            }

            Ticket ticket = new Ticket();
            ticket.setSession(session);
            ticket.setSeat(seat);
            ticket.setUser(buyer.orElse(null));
            ticket.setClienteEmail(email);
            ticket.setTicketType(item.ticketType());
            ticket.setDocumentoMeia(studentDocument(item, assento));
            ticket.setValorPago(priceFor(session, item.ticketType()));
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
                buyer.isPresent(),
                saved.stream()
                        .map(ticket -> new BoxOfficeSaleResponseDTO.Ingresso(
                                ticket.getId(),
                                ticket.getSeat().getFila() + "-" + ticket.getSeat().getNumero(),
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
        if (ticket.getCanal() != TicketChannel.BALCAO
                || !ProgramacaoService.cinemaIdOf(seller).equals(ticket.getSession().getRoom().getCinema().getId())) {
            throw new ForbiddenException("Só é possível imprimir ingressos vendidos no balcão deste cinema.");
        }
        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new BusinessException("Ingresso cancelado não pode ser impresso.");
        }
        return pdfService.generateTicketPdf(ticket);
    }

    private void checkHalfPriceQuota(Session session, List<BoxOfficeSaleRequestDTO.Item> itens) {
        long requested = itens.stream().filter(item -> HALF_PRICE.contains(item.ticketType())).count();
        if (requested == 0) {
            return;
        }
        int capacity = session.getRoom().getCapacidade() == null ? 0 : session.getRoom().getCapacidade();
        long quota = (long) Math.floor(capacity * halfPriceQuota);
        long sold = ticketRepository.countBySessionIdAndTicketTypeInAndStatusIn(session.getId(), HALF_PRICE, OCCUPYING);
        if (sold + requested > quota) {
            throw new ConflictException("Cota de meia-entrada insuficiente para esta sessão: restam "
                    + Math.max(0, quota - sold) + " de " + quota + ". A inteira continua disponível.");
        }
    }

    /** Estudante: número da carteira, conferido na entrada. Idoso: documento conferido na hora, no caixa. */
    private static String studentDocument(BoxOfficeSaleRequestDTO.Item item, String assento) {
        if (item.ticketType() != TicketType.MEIA_ESTUDANTE) {
            return null;
        }
        String documento = item.documentoMeia() == null ? "" : item.documentoMeia().trim().toUpperCase(Locale.ROOT);
        if (documento.length() < MIN_STUDENT_DOCUMENT) {
            throw new BusinessException("Informe o número da carteira de estudante da cadeira " + assento + ".");
        }
        return documento;
    }

    private static BigDecimal priceFor(Session session, TicketType type) {
        return type == TicketType.INTEIRA
                ? session.getValorBase()
                : session.getValorBase().divide(new BigDecimal("2"), RoundingMode.HALF_UP);
    }
}
