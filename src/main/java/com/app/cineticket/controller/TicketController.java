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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final RateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<TicketResponseDTO> buyTicket(@AuthenticationPrincipal User loggedUser,
                                                       @RequestBody @Valid TicketRequestDTO requestDTO) {

        Bucket bucket = rateLimitService.getUserBucket("purchase:" + loggedUser.getEmail());

        if(!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Você atingiu o limite de compras por minuto. " +
                    "Tente novamente mais tarde."
            );
        }

        TicketResponseDTO responseDTO = ticketService.buyTicket(requestDTO, loggedUser);
        java.net.URI location = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}").buildAndExpand(responseDTO.id()).toUri();
        return ResponseEntity.created(location).body(responseDTO);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@AuthenticationPrincipal User loggedUser, @PathVariable Long id) {
        if (!rateLimitService.getUserBucket("pdf:" + loggedUser.getEmail()).tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de geração de ingressos atingido. Tente novamente mais tarde.");
        }
        byte[] pdfBytes = ticketService.downloadPdf(id, loggedUser);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=ingresso_" + id + ".pdf");
        headers.add("Content-Type", "application/pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);

    }



    @GetMapping("/my-tickets")
    public ResponseEntity<org.springframework.data.domain.Page<TicketResponseDTO>> getMyTickets(
            @AuthenticationPrincipal User loggedUser,
            @org.springdoc.core.annotations.ParameterObject @org.springframework.data.web.PageableDefault(size = 10, page = 0, sort = "id") org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ticketService.findMyTickets(loggedUser, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> findById(@AuthenticationPrincipal User loggedUser, @PathVariable Long id) {
        return ResponseEntity.ok(ticketService.findById(id, loggedUser));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<TicketResponseDTO> cancelTicket(@AuthenticationPrincipal User loggedUser, @PathVariable Long id) {
        TicketResponseDTO responseDTO = ticketService.cancelTicket(id, loggedUser);
        return ResponseEntity.ok(responseDTO);
    }


}
