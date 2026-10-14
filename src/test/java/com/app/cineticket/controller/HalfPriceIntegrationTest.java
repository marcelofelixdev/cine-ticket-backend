package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Meia-entrada ponta a ponta: perfil com data de nascimento, carteira de estudante e cota de 40%. */
class HalfPriceIntegrationTest extends AbstractIntegrationTest {

    /** Um e-mail por teste: o rate limit de compras (5/min) é por usuário. */
    private String email;

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;

    private Session session;
    private final List<Seat> seats = new ArrayList<>();
    private String token;

    @BeforeEach
    void setUp() {
        cleanUp();
        email = "meia-" + System.nanoTime() + "@teste.com";
        Cinema cinema = new Cinema();
        cinema.setNome("Cine Meia");
        cinema.setCnpj("77777777000177");
        cinema.setEndereco("Rua M, 7");
        cinema = cinemaRepository.save(cinema);

        Room room = new Room();
        room.setNome("Sala M");
        room.setCapacidade(5); // cota de meia: 2 lugares
        room.setCinema(cinema);
        room = roomRepository.save(room);

        seats.clear();
        for (int numero = 1; numero <= 5; numero++) {
            Seat seat = new Seat();
            seat.setFila("A");
            seat.setNumero(numero);
            seat.setRoom(room);
            seats.add(seatRepository.save(seat));
        }

        Movie movie = new Movie();
        movie.setTitulo("Filme da Meia");
        movie.setDuracaoEmMinutos(100);
        movie = movieRepository.save(movie);

        session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(LocalDateTime.now().plusDays(1));
        session.setValorBase(new BigDecimal("40.00"));
        session.setAtivo(true);
        session = sessionRepository.save(session);

        User user = new User();
        user.setNome("Comprador");
        user.setEmail(email);
        user.setSenha("hash-irrelevante");
        token = tokenService.generateToken(userRepository.save(user));
    }

    @AfterEach
    void cleanUp() {
        ticketRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
        if (email != null) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void idosoInformaANascimentoNoPerfilEPagaMeia() throws Exception {
        buy(0, "MEIA_IDOSO", null).andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataNascimento\":\"1950-03-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataNascimento").value("1950-03-10"));

        buy(0, "MEIA_IDOSO", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorPago").value(20.0));
    }

    @Test
    void cotaDeMeiaEsgotaMasAInteiraContinua() throws Exception {
        buy(0, "MEIA_ESTUDANTE", "CIE-0001").andExpect(status().isCreated());
        buy(1, "MEIA_ESTUDANTE", "CIE-0002").andExpect(status().isCreated());

        buy(2, "MEIA_ESTUDANTE", "CIE-0003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Cota de meia-entrada esgotada")));

        buy(2, "INTEIRA", null).andExpect(status().isCreated());
    }

    private ResultActions buy(int seatIndex, String type, String documento) throws Exception {
        String doc = documento == null ? "" : ",\"documentoMeia\":\"" + documento + "\"";
        return mockMvc.perform(post("/api/v1/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":" + session.getId() + ",\"seatId\":" + seats.get(seatIndex).getId()
                        + ",\"ticketType\":\"" + type + "\"" + doc + "}"));
    }
}
