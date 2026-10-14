package com.app.cineticket.controller;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.domain.enums.SessionAudio;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.support.AbstractIntegrationTest;
import com.app.cineticket.support.TestRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sessões criadas pelo gerente no próprio cinema, cidade dos cinemas e "Em breve". */
class ManagerSessionIntegrationTest extends AbstractIntegrationTest {

    private static final String DOMAIN = "@sessoes.teste";
    private static final String CNPJ_A = "46464646000146";
    private static final String CNPJ_B = "47474747000147";

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CinemaRepository cinemaRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private CinemaMovieRepository cinemaMovieRepository;
    @Autowired private SessionRepository sessionRepository;

    private Cinema cinemaA;
    private Cinema cinemaB;
    private Room roomA;
    private Room roomB;
    private Movie emCartaz;
    private Movie foraDaProgramacao;
    private String adminToken;
    private String managerToken;
    private String staffToken;
    private String clientToken;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        cleanUp();
        cinemaA = cinema("Cine Sessão A", CNPJ_A, "São Paulo", "SP");
        cinemaB = cinema("Cine Sessão B", CNPJ_B, "Rio de Janeiro", "RJ");
        roomA = room("Sala A1", cinemaA);
        roomB = room("Sala B1", cinemaB);
        emCartaz = movie("Filme em Cartaz", null);
        foraDaProgramacao = movie("Filme Fora", null);
        program(cinemaA, emCartaz, ProgramacaoStatus.ATIVO);
        program(cinemaB, emCartaz, ProgramacaoStatus.ATIVO);
        day = LocalDate.now().plusDays(2);

