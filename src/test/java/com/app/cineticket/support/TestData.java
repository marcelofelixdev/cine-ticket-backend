package com.app.cineticket.support;

import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.CinemaMovie;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Seat;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.domain.enums.ProgramacaoStatus;
import com.app.cineticket.repository.CinemaMovieRepository;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SeatRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.security.TokenService;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
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
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;

    public TestData(CinemaRepository cinemaRepository, RoomRepository roomRepository, SeatRepository seatRepository,
                    MovieRepository movieRepository, SessionRepository sessionRepository,
                    CinemaMovieRepository cinemaMovieRepository, UserRepository userRepository,
                    RoleRepository roleRepository, TokenService tokenService) {
        this.cinemaRepository = cinemaRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.movieRepository = movieRepository;
        this.sessionRepository = sessionRepository;
        this.cinemaMovieRepository = cinemaMovieRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenService = tokenService;
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

    public Movie movie(String titulo, int duracaoEmMinutos) {
        Movie movie = new Movie();
        movie.setTitulo(titulo);
        movie.setDuracaoEmMinutos(duracaoEmMinutos);
        return movieRepository.save(movie);
    }

    /** Sessão ativa de R$ 30,00. */
    public Session session(Movie movie, Room room, LocalDateTime start) {
        Session session = new Session();
        session.setMovie(movie);
        session.setRoom(room);
        session.setHorarioInicio(start);
        session.setValorBase(SESSION_PRICE);
        session.setAtivo(true);
        return sessionRepository.save(session);
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

    public String token(User user) {
        return tokenService.generateToken(user);
    }

    public String token(String email, String role, Cinema cinema) {
        return token(user(email, role, cinema));
    }

    public void deleteUsersEndingWith(String domain) {
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(user -> user.getEmail().endsWith(domain))
                .toList());
    }

    /** Apaga os cinemas com esses CNPJs junto com salas e assentos (as sessões já devem ter sido apagadas). */
    public void deleteCinemas(Collection<String> cnpjs) {
        cinemaRepository.findAll().stream()
                .filter(cinema -> cnpjs.contains(cinema.getCnpj()))
                .forEach(cinema -> {
                    roomRepository.findByCinemaIdOrderByNome(cinema.getId()).forEach(room -> {
                        seatRepository.deleteAll(seatRepository.findByRoomIdOrderByFilaAscNumeroAsc(room.getId()));
                        roomRepository.delete(room);
                    });
                    cinemaRepository.delete(cinema);
                });
    }
}
