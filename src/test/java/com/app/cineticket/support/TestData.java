package com.app.cineticket.support;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.Ticket;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.FormaPagamento;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.domain.enums.TicketChannel;
import com.app.cineticket.domain.enums.TicketStatus;
import com.app.cineticket.domain.enums.TicketType;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.MovieRequestRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import com.app.cineticket.service.HalfPricePolicy;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Montagem de dados dos testes de integração (cinema, sala, filme, sessão, equipe).
 * Registrado na classe base: todos os testes de integração podem injetar.
 */
@TestComponent
public class TestData {

    private static final BigDecimal SESSION_PRICE = new BigDecimal("30.00");

    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final MovieRepository movieRepository;
    private final SessionRepository sessionRepository;
    private final CinemaMovieRepository cinemaMovieRepository;
    private final MovieRequestRepository movieRequestRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    @SuppressWarnings("java:S107") // um repositório por entidade montada
    public TestData(CinemaRepository cinemaRepository, RoomRepository roomRepository, SeatRepository seatRepository,
                    MovieRepository movieRepository, SessionRepository sessionRepository,
                    CinemaMovieRepository cinemaMovieRepository, MovieRequestRepository movieRequestRepository,
                    TicketRepository ticketRepository, UserRepository userRepository, RoleRepository roleRepository,
                    TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.cinemaRepository = cinemaRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.sessionRepository = sessionRepository;
        this.cinemaMovieRepository = cinemaMovieRepository;
        this.movieRequestRepository = movieRequestRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    public Cinema cinema(String nome, String cnpj) {
        return cinema(nome, cnpj, null, null);
    }

    public Cinema cinema(String nome, String cnpj, String cidade, String uf) {
        Cinema cinema = new Cinema();
        cinema.setNome(nome);
        cinema.setCnpj(cnpj);
        cinema.setEndereco("Endereço de " + nome);
        cinema.setCidade(cidade);
        cinema.setUf(uf);
        return cinemaRepository.save(cinema);
    }

    public Room room(String nome, Cinema cinema, int capacidade) {
        Room room = new Room();
        room.setNome(nome);
        room.setCapacidade(capacidade);
        room.setCinema(cinema);
        return roomRepository.save(room);
    }

    public Seat seat(Room room, String fila, int numero) {
        Seat seat = new Seat();
        seat.setFila(fila);
        seat.setNumero(numero);
        seat.setRoom(room);
        return seatRepository.save(seat);
    }

    /** Assentos numerados de 1 a {@code quantidade} numa fila. */
    public List<Seat> seats(Room room, String fila, int quantidade) {
        List<Seat> seats = new ArrayList<>();
        for (int numero = 1; numero <= quantidade; numero++) {
            seats.add(seat(room, fila, numero));
        }
        return seats;
    }

    public Movie movie(String titulo, int duracaoEmMinutos) {
        Movie movie = new Movie();
        movie.setTitulo(titulo);
        movie.setDuracaoEmMinutos(duracaoEmMinutos);
        return movieRepository.save(movie);
    }

    /** Sessão ativa de R$ 30,00. */
    public Session session(Movie movie, Room room, LocalDateTime start) {
        return session(movie, room, start, SESSION_PRICE);
    }

    public Session session(Movie movie, Room room, LocalDateTime start, BigDecimal valorBase) {
        Session session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(start);
        session.setValorBase(valorBase);
        session.setAtivo(true);
        return sessionRepository.save(session);
    }

    /** Ingresso inteira no valor da sessão; PENDING com prazo de pagamento de 10 minutos. */
    public Ticket ticket(Session session, Seat seat, User user, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setUser(user);
        ticket.setTicketType(TicketType.INTEIRA);
        ticket.setValorPago(HalfPricePolicy.priceFor(session.getValorBase(), TicketType.INTEIRA));
        ticket.setStatus(status);
        if (status == TicketStatus.PENDING) {
            ticket.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        }
        return ticketRepository.save(ticket);
    }

    /** Venda de balcão aprovada (sem conta de cliente, a não ser pelo e-mail informado). */
    public Ticket counterTicket(Session session, Seat seat, TicketType type, FormaPagamento forma, User seller,
                                String clienteEmail) {
        Ticket ticket = new Ticket();
        ticket.setSession(session);
        ticket.setSeat(seat);
        ticket.setTicketType(type);
        ticket.setValorPago(HalfPricePolicy.priceFor(session.getValorBase(), type));
        ticket.setStatus(TicketStatus.APPROVED);
        ticket.setCanal(TicketChannel.BALCAO);
        ticket.setFormaPagamento(forma);
        ticket.setVendidoPor(seller);
        ticket.setClienteEmail(clienteEmail);
        return ticketRepository.save(ticket);
    }

    public CinemaMovie program(Cinema cinema, Movie movie, ProgramacaoStatus status) {
        CinemaMovie entry = new CinemaMovie();
        entry.setCinema(cinema);
        entry.setMovie(movie);
        entry.setStatus(status);
        return cinemaMovieRepository.save(entry);
    }

    /** Usuário com um papel (recriado se outro teste apagou tb_role) e, para a equipe, um cinema. */
    public User user(String email, String role, Cinema cinema) {
        User user = new User();
        user.setNome("Pessoa " + email);
        user.setEmail(email);
        user.setSenha("hash-irrelevante");
        user.setRoles(new HashSet<>(Set.of(TestRoles.ensure(roleRepository, role))));
        user.setCinema(cinema);
        return userRepository.save(user);
    }

    /** Cliente comum (ROLE_USER). */
    public User customer(String email) {
        return user(email, "ROLE_USER", null);
    }

    /** Cliente com senha de verdade, para os testes de login. */
    public User customerWithPassword(String email, String rawPassword) {
        User user = customer(email);
        user.setSenha(passwordEncoder.encode(rawPassword));
        return userRepository.save(user);
    }

    public String token(User user) {
        return tokenService.generateToken(user);
    }

    public String token(String email, String role, Cinema cinema) {
        return token(user(email, role, cinema));
    }

    public void deleteUsersEndingWith(String suffix) {
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith(suffix))
                .toList());
    }

    public void deleteUsers(String... emails) {
        for (String email : emails) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    /**
     * Limpa o catálogo e as vendas, na ordem das chaves estrangeiras. Papéis (tb_role) ficam:
     * são dados de referência das migrações.
     */
    public void deleteCatalog() {
        ticketRepository.deleteAll();
        movieRequestRepository.deleteAll();
        cinemaMovieRepository.deleteAll();
        sessionRepository.deleteAll();
        seatRepository.deleteAll();
        roomRepository.deleteAll();
        cinemaRepository.deleteAll();
        movieRepository.deleteAll();
    }
}