        adminToken = token("admin", "ROLE_ADMIN", null);
        managerToken = token("gerente", "ROLE_MANAGER", cinemaA);
        staffToken = token("funcionario", "ROLE_STAFF", cinemaA);
        clientToken = token("cliente", "ROLE_USER", null);
    }

    @AfterEach
    void cleanUp() {
        sessionRepository.deleteAll();
        cinemaMovieRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith(DOMAIN))
                .toList());
        cinemaRepository.findAll().stream()
                .filter(cinema -> Set.of(CNPJ_A, CNPJ_B).contains(cinema.getCnpj()))
                .forEach(cinema -> {
                    roomRepository.deleteAll(roomRepository.findByCinemaIdOrderByNome(cinema.getId()));
                    cinemaRepository.delete(cinema);
                });
    }

    @Test
    void gerenteCriaSessaoNoProprioCinemaComFormatoEAudio() throws Exception {
        perform(post("/api/v1/manager/sessions"), managerToken, sessionJson(emCartaz, roomA, "14:00", "\"3D\"", "\"DUBLADO\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formato").value("3D"))
                .andExpect(jsonPath("$.audio").value("DUBLADO"))
                .andExpect(jsonPath("$.cinemaId").value(cinemaA.getId()));
    }

    @Test
    void semFormatoEAudioUsaOPadrao2DLegendado() throws Exception {
        perform(post("/api/v1/manager/sessions"), managerToken, sessionJson(emCartaz, roomA, "14:00", null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formato").value("2D"))
                .andExpect(jsonPath("$.audio").value("LEGENDADO"));
    }

    @Test
    void salaDeOutroCinemaRecebe403() throws Exception {
        perform(post("/api/v1/manager/sessions"), managerToken, sessionJson(emCartaz, roomB, "14:00", null, null))
                .andExpect(status().isForbidden());
    }

    @Test
    void filmeForaDaProgramacaoRecebe422InclusiveParaOAdmin() throws Exception {
        perform(post("/api/v1/manager/sessions"), managerToken, sessionJson(foraDaProgramacao, roomA, "14:00", null, null))
                .andExpect(status().isUnprocessableEntity());
        perform(post("/api/v1/sessions"), adminToken, sessionJson(foraDaProgramacao, roomA, "14:00", null, null))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void conflitoDeHorarioNaSalaContinuaDando409() throws Exception {
        perform(post("/api/v1/manager/sessions"), managerToken, sessionJson(emCartaz, roomA, "14:00", null, null))
                .andExpect(status().isCreated());
        perform(post("/api/v1/manager/sessions"), managerToken, sessionJson(emCartaz, roomA, "15:00", null, null))
                .andExpect(status().isConflict());
    }

    @Test
    void listaDoDiaTrazSoAsSessoesDoProprioCinemaComOcupacao() throws Exception {
        session(roomA, day.atTime(14, 0));
        session(roomB, day.atTime(14, 0));

        perform(get("/api/v1/manager/sessions").param("date", day.toString()), managerToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomNome").value("Sala A1"))
                .andExpect(jsonPath("$[0].lugares").value(50))
                .andExpect(jsonPath("$[0].ocupados").value(0));

        perform(get("/api/v1/manager/rooms"), managerToken, null)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Sala A1"));
    }

    @Test
    void gerenteCancelaSoSessaoDoProprioCinema() throws Exception {
        Session own = session(roomA, day.atTime(14, 0));
        Session other = session(roomB, day.atTime(14, 0));

        perform(delete("/api/v1/manager/sessions/" + other.getId()), managerToken, null)
                .andExpect(status().isForbidden());
        perform(delete("/api/v1/manager/sessions/" + own.getId()), managerToken, null)
                .andExpect(status().isNoContent());

        assertFalse(sessionRepository.findById(own.getId()).orElseThrow().getAtivo());
    }

    @Test
    void cidadesFiltrosEEmBreve() throws Exception {
        Movie futuro = movie("Filme do Mês Que Vem", LocalDate.now().plusDays(30));
        program(cinemaA, futuro, ProgramacaoStatus.ATIVO);
        Session dublada = session(roomA, day.atTime(14, 0));
        dublada.setAudio(SessionAudio.DUBLADO);
        sessionRepository.save(dublada);
        session(roomA, day.atTime(18, 0));

        mockMvc.perform(get("/api/v1/cinemas/cidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].cidade", hasItem("São Paulo")))
                .andExpect(jsonPath("$[*].cidade", hasItem("Rio de Janeiro")));
        mockMvc.perform(get("/api/v1/cinemas").param("cidade", "são paulo"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].uf").value("SP"));

        mockMvc.perform(get("/api/v1/movies").param("cidade", "São Paulo"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Filme em Cartaz"));
        mockMvc.perform(get("/api/v1/movies/em-breve").param("cidade", "São Paulo"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Filme do Mês Que Vem"));
        mockMvc.perform(get("/api/v1/movies/em-breve").param("cidade", "Rio de Janeiro"))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mockMvc.perform(get("/api/v1/sessions")
                        .param("cinemaId", String.valueOf(cinemaA.getId()))
                        .param("audio", "DUBLADO"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].audio").value("DUBLADO"));
    }

    @Test
    void cinemaExigeCidadeEUf() throws Exception {
        perform(post("/api/v1/cinemas"), adminToken,
                "{\"nome\":\"Cine Sem Cidade\",\"cnpj\":\"48484848000148\",\"endereco\":\"Rua X, 1\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItem("cidade")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("uf")));
    }

    @Test
    void soOGerenteAcessaAsRotasDeSessaoDoCinema() throws Exception {
        for (String token : List.of(adminToken, staffToken, clientToken)) {
            perform(get("/api/v1/manager/sessions"), token, null).andExpect(status().isForbidden());
            perform(get("/api/v1/manager/rooms"), token, null).andExpect(status().isForbidden());
            perform(post("/api/v1/manager/sessions"), token, sessionJson(emCartaz, roomA, "14:00", null, null))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/v1/manager/sessions")).andExpect(status().isUnauthorized());
    }

    private String sessionJson(Movie movie, Room room, String time, String formato, String audio) {
        StringBuilder json = new StringBuilder()
                .append("{\"horarioInicio\":\"").append(day).append('T').append(time).append(":00\"")
                .append(",\"valorBase\":30.00")
                .append(",\"movieId\":").append(movie.getId())
                .append(",\"roomId\":").append(room.getId());
        if (formato != null) {
            json.append(",\"formato\":").append(formato);
        }
        if (audio != null) {
            json.append(",\"audio\":").append(audio);
        }
        return json.append('}').toString();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Cinema cinema(String nome, String cnpj, String cidade, String uf) {
        Cinema cinema = new Cinema();
        cinema.setNome(nome);
        cinema.setCnpj(cnpj);
        cinema.setEndereco("Rua das Sessões, 1");
        cinema.setCidade(cidade);
        cinema.setUf(uf);
        return cinemaRepository.save(cinema);
    }

    private Room room(String nome, Cinema cinema) {
        Room room = new Room();
        room.setNome(nome);
        room.setCapacidade(50);
        room.setCinema(cinema);
        return roomRepository.save(room);
    }

    private Movie movie(String titulo, LocalDate estreia) {
        Movie movie = new Movie();
        movie.setTitulo(titulo);
        movie.setDuracaoEmMinutos(120);
        movie.setDataEstreia(estreia);
        return movieRepository.save(movie);
    }

    private void program(Cinema cinema, Movie movie, ProgramacaoStatus status) {
        CinemaMovie entry = new CinemaMovie();
        entry.setCinema(cinema);
        entry.setMovie(movie);
        entry.setStatus(status);
        cinemaMovieRepository.save(entry);
    }

    private Session session(Room room, LocalDateTime start) {
        Session session = new Session();
        session.setMovie(emCartaz);
        session.setRoom(room);
        session.setHorarioInicio(start);
        session.setValorBase(new BigDecimal("30.00"));
        session.setAtivo(true);
        return sessionRepository.save(session);
    }

    private String token(String login, String role, Cinema cinema) {
        User user = new User();
        user.setNome("Pessoa " + login);
        user.setEmail(login + DOMAIN);
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(TestRoles.ensure(roleRepository, role))));
        user.setCinema(cinema);
        return tokenService.generateToken(userRepository.save(user));
    }
}
