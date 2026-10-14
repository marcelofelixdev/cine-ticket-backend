package com.app.cineticket.config;

import com.app.cineticket.security.RestAccessDeniedHandler;
import com.app.cineticket.security.RestAuthenticationEntryPoint;
import com.app.cineticket.security.SecurityFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /** Libera /actuator/prometheus sem login (só no dev/docker-compose, para o Prometheus local). */
    @Value("${app.metrics.public:false}")
    private boolean metricsPublic;

    private AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry permitMetricsIfPublic(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        return metricsPublic ? auth.requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll() : auth;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                // CSRF do Spring desligado de propósito (revisado no SonarQube Cloud, regra java:S4502):
                // - a API é stateless e se autentica pelo header Authorization: Bearer, que o navegador
                //   não envia sozinho em requisições de outro site.
                // - As únicas rotas que usam cookie, refresh e logout, validam o header Origin contra
                //   a lista do CORS. Veja o método rejectForeignOrigin do AuthController e o AuthFlowTest.
                // - O token CSRF em cookie não funcionaria com o frontend em outro domínio, que não
                //   consegue ler os cookies da API.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> permitMetricsIfPublic(auth)
                        // Login, refresh e logout: o refresh/logout se autenticam pelo cookie, não pelo access token.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        // Catálogo público: visitantes navegam por filmes, cinemas e sessões sem login.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/movies", "/api/v1/movies/*",
                                "/api/v1/cinemas", "/api/v1/cinemas/*",
                                "/api/v1/sessions", "/api/v1/sessions/*", "/api/v1/sessions/*/seats",
                                "/api/v1/demo/info").permitAll()
                        // Webhook do Stripe: sem JWT, autenticado pela assinatura Stripe-Signature.
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
                        // Documentação da API (contrato oficial para o frontend).
                        .requestMatchers(HttpMethod.GET,
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/cinemas", "/api/v1/movies", "/api/v1/rooms", "/api/v1/sessions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/movies/*/tmdb-sync").hasRole("ADMIN")
                        // Busca no TMDB (gasta a cota da nossa chave): só quem cadastra ou pede filmes.
                        .requestMatchers(HttpMethod.GET, "/api/v1/tmdb/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/cinemas/**", "/api/v1/movies/**", "/api/v1/rooms/**", "/api/v1/sessions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/cinemas/**", "/api/v1/movies/**", "/api/v1/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sessions/**").hasRole("ADMIN")
                        // Entrada na sala: funcionário (STAFF) ou admin. STAFF não tem acesso a mais nada administrativo.
                        .requestMatchers(HttpMethod.POST, "/api/v1/tickets/validate").hasAnyRole("STAFF", "ADMIN")
                        // Equipe: o admin cuida da rede toda; o gerente, só do próprio cinema (checado no serviço).
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/manager/**").hasRole("MANAGER")
                        // Caixa: funcionário e gerente, só no próprio cinema (checado no serviço).
                        .requestMatchers("/api/v1/box-office/**").hasAnyRole("STAFF", "MANAGER")
                        .anyRequest().authenticated()
                )

                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
