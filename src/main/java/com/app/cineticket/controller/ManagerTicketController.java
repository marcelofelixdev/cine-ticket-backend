package com.app.cineticket.controller;

import com.app.cineticket.demo.DemoRestrictions;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.dto.request.TicketAdminFilter;
import com.app.cineticket.dto.response.SessionSummaryDTO;
import com.app.cineticket.dto.response.TicketAdminDTO;
import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.service.CinemaScope;
import com.app.cineticket.service.TicketAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Ingressos do cinema do gerente logado (só MANAGER). */
@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
public class ManagerTicketController {

    private final TicketAdminService ticketAdminService;
    private final DemoRestrictions demoRestrictions;

    @Operation(summary = "Ingressos do cinema com filtros (MANAGER ou ADMIN)", description = CinemaScope.CINEMA_ID_DOC)
    @GetMapping("/tickets")
    public ResponseEntity<Page<TicketAdminDTO>> list(
            @AuthenticationPrincipal User loggedUser,
            @RequestParam(required = false) Long cinemaId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketChannel canal,
            @RequestParam(required = false) String email,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        TicketAdminFilter filter = new TicketAdminFilter(null, sessionId, date, status, canal, email);
        return ResponseEntity.ok(ticketAdminService.list(filter, CinemaScope.actingCinemaId(loggedUser, cinemaId), pageable));
    }

    @Operation(summary = "Resumo de uma sessão do cinema: ocupação, meias e receita (MANAGER ou ADMIN)",
            description = CinemaScope.CINEMA_ID_DOC)
    @GetMapping("/sessions/{id}/summary")
    public ResponseEntity<SessionSummaryDTO> summary(@AuthenticationPrincipal User loggedUser, @PathVariable Long id,
                                                     @RequestParam(required = false) Long cinemaId) {
        return ResponseEntity.ok(ticketAdminService.summary(id, CinemaScope.actingCinemaId(loggedUser, cinemaId)));
    }

    @Operation(summary = "Cancela uma venda de balcão e registra a devolução no caixa (MANAGER ou ADMIN)",
            description = "Só ingressos de balcão do cinema, aprovados, não usados e antes do início da sessão. "
                    + CinemaScope.CINEMA_ID_DOC)
    @ApiResponse(responseCode = "200", description = "Cancelado; reembolso REFUNDED")
    @ApiResponse(responseCode = "403", description = "Ingresso de outro cinema ou demonstração pública", content = @Content)
    @ApiResponse(responseCode = "409", description = "Já cancelado ou já usado na entrada", content = @Content)
    @ApiResponse(responseCode = "422", description = "Ingresso online ou sessão já começou", content = @Content)
    @PostMapping("/tickets/{id}/cancel")
    public ResponseEntity<TicketResponseDTO> cancel(@AuthenticationPrincipal User loggedUser, @PathVariable Long id,
                                                    @RequestParam(required = false) Long cinemaId) {
        demoRestrictions.requireNotDemo("Cancelar vendas");
        return ResponseEntity.ok(ticketAdminService.cancelCounterSale(id, CinemaScope.actingCinemaId(loggedUser, cinemaId),
                loggedUser));
    }
}
