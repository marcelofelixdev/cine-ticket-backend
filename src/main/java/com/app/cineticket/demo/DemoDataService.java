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
import com.app.cineticket.service.MovieTmdbEnricher;
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

    private static final Set<DayOfWeek> WEEKEND = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final MovieRepository movieRepository;
    private final com.app.cineticket.repository.CinemaMovieRepository cinemaMovieRepository;
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
    private final Set<String> bootstrapTeamEmails;

    public DemoDataService(MovieRepository movieRepository,
                           com.app.cineticket.repository.CinemaMovieRepository cinemaMovieRepository,
                           CinemaRepository cinemaRepository,
                           RoomRepository roomRepository, RoomService roomService,
                           SessionRepository sessionRepository, TicketRepository ticketRepository,
                           RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                           RoleRepository roleRepository, PasswordEncoder passwordEncoder, TmdbClient tmdbClient,
                           @Value("${app.demo.visitor.email}") String visitorEmail,
                           @Value("${app.demo.visitor.password}") String visitorPassword,
                           @Value("${app.bootstrap.staff.email:}") String staffEmail,
                           @Value("${app.bootstrap.manager.email:}") String managerEmail) {
        this.movieRepository = movieRepository;
        this.cinemaMovieRepository = cinemaMovieRepository;
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
        this.bootstrapTeamEmails = java.util.stream.Stream.of(staffEmail, managerEmail)
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
        seedProgramacao();
        linkTeamWithoutCinema();
        seedVisitor();
        int created = createMissingSessions(LocalDateTime.now(AppTimeZone.ZONE));
        log.info("[DEMO] Ambiente pronto: {} filme(s), {} cinema(s), {} sessão(ões) nova(s).",
                movieRepository.count(), cinemaRepository.count(), created);
    }

    /**
     * Reset diário: apaga ingressos, sessões e as contas criadas por visitantes (inclusive
     * funcionários cadastrados pelo gerente público), mantém filmes, cinemas, admins, a equipe
     * de exemplo e o visitante, e recria a programação.
     */
    @Transactional
    public void reset() {
        ticketRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();

        List<User> visitorAccounts = userRepository.findAll().stream()
                .filter(user -> !isAdmin(user)
                        && !bootstrapTeamEmails.contains(user.getEmail().toLowerCase(Locale.ROOT))
                        && !visitorEmail.equalsIgnoreCase(user.getEmail()))
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
                int slotIndex = 0;
                for (DemoSchedulePlanner.Slot slot : DemoSchedulePlanner.planDay(day, durations, r, now)) {
                    Movie movie = movies.get(slot.movieIndex());
                    Session session = new Session();
                    session.setMovie(movie);
                    session.setRoom(room);
                    session.setHorarioInicio(slot.start());
                    session.setValorBase(priceFor(room, day));
                    session.setAtivo(true);
                    session.setFormato(room.getNome().contains("IMAX") ? "3D" : "2D");
                    session.setAudio(audioFor(movie, slotIndex++));
                    sessionRepository.save(session);
                    created++;
                }
            }
        }
        return created;
    }

    /**
     * Cria os filmes que faltam e completa a ficha dos que nunca foram sincronizados com o TMDB
     * (inclusive os criados antes da V14). Sem TMDB, usa o plano B do catálogo local.
     */
    private void seedMovies() {
        boolean tmdbAvailable = true;
        for (DemoCatalog.DemoMovie demoMovie : DemoCatalog.MOVIES) {
            String tmdbId = String.valueOf(demoMovie.tmdbId());
            Movie movie = movieRepository.findByTmdbIdIn(List.of(tmdbId)).stream().findFirst().orElse(null);
            if (movie != null && movie.getTmdbSincronizadoEm() != null) {
                continue;
            }
            if (movie == null) {
                movie = new Movie();
                movie.setTmdbId(tmdbId);
                movie.setTitulo(demoMovie.titulo());
                movie.setDuracaoEmMinutos(demoMovie.duracaoMinutos());
            }

            if (tmdbAvailable) {
                try {
                    enrichFromTmdb(movie, tmdbClient.getMovieDetails(demoMovie.tmdbId()));
                } catch (Exception e) {
                    // Se o TMDB não responde, não insiste filme a filme: usa o plano B para todos.
                    tmdbAvailable = false;
                    log.warn("[DEMO] TMDB indisponível ({}). Usando os dados do catálogo local.", e.getMessage());
                }
            }
            if (movie.getDiretor() == null) {
                movie.setDiretor(demoMovie.diretor());
            }
            if (movie.getGeneros() == null) {
                movie.setGeneros(demoMovie.generos());
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
        if (details.runtime() != null && details.runtime() > 0) {
            movie.setDuracaoEmMinutos(details.runtime());
        }
        MovieTmdbEnricher.apply(movie, details, LocalDateTime.now(AppTimeZone.ZONE));
    }

    private void seedCinemas() {
        for (DemoCatalog.DemoCinema demoCinema : DemoCatalog.CINEMAS) {
            if (cinemaRepository.existsByCnpj(demoCinema.cnpj())) {
                // Cinemas criados antes da V16 ganham cidade e UF.
                cinemaRepository.findAll().stream()
                        .filter(existing -> demoCinema.cnpj().equals(existing.getCnpj()) && existing.getCidade() == null)
                        .forEach(existing -> {
                            existing.setCidade(demoCinema.cidade());
                            existing.setUf(demoCinema.uf());
                        });
                continue;
            }
            Cinema cinema = new Cinema();
            cinema.setNome(demoCinema.nome());
            cinema.setCnpj(demoCinema.cnpj());
            cinema.setEndereco(demoCinema.endereco());
            cinema.setCidade(demoCinema.cidade());
            cinema.setUf(demoCinema.uf());
            Long cinemaId = cinemaRepository.save(cinema).getId();

            for (DemoCatalog.DemoRoom demoRoom : demoCinema.salas()) {
                roomService.create(new RoomRequestDTO(demoRoom.nome(), demoRoom.capacidade(), cinemaId));
            }
        }
    }

    /** No demo, todo filme está em cartaz em todos os cinemas. */
    private void seedProgramacao() {
        List<Cinema> cinemas = cinemaRepository.findAll(Sort.by("id"));
        for (Movie movie : movieRepository.findAll(Sort.by("id"))) {
            for (Cinema cinema : cinemas) {
                if (cinemaMovieRepository.findByCinemaIdAndMovieId(cinema.getId(), movie.getId()).isPresent()) {
                    continue;
                }
                com.app.cineticket.domain.entity.CinemaMovie entry = new com.app.cineticket.domain.entity.CinemaMovie();
                entry.setCinema(cinema);
                entry.setMovie(movie);
                entry.setStatus(com.app.cineticket.domain.enums.ProgramacaoStatus.ATIVO);
                entry.setConfirmedAt(LocalDateTime.now(AppTimeZone.ZONE));
                cinemaMovieRepository.save(entry);
            }
        }
    }

    /** A equipe criada no boot antes dos cinemas existirem passa a pertencer ao primeiro cinema. */
    private void linkTeamWithoutCinema() {
        cinemaRepository.findFirstByOrderByIdAsc().ifPresent(cinema ->
                userRepository.findTeamWithoutCinema().forEach(user -> user.setCinema(cinema)));
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

    /** Animação dublada, filme brasileiro nacional, os demais alternando dublado e legendado. */
    private static com.app.cineticket.domain.enums.SessionAudio audioFor(Movie movie, int slotIndex) {
        if (movie.getTmdbId() != null && DemoCatalog.NATIONAL_MOVIES.contains(Long.parseLong(movie.getTmdbId()))) {
            return com.app.cineticket.domain.enums.SessionAudio.NACIONAL;
        }
        if (movie.getGeneros() != null && movie.getGeneros().contains("Animação")) {
            return com.app.cineticket.domain.enums.SessionAudio.DUBLADO;
        }
        return slotIndex % 2 == 0
                ? com.app.cineticket.domain.enums.SessionAudio.DUBLADO
                : com.app.cineticket.domain.enums.SessionAudio.LEGENDADO;
    }

    private static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getNome()));
    }
}
