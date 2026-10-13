package com.app.cineticket.repository;

import com.app.cineticket.domain.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Busca com lock e já carrega o usuário: a rotação usa o usuário fora da transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r join fetch r.user where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now, r.updatedAt = :now " +
            "where r.familyId = :familyId and r.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from RefreshToken r where r.user.id in :userIds")
    int deleteByUserIdIn(@Param("userIds") java.util.Collection<Long> userIds);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
