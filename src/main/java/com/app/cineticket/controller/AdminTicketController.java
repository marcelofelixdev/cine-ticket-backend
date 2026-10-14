package com.app.cineticket.controller;

import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.TicketAdminFilter;
import com.app.cineticket.dto.response.SessionSummaryDTO;
import com.app.cineticket.dto.response.TicketAdminDTO;
import com.app.cineticket.service.TicketAdminService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Ingressos da rede toda (só ADMIN). */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTicketController {

    private final TicketAdminService ticketAdminService;

    @Operation(summary = "Ingressos da rede com filtros (ADMIN)")
    @GetMapping("/tickets")
    public ResponseEntity<Page<TicketAdminDTO>> list(
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketChannel canal,
            @RequestParam(required = false) String email,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        TicketAdminFilter filter = new TicketAdminFilter(cinemaId, sessionId, date, status, canal, email);
        return ResponseEntity.ok(ticketAdminService.list(filter, null, pageable));
    }

    @Operation(summary = "Resumo de qualquer sessão: ocupação, meias e receita (ADMIN)")
    @GetMapping("/sessions/{id}/summary")
    public ResponseEntity<SessionSummaryDTO> summary(@PathVariable Long id) {
        return ResponseEntity.ok(ticketAdminService.summary(id, null));
    }
}
