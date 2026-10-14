package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    /** Equipe (usuários com algum dos papéis) de todos os cinemas, com o cinema carregado. */
    @Query("""
            select distinct u from User u left join fetch u.cinema join u.roles r
            where r.nome in :roles order by u.nome""")
    List<User> findTeam(@Param("roles") Collection<String> roles);

    /** Equipe de um cinema, com o cinema carregado. */
    @Query("""
            select distinct u from User u join fetch u.cinema c join u.roles r
            where r.nome in :roles and c.id = :cinemaId order by u.nome""")
    List<User> findTeamByCinema(@Param("roles") Collection<String> roles, @Param("cinemaId") Long cinemaId);

    /** Gerentes e funcionários ainda sem cinema (vinculados no boot pelo bootstrap ou pelo demo). */
    @Query("""
            select distinct u from User u join u.roles r
            where u.cinema is null and r.nome in ('ROLE_MANAGER', 'ROLE_STAFF')""")
    List<User> findTeamWithoutCinema();
}


