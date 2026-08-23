package com.app.cineticket.controller;

import com.app.cineticket.dto.response.TicketResponseDTO;
import com.app.cineticket.service.RateLimitService;
import com.app.cineticket.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TicketController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private com.app.cineticket.security.TokenService tokenService;

    @MockBean
    private com.app.cineticket.repository.UserRepository userRepository;

    @Test
    void shouldReturnMyTickets() throws Exception {
        com.app.cineticket.domain.entity.User fakeUser = new com.app.cineticket.domain.entity.User();
        fakeUser.setId(1L);
        
        org.springframework.security.core.Authentication auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(fakeUser, null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        TicketResponseDTO t1 = new TicketResponseDTO(1L, "APPROVED", "Matrix", "Sala 1", "A1", new java.math.BigDecimal("25.00"), com.app.cineticket.domain.enums.TicketType.INTEIRA);
        org.springframework.data.domain.Page<TicketResponseDTO> page = new org.springframework.data.domain.PageImpl<>(List.of(t1));
        when(ticketService.findMyTickets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/tickets/my-tickets"))
                .andExpect(status().isOk());
    }
}
