package com.app.cineticket.domain.entity;

import org.springframework.security.core.userdetails.UserDetails;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "tb_user")
public class User extends AuditableEntity implements org.springframework.security.core.userdetails.UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String senha;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "tb_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /** Senhas erradas seguidas desde o último login certo ou bloqueio. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** Login bloqueado até este instante (proteção contra força bruta). */
    @Column(name = "locked_until")
    private java.time.LocalDateTime lockedUntil;

    /** Opcional; exigida para comprar meia-entrada de idoso. */
    @Column(name = "data_nascimento")
    private java.time.LocalDate dataNascimento;

    public boolean isLockedAt(java.time.LocalDateTime instant) {
        return lockedUntil != null && lockedUntil.isAfter(instant);
    }

    @Override
    public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities()
    {
        return this.roles.stream()
                .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(role.getNome()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return !isLockedAt(java.time.LocalDateTime.now()); }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
