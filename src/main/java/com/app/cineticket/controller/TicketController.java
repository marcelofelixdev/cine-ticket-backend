package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.request.TicketRequestDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.service.RateLimitService;
import com.app.cineticket.service.TicketService;
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
    private final RateLimitService rateLimitService;

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
        byte[] pdfBytes = ticketService.downloadPdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=ingresso_" + id + ".pdf");
        headers.add("Content-Type", "application/pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

    }



    @GetMapping("/my-tickets")
    public ResponseEntity<org.springframework.data.domain.Page<TicketResponseDTO>> getMyTickets(
            @org.springframework.data.web.PageableDefault(size = 10, page = 0, sort = "id") org.springframework.data.domain.Pageable pageable) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var loggedUser = (com.app.cineticket.domain.entity.User) auth.getPrincipal();

        return ResponseEntity.ok(ticketService.findMyTickets(loggedUser, pageable));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<TicketResponseDTO> cancelTicket(@PathVariable Long id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var loggedUser = (User) auth.getPrincipal();

        TicketResponseDTO responseDTO = ticketService.cancelTicket(id, loggedUser);
        return ResponseEntity.ok(responseDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelMyTicket(@PathVariable Long id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        var loggedUser = (User) auth.getPrincipal();
        ticketService.cancelTicket(id, loggedUser);
        return ResponseEntity.noContent().build();
    }

}
