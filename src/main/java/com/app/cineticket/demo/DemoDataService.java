package com.app.cineticket.demo;

import com.app.cineticket.client.TmdbClient;
import com.app.cineticket.config.AppTimeZone;
import com.app.cineticket.domain.entity.Cinema;
import com.app.cineticket.domain.entity.Movie;
import com.app.cineticket.domain.entity.Role;
import com.app.cineticket.domain.entity.Room;
import com.app.cineticket.domain.entity.Session;
import com.app.cineticket.domain.entity.User;
import com.app.cineticket.dto.client.TmdbMovieDetailsDTO;
import com.app.cineticket.dto.request.RoomRequestDTO;
import com.app.cineticket.repository.CinemaRepository;
import com.app.cineticket.repository.MovieRepository;
import com.app.cineticket.repository.RefreshTokenRepository;
import com.app.cineticket.repository.RoleRepository;
import com.app.cineticket.repository.RoomRepository;
import com.app.cineticket.repository.SessionRepository;
import com.app.cineticket.repository.TicketRepository;
import com.app.cineticket.repository.UserRepository;
import com.app.cineticket.service.RoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Popula e mantém o ambiente de demonstração. Tudo é idempotente: rodar de novo
 * não duplica nada.
 */
@Slf4j
@Service
@Profile("demo")
public class DemoDataService {

    private static final String TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500";
    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final SessionRepository sessionRepository;
    private final TicketRepository ticketRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TmdbClient tmdbClient;
    private final String visitorEmail;
    private final String visitorPassword;

    public DemoDataService(MovieRepository movieRepository, CinemaRepository cinemaRepository,
                           RoomRepository roomRepository, RoomService roomService,
                           SessionRepository sessionRepository, TicketRepository ticketRepository,
                           RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                           RoleRepository roleRepository, PasswordEncoder passwordEncoder, TmdbClient tmdbClient,
                           @Value("${app.demo.visitor.email}") String visitorEmail,
                           @Value("${app.demo.visitor.password}") String visitorPassword) {
        this.movieRepository = movieRepository;
        this.cinemaRepository = cinemaRepository;
        this.roomRepository = roomRepository;
        this.roomService = roomService;
        this.sessionRepository = sessionRepository;
        this.ticketRepository = ticketRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tmdbClient = tmdbClient;
        this.visitorEmail = visitorEmail.trim().toLowerCase(Locale.ROOT);
        this.visitorPassword = visitorPassword;
    }

    public String getVisitorEmail() {
        return visitorEmail;
    }

    public String getVisitorPassword() {
        return visitorPassword;
    }

    /** Boot do demo: filmes, cinemas e salas, visitante e sessões dos próximos dias. */
    @Transactional
    public void seed() {
        seedMovies();
        seedCinemas();
        seedVisitor();
        int created = createMissingSessions(LocalDateTime.now(AppTimeZone.ZONE));
        log.info("[DEMO] Ambiente pronto: {} filme(s), {} cinema(s), {} sessão(ões) nova(s).",
                movieRepository.count(), cinemaRepository.count(), created);
    }

    /**
     * Reset diário: apaga ingressos, sessões e as contas criadas por visitantes,
     * mantém filmes, cinemas, admins e o visitante, e recria a programação.
     */
    @Transactional
    public void reset() {
        ticketRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();

        List<User> visitorAccounts = userRepository.findAll().stream()
                .filter(user -> !isAdmin(user) && !visitorEmail.equalsIgnoreCase(user.getEmail()))
                .toList();
        if (!visitorAccounts.isEmpty()) {
            refreshTokenRepository.deleteByUserIdIn(visitorAccounts.stream().map(User::getId).toList());
            userRepository.deleteAll(visitorAccounts);
        }

        userRepository.findByEmailIgnoreCase(visitorEmail).ifPresent(visitor -> {
            visitor.setFailedLoginAttempts(0);
            visitor.setLockedUntil(null);
        });

        int created = createMissingSessions(LocalDateTime.now(AppTimeZone.ZONE));
        log.info("[DEMO] Reset diário: {} conta(s) de visitantes removida(s), {} sessão(ões) recriada(s).",
                visitorAccounts.size(), created);
    }

