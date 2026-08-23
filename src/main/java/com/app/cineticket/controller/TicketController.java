package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.service.RateLimitService;
import com.app.cineticket.service.TicketService;
import com.app.cineticket.service.PdfService;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final RateLimitService rateLimitService;
    private final PdfService pdfService;

    @PostMapping
    public ResponseEntity<TicketResponseDTO> buyTicket(@RequestBody @Valid TicketRequestDTO requestDTO) {

        String emailUsuario = SecurityContextHolder.getContext().getAuthentication().getName();

        Bucket bucket = rateLimitService.getUserBucket(emailUsuario);

        if(!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Você atingiu o limite de compras por minuto. " +
                    "Tente novamente mais tarde."
            );
        }

        TicketResponseDTO responseDTO = ticketService.buyTicket(requestDTO);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingresso não encontrado."));

        if (ticket.getStatus() != TicketStatus.APPROVED) {
            throw new RuntimeException("Ingresso não aprovado ainda.");
        }

        byte[] pdfBytes = pdfService.generateTicketPdf(ticket);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=ingresso_" + id + ".pdf");
        headers.add("Content-Type", "application/pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

    }

    @GetMapping("/me")
    public ResponseEntity<List<TicketResponseDTO>> getMyTickets() {
        return ResponseEntity.ok(ticketService.getMyTickets());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelMyTicket(@PathVariable Long id) {
        ticketService.cancelMyTicket(id);
        return ResponseEntity.noContent().build();
    }

}