    /**
     * Garante a programação de hoje até {@link DemoCatalog#DAYS_AHEAD} dias à frente.
     * Roda dentro da transação de {@link #seed()} ou {@link #reset()}.
     */
    private int createMissingSessions(LocalDateTime now) {
        List<Movie> movies = movieRepository.findAll(Sort.by("id"));
        List<Integer> durations = movies.stream().map(Movie::getDuracaoEmMinutos).toList();
        List<Room> rooms = roomRepository.findAll(Sort.by("id"));
        int created = 0;

        for (int d = 0; d < DemoCatalog.DAYS_AHEAD; d++) {
            LocalDate day = now.toLocalDate().plusDays(d);
            for (int r = 0; r < rooms.size(); r++) {
                Room room = rooms.get(r);
                boolean alreadyScheduled = sessionRepository.existsByRoomIdAndHorarioInicioBetween(
                        room.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay().minusNanos(1));
                if (alreadyScheduled) {
                    continue;
                }
                for (DemoSchedulePlanner.Slot slot : DemoSchedulePlanner.planDay(day, durations, r, now)) {
                    Session session = new Session();
                    session.setMovie(movies.get(slot.movieIndex()));
                    session.setRoom(room);
                    session.setHorarioInicio(slot.start());
                    session.setValorBase(priceFor(room, day));
                    session.setAtivo(true);
                    sessionRepository.save(session);
                    created++;
                }
            }
        }
        return created;
    }

    private void seedMovies() {
        boolean tmdbAvailable = true;
        for (DemoCatalog.DemoMovie demoMovie : DemoCatalog.MOVIES) {
            String tmdbId = String.valueOf(demoMovie.tmdbId());
            if (movieRepository.existsByTmdbId(tmdbId)) {
                continue;
            }

            Movie movie = new Movie();
            movie.setTmdbId(tmdbId);
            movie.setTitulo(demoMovie.titulo());
            movie.setDuracaoEmMinutos(demoMovie.duracaoMinutos());

            if (tmdbAvailable) {
                try {
                    enrichFromTmdb(movie, tmdbClient.getMovieDetails(demoMovie.tmdbId()));
                } catch (Exception e) {
                    // Se o TMDB não responde, não insiste filme a filme: usa o plano B para todos.
                    tmdbAvailable = false;
                    log.warn("[DEMO] TMDB indisponível ({}). Usando título e duração do catálogo local.",
                            e.getMessage());
                }
            }
            movieRepository.save(movie);
        }
    }

    private static void enrichFromTmdb(Movie movie, TmdbMovieDetailsDTO details) {
        if (details == null) {
            return;
        }
        if (details.title() != null && !details.title().isBlank()) {
            movie.setTitulo(details.title().length() > 150 ? details.title().substring(0, 150) : details.title());
        }
        movie.setSinopse(details.overview());
        if (details.poster_path() != null) {
            movie.setPosterUrl(TMDB_POSTER_BASE + details.poster_path());
        }
        if (details.runtime() != null && details.runtime() > 0) {
            movie.setDuracaoEmMinutos(details.runtime());
        }
    }

    private void seedCinemas() {
        for (DemoCatalog.DemoCinema demoCinema : DemoCatalog.CINEMAS) {
            if (cinemaRepository.existsByCnpj(demoCinema.cnpj())) {
                continue;
            }
            Cinema cinema = new Cinema();
            cinema.setNome(demoCinema.nome());
            cinema.setCnpj(demoCinema.cnpj());
            cinema.setEndereco(demoCinema.endereco());
            Long cinemaId = cinemaRepository.save(cinema).getId();

            for (DemoCatalog.DemoRoom demoRoom : demoCinema.salas()) {
                roomService.create(new RoomRequestDTO(demoRoom.nome(), demoRoom.capacidade(), cinemaId));
            }
        }
    }

    private void seedVisitor() {
        if (userRepository.existsByEmailIgnoreCase(visitorEmail)) {
            return;
        }
        Role userRole = roleRepository.findByNome("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Role ROLE_USER não encontrada."));
        User visitor = new User();
        visitor.setNome("Visitante");
        visitor.setEmail(visitorEmail);
        visitor.setSenha(passwordEncoder.encode(visitorPassword));
        visitor.setRoles(new HashSet<>(Set.of(userRole)));
        userRepository.save(visitor);
    }

    private static BigDecimal priceFor(Room room, LocalDate day) {
        BigDecimal base = DemoCatalog.CINEMAS.stream()
                .filter(cinema -> cinema.nome().equals(room.getCinema().getNome()))
                .flatMap(cinema -> cinema.salas().stream())
                .filter(demoRoom -> demoRoom.nome().equals(room.getNome()))
                .map(DemoCatalog.DemoRoom::precoBase)
                .findFirst()
                .orElse(new BigDecimal("30.00"));
        return WEEKEND.contains(day.getDayOfWeek()) ? base.add(DemoCatalog.WEEKEND_SURCHARGE) : base;
    }

    private static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getNome()));
    }
}
